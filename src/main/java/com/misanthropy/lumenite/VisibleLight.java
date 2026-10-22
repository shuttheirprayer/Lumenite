package com.misanthropy.lumenite;

import net.minecraft.world.level.chunk.DataLayer;

public interface VisibleLight {
    DataLayer lumenite$visibleLayer(long key);
}
