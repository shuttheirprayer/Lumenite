package com.misanthropy.lumenite;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SectionTableCheck {
    public static void main(String[] args) throws Exception {
        SectionTable table = new SectionTable();
        HashMap<Long, Object> model = new HashMap<>();
        Random random = new Random(42);
        for (int i = 0; i < 2_000_000; i++) {
            long key = random.nextInt(5000) * 0x9E3779B97F4A7C15L;
            int op = random.nextInt(3);
            if (op == 0) { table.remove(key); model.remove(key); }
            else { Object v = new Object(); table.put(key, v); model.put(key, v); }
            if (i % 50_000 == 0) {
                for (int k = 0; k < 5000; k++) { long kk = k * 0x9E3779B97F4A7C15L; assert table.get(kk) == model.get(kk) : "mismatch"; }
                assert table.size() == model.size() : "size " + table.size() + " " + model.size();
            }
        }
        long[] stable = new long[20000];
        Object[] stableValues = new Object[20000];
        for (int i = 0; i < stable.length; i++) { stable[i] = (1L << 40) + i * 7919L; stableValues[i] = new Object(); table.put(stable[i], stableValues[i]); }
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong reads = new AtomicLong();
        Thread[] readers = new Thread[6];
        Throwable[] failure = new Throwable[1];
        for (int r = 0; r < readers.length; r++) {
            readers[r] = new Thread(() -> {
                try {
                    Random rr = new Random();
                    long n = 0;
                    while (!stop.get()) {
                        int i = rr.nextInt(stable.length);
                        if (table.get(stable[i]) != stableValues[i]) throw new AssertionError("stable key lost " + i);
                        n++;
                    }
                    reads.addAndGet(n);
                } catch (Throwable t) { failure[0] = t; }
            });
            readers[r].start();
        }
        long end = System.nanoTime() + 6_000_000_000L;
        long ops = 0;
        while (System.nanoTime() < end) {
            long key = (2L << 40) + random.nextInt(400_000);
            if (random.nextBoolean()) table.put(key, Boolean.TRUE); else table.remove(key);
            ops++;
        }
        stop.set(true);
        for (Thread t : readers) t.join();
        if (failure[0] != null) throw new AssertionError(failure[0]);
        System.out.println("ok writer ops " + ops + " reads " + reads.get() + " size " + table.size());
    }
}
