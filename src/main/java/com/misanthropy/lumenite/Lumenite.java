package com.misanthropy.lumenite;

import com.mojang.logging.LogUtils;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Lumenite.MODID)
public class Lumenite {
    public static final String MODID = "lumenite";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static volatile ExtraLight extraLight = ExtraLight.NONE;

    public Lumenite() {
        String conflict = ConflictingLightMods.firstPresent();
        if (conflict != null) {
            LOGGER.warn("Lumenite is disabled: another light engine mod ({}) is installed", conflict);
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> VerifyCommand.register(event.getDispatcher()));
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> ClientVerifyCommand.register(event.getDispatcher()));
        }
    }
}
