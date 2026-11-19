package com.misanthropy.lumenite.mixin;

import com.misanthropy.lumenite.SkyClear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkSkyLightSources.class)
public abstract class SkyLightSourcesMixin {
    @Unique
    private static final BlockState lumenite$AIR = Blocks.AIR.defaultBlockState();

    @Shadow
    @Final
    private int minY;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos1;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos2;

    @Unique
    private static final boolean lumenite$CHECK = Boolean.getBoolean("lumenite.skyCheck");

    @Unique
    private static int lumenite$mismatches;

    @Unique
    private static long lumenite$checked;

    @Shadow
    private void fill(int y) {
        throw new AssertionError();
    }

    @Shadow
    private void set(int index, int y) {
        throw new AssertionError();
    }

    @Shadow
    private int findLowestSourceY(ChunkAccess chunk, int topSection, int x, int z) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isEdgeOccluded(BlockGetter level, BlockPos abovePos, BlockState aboveState, BlockPos belowPos, BlockState belowState) {
        throw new AssertionError();
    }

    /**
     * @author Lumenite
     * @reason skip runs of sky-clear blocks and fully sky-clear sections; result identical to vanilla
     */
    @Overwrite
    public void fillFrom(ChunkAccess chunk) {
        int top = chunk.getHighestFilledSectionIndex();
        if (top == -1) {
            fill(minY);
            return;
        }
        if (!com.misanthropy.lumenite.Toggles.fastSkySources) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    set(x + z * 16, Math.max(findLowestSourceY(chunk, top, x, z), minY));
                }
            }
            return;
        }
        LevelChunkSection[] sections = new LevelChunkSection[top + 1];
        byte[] kind = new byte[top + 1];
        for (int i = 0; i <= top; i++) {
            LevelChunkSection section = chunk.getSection(i);
            sections[i] = section;
            kind[i] = section.hasOnlyAir() ? (byte) 0 : section.maybeHas(SkyClear.BLOCKS_SKY) ? (byte) 2 : (byte) 1;
        }
        int topY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(top) + 1);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = lumenite$lowest(chunk, sections, kind, top, topY, x, z);
                if (lumenite$CHECK) {
                    int vanilla = findLowestSourceY(chunk, top, x, z);
                    if (lumenite$checked++ % 500000 == 0) {
                        com.mojang.logging.LogUtils.getLogger().info("[lumenite] sky source check: {} columns, {} mismatches", lumenite$checked, lumenite$mismatches);
                    }
                    if (vanilla != y && lumenite$mismatches++ < 50) {
                        com.mojang.logging.LogUtils.getLogger().warn("[lumenite] sky source mismatch at {} {} {}: fast {} vanilla {}", chunk.getPos(), x, z, y, vanilla);
                    }
                }
                set(x + z * 16, Math.max(y, minY));
            }
        }
    }

    @Unique
    private int lumenite$lowest(ChunkAccess chunk, LevelChunkSection[] sections, byte[] kind, int top, int topY, int x, int z) {
        BlockPos.MutableBlockPos above = mutablePos1.set(x, topY, z);
        BlockPos.MutableBlockPos below = mutablePos2.setWithOffset(above, Direction.DOWN);
        BlockState aboveState = lumenite$AIR;
        boolean aboveClear = true;
        for (int i = top; i >= 0; i--) {
            byte k = kind[i];
            if (k == 0) {
                aboveState = lumenite$AIR;
                aboveClear = true;
                above.setY(SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i)));
                below.setY(above.getY() - 1);
                continue;
            }
            LevelChunkSection section = sections[i];
            if (k == 1) {
                if (!aboveClear) {
                    BlockState first = section.getBlockState(x, 15, z);
                    if (isEdgeOccluded(chunk, above, aboveState, below, first)) {
                        return above.getY();
                    }
                }
                aboveState = lumenite$AIR;
                aboveClear = true;
                above.setY(SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i)));
                below.setY(above.getY() - 1);
                continue;
            }
            for (int y = 15; y >= 0; y--) {
                BlockState state = section.getBlockState(x, y, z);
                boolean clear = ((SkyClear) state).lumenite$skyClear();
                if (!(clear && aboveClear) && isEdgeOccluded(chunk, above, aboveState, below, state)) {
                    return above.getY();
                }
                aboveState = state;
                aboveClear = clear;
                above.set(below);
                below.move(Direction.DOWN);
            }
        }
        return minY;
    }
}
