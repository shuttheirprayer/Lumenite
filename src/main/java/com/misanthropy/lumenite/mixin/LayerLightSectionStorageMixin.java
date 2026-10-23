package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.SharedLayerMap;
import com.misanthropy.lumenite.SharedTopMap;
import com.misanthropy.lumenite.VisibleLight;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Unique
    private SharedLayerMap lumenite$layers;

    @Unique
    private SharedTopMap lumenite$tops;

    @Override
    public DataLayer lumenite$visibleLayer(long key) {
        return visibleSectionData.getLayer(key);
    }

    @Inject(method = "swapSectionMap", at = @At("HEAD"))
    private void lumenite$publishInPlace(CallbackInfo ci) {
        if (changedSections.isEmpty()) {
            return;
        }
        M visible = visibleSectionData;
        Long2ObjectOpenHashMap<DataLayer> source = ((DataLayerStorageMapAccessor) updatingSectionData).lumenite$map();
        SkyDataLayerStorageMapAccessor sky = updatingSectionData instanceof SkyDataLayerStorageMapAccessor accessor ? accessor : null;
        if (lumenite$layers == null) {
            lumenite$layers = new SharedLayerMap(source);
            if (sky != null) {
                lumenite$tops = new SharedTopMap(sky.lumenite$topSections());
                ((SkyDataLayerStorageMapAccessor) visible).lumenite$setTopSections(lumenite$tops);
                ((SkyDataLayerStorageMapAccessor) visible).lumenite$setLowestY(sky.lumenite$lowestY());
            }
            ((DataLayerStorageMapAccessor) visible).lumenite$setMap(lumenite$layers);
        } else {
            SharedLayerMap layers = lumenite$layers;
            for (LongIterator it = changedSections.iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                DataLayer layer = source.get(key);
                if (layer != null && layers.get(key) != layer) {
                    layers.put(key, layer);
                }
            }
            if (sky != null) {
                Long2IntOpenHashMap sourceTops = sky.lumenite$topSections();
                SharedTopMap tops = lumenite$tops;
                tops.defaultReturnValue(sourceTops.defaultReturnValue());
                ((SkyDataLayerStorageMapAccessor) visible).lumenite$setLowestY(sky.lumenite$lowestY());
                for (LongIterator it = changedSections.iterator(); it.hasNext(); ) {
                    long column = SectionPos.getZeroNode(it.nextLong());
                    if (sourceTops.containsKey(column)) {
                        tops.put(column, sourceTops.get(column));
                    } else {
                        tops.remove(column);
                    }
                }
            }
            for (LongIterator it = changedSections.iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                if (!source.containsKey(key)) {
                    layers.remove(key);
                }
            }
        }
        visibleSectionData = visible;
        changedSections.clear();
    }
}
