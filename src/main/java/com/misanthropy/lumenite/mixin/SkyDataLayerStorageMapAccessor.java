package com.misanthropy.lumenite.mixin;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap")
public interface SkyDataLayerStorageMapAccessor {
    @Accessor("topSections")
    Long2IntOpenHashMap lumenite$topSections();

    @Mutable
    @Accessor("topSections")
    void lumenite$setTopSections(Long2IntOpenHashMap topSections);

    @Accessor("currentLowestY")
    int lumenite$lowestY();

    @Accessor("currentLowestY")
    void lumenite$setLowestY(int lowestY);
}
