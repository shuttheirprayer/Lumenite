package com.misanthropy.lumenite;

import net.minecraft.world.level.chunk.DataLayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class LightWindowCheck {
    private static final int SECTIONS = 6;
    private static final int HEIGHT = SECTIONS * 16;
    private static final int WORLD = 48;

    private final int[] props = new int[WORLD * HEIGHT * WORLD];
    private final byte[] faces = new byte[WORLD * HEIGHT * WORLD];
    private final boolean[] isBlock = new boolean[WORLD * HEIGHT * WORLD];
    private final int[] lowest = new int[WORLD * WORLD];
    private final DataLayer[][][] world = new DataLayer[2][9][SECTIONS];
    private final boolean[] lit = new boolean[9];
    private final boolean[] storedGlobal = new boolean[9 * SECTIONS];
    private int cellReads;

    private static int index(int x, int y, int z) {
        return (y * WORLD + z) * WORLD + x;
    }

    private final class ArrayTerrain extends Terrain {
        private final int offsetX;
        private final int offsetZ;

        ArrayTerrain(int offsetX, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }

        private boolean inside(int x, int z) {
            return x >= 0 && x < WORLD && z >= 0 && z < WORLD;
        }

        @Override
        int cell(int x, int y, int z) {
            cellReads++;
            int wx = x + offsetX;
            int wz = z + offsetZ;
            return inside(wx, wz) ? props[index(wx, y, wz)] : 15;
        }

        @Override
        boolean occludes(int fromX, int fromY, int fromZ, boolean fromEmpty, int toX, int toY, int toZ, boolean toEmpty, int direction) {
            return LightWindowCheck.this.occludes(fromX + offsetX, fromY, fromZ + offsetZ, toX + offsetX, toY, toZ + offsetZ, direction);
        }

        @Override
        int lowestSkySource(int x, int z) {
            int wx = x + offsetX;
            int wz = z + offsetZ;
            return inside(wx, wz) ? lowest[wz * WORLD + wx] : Integer.MIN_VALUE;
        }

        @Override
        boolean sectionMayEmit(int chunkSlot, int sectionIndex) {
            return true;
        }

        private int worldChunk(int chunkSlot) {
            return ((offsetX >> 4) + chunkSlot / 3) * 3 + (offsetZ >> 4) + chunkSlot % 3;
        }

        @Override
        boolean sectionHasBlocks(int chunkSlot, int section) {
            return LightWindowCheck.this.sectionHasBlocks(worldChunk(chunkSlot), section);
        }

        @Override
        DataLayer baseLayer(int layer, int chunkSlot, int section) {
            return world[layer][worldChunk(chunkSlot)][section];
        }
    }

    private boolean occludes(int fx, int fy, int fz, int tx, int ty, int tz, int direction) {
        boolean fromSolid = (faces[index(fx, fy, fz)] & 1 << direction) != 0;
        boolean toSolid = (faces[index(tx, ty, tz)] & 1 << (direction ^ 1)) != 0;
        return fromSolid || toSolid;
    }

    private void generate(Random random) {
        Arrays.fill(props, Terrain.EMPTY_SHAPE);
        for (int z = 0; z < WORLD; z++) {
            for (int x = 0; x < WORLD; x++) {
                int ground = 16 + 4 + random.nextInt(8);
                for (int y = 16; y < ground; y++) {
                    place(x, y, z, 15, 0, true, 0);
                }
                for (int y = 16; y < 48; y++) {
                    int roll = random.nextInt(100);
                    if (y < ground && roll < 8) {
                        place(x, y, z, 0, 0, true, 0);
                    } else if (y >= ground && roll < 3) {
                        place(x, y, z, 15, 0, true, 0);
                    } else if (roll < 4) {
                        place(x, y, z, 0, 1 + random.nextInt(15), true, 0);
                    } else if (roll < 6) {
                        place(x, y, z, 1 + random.nextInt(3), 0, true, 0);
                    } else if (roll < 8) {
                        place(x, y, z, 0, 0, false, random.nextInt(64));
                    }
                }
            }
        }
        for (int z = 0; z < WORLD; z++) {
            for (int x = 0; x < WORLD; x++) {
                lowest[z * WORLD + x] = computeLowest(x, z);
            }
        }
    }

    private void place(int x, int y, int z, int opacity, int emission, boolean emptyShape, int solidFaces) {
        int i = index(x, y, z);
        props[i] = opacity | emission << 4 | (emptyShape ? Terrain.EMPTY_SHAPE : 0);
        faces[i] = (byte) solidFaces;
        isBlock[i] = opacity != 0 || emission != 0 || !emptyShape;
    }

    private int computeLowest(int x, int z) {
        for (int y = HEIGHT - 2; y >= 0; y--) {
            int lower = index(x, y, z);
            boolean occluded = (props[lower] & 15) != 0 || occludes(x, y + 1, z, x, y, z, LightWindow.DOWN);
            if (occluded) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean sectionHasBlocks(int chunk, int section) {
        if (section == 0 || section == SECTIONS - 1) {
            return false;
        }
        int x0 = chunk / 3 << 4;
        int z0 = chunk % 3 << 4;
        for (int y = section << 4; y < (section + 1) << 4; y++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int x = x0; x < x0 + 16; x++) {
                    if (isBlock[index(x, y, z)]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private LightWindow reused;

    private LightWindow window(int chunk) {
        int cx = chunk / 3;
        int cz = chunk % 3;
        ArrayTerrain terrain = new ArrayTerrain((cx - 1) << 4, (cz - 1) << 4);
        if (reused == null) {
            reused = new LightWindow(terrain, SECTIONS);
        } else {
            reused.release();
            reused.attach(terrain);
        }
        LightWindow window = reused;
        for (int c = 0; c < 9; c++) {
            int wx = cx - 1 + c / 3;
            int wz = cz - 1 + c % 3;
            if (wx < 0 || wx > 2 || wz < 0 || wz > 2) {
                continue;
            }
            window.chunkPresent[c] = true;
            window.skyEnabled[c] = lit[wx * 3 + wz];
        }
        return window;
    }

    private void publish(LightWindow window, int chunk) {
        int cx = chunk / 3;
        int cz = chunk % 3;
        for (int c = 0; c < 9; c++) {
            if (!window.chunkPresent[c]) {
                continue;
            }
            int wc = (cx - 1 + c / 3) * 3 + cz - 1 + c % 3;
            for (int s = 0; s < SECTIONS; s++) {
                int idx = c * SECTIONS + s;
                for (int layer = 0; layer < 2; layer++) {
                    if (window.stored(idx) && window.changed(layer, idx) != 0) {
                        world[layer][wc][s] = new DataLayer(window.owned[layer][idx]);
                    }
                }
            }
        }
    }

    private void lightChunk(int chunk) {
        lit[chunk] = true;
        LightWindow window = window(chunk);
        window.lightCentre(true, true);
        publish(window, chunk);
    }

    private void changeBlocks(int count, Random random) {
        int[][] cells = new int[9][count];
        int[] counts = new int[9];
        for (int i = 0; i < count; i++) {
            int chunk = random.nextInt(9);
            int x = (chunk / 3) * 16 + random.nextInt(16);
            int z = (chunk % 3) * 16 + random.nextInt(16);
            int y = 16 + random.nextInt(32);
            changeBlock(x, y, z, random);
            cells[chunk][counts[chunk]++] = LightWindow.packCell(x - (((x >> 4) - 1) << 4), y, z - (((z >> 4) - 1) << 4));
        }
        for (int chunk = 0; chunk < 9; chunk++) {
            if (counts[chunk] == 0) {
                continue;
            }
            LightWindow window = window(chunk);
            window.checkBlocks(cells[chunk], counts[chunk], true);
            publish(window, chunk);
        }
    }

    private void cube(int x0, int y0, int z0, int size, boolean solid) {
        int[][] cells = new int[9][size * size * size];
        int[] counts = new int[9];
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                for (int y = y0; y < y0 + size; y++) {
                    if (solid) {
                        place(x, y, z, 15, 0, true, 0);
                    } else {
                        place(x, y, z, 0, 0, true, 0);
                        isBlock[index(x, y, z)] = false;
                    }
                    int chunk = (x >> 4) * 3 + (z >> 4);
                    cells[chunk][counts[chunk]++] = LightWindow.packCell(x - (((x >> 4) - 1) << 4), y, z - (((z >> 4) - 1) << 4));
                }
                lowest[z * WORLD + x] = computeLowest(x, z);
            }
        }
        for (int chunk = 0; chunk < 9; chunk++) {
            if (counts[chunk] != 0) {
                LightWindow window = window(chunk);
                window.checkBlocks(cells[chunk], counts[chunk], true);
                publish(window, chunk);
            }
        }
    }

    private void changeBlock(int x, int y, int z, Random random) {
        int roll = random.nextInt(5);
        if (roll == 0) {
            place(x, y, z, 15, 0, true, 0);
        } else if (roll == 1) {
            place(x, y, z, 0, 1 + random.nextInt(15), true, 0);
        } else if (roll == 2) {
            place(x, y, z, 0, 0, false, random.nextInt(64));
        } else if (roll == 3) {
            place(x, y, z, 1 + random.nextInt(3), 0, true, 0);
        } else {
            place(x, y, z, 0, 0, true, 0);
            isBlock[index(x, y, z)] = false;
        }
        lowest[z * WORLD + x] = computeLowest(x, z);
    }

    private void deriveStoredGlobal() {
        boolean[] hasBlocks = new boolean[9 * SECTIONS];
        for (int c = 0; c < 9; c++) {
            for (int s = 0; s < SECTIONS; s++) {
                hasBlocks[c * SECTIONS + s] = sectionHasBlocks(c, s);
            }
        }
        for (int c = 0; c < 9; c++) {
            for (int s = 0; s < SECTIONS; s++) {
                boolean near = false;
                for (int nc = 0; nc < 9 && !near; nc++) {
                    if (Math.abs(nc / 3 - c / 3) > 1 || Math.abs(nc % 3 - c % 3) > 1) {
                        continue;
                    }
                    for (int ns = Math.max(0, s - 1); ns <= Math.min(SECTIONS - 1, s + 1); ns++) {
                        if (hasBlocks[nc * SECTIONS + ns]) {
                            near = true;
                        }
                    }
                }
                storedGlobal[c * SECTIONS + s] = near;
            }
        }
    }

    private boolean storedAt(int x, int y, int z) {
        return x >= 0 && x < WORLD && z >= 0 && z < WORLD && y >= 0 && y < HEIGHT && storedGlobal[((x >> 4) * 3 + (z >> 4)) * SECTIONS + (y >> 4)];
    }

    private int[] reference(int layer) {
        int[] value = new int[WORLD * HEIGHT * WORLD];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < WORLD; z++) {
                    for (int x = 0; x < WORLD; x++) {
                        if (!storedAt(x, y, z)) {
                            continue;
                        }
                        int cell = props[index(x, y, z)];
                        int best = layer == LightWindow.BLOCK ? Terrain.emission(cell) : (y >= lowest[z * WORLD + x] ? 15 : 0);
                        for (int d = 0; d < 6; d++) {
                            int nx = x + LightWindow.DX[d];
                            int ny = y + LightWindow.DY[d];
                            int nz = z + LightWindow.DZ[d];
                            if (!storedAt(nx, ny, nz)) {
                                continue;
                            }
                            int candidate = value[index(nx, ny, nz)] - Terrain.opacity(cell);
                            if (candidate <= best) {
                                continue;
                            }
                            boolean fromEmpty = Terrain.emptyShape(props[index(nx, ny, nz)]);
                            boolean toEmpty = Terrain.emptyShape(cell);
                            if (!(fromEmpty && toEmpty) && occludes(nx, ny, nz, x, y, z, d ^ 1)) {
                                continue;
                            }
                            best = candidate;
                        }
                        if (best != value[index(x, y, z)]) {
                            value[index(x, y, z)] = best;
                            changed = true;
                        }
                    }
                }
            }
        }
        return value;
    }

    private int worldValue(int layer, int x, int y, int z) {
        DataLayer data = world[layer][(x >> 4) * 3 + (z >> 4)][y >> 4];
        return data == null ? 0 : data.get(x & 15, y & 15, z & 15);
    }

    private int compare(int layer, int[] expected) {
        int mismatches = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < WORLD; z++) {
                for (int x = 0; x < WORLD; x++) {
                    if (storedAt(x, y, z) && worldValue(layer, x, y, z) != expected[index(x, y, z)]) {
                        if (mismatches < 5) {
                            System.out.println("layer " + layer + " mismatch at " + x + "," + y + "," + z + ": got " + worldValue(layer, x, y, z) + " expected " + expected[index(x, y, z)]);
                        }
                        mismatches++;
                    }
                }
            }
        }
        return mismatches;
    }

    private int run(long seed) {
        Random random = new Random(seed);
        generate(random);
        deriveStoredGlobal();
        List<Integer> order = new ArrayList<>();
        for (int c = 0; c < 9; c++) {
            order.add(c);
        }
        Collections.shuffle(order, random);
        for (int c : order) {
            lightChunk(c);
        }
        int mismatches = compare(LightWindow.BLOCK, reference(LightWindow.BLOCK)) + compare(LightWindow.SKY, reference(LightWindow.SKY));
        for (int i = 0; i < 200; i++) {
            changeBlocks(i % 10 == 9 ? 1 + random.nextInt(40) : 1, random);
            if (i % 50 == 25) {
                int size = 3 + random.nextInt(6);
                int x0 = 8 + random.nextInt(WORLD - 16 - size);
                int z0 = 8 + random.nextInt(WORLD - 16 - size);
                int y0 = 16 + random.nextInt(32 - size);
                cube(x0, y0, z0, size, true);
                cube(x0, y0, z0, size, false);
            }
        }
        deriveStoredGlobal();
        return mismatches + compare(LightWindow.BLOCK, reference(LightWindow.BLOCK)) + compare(LightWindow.SKY, reference(LightWindow.SKY));
    }

    private int readsToShadeNextToLight() {
        Arrays.fill(props, Terrain.EMPTY_SHAPE);
        for (int z = 0; z < WORLD; z++) {
            for (int x = 0; x < WORLD; x++) {
                place(x, 16, z, 15, 0, true, 0);
                lowest[z * WORLD + x] = computeLowest(x, z);
            }
        }
        place(24, 32, 24, 0, 15, true, 0);
        deriveStoredGlobal();
        for (int c = 0; c < 9; c++) {
            lightChunk(c);
        }
        place(25, 32, 24, 15, 0, true, 0);
        LightWindow window = window(4);
        cellReads = 0;
        window.checkBlocks(new int[]{LightWindow.packCell(25, 32, 24)}, 1, false);
        publish(window, 4);
        if (compare(LightWindow.BLOCK, reference(LightWindow.BLOCK)) != 0) {
            throw new AssertionError("shading next to a light gave wrong light");
        }
        return cellReads;
    }

    private int floorOverSectionsWithoutData() {
        Arrays.fill(props, Terrain.EMPTY_SHAPE);
        for (int z = 0; z < WORLD; z++) {
            for (int x = 0; x < WORLD; x++) {
                for (int y = 16; y <= 20; y++) {
                    place(x, y, z, 15, 0, true, 0);
                }
            }
        }
        place(20, 72, 4, 15, 0, true, 0);
        place(20, 72, 44, 15, 0, true, 0);
        for (int z = 0; z < WORLD; z++) {
            for (int x = 0; x < WORLD; x++) {
                lowest[z * WORLD + x] = computeLowest(x, z);
            }
        }
        deriveStoredGlobal();
        for (int c = 0; c < 9; c++) {
            lightChunk(c);
        }
        for (int s = 2; s <= 4; s++) {
            world[LightWindow.BLOCK][4][s] = null;
            world[LightWindow.SKY][4][s] = null;
        }
        int[] cells = new int[256];
        int count = 0;
        for (int x = 16; x < 32; x++) {
            for (int z = 16; z < 32; z++) {
                place(x, 64, z, 15, 0, true, 0);
                lowest[z * WORLD + x] = computeLowest(x, z);
                cells[count++] = LightWindow.packCell(x, 64, z);
            }
        }
        deriveStoredGlobal();
        LightWindow window = window(4);
        window.checkBlocks(cells, count, true);
        publish(window, 4);
        return compare(LightWindow.SKY, reference(LightWindow.SKY)) + compare(LightWindow.BLOCK, reference(LightWindow.BLOCK));
    }

    public static void main(String[] args) {
        int floorMismatches = new LightWindowCheck().floorOverSectionsWithoutData();
        if (floorMismatches != 0) {
            throw new AssertionError("a floor over open sky left " + floorMismatches + " wrong sky light cells below it");
        }
        int reads = new LightWindowCheck().readsToShadeNextToLight();
        System.out.println("shading next to a light read " + reads + " cells");
        if (reads > 1500) {
            throw new AssertionError("placing a block next to a light darkened far more than it shaded: " + reads + " cell reads");
        }
        if (LightWindow.neighbourMask(7 << 8 | 7 << 4 | 7) != 1 << 13
                || LightWindow.neighbourMask(0) != (27 << 9 | 27)
                || Integer.bitCount(LightWindow.neighbourMask(4095)) != 8) {
            throw new AssertionError("neighbour mask");
        }
        int failures = 0;
        for (long seed = 1; seed <= 20; seed++) {
            int mismatches = new LightWindowCheck().run(seed);
            if (mismatches != 0) {
                System.out.println("seed " + seed + ": " + mismatches + " mismatches");
                failures++;
            }
        }
        if (failures != 0) {
            throw new AssertionError(failures + " seeds failed");
        }
        System.out.println("light window check passed");
    }
}
