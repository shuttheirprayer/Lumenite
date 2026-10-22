package com.misanthropy.lumenite;

import net.minecraft.world.level.chunk.DataLayer;

import java.util.Arrays;

final class LightWindow {
    static final int BLOCK = 0;
    static final int SKY = 1;
    static final int SIZE = 48;
    static final int CENTER = 4;
    static final int DOWN = 0;
    static final int UP = 1;
    static final int NORTH = 2;
    static final int SOUTH = 3;
    static final int WEST = 4;
    static final int EAST = 5;
    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};
    private static final long ALL_DIRECTIONS = 63L << 4;
    private static final long FROM_EMPTY_SHAPE = 1L << 10;
    private static final long FROM_EMISSION = 1L << 11;
    private static final long CHECK_SUPPORT = 1L << 12;
    private static final long DARKEN_ALL = 1L << 13;
    private static final long PULL_IN = ALL_DIRECTIONS | 1L;

    Terrain terrain;
    final int sectionCount;
    final int height;
    final boolean[] chunkPresent = new boolean[9];
    final boolean[] skyEnabled = new boolean[9];
    long received;
    private final byte[] storedState;
    private final boolean[][] baseLoaded;
    private final byte[][][] baseData;
    private final byte[][] baseFill;
    final byte[][][] owned;
    private final long[][] decreases = new long[16][64];
    private final int[] decreaseCounts = new int[16];
    private final long[][] increases = new long[16][64];
    private final int[] increaseCounts = new int[16];
    private final long[] seenColumns = new long[SIZE * SIZE / 64];
    private int layer;

    LightWindow(Terrain terrain, int sectionCount) {
        this.terrain = terrain;
        this.sectionCount = sectionCount;
        this.height = sectionCount * 16;
        this.storedState = new byte[9 * sectionCount];
        this.baseLoaded = new boolean[2][9 * sectionCount];
        this.baseData = new byte[2][9 * sectionCount][];
        this.baseFill = new byte[2][9 * sectionCount];
        this.owned = new byte[2][9 * sectionCount][];
    }

    void attach(Terrain terrain) {
        this.terrain = terrain;
    }

    void release() {
        this.terrain = null;
        this.received = 0L;
        this.layer = 0;
        Arrays.fill(chunkPresent, false);
        Arrays.fill(skyEnabled, false);
        Arrays.fill(storedState, (byte) 0);
        for (int l = BLOCK; l <= SKY; l++) {
            Arrays.fill(baseLoaded[l], false);
            Arrays.fill(baseData[l], null);
            Arrays.fill(baseFill[l], (byte) 0);
            Arrays.fill(owned[l], null);
        }
        Arrays.fill(decreaseCounts, 0);
        Arrays.fill(increaseCounts, 0);
    }

    static int chunkSlot(int x, int z) {
        return (x >> 4) * 3 + (z >> 4);
    }

    int slot(int x, int y, int z) {
        return chunkSlot(x, z) * sectionCount + (y >> 4);
    }

    private boolean hasBase(int layer, int idx) {
        if (!baseLoaded[layer][idx]) {
            baseLoaded[layer][idx] = true;
            int c = idx / sectionCount;
            DataLayer b = chunkPresent[c] ? terrain.baseLayer(layer, c, idx % sectionCount) : null;
            if (b == null) {
                baseFill[layer][idx] = -1;
            } else if (b.isDefinitelyHomogenous()) {
                baseFill[layer][idx] = (byte) b.get(0, 0, 0);
            } else {
                baseData[layer][idx] = b.getData();
            }
        }
        return baseData[layer][idx] != null || baseFill[layer][idx] >= 0;
    }

    static final int ALL_NEIGHBOURS = (1 << 27) - 1;

    int changed(int layer, int idx) {
        byte[] o = owned[layer][idx];
        if (o == null) {
            return 0;
        }
        if (!hasBase(layer, idx)) {
            return ALL_NEIGHBOURS;
        }
        byte[] base = baseData[layer][idx];
        int fill = baseFill[layer][idx];
        byte fillByte = (byte) (fill | fill << 4);
        int mask = 0;
        for (int i = 0; i < 2048; i++) {
            int diff = (o[i] ^ (base == null ? fillByte : base[i])) & 255;
            if (diff == 0) {
                continue;
            }
            if ((diff & 15) != 0) {
                mask |= neighbourMask(i << 1);
            }
            if ((diff & 240) != 0) {
                mask |= neighbourMask(i << 1 | 1);
            }
        }
        return mask;
    }

    static int neighbourMask(int cell) {
        int x = cell & 15;
        int z = cell >> 4 & 15;
        int y = cell >> 8;
        int mask = 0;
        for (int dx = x == 0 ? -1 : 0; dx <= (x == 15 ? 1 : 0); dx++) {
            for (int dy = y == 0 ? -1 : 0; dy <= (y == 15 ? 1 : 0); dy++) {
                for (int dz = z == 0 ? -1 : 0; dz <= (z == 15 ? 1 : 0); dz++) {
                    mask |= 1 << ((dx + 1) * 9 + (dy + 1) * 3 + dz + 1);
                }
            }
        }
        return mask;
    }

    boolean stored(int idx) {
        byte state = storedState[idx];
        if (state == 0) {
            state = (byte) (deriveStored(idx) ? 2 : 1);
            storedState[idx] = state;
        }
        return state == 2;
    }

    private boolean deriveStored(int idx) {
        int c = idx / sectionCount;
        if (!chunkPresent[c]) {
            return false;
        }
        int s = idx % sectionCount;
        if (hasBase(BLOCK, idx) || hasBase(SKY, idx)) {
            return true;
        }
        int cx = c / 3;
        int cz = c % 3;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = cx + dx;
                int nz = cz + dz;
                if (nx < 0 || nx > 2 || nz < 0 || nz > 2) {
                    continue;
                }
                int nc = nx * 3 + nz;
                if (!chunkPresent[nc]) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    int ns = s + dy;
                    if (ns >= 0 && ns < sectionCount && terrain.sectionHasBlocks(nc, ns)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean stored(int x, int y, int z) {
        return inside(x, y, z) && stored(slot(x, y, z));
    }

    int get(int layer, int x, int y, int z) {
        return getAt(layer, slot(x, y, z), (y & 15) << 8 | (z & 15) << 4 | (x & 15));
    }

    private int getAt(int layer, int idx, int cell) {
        byte[] o = owned[layer][idx];
        if (o == null) {
            if (hasBase(layer, idx)) {
                o = baseData[layer][idx];
                if (o == null) {
                    return baseFill[layer][idx];
                }
            } else if (!stored(idx)) {
                return 0;
            } else {
                o = materialize(layer, idx);
            }
        }
        return o[cell >> 1] >> ((cell & 1) << 2) & 15;
    }

    void set(int layer, int x, int y, int z, int value) {
        setAt(layer, slot(x, y, z), (y & 15) << 8 | (z & 15) << 4 | (x & 15), value);
    }

    private void setAt(int layer, int idx, int cell, int value) {
        byte[] o = owned[layer][idx];
        if (o == null) {
            o = materialize(layer, idx);
        }
        int shift = (cell & 1) << 2;
        o[cell >> 1] = (byte) (o[cell >> 1] & ~(15 << shift) | value << shift);
    }

    private static int cellIndex(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && x < SIZE && z >= 0 && z < SIZE && y >= 0 && y < height;
    }

    private byte[] materialize(int layer, int idx) {
        byte[] o;
        if (hasBase(layer, idx)) {
            byte[] shared = baseData[layer][idx];
            if (shared != null) {
                o = shared.clone();
            } else {
                o = new byte[2048];
                int fill = baseFill[layer][idx];
                Arrays.fill(o, (byte) (fill | fill << 4));
            }
        } else {
            o = new byte[2048];
            int c = idx / sectionCount;
            int s = idx % sectionCount;
            if (layer == SKY && skyEnabled[c]) {
                int above = -1;
                for (int t = s + 1; t < sectionCount; t++) {
                    if (stored(c * sectionCount + t)) {
                        above = t;
                        break;
                    }
                }
                if (above < 0) {
                    Arrays.fill(o, (byte) 0xFF);
                    baseFill[layer][idx] = 15;
                } else {
                    int aboveIdx = c * sectionCount + above;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int v = originalValue(SKY, aboveIdx, z << 4 | x);
                            for (int y = 0; y < 16; y++) {
                                int cell = y << 8 | z << 4 | x;
                                o[cell >> 1] |= (byte) (v << ((cell & 1) << 2));
                            }
                        }
                    }
                    baseData[layer][idx] = o.clone();
                }
            } else {
                baseFill[layer][idx] = 0;
            }
        }
        owned[layer][idx] = o;
        return o;
    }

    private int originalValue(int layer, int idx, int cell) {
        if (!hasBase(layer, idx)) {
            materialize(layer, idx);
        }
        byte[] base = baseData[layer][idx];
        return base == null ? baseFill[layer][idx] : base[cell >> 1] >> ((cell & 1) << 2) & 15;
    }

    private static long entry(int x, int y, int z, long flags) {
        return (long) (x | z << 6 | y << 12) << 16 | flags;
    }

    private static long allDirections(int level) {
        return ALL_DIRECTIONS | level;
    }

    private static long skipOneDirection(int level, boolean fromEmpty, int direction) {
        return (ALL_DIRECTIONS & ~(1L << (4 + direction))) | level | (fromEmpty ? FROM_EMPTY_SHAPE : 0);
    }

    private static long onlyOneDirection(int level, int direction) {
        return 1L << (4 + direction) | level;
    }

    private static long fromEmission(int level, boolean empty) {
        return ALL_DIRECTIONS | FROM_EMISSION | level | (empty ? FROM_EMPTY_SHAPE : 0);
    }

    private static boolean hasDirection(long flags, int direction) {
        return (flags & 1L << (4 + direction)) != 0;
    }

    private void enqueueIncrease(int x, int y, int z, long flags) {
        push(increases, increaseCounts, flags, entry(x, y, z, flags));
    }

    private void enqueueDecrease(int x, int y, int z, long flags) {
        push(decreases, decreaseCounts, flags, entry(x, y, z, flags));
    }

    private static void push(long[][] buckets, int[] counts, long flags, long entry) {
        int level = (int) (flags & 15);
        int count = counts[level];
        if (count == buckets[level].length) {
            buckets[level] = Arrays.copyOf(buckets[level], count * 2);
        }
        buckets[level][count] = entry;
        counts[level] = count + 1;
    }

    void runQueues(int layer) {
        this.layer = layer;
        for (int level = 15; level >= 0; level--) {
            for (int i = 0; i < decreaseCounts[level]; i++) {
                long e = decreases[level][i];
                int p = (int) (e >>> 16);
                int x = p & 63;
                int y = p >> 12;
                int z = p >> 6 & 63;
                if ((e & CHECK_SUPPORT) == 0 || darkenUnsupported(x, y, z, e)) {
                    propagateDecrease(x, y, z, e);
                }
            }
            decreaseCounts[level] = 0;
        }
        for (int level = 15; level >= 0; level--) {
            for (int i = 0; i < increaseCounts[level]; i++) {
                increaseFrom(increases[level][i]);
            }
            increaseCounts[level] = 0;
        }
    }

    private void increaseFrom(long e) {
        int p = (int) (e >>> 16);
        int x = p & 63;
        int y = p >> 12;
        int z = p >> 6 & 63;
        int level = (int) (e & 15);
        int current = get(layer, x, y, z);
        if ((e & FROM_EMISSION) != 0 && current < level) {
            set(layer, x, y, z, level);
            current = level;
        }
        if (current == level) {
            propagateIncrease(x, y, z, e, level);
        }
    }

    private void propagateIncrease(int x, int y, int z, long e, int level) {
        int fromCell = -1;
        int gap = layer == SKY ? gapBelowIfAtBorder(x, y, z) : 0;
        for (int d = 0; d < 6; d++) {
            if (!hasDirection(e, d)) {
                continue;
            }
            int nx = x + DX[d];
            int ny = y + DY[d];
            int nz = z + DZ[d];
            if (!inside(nx, ny, nz)) {
                continue;
            }
            int idx = slot(nx, ny, nz);
            if (!stored(idx)) {
                continue;
            }
            int cellIndex = cellIndex(nx, ny, nz);
            int current = getAt(layer, idx, cellIndex);
            if (level - 1 <= current) {
                continue;
            }
            int cell = terrain.cell(nx, ny, nz);
            int target = level - Terrain.opacity(cell);
            if (target <= current) {
                continue;
            }
            boolean toEmpty = Terrain.emptyShape(cell);
            boolean fromEmpty;
            if ((e & FROM_EMPTY_SHAPE) != 0) {
                fromEmpty = true;
            } else {
                if (fromCell < 0) {
                    fromCell = terrain.cell(x, y, z);
                }
                fromEmpty = Terrain.emptyShape(fromCell);
            }
            if (!(fromEmpty && toEmpty) && terrain.occludes(x, y, z, fromEmpty, nx, ny, nz, toEmpty, d)) {
                continue;
            }
            setAt(layer, idx, cellIndex, target);
            if (target > 1) {
                enqueueIncrease(nx, ny, nz, skipOneDirection(target, toEmpty, d ^ 1));
            }
            if (gap != 0) {
                spillIntoGap(nx, ny, nz, d, target, true, gap);
            }
        }
    }

    private void propagateDecrease(int x, int y, int z, long e) {
        int level = (int) (e & 15);
        int gap = layer == SKY ? gapBelowIfAtBorder(x, y, z) : 0;
        for (int d = 0; d < 6; d++) {
            if (!hasDirection(e, d)) {
                continue;
            }
            int nx = x + DX[d];
            int ny = y + DY[d];
            int nz = z + DZ[d];
            if (!inside(nx, ny, nz)) {
                continue;
            }
            int idx = slot(nx, ny, nz);
            if (!stored(idx)) {
                continue;
            }
            int cellIndex = cellIndex(nx, ny, nz);
            int current = getAt(layer, idx, cellIndex);
            if (current == 0) {
                continue;
            }
            if (current > level - 1) {
                enqueueIncrease(nx, ny, nz, onlyOneDirection(current, d ^ 1));
                continue;
            }
            if (layer == BLOCK && (e & DARKEN_ALL) == 0) {
                enqueueDecrease(nx, ny, nz, skipOneDirection(current, false, d ^ 1) | CHECK_SUPPORT);
                continue;
            }
            setAt(layer, idx, cellIndex, 0);
            if (layer == BLOCK) {
                int cell = terrain.cell(nx, ny, nz);
                int emission = Terrain.emission(cell);
                if (emission < current) {
                    enqueueDecrease(nx, ny, nz, skipOneDirection(current, false, d ^ 1) | DARKEN_ALL);
                }
                if (emission > 0) {
                    enqueueIncrease(nx, ny, nz, fromEmission(emission, Terrain.emptyShape(cell)));
                }
            } else {
                enqueueDecrease(nx, ny, nz, skipOneDirection(current, false, d ^ 1));
                if (gap != 0) {
                    spillIntoGap(nx, ny, nz, d, current, false, gap);
                }
            }
        }
    }

    private boolean darkenUnsupported(int x, int y, int z, long e) {
        int level = (int) (e & 15);
        if (get(BLOCK, x, y, z) != level) {
            return false;
        }
        int cell = terrain.cell(x, y, z);
        int emission = Terrain.emission(cell);
        if (supportedLevel(BLOCK, x, y, z, cell, emission) >= level) {
            enqueueIncrease(x, y, z, ALL_DIRECTIONS & ~e | level);
            return false;
        }
        set(BLOCK, x, y, z, 0);
        if (emission > 0) {
            enqueueIncrease(x, y, z, fromEmission(emission, Terrain.emptyShape(cell)));
        }
        return true;
    }

    private boolean brighterNeighbour(int x, int y, int z, int level) {
        for (int d = 0; d < 6; d++) {
            int nx = x + DX[d];
            int ny = y + DY[d];
            int nz = z + DZ[d];
            if (stored(nx, ny, nz) && get(BLOCK, nx, ny, nz) > level) {
                return true;
            }
        }
        return false;
    }

    private int gapBelowIfAtBorder(int x, int y, int z) {
        if ((y & 15) != 0) {
            return 0;
        }
        int rx = x & 15;
        int rz = z & 15;
        if (rx != 0 && rx != 15 && rz != 0 && rz != 15) {
            return 0;
        }
        int column = chunkSlot(x, z) * sectionCount;
        int s = y >> 4;
        int gap = 0;
        while (s - gap - 1 >= 0 && !stored(column + s - gap - 1)) {
            gap++;
        }
        return gap;
    }

    private void spillIntoGap(int x, int y, int z, int direction, int level, boolean increase, int gap) {
        if (direction < NORTH) {
            return;
        }
        int px = x - DX[direction];
        int pz = z - DZ[direction];
        if (px >> 4 == x >> 4 && pz >> 4 == z >> 4) {
            return;
        }
        int column = chunkSlot(x, z) * sectionCount;
        int top = (y >> 4) - 1;
        for (int s = top; s > top - gap && s >= 0; s--) {
            if (!stored(column + s)) {
                continue;
            }
            for (int i = 15; i >= 0; i--) {
                int cy = (s << 4) + i;
                if (increase) {
                    set(SKY, x, cy, z, level);
                    if (level > 1) {
                        enqueueIncrease(x, cy, z, skipOneDirection(level, true, direction ^ 1));
                    }
                } else {
                    set(SKY, x, cy, z, 0);
                    enqueueDecrease(x, cy, z, skipOneDirection(level, false, direction ^ 1));
                }
            }
        }
    }

    private boolean skySource(int x, int y, int z) {
        return skyEnabled[chunkSlot(x, z)] && y >= terrain.lowestSkySource(x, z);
    }

    private int neighbourLowestSkySource(int x, int z) {
        return x < 0 || x >= SIZE || z < 0 || z >= SIZE ? Integer.MIN_VALUE : terrain.lowestSkySource(x, z);
    }

    private long skySourceDirections(int x, int y, int z, int lowest) {
        long flags = 15;
        if (y == lowest) {
            flags |= 1L << (4 + DOWN);
        }
        if (y < neighbourLowestSkySource(x, z - 1)) {
            flags |= 1L << (4 + NORTH);
        }
        if (y < neighbourLowestSkySource(x, z + 1)) {
            flags |= 1L << (4 + SOUTH);
        }
        if (y < neighbourLowestSkySource(x - 1, z)) {
            flags |= 1L << (4 + WEST);
        }
        if (y < neighbourLowestSkySource(x + 1, z)) {
            flags |= 1L << (4 + EAST);
        }
        return flags;
    }

    void seedSkyColumns(int chunkSlot) {
        int x0 = chunkSlot / 3 << 4;
        int z0 = chunkSlot % 3 << 4;
        for (int s = sectionCount - 1; s >= 0; s--) {
            if (!stored(chunkSlot * sectionCount + s)) {
                continue;
            }
            int bottom = s << 4;
            int top = bottom + 15;
            boolean continues = false;
            for (int z = z0; z < z0 + 16; z++) {
                for (int x = x0; x < x0 + 16; x++) {
                    int lowest = terrain.lowestSkySource(x, z);
                    if (lowest > top) {
                        continue;
                    }
                    int highestNeighbour = Math.max(
                            Math.max(neighbourLowestSkySource(x, z - 1), neighbourLowestSkySource(x, z + 1)),
                            Math.max(neighbourLowestSkySource(x - 1, z), neighbourLowestSkySource(x + 1, z)));
                    for (int y = top; y >= Math.max(bottom, lowest); y--) {
                        set(SKY, x, y, z, 15);
                        if (y == lowest || y < highestNeighbour) {
                            enqueueIncrease(x, y, z, skySourceDirections(x, y, z, lowest));
                        }
                    }
                    if (lowest < bottom) {
                        continues = true;
                    }
                }
            }
            if (!continues) {
                break;
            }
        }
    }

    void seedEmitters(int chunkSlot) {
        int x0 = chunkSlot / 3 << 4;
        int z0 = chunkSlot % 3 << 4;
        for (int s = 1; s < sectionCount - 1; s++) {
            if (!stored(chunkSlot * sectionCount + s) || !terrain.sectionMayEmit(chunkSlot, s - 1)) {
                continue;
            }
            int y0 = s << 4;
            for (int y = y0; y < y0 + 16; y++) {
                for (int z = z0; z < z0 + 16; z++) {
                    for (int x = x0; x < x0 + 16; x++) {
                        int cell = terrain.cell(x, y, z);
                        int emission = Terrain.emission(cell);
                        if (emission != 0) {
                            enqueueIncrease(x, y, z, fromEmission(emission, Terrain.emptyShape(cell)));
                        }
                    }
                }
            }
        }
    }

    void exchangeEdges(int layer, boolean intoNeighbours) {
        for (int i = 16; i < 32; i++) {
            exchangeColumn(layer, 15, i, EAST, intoNeighbours);
            exchangeColumn(layer, 32, i, WEST, intoNeighbours);
            exchangeColumn(layer, i, 15, SOUTH, intoNeighbours);
            exchangeColumn(layer, i, 32, NORTH, intoNeighbours);
        }
    }

    private void exchangeColumn(int layer, int x, int z, int direction, boolean intoNeighbour) {
        int cx = x + DX[direction];
        int cz = z + DZ[direction];
        int outerColumn = chunkSlot(x, z) * sectionCount;
        int innerColumn = chunkSlot(cx, cz) * sectionCount;
        int outerCell = (z & 15) << 4 | (x & 15);
        int innerCell = (cz & 15) << 4 | (cx & 15);
        for (int s = 0; s < sectionCount; s++) {
            if (!stored(outerColumn + s) || !stored(innerColumn + s)) {
                continue;
            }
            for (int yy = 0; yy < 16; yy++) {
                int outer = getAt(layer, outerColumn + s, yy << 8 | outerCell);
                int inner = getAt(layer, innerColumn + s, yy << 8 | innerCell);
                if (inner < outer - 1) {
                    enqueueIncrease(x, (s << 4) + yy, z, onlyOneDirection(outer, direction));
                } else if (intoNeighbour && outer < inner - 1) {
                    enqueueIncrease(cx, (s << 4) + yy, cz, onlyOneDirection(inner, direction ^ 1));
                }
            }
        }
    }

    void recheckStaleEdges(int layer) {
        for (int i = 16; i < 32; i++) {
            recheckLitColumn(layer, 16, i);
            recheckLitColumn(layer, 31, i);
            recheckLitColumn(layer, i, 16);
            recheckLitColumn(layer, i, 31);
        }
    }

    private void recheckLitColumn(int layer, int x, int z) {
        int column = chunkSlot(x, z) * sectionCount;
        int cell = (z & 15) << 4 | (x & 15);
        int lowest = layer == SKY && skyEnabled[chunkSlot(x, z)] ? terrain.lowestSkySource(x, z) : Integer.MAX_VALUE;
        for (int s = 0; s < sectionCount; s++) {
            if (!stored(column + s)) {
                continue;
            }
            for (int yy = 0; yy < 16; yy++) {
                int y = (s << 4) + yy;
                if (y < lowest && getAt(layer, column + s, yy << 8 | cell) > 0) {
                    recheck(layer, x, y, z);
                }
            }
        }
    }

    void recheck(int layer, int x, int y, int z) {
        if (!stored(x, y, z)) {
            return;
        }
        int current = get(layer, x, y, z);
        if (layer == SKY && skySource(x, y, z)) {
            if (current < 15) {
                set(SKY, x, y, z, 15);
            }
            long flags = skySourceDirections(x, y, z, terrain.lowestSkySource(x, z));
            if (flags != 15) {
                enqueueIncrease(x, y, z, flags);
            }
            return;
        }
        int cell = terrain.cell(x, y, z);
        int emission = layer == BLOCK ? Terrain.emission(cell) : 0;
        int supported = supportedLevel(layer, x, y, z, cell, emission);
        if (current > supported) {
            set(layer, x, y, z, 0);
            enqueueDecrease(x, y, z, allDirections(current) | (layer == BLOCK && !brighterNeighbour(x, y, z, current) ? DARKEN_ALL : 0));
            if (emission > 0) {
                enqueueIncrease(x, y, z, fromEmission(emission, Terrain.emptyShape(cell)));
            }
        } else if (current < supported) {
            enqueueDecrease(x, y, z, PULL_IN);
            if (emission > current) {
                enqueueIncrease(x, y, z, fromEmission(emission, Terrain.emptyShape(cell)));
            }
        }
    }

    private int supportedLevel(int layer, int x, int y, int z, int cell, int emission) {
        int opacity = Terrain.opacity(cell);
        boolean empty = Terrain.emptyShape(cell);
        int best = emission;
        for (int d = 0; d < 6; d++) {
            int nx = x + DX[d];
            int ny = y + DY[d];
            int nz = z + DZ[d];
            if (!stored(nx, ny, nz)) {
                continue;
            }
            int candidate = get(layer, nx, ny, nz) - opacity;
            if (candidate <= best) {
                continue;
            }
            boolean fromEmpty = Terrain.emptyShape(terrain.cell(nx, ny, nz));
            if (!(fromEmpty && empty) && terrain.occludes(nx, ny, nz, fromEmpty, x, y, z, empty, d ^ 1)) {
                continue;
            }
            best = candidate;
        }
        return best;
    }

    private void clearCentre(int layer) {
        for (int s = 0; s < sectionCount; s++) {
            int idx = CENTER * sectionCount + s;
            if (stored(idx)) {
                owned[layer][idx] = new byte[2048];
            }
        }
    }

    private void updateSkyColumn(int x, int z, int lowest) {
        int column = chunkSlot(x, z) * sectionCount;
        for (int y = lowest <= 0 ? -1 : Math.min(lowest, height) - 1; y >= 0; y--) {
            if (!stored(column + (y >> 4))) {
                y = y >> 4 << 4;
                continue;
            }
            if (get(SKY, x, y, z) != 15) {
                continue;
            }
            set(SKY, x, y, z, 0);
            enqueueDecrease(x, y, z, allDirections(15));
        }
        int highestNeighbour = Math.max(
                Math.max(neighbourLowestSkySource(x, z - 1), neighbourLowestSkySource(x, z + 1)),
                Math.max(neighbourLowestSkySource(x - 1, z), neighbourLowestSkySource(x + 1, z)));
        for (int y = Math.max(lowest, 0); y < height; y++) {
            if (!stored(column + (y >> 4))) {
                y = (y >> 4 << 4) + 15;
                continue;
            }
            if (get(SKY, x, y, z) == 15) {
                continue;
            }
            set(SKY, x, y, z, 15);
            if (y < highestNeighbour || y == lowest) {
                enqueueIncrease(x, y, z, skipOneDirection(15, false, UP));
            }
        }
    }

    static int packCell(int x, int y, int z) {
        return x | z << 6 | y << 12;
    }

    void checkBlocks(int[] cells, int count, boolean hasSky) {
        if (hasSky) {
            long[] seen = seenColumns;
            Arrays.fill(seen, 0L);
            for (int i = 0; i < count; i++) {
                int x = cells[i] & 63;
                int z = cells[i] >> 6 & 63;
                int column = x * SIZE + z;
                if (skyEnabled[chunkSlot(x, z)] && (seen[column >> 6] & 1L << column) == 0) {
                    seen[column >> 6] |= 1L << column;
                    updateSkyColumn(x, z, terrain.lowestSkySource(x, z));
                }
            }
            runQueues(SKY);
        }
        for (int i = 0; i < count; i++) {
            int x = cells[i] & 63;
            int z = cells[i] >> 6 & 63;
            int y = cells[i] >> 12;
            recheck(BLOCK, x, y, z);
            runQueues(BLOCK);
            recheckNeighbours(BLOCK, x, y, z);
            runQueues(BLOCK);
            if (hasSky) {
                recheck(SKY, x, y, z);
                recheckNeighbours(SKY, x, y, z);
                runQueues(SKY);
            }
        }
    }

    private void recheckNeighbours(int layer, int x, int y, int z) {
        for (int d = 0; d < 6; d++) {
            recheck(layer, x + DX[d], y + DY[d], z + DZ[d]);
        }
    }

    void lightCentre(boolean hasSky, boolean fromScratch) {
        for (int layer = BLOCK; layer <= (hasSky ? SKY : BLOCK); layer++) {
            if (fromScratch) {
                clearCentre(layer);
                if (layer == BLOCK) {
                    seedEmitters(CENTER);
                } else {
                    seedSkyColumns(CENTER);
                }
                exchangeEdges(layer, false);
            } else {
                exchangeEdges(layer, true);
                recheckStaleEdges(layer);
            }
            runQueues(layer);
        }
    }
}
