package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.LevelLighting;
import com.misanthropy.lumenite.LightScheduler;
import com.misanthropy.lumenite.LightingHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineMixin {
    private MinecraftServer lumenite$server;

    private LevelLighting lumenite$host() {
        return ((LightingHost) this).lumenite$lighting();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lumenite$attach(LightChunkGetter chunkGetter, ChunkMap chunkMap, boolean hasSkyLight, ProcessorMailbox<Runnable> mailbox, ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorter, CallbackInfo ci) {
        lumenite$server = ((ServerLevel) chunkGetter.getLevel()).getServer();
        LightScheduler scheduler = new LightScheduler(LightScheduler.SERVER);
        ((LightingHost) this).lumenite$lighting(new LevelLighting((ThreadedLevelLightEngine) (Object) this, chunkGetter, chunkMap, scheduler, mailbox::tell, false));
    }

    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private void lumenite$lightChunk(ChunkAccess chunk, boolean lit, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        cir.setReturnValue(lumenite$host().lightChunk(chunk, lit));
    }

    @Inject(method = "checkBlock", at = @At("HEAD"), cancellable = true)
    private void lumenite$checkBlock(BlockPos pos, CallbackInfo ci) {
        lumenite$host().checkBlock(pos);
        ci.cancel();
    }

    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void lumenite$sectionLoaded(LightLayer layer, SectionPos pos, DataLayer data, CallbackInfo ci) {
        if (data != null) {
            lumenite$host().sectionLoaded(layer, pos, data);
            ci.cancel();
        }
    }

    @Inject(method = "tryScheduleUpdate", at = @At("HEAD"))
    private void lumenite$flushChecks(CallbackInfo ci) {
        if (lumenite$server.isSameThread()) {
            lumenite$host().flushChecks();
        }
    }

    @Inject(method = "updateChunkStatus", at = @At("HEAD"))
    private void lumenite$chunkUnloaded(ChunkPos pos, CallbackInfo ci) {
        lumenite$host().lightEnabled(pos, false);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void lumenite$close(CallbackInfo ci) {
        lumenite$host().close();
    }
}
