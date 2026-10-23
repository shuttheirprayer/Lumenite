package com.misanthropy.lumenite.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DataLayerStorageMap.class)
public interface DataLayerStorageMapAccessor {
    @Accessor("map")
    Long2ObjectOpenHashMap<DataLayer> lumenite$map();

    @Mutable
    @Accessor("map")
    void lumenite$setMap(Long2ObjectOpenHashMap<DataLayer> map);
}
