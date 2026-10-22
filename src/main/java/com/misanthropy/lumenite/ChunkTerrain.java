package com.misanthropy.lumenite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class ChunkTerrain extends Terrain {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final LevelLighting lighting;
    private final BlockGetter level;
    final int chunkX;
    final int chunkZ;
    private final int originX;
    private final int minY;
    private final int originZ;
    final ChunkAccess[] chunks = new ChunkAccess[9];
    private final LevelChunkSection[][] sections = new LevelChunkSection[9][];
    private final ChunkSkyLightSources[] skySources = new ChunkSkyLightSources[9];
    private final BlockPos.MutableBlockPos fromPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos toPos = new BlockPos.MutableBlockPos();
    private final ExtraLight extra = Lumenite.extraLight;
    private final ExtraLight.Chunk[] extraChunks = new ExtraLight.Chunk[9];

    ChunkTerrain(LevelLighting lighting, BlockGetter level, int chunkX, int chunkZ, int minY) {
        this.lighting = lighting;
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.originX = (chunkX - 1) << 4;
        this.originZ = (chunkZ - 1) << 4;
        this.minY = minY;
    }

    boolean stillPresent(int chunkSlot, LightChunk current) {
        if (!(current instanceof ChunkAccess chunk)) {
            return false;
        }
        LevelChunkSection[] captured = sections[chunkSlot];
        LevelChunkSection[] live = chunk.getSections();
        if (live.length != captured.length) {
            return false;
        }
        for (int i = 0; i < live.length; i++) {
            if (live[i] != captured[i]) {
                return false;
            }
        }
        return true;
    }

    void attach(int chunkSlot, ChunkAccess chunk) {
        chunks[chunkSlot] = chunk;
        sections[chunkSlot] = chunk.getSections();
        skySources[chunkSlot] = chunk.getSkyLightSources();
        extraChunks[chunkSlot] = extra.chunk(level, chunk.getPos().x, chunk.getPos().z);
    }

    private BlockState state(int x, int y, int z) {
        LevelChunkSection[] column = sections[LightWindow.chunkSlot(x, z)];
        int index = (y >> 4) - 1;
        if (column == null || index < 0 || index >= column.length) {
            return AIR;
        }
        LevelChunkSection section = column[index];
        return section.hasOnlyAir() ? AIR : section.getBlockState(x & 15, y & 15, z & 15);
    }

    @Override
    int cell(int x, int y, int z) {
        BlockState state = state(x, y, z);
        int cached = ((LightCell) state).lumenite$cell();
        int cell;
        if (cached >= 0) {
            cell = cached;
        } else {
            fromPos.set(originX + x, minY + y, originZ + z);
            int opacity = Math.min(15, Math.max(0, state.getLightBlock(level, fromPos)));
            int emission = Math.min(15, Math.max(0, state.getLightEmission(level, fromPos)));
            boolean empty = !state.canOcclude() || !state.useShapeForLightOcclusion();
            cell = opacity | emission << 4 | (empty ? EMPTY_SHAPE : 0);
            if (cached == -1 && PositionIndependence.check(state.getBlock())) {
                ((LightCell) state).lumenite$cell(cell);
            } else if (cached == -1) {
                ((LightCell) state).lumenite$cell(-2);
            }
        }
        ExtraLight.Chunk more = extraChunks[LightWindow.chunkSlot(x, z)];
        if (more != null && !state.isAir()) {
            int extraEmission = Math.min(15, more.emission(originX + x, minY + y, originZ + z));
            if (extraEmission > emission(cell)) {
                cell = cell & ~(15 << 4) | extraEmission << 4;
            }
        }
        return cell;
    }

    @Override
    boolean occludes(int fromX, int fromY, int fromZ, boolean fromEmpty, int toX, int toY, int toZ, boolean toEmpty, int direction) {
        Direction d = DIRECTIONS[direction];
        VoxelShape from = fromEmpty ? Shapes.empty() : state(fromX, fromY, fromZ).getFaceOcclusionShape(level, fromPos.set(originX + fromX, minY + fromY, originZ + fromZ), d);
        VoxelShape to = toEmpty ? Shapes.empty() : state(toX, toY, toZ).getFaceOcclusionShape(level, toPos.set(originX + toX, minY + toY, originZ + toZ), d.getOpposite());
        return Shapes.faceShapeOccludes(from, to);
    }

    @Override
    int lowestSkySource(int x, int z) {
        ChunkSkyLightSources sources = skySources[LightWindow.chunkSlot(x, z)];
        if (sources == null) {
            return Integer.MIN_VALUE;
        }
        int y = sources.getLowestSourceY(x & 15, z & 15);
        return y == Integer.MIN_VALUE ? Integer.MIN_VALUE : y - minY;
    }

    @Override
    boolean sectionMayEmit(int chunkSlot, int sectionIndex) {
        LevelChunkSection section = sections[chunkSlot][sectionIndex];
        ChunkAccess chunk = chunks[chunkSlot];
        return !section.hasOnlyAir() && (extraChunks[chunkSlot] != null || section.maybeHas(state -> state.getLightEmission(chunk, BlockPos.ZERO) != 0));
    }

    @Override
    boolean sectionHasBlocks(int chunkSlot, int section) {
        LevelChunkSection[] column = sections[chunkSlot];
        int index = section - 1;
        return column != null && index >= 0 && index < column.length && !column[index].hasOnlyAir();
    }

    @Override
    DataLayer baseLayer(int layer, int chunkSlot, int section) {
        return lighting.storedLayer(layer, chunkX - 1 + chunkSlot / 3, section, chunkZ - 1 + chunkSlot % 3);
    }
}
