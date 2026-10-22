package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.VisibleLight;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.VarHandle;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin<M extends DataLayerStorageMap<M>> implements VisibleLight {
    @Shadow
    protected volatile M visibleSectionData;

    @Shadow
    @Final
    protected M updatingSectionData;

    @Shadow
    @Final
    protected LongSet changedSections;

    @Override
    public DataLayer lumenite$visibleLayer(long key) {
        return visibleSectionData.getLayer(key);
    }

    @Inject(method = "swapSectionMap", at = @At("HEAD"))
    private void lumenite$updateInPlace(CallbackInfo ci) {
        if (changedSections.isEmpty()) {
            return;
        }
        M visible = visibleSectionData;
        for (LongIterator it = changedSections.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if (!visible.hasLayer(key) || !updatingSectionData.hasLayer(key)) {
                return;
            }
        }
        VarHandle.releaseFence();
        for (LongIterator it = changedSections.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            visible.setLayer(key, updatingSectionData.getLayer(key));
        }
        visibleSectionData = visible;
        changedSections.clear();
    }
}
