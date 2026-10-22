package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.LightCell;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements LightCell {
    @Unique
    private int lumenite$cell = -1;

    @Override
    public int lumenite$cell() {
        return lumenite$cell;
    }

    @Override
    public void lumenite$cell(int cell) {
        lumenite$cell = cell;
    }
}
