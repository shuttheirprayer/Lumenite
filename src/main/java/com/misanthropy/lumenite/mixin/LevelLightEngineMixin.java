package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.LevelLighting;
import com.misanthropy.lumenite.LightingHost;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin implements LightingHost {
    @Unique
    private LevelLighting lumenite$lighting;

    @Override
    public LevelLighting lumenite$lighting() {
        return lumenite$lighting;
    }

    @Override
    public void lumenite$lighting(LevelLighting lighting) {
        lumenite$lighting = lighting;
    }
}
