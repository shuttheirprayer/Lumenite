package com.misanthropy.lumenite;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

public final class SharedTopMap extends Long2IntOpenHashMap {
    private final SectionTable table = new SectionTable();

    public SharedTopMap(Long2IntOpenHashMap source) {
        super(0);
        defaultReturnValue(source.defaultReturnValue());
        for (Long2IntMap.Entry entry : source.long2IntEntrySet()) {
            table.put(entry.getLongKey(), entry.getIntValue());
        }
    }

    @Override
    public int get(long key) {
        Object value = table.get(key);
        return value == null ? defRetValue : (Integer) value;
    }

    @Override
    public int getOrDefault(long key, int fallback) {
        Object value = table.get(key);
        return value == null ? fallback : (Integer) value;
    }

    @Override
    public boolean containsKey(long key) {
        return table.get(key) != null;
    }

    @Override
    public int put(long key, int value) {
        int old = get(key);
        Object current = table.get(key);
        if (current == null || (Integer) current != value) table.put(key, value);
        return old;
    }

    @Override
    public int remove(long key) {
        int old = get(key);
        table.remove(key);
        return old;
    }

    @Override
    public int size() {
        return table.size();
    }

    @Override
    public boolean isEmpty() {
        return table.size() == 0;
    }

    @Override
    public void clear() {
        snapshot().keySet().forEach((long key) -> table.remove(key));
    }

    public Long2IntOpenHashMap snapshot() {
        Long2IntOpenHashMap copy = new Long2IntOpenHashMap(table.size());
        copy.defaultReturnValue(defRetValue);
        table.forEach((key, value) -> copy.put((long) key, (int) (Integer) value));
        return copy;
    }

    @Override
    public Long2IntOpenHashMap clone() {
        return snapshot();
    }

    @Override
    public FastEntrySet long2IntEntrySet() {
        return snapshot().long2IntEntrySet();
    }

    @Override
    public LongSet keySet() {
        return snapshot().keySet();
    }

    @Override
    public IntCollection values() {
        return snapshot().values();
    }
}
