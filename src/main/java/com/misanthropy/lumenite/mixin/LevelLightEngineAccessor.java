package com.misanthropy.lumenite.mixin;

import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelLightEngine.class)
public interface LevelLightEngineAccessor {
    @Accessor("blockEngine")
    LightEngine<?, ?> lumenite$blockEngine();

    @Accessor("skyEngine")
    LightEngine<?, ?> lumenite$skyEngine();
}
