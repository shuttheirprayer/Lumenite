package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.Uniform;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientboundLightUpdatePacketData.class)
public abstract class LightPacketMixin {
    @Redirect(method = "prepareSectionData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/DataLayer;copy()Lnet/minecraft/world/level/chunk/DataLayer;"), require = 0)
    private DataLayer lumenite$sharedUniformCopy(DataLayer layer) {
        return Uniform.packetCopy(layer);
    }
}
