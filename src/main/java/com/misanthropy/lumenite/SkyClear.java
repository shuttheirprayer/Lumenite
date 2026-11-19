package com.misanthropy.lumenite;

import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public interface SkyClear {
    Predicate<BlockState> BLOCKS_SKY = state -> !((SkyClear) state).lumenite$skyClear();

    boolean lumenite$skyClear();
}
