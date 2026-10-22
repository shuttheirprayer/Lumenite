package com.misanthropy.lumenite;

import net.minecraft.world.level.chunk.DataLayer;

abstract class Terrain {
    static final int EMPTY_SHAPE = 1 << 8;

    static int opacity(int cell) {
        return Math.max(1, cell & 15);
    }

    static int emission(int cell) {
        return cell >> 4 & 15;
    }

    static boolean emptyShape(int cell) {
        return (cell & EMPTY_SHAPE) != 0;
    }

    abstract int cell(int x, int y, int z);

    abstract boolean occludes(int fromX, int fromY, int fromZ, boolean fromEmpty, int toX, int toY, int toZ, boolean toEmpty, int direction);

    abstract int lowestSkySource(int x, int z);

    abstract boolean sectionMayEmit(int chunkSlot, int sectionIndex);

    abstract boolean sectionHasBlocks(int chunkSlot, int section);

    abstract DataLayer baseLayer(int layer, int chunkSlot, int section);
}
