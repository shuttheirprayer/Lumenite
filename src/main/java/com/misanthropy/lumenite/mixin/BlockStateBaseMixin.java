package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.LightCell;
import com.misanthropy.lumenite.SkyClear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements LightCell, SkyClear {
    @Unique
    private int lumenite$cell = -1;

    @Unique
    private byte lumenite$skyClear;

    @Override
    public int lumenite$cell() {
        return lumenite$cell;
    }

    @Override
    public void lumenite$cell(int cell) {
        lumenite$cell = cell;
    }

    @Override
    public boolean lumenite$skyClear() {
        byte known = lumenite$skyClear;
        if (known != 0) {
            return known > 0;
        }
        BlockState self = (BlockState) (Object) this;
        if (self.getBlock().hasDynamicShape()) {
            return false;
        }
        boolean clear = self.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0 && (!self.canOcclude() || !self.useShapeForLightOcclusion());
        lumenite$skyClear = clear ? (byte) 1 : (byte) -1;
        return clear;
    }
}
