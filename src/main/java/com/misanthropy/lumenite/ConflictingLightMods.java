package com.misanthropy.lumenite;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;

import java.util.List;

public final class ConflictingLightMods {
    private static final List<String> MOD_IDS = List.of("starlight", "lucis", "scalablelux", "moonrise");

    private ConflictingLightMods() {
    }

    public static String firstPresent() {
        LoadingModList mods = FMLLoader.getLoadingModList();
        if (mods == null) {
            return null;
        }
        for (String id : MOD_IDS) {
            if (mods.getModFileById(id) != null) {
                return id;
            }
        }
        return null;
    }
}
