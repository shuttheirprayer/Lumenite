package com.misanthropy.lumenite;

import net.minecraft.world.level.BlockGetter;

public interface ExtraLight {
    ExtraLight NONE = (level, chunkX, chunkZ) -> null;

    Chunk chunk(BlockGetter level, int chunkX, int chunkZ);

    interface Chunk {
        int emission(int x, int y, int z);
    }
}
