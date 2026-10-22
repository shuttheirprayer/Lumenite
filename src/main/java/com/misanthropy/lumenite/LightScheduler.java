package com.misanthropy.lumenite;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

public final class LightScheduler {
    public static final int DEFAULT_THREADS = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 2, 6));
    public static final int DEFAULT_CLIENT_THREADS = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 4, 2));

    public static final Pool SERVER = new Pool("Lumenite light", Integer.getInteger("lumenite.threads", DEFAULT_THREADS));
    public static final Pool CLIENT = new Pool("Lumenite client light", Integer.getInteger("lumenite.clientThreads", DEFAULT_CLIENT_THREADS));

    private record Task(LightScheduler owner, long chunk, Runnable body) {
    }

    private final Pool pool;
    private final ArrayDeque<Task> pending = new ArrayDeque<>();
    private final LongOpenHashSet busy = new LongOpenHashSet();
    private boolean closed;

    public LightScheduler(Pool pool) {
        this.pool = pool;
        pool.register(this);
    }

    void submit(int chunkX, int chunkZ, Runnable body) {
        synchronized (pool) {
            if (closed) {
                return;
            }
            pending.add(new Task(this, ChunkPos.asLong(chunkX, chunkZ), body));
            pool.notifyAll();
        }
    }

    void close() {
        synchronized (pool) {
            closed = true;
            pending.clear();
            pool.unregister(this);
            pool.notifyAll();
        }
    }

    private Task claim() {
        for (Iterator<Task> it = pending.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (windowFree(task.chunk)) {
                it.remove();
                occupy(task.chunk, true);
                return task;
            }
        }
        return null;
    }

    private boolean windowFree(long chunk) {
        int x = ChunkPos.getX(chunk);
        int z = ChunkPos.getZ(chunk);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (busy.contains(ChunkPos.asLong(x + dx, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void occupy(long chunk, boolean occupied) {
        int x = ChunkPos.getX(chunk);
        int z = ChunkPos.getZ(chunk);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = ChunkPos.asLong(x + dx, z + dz);
                if (occupied) {
                    busy.add(key);
                } else {
                    busy.remove(key);
                }
            }
        }
    }

    public static final class Pool {
        private final String name;
        private final int threads;
        private final ArrayList<LightScheduler> queues = new ArrayList<>();
        private int started;
        private int cursor;

        public Pool(String name, int threads) {
            this.name = name;
            this.threads = Math.max(1, threads);
        }

        private synchronized void register(LightScheduler scheduler) {
            queues.add(scheduler);
            while (started < threads) {
                Thread thread = new Thread(this::work, name + " " + started);
                thread.setDaemon(true);
                thread.start();
                started++;
            }
        }

        private void unregister(LightScheduler scheduler) {
            queues.remove(scheduler);
        }

        private void work() {
            while (true) {
                Task task = take();
                if (task == null) {
                    return;
                }
                try {
                    task.body.run();
                } catch (Throwable t) {
                    Lumenite.LOGGER.error("Light task failed", t);
                } finally {
                    synchronized (this) {
                        task.owner.occupy(task.chunk, false);
                        notifyAll();
                    }
                }
            }
        }

        private synchronized Task take() {
            while (true) {
                int count = queues.size();
                for (int i = 0; i < count; i++) {
                    int index = (cursor + i) % count;
                    Task task = queues.get(index).claim();
                    if (task != null) {
                        cursor = (index + 1) % count;
                        return task;
                    }
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }
}
