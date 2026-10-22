package com.misanthropy.lumenite;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.extensions.IForgeBlock;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PositionIndependence {
    private static final String ZETA_DELEGATE_MIXIN = ".IZetaBlockMixin_FAKE";
    private static final String ZETA_DEFAULT_EMISSION = "org.violetmoon.zeta.block.ext.IZetaBlockExtensions";
    private static final Map<Class<?>, Boolean> BY_BLOCK_CLASS = new ConcurrentHashMap<>();

    private PositionIndependence() {
    }

    static boolean check(Block block) {
        return !block.hasDynamicShape() && BY_BLOCK_CLASS.computeIfAbsent(block.getClass(), PositionIndependence::emissionIsDefault);
    }

    private static boolean emissionIsDefault(Class<?> type) {
        try {
            Method emission = type.getMethod("getLightEmission", BlockState.class, BlockGetter.class, BlockPos.class);
            return emission.getDeclaringClass() == IForgeBlock.class || isZetaDelegate(type, emission);
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean isZetaDelegate(Class<?> type, Method emission) throws NoSuchMethodException {
        MixinMerged merged = emission.getAnnotation(MixinMerged.class);
        return merged != null && merged.mixin().endsWith(ZETA_DELEGATE_MIXIN)
                && type.getMethod("getLightEmissionZeta", BlockState.class, BlockGetter.class, BlockPos.class).getDeclaringClass().getName().equals(ZETA_DEFAULT_EMISSION);
    }
}
