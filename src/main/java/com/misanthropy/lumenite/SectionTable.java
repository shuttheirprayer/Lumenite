package com.misanthropy.lumenite;

import it.unimi.dsi.fastutil.HashCommon;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.BiConsumer;

final class SectionTable {
    private static final Object REMOVED = new Object();
    private static final VarHandle VALUES = MethodHandles.arrayElementVarHandle(Object[].class);

    private static final class Table {
        final long[] keys;
        final Object[] values;
        final int mask;
        int used;

        Table(int capacity) {
            keys = new long[capacity];
            values = new Object[capacity];
            mask = capacity - 1;
        }
    }

    private volatile Table table = new Table(16);
    private int live;

    Object get(long key) {
        Table t = table;
        for (int i = (int) HashCommon.mix(key) & t.mask; ; i = (i + 1) & t.mask) {
            Object value = VALUES.getAcquire(t.values, i);
            if (value == null) return null;
            if (t.keys[i] == key) return value == REMOVED ? null : value;
        }
    }

    void put(long key, Object value) {
        Table t = table;
        int i = (int) HashCommon.mix(key) & t.mask;
        for (; ; i = (i + 1) & t.mask) {
            Object old = t.values[i];
            if (old == null) break;
            if (t.keys[i] == key) {
                if (old == REMOVED) live++;
                VALUES.setRelease(t.values, i, value);
                return;
            }
        }
        if ((t.used + 1) * 2 > t.keys.length) {
            t = rebuild(live + 1);
            i = (int) HashCommon.mix(key) & t.mask;
            while (t.values[i] != null) i = (i + 1) & t.mask;
        }
        t.keys[i] = key;
        VALUES.setRelease(t.values, i, value);
        t.used++;
        live++;
    }

    void remove(long key) {
        Table t = table;
        for (int i = (int) HashCommon.mix(key) & t.mask; ; i = (i + 1) & t.mask) {
            Object old = t.values[i];
            if (old == null) return;
            if (t.keys[i] == key) {
                if (old != REMOVED) {
                    VALUES.setRelease(t.values, i, REMOVED);
                    live--;
                }
                return;
            }
        }
    }

    int size() {
        return live;
    }

    void forEach(BiConsumer<Long, Object> action) {
        Table t = table;
        for (int i = 0; i < t.keys.length; i++) {
            Object value = VALUES.getAcquire(t.values, i);
            if (value != null && value != REMOVED) action.accept(t.keys[i], value);
        }
    }

    private Table rebuild(int expected) {
        Table old = table;
        Table fresh = new Table(Math.max(16, HashCommon.nextPowerOfTwo(expected * 4)));
        for (int j = 0; j < old.keys.length; j++) {
            Object value = old.values[j];
            if (value == null || value == REMOVED) continue;
            int i = (int) HashCommon.mix(old.keys[j]) & fresh.mask;
            while (fresh.values[i] != null) i = (i + 1) & fresh.mask;
            fresh.keys[i] = old.keys[j];
            fresh.values[i] = value;
            fresh.used++;
        }
        table = fresh;
        return fresh;
    }
}
