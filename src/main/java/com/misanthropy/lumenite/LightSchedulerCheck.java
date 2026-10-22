package com.misanthropy.lumenite;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class LightSchedulerCheck {
    static void run() throws InterruptedException {
        LightScheduler.Pool pool = new LightScheduler.Pool("check", 4);
        LightScheduler[] schedulers = {new LightScheduler(pool), new LightScheduler(pool)};
        ConcurrentHashMap<Long, Boolean>[] running = new ConcurrentHashMap[]{new ConcurrentHashMap<Long, Boolean>(), new ConcurrentHashMap<Long, Boolean>()};
        AtomicInteger overlaps = new AtomicInteger();
        AtomicInteger crossLevel = new AtomicInteger();
        int tasks = 3000;
        CountDownLatch done = new CountDownLatch(tasks);
        Random random = new Random(7);
        for (int i = 0; i < tasks; i++) {
            int level = i & 1;
            int x = random.nextInt(8);
            int z = random.nextInt(8);
            long key = (long) x << 32 | z;
            schedulers[level].submit(x, z, () -> {
                for (long other : running[level].keySet()) {
                    if (Math.abs((int) (other >> 32) - x) <= 2 && Math.abs((int) other - z) <= 2) {
                        overlaps.incrementAndGet();
                    }
                }
                if (!running[1 - level].isEmpty()) {
                    crossLevel.incrementAndGet();
                }
                running[level].put(key, Boolean.TRUE);
                try {
                    Thread.sleep(0, 200_000);
                } catch (InterruptedException ignored) {
                }
                running[level].remove(key);
                done.countDown();
            });
        }
        if (!done.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("scheduler stalled with " + done.getCount() + " tasks left");
        }
        schedulers[0].close();
        CountDownLatch after = new CountDownLatch(1);
        schedulers[1].submit(0, 0, after::countDown);
        if (!after.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("closing one level stalled the shared pool");
        }
        schedulers[1].close();
        if (overlaps.get() != 0) {
            throw new AssertionError(overlaps.get() + " overlapping windows ran concurrently");
        }
        if (crossLevel.get() == 0) {
            throw new AssertionError("two levels never ran at the same time on the shared pool");
        }
        System.out.println("light scheduler check passed");
    }
}
