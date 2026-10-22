package com.misanthropy.lumenite;

import com.misanthropy.lumenite.mixin.ChunkMapAccessor;
import com.misanthropy.lumenite.mixin.LevelLightEngineAccessor;
import com.misanthropy.lumenite.mixin.LightEngineAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public final class LevelLighting {
    private static final int FLUSH_EVERY = 64;

    private final LevelLightEngine engine;
    private final LightEngine<?, ?> blockEngine;
    private final LightEngine<?, ?> skyEngine;
    private final VisibleLight blockVisible;
    private final VisibleLight skyVisible;
    private final LightChunkGetter chunkGetter;
    private final ChunkMap chunkMap;
    private final Consumer<Runnable> engineThread;
    private static final ThreadLocal<LightWindow> WINDOWS = new ThreadLocal<>();
    private final LightScheduler scheduler;
    private final boolean client;
    private final Long2ObjectOpenHashMap<DataLayer> pendingBlock = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<DataLayer> pendingSky = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<IntArrayList> pendingChecks = new Long2ObjectOpenHashMap<>();
    private final LongArrayList newChecks = new LongArrayList();
    private final LongOpenHashSet enabled = new LongOpenHashSet();
    private final Long2LongOpenHashMap receivedBlock = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap receivedSky = new Long2LongOpenHashMap();
    private volatile long received;
    private final List<Publication> receivedPending = new ArrayList<>();
    private final Object outboxLock = new Object();
    private List<Publication> outbox = new ArrayList<>();
    private int queuedTasks;
    private int unflushedTasks;
    private final int minSection;
    private final int minY;
    private final int sectionCount;
    private final LongAdder chunkTasks = new LongAdder();
    private final LongAdder chunkNanos = new LongAdder();
    private final LongAdder blockTasks = new LongAdder();
    private final LongAdder blockNanos = new LongAdder();

    private record Publication(long key, DataLayer data, LightLayer layer, SectionPos pos, long received, int dirty) {
    }

    public LevelLighting(LevelLightEngine engine, LightChunkGetter chunkGetter, ChunkMap chunkMap, LightScheduler scheduler, Consumer<Runnable> engineThread, boolean client) {
        this.engine = engine;
        this.blockEngine = ((LevelLightEngineAccessor) engine).lumenite$blockEngine();
        this.skyEngine = ((LevelLightEngineAccessor) engine).lumenite$skyEngine();
        this.blockVisible = (VisibleLight) ((LightEngineAccessor) blockEngine).lumenite$storage();
        this.skyVisible = skyEngine == null ? null : (VisibleLight) ((LightEngineAccessor) skyEngine).lumenite$storage();
        this.chunkGetter = chunkGetter;
        this.chunkMap = chunkMap;
        this.scheduler = scheduler;
        this.engineThread = engineThread;
        this.client = client;
        LevelHeightAccessor heights = chunkGetter.getLevel();
        this.minSection = heights.getMinSection() - 1;
        this.minY = minSection << 4;
        this.sectionCount = heights.getSectionsCount() + 2;
    }

    private void submit(int chunkX, int chunkZ, Runnable task) {
        synchronized (outboxLock) {
            queuedTasks++;
        }
        scheduler.submit(chunkX, chunkZ, () -> {
            try {
                task.run();
            } finally {
                synchronized (outboxLock) {
                    if (--queuedTasks == 0 || ++unflushedTasks >= FLUSH_EVERY) {
                        flush(null);
                    }
                }
            }
        });
    }

    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean lit) {
        chunk.setLightCorrect(false);
        ChunkPos pos = chunk.getPos();
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        synchronized (enabled) {
            enabled.add(pos.toLong());
        }
        submitLight(chunk, lit, future, true);
        return future;
    }

    private void submitLight(ChunkAccess chunk, boolean lit, CompletableFuture<ChunkAccess> future, boolean retry) {
        ChunkPos pos = chunk.getPos();
        submit(pos.x, pos.z, () -> {
            boolean correct = true;
            try {
                long start = System.nanoTime();
                LightWindow window = window(pos.x, pos.z, chunk);
                try {
                    window.lightCentre(skyEngine != null, !lit);
                    stage(window);
                } finally {
                    window.release();
                }
                chunkTasks.increment();
                chunkNanos.add(System.nanoTime() - start);
            } catch (Throwable t) {
                if (retry) {
                    Lumenite.LOGGER.warn("Lighting chunk {} failed, retrying once", pos, t);
                    submitLight(chunk, lit, future, false);
                    return;
                }
                Lumenite.LOGGER.error("Lighting chunk {} failed twice, it will be relit when it next loads", pos, t);
                correct = false;
            }
            boolean lightCorrect = correct;
            synchronized (outboxLock) {
                flush(() -> {
                    if (!lit) {
                        engine.setLightEnabled(pos, true);
                    }
                    chunk.setLightCorrect(lightCorrect);
                    ((ChunkMapAccessor) chunkMap).lumenite$releaseLightTicket(pos);
                    future.complete(chunk);
                });
            }
        });
    }

    public void checkBlock(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int cell = LightWindow.packCell(pos.getX() - ((chunkX - 1) << 4), pos.getY() - minY, pos.getZ() - ((chunkZ - 1) << 4));
        synchronized (pendingChecks) {
            IntArrayList cells = pendingChecks.get(key);
            if (cells == null) {
                cells = new IntArrayList();
                pendingChecks.put(key, cells);
                newChecks.add(key);
            }
            cells.add(cell);
        }
    }

    public void flushChecks() {
        long[] keys;
        synchronized (pendingChecks) {
            if (newChecks.isEmpty()) {
                return;
            }
            keys = newChecks.toLongArray();
            newChecks.clear();
        }
        for (long key : keys) {
            submitCheck(key);
        }
    }

    private void submitCheck(long key) {
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        submit(chunkX, chunkZ, () -> {
            IntArrayList cells;
            synchronized (pendingChecks) {
                cells = pendingChecks.remove(key);
            }
            runChecks(chunkX, chunkZ, cells, true);
        });
    }

    private void runChecks(int chunkX, int chunkZ, IntArrayList cells, boolean retry) {
        try {
            long start = System.nanoTime();
            LightWindow window = window(chunkX, chunkZ, null);
            try {
                if (!window.chunkPresent[LightWindow.CENTER]) {
                    return;
                }
                window.checkBlocks(cells.elements(), cells.size(), skyEngine != null);
                stage(window);
            } finally {
                window.release();
            }
            blockTasks.add(cells.size());
            blockNanos.add(System.nanoTime() - start);
        } catch (Throwable t) {
            if (retry) {
                Lumenite.LOGGER.warn("Light update in chunk {},{} failed, retrying once", chunkX, chunkZ, t);
                submit(chunkX, chunkZ, () -> runChecks(chunkX, chunkZ, cells, false));
            } else {
                Lumenite.LOGGER.error("Light update in chunk {},{} failed twice, dropping {} positions", chunkX, chunkZ, cells.size(), t);
            }
        }
    }

    public void lightEnabled(ChunkPos pos, boolean value) {
        synchronized (enabled) {
            if (value) {
                enabled.add(pos.toLong());
            } else {
                enabled.remove(pos.toLong());
            }
        }
    }

    public void close() {
        scheduler.close();
    }

    public void sectionLoaded(LightLayer layer, SectionPos pos, DataLayer data) {
        Long2ObjectOpenHashMap<DataLayer> pending = layer == LightLayer.BLOCK ? pendingBlock : pendingSky;
        long key = pos.asLong();
        synchronized (pending) {
            pending.put(key, data);
        }
        synchronized (outboxLock) {
            outbox.add(new Publication(key, data, layer, pos, 0, 0));
            if (queuedTasks == 0) {
                flush(null);
            }
        }
    }

    public void sectionReceived(LightLayer layer, SectionPos pos, DataLayer data) {
        if (!client) {
            return;
        }
        Long2LongOpenHashMap map = layer == LightLayer.BLOCK ? receivedBlock : receivedSky;
        Long2ObjectOpenHashMap<DataLayer> pending = layer == LightLayer.BLOCK ? pendingBlock : pendingSky;
        long key = pos.asLong();
        if (data == null) {
            map.remove(key);
            synchronized (pending) {
                pending.remove(key);
            }
        } else {
            map.put(key, ++received);
            synchronized (pending) {
                pending.put(key, data);
            }
            receivedPending.add(new Publication(key, data, layer, pos, 0, 0));
        }
    }

    public void receivedApplied() {
        for (Publication publication : receivedPending) {
            Long2ObjectOpenHashMap<DataLayer> pending = publication.layer == LightLayer.BLOCK ? pendingBlock : pendingSky;
            synchronized (pending) {
                if (pending.get(publication.key) == publication.data) {
                    pending.remove(publication.key);
                }
            }
        }
        receivedPending.clear();
    }

    public String stats() {
        long chunks = chunkTasks.sum();
        long blocks = blockTasks.sum();
        return chunks + " chunk lights, " + chunkNanos.sum() / 1_000_000 + " ms (" + (chunks == 0 ? 0 : chunkNanos.sum() / chunks / 1000) + " us each); "
                + blocks + " block updates, " + blockNanos.sum() / 1_000_000 + " ms (" + (blocks == 0 ? 0 : blockNanos.sum() / blocks / 1000) + " us each)";
    }

    DataLayer storedLayer(int layer, int x, int section, int z) {
        long key = SectionPos.asLong(x, minSection + section, z);
        Long2ObjectOpenHashMap<DataLayer> pending = layer == LightWindow.BLOCK ? pendingBlock : pendingSky;
        synchronized (pending) {
            DataLayer staged = pending.get(key);
            if (staged != null) {
                return staged;
            }
        }
        VisibleLight visible = layer == LightWindow.BLOCK ? blockVisible : skyVisible;
        return visible == null ? null : visible.lumenite$visibleLayer(key);
    }

    private LightWindow window(int chunkX, int chunkZ, ChunkAccess centre) {
        ChunkTerrain terrain = new ChunkTerrain(this, chunkGetter.getLevel(), chunkX, chunkZ, minY);
        LightWindow window = WINDOWS.get();
        if (window == null || window.sectionCount != sectionCount) {
            window = new LightWindow(terrain, sectionCount);
            WINDOWS.set(window);
        } else {
            window.attach(terrain);
        }
        window.received = received;
        for (int c = 0; c < 9; c++) {
            int cx = chunkX - 1 + c / 3;
            int cz = chunkZ - 1 + c % 3;
            LightChunk chunk = c == LightWindow.CENTER && centre != null ? centre : chunkGetter.getChunkForLighting(cx, cz);
            if (chunk instanceof ChunkAccess access) {
                terrain.attach(c, access);
                window.chunkPresent[c] = true;
                if (skyEngine != null) {
                    synchronized (enabled) {
                        window.skyEnabled[c] = enabled.contains(ChunkPos.asLong(cx, cz));
                    }
                }
            }
        }
        return window;
    }

    private void stage(LightWindow window) {
        ChunkTerrain terrain = (ChunkTerrain) window.terrain;
        for (int c = 0; c < 9; c++) {
            if (!window.chunkPresent[c]) {
                continue;
            }
            int cx = terrain.chunkX - 1 + c / 3;
            int cz = terrain.chunkZ - 1 + c % 3;
            if (!terrain.stillPresent(c, chunkGetter.getChunkForLighting(cx, cz))) {
                continue;
            }
            for (int s = 0; s < sectionCount; s++) {
                int idx = c * sectionCount + s;
                if (!window.stored(idx)) {
                    continue;
                }
                stage(window, LightWindow.BLOCK, idx, LightLayer.BLOCK, pendingBlock, cx, minSection + s, cz);
                if (skyEngine != null) {
                    stage(window, LightWindow.SKY, idx, LightLayer.SKY, pendingSky, cx, minSection + s, cz);
                }
            }
        }
    }

    private void stage(LightWindow window, int windowLayer, int idx, LightLayer layer, Long2ObjectOpenHashMap<DataLayer> pending, int x, int y, int z) {
        int dirty = window.changed(windowLayer, idx);
        if (dirty == 0) {
            return;
        }
        long key = SectionPos.asLong(x, y, z);
        DataLayer dataLayer = new DataLayer(window.owned[windowLayer][idx]);
        synchronized (pending) {
            pending.put(key, dataLayer);
        }
        synchronized (outboxLock) {
            outbox.add(new Publication(key, dataLayer, layer, SectionPos.of(x, y, z), window.received, dirty));
        }
    }

    private void flush(Runnable afterwards) {
        unflushedTasks = 0;
        if (outbox.isEmpty() && afterwards == null) {
            return;
        }
        List<Publication> publications = outbox;
        outbox = new ArrayList<>();
        engineThread.accept(() -> {
            for (Publication publication : publications) {
                if (!client || !superseded(publication)) {
                    target(publication.layer).queueSectionData(publication.key, publication.data);
                }
            }
            blockEngine.runLightUpdates();
            if (skyEngine != null) {
                skyEngine.runLightUpdates();
            }
            for (Publication publication : publications) {
                Long2ObjectOpenHashMap<DataLayer> pending = publication.layer == LightLayer.BLOCK ? pendingBlock : pendingSky;
                synchronized (pending) {
                    if (pending.get(publication.key) == publication.data) {
                        pending.remove(publication.key);
                    }
                }
                if (client) {
                    for (int bit = 0; bit < 27; bit++) {
                        if ((publication.dirty & 1 << bit) != 0) {
                            chunkGetter.onLightUpdate(publication.layer, publication.pos.offset(bit / 9 - 1, bit / 3 % 3 - 1, bit % 3 - 1));
                        }
                    }
                } else {
                    chunkGetter.onLightUpdate(publication.layer, publication.pos);
                }
            }
            if (afterwards != null) {
                afterwards.run();
            }
        });
    }

    private boolean superseded(Publication publication) {
        Long2LongOpenHashMap map = publication.layer == LightLayer.BLOCK ? receivedBlock : receivedSky;
        return map.get(publication.key) > publication.received || chunkGetter.getChunkForLighting(publication.pos.x(), publication.pos.z()) == null;
    }

    private LightEngine<?, ?> target(LightLayer layer) {
        return layer == LightLayer.BLOCK ? blockEngine : skyEngine;
    }
}
