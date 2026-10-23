package com.misanthropy.lumenite;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.world.level.chunk.DataLayer;

public final class SharedLayerMap extends Long2ObjectOpenHashMap<DataLayer> {
    private final SectionTable table = new SectionTable();

    public SharedLayerMap(Long2ObjectOpenHashMap<DataLayer> source) {
        super(0);
        for (Long2ObjectMap.Entry<DataLayer> entry : source.long2ObjectEntrySet()) {
            table.put(entry.getLongKey(), entry.getValue());
        }
    }

    @Override
    public DataLayer get(long key) {
        return (DataLayer) table.get(key);
    }

    @Override
    public DataLayer getOrDefault(long key, DataLayer fallback) {
        DataLayer layer = get(key);
        return layer == null ? fallback : layer;
    }

    @Override
    public boolean containsKey(long key) {
        return table.get(key) != null;
    }

    @Override
    public DataLayer put(long key, DataLayer layer) {
        DataLayer old = get(key);
        table.put(key, layer);
        return old;
    }

    @Override
    public DataLayer remove(long key) {
        DataLayer old = get(key);
        if (old != null) table.remove(key);
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

    public Long2ObjectOpenHashMap<DataLayer> snapshot() {
        Long2ObjectOpenHashMap<DataLayer> copy = new Long2ObjectOpenHashMap<>(table.size());
        table.forEach((key, value) -> copy.put((long) key, (DataLayer) value));
        return copy;
    }

    @Override
    public Long2ObjectOpenHashMap<DataLayer> clone() {
        return snapshot();
    }

    @Override
    public FastEntrySet<DataLayer> long2ObjectEntrySet() {
        return snapshot().long2ObjectEntrySet();
    }

    @Override
    public LongSet keySet() {
        return snapshot().keySet();
    }

    @Override
    public ObjectCollection<DataLayer> values() {
        return snapshot().values();
    }
}
