package com.misanthropy.lumenite;

import net.minecraft.world.level.chunk.DataLayer;

import java.util.Arrays;

public final class Uniform {
    private static final byte[][] FILLED = new byte[16][];
    private static final DataLayer[] SHARED = new DataLayer[16];

    static {
        for (int v = 0; v < 16; v++) {
            FILLED[v] = new byte[2048];
            Arrays.fill(FILLED[v], (byte) (v | v << 4));
            SHARED[v] = new DataLayer(FILLED[v]);
        }
    }

    private Uniform() {
    }

    public static int value(byte[] data) {
        for (int v = 0; v < 16; v++) {
            if (data == FILLED[v]) {
                return v;
            }
        }
        return LightWindow.uniform(data);
    }

    public static DataLayer compact(DataLayer layer) {
        if (layer == null || layer.isDefinitelyHomogenous()) {
            return layer;
        }
        int v = value(layer.getData());
        return v < 0 ? layer : new DataLayer(v);
    }

    public static DataLayer packetCopy(DataLayer layer) {
        return layer.isDefinitelyHomogenous() ? SHARED[layer.get(0, 0, 0)] : layer.copy();
    }
}
