package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.LevelLighting;
import com.misanthropy.lumenite.LightScheduler;
import com.misanthropy.lumenite.LightingHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLightEngine.class)
public abstract class ClientLevelLightEngineMixin {
    private static final LightScheduler SCHEDULER = new LightScheduler(LightScheduler.CLIENT);

    private LevelLighting lumenite$host() {
        return ((LightingHost) this).lumenite$lighting();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lumenite$attach(LightChunkGetter chunkGetter, boolean hasBlockLight, boolean hasSkyLight, CallbackInfo ci) {
        if (hasBlockLight && chunkGetter instanceof ClientChunkCache cache && !Boolean.getBoolean("lumenite.vanillaclient")) {
            ClientLevel level = (ClientLevel) cache.getLevel();
            Minecraft minecraft = Minecraft.getInstance();
            ((LightingHost) this).lumenite$lighting(new LevelLighting((LevelLightEngine) (Object) this, chunkGetter, null, SCHEDULER, runnable -> minecraft.tell(() -> level.queueLightUpdate(runnable)), true));
        }
    }

    @Inject(method = "checkBlock", at = @At("HEAD"), cancellable = true)
    private void lumenite$checkBlock(BlockPos pos, CallbackInfo ci) {
        LevelLighting lighting = lumenite$host();
        if (lighting != null) {
            lighting.checkBlock(pos);
            ci.cancel();
        }
    }

    @Inject(method = "setLightEnabled", at = @At("HEAD"))
    private void lumenite$lightEnabled(ChunkPos pos, boolean value, CallbackInfo ci) {
        LevelLighting lighting = lumenite$host();
        if (lighting != null) {
            lighting.lightEnabled(pos, value);
        }
    }

    @Inject(method = "runLightUpdates", at = @At("RETURN"))
    private void lumenite$receivedApplied(CallbackInfoReturnable<Integer> cir) {
        LevelLighting lighting = lumenite$host();
        if (lighting != null) {
            lighting.receivedApplied();
            lighting.flushChecks();
        }
    }

    @Inject(method = "queueSectionData", at = @At("HEAD"))
    private void lumenite$sectionReceived(LightLayer layer, SectionPos pos, DataLayer data, CallbackInfo ci) {
        LevelLighting lighting = lumenite$host();
        if (lighting != null) {
            lighting.sectionReceived(layer, pos, data);
        }
    }
}
