package com.misanthropy.lumenite;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public final class ClientVerifyCommand {
    private ClientVerifyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lumeniteclient")
                .then(Commands.literal("verify")
                        .executes(context -> verify(context.getSource(), 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(context -> verify(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("stats")
                        .executes(context -> {
                            LevelLighting lighting = ((LightingHost) Minecraft.getInstance().level.getLightEngine()).lumenite$lighting();
                            String stats = lighting == null ? "vanilla client light" : lighting.stats();
                            context.getSource().sendSuccess(() -> Component.literal(stats), false);
                            return 1;
                        })));
    }

    public static String verify(Minecraft minecraft, ClientLevel level, ChunkPos centre, int radius) {
        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(level.dimension());
        return minecraft.getSingleplayerServer().submit(() -> verify(level, serverLevel, centre, radius)).join();
    }

    private static String verify(ClientLevel level, ServerLevel serverLevel, ChunkPos centre, int radius) {
        LevelLightEngine client = level.getLightEngine();
        LevelLightEngine server = serverLevel.getLightEngine();
        int chunks = 0;
        int mismatches = 0;
        int blocks = 0;
        String first = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = centre.x - radius; x <= centre.x + radius; x++) {
            for (int z = centre.z - radius; z <= centre.z + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                LevelChunk serverChunk = serverLevel.getChunkSource().getChunkNow(x, z);
                if (chunk == null || serverChunk == null || !serverChunk.isLightCorrect()) {
                    continue;
                }
                chunks++;
                for (int i = 0; i < 4096 * (level.getSectionsCount()); i++) {
                    pos.set(x * 16 + (i & 15), level.getMinBuildHeight() + (i >> 8), z * 16 + (i >> 4 & 15));
                    if (chunk.getBlockState(pos) != serverChunk.getBlockState(pos)) {
                        if (blocks < 3) {
                            first = (first == null ? "" : first + "; ") + "block " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " client " + chunk.getBlockState(pos) + " server " + serverChunk.getBlockState(pos);
                        }
                        blocks++;
                    }
                }
                for (int s = client.getMinLightSection(); s < client.getMinLightSection() + client.getLightSectionCount(); s++) {
                    for (LightLayer layer : LightLayer.values()) {
                        DataLayer a = client.getLayerListener(layer).getDataLayerData(SectionPos.of(x, s, z));
                        DataLayer b = server.getLayerListener(layer).getDataLayerData(SectionPos.of(x, s, z));
                        if (a == null || b == null) {
                            continue;
                        }
                        for (int i = 0; i < 4096; i++) {
                            int cx = i & 15;
                            int cz = i >> 4 & 15;
                            int cy = i >> 8;
                            if (a.get(cx, cy, cz) != b.get(cx, cy, cz)) {
                                mismatches++;
                                if (first == null) {
                                    first = layer + " at " + (x * 16 + cx) + "," + (s * 16 + cy) + "," + (z * 16 + cz) + " client " + a.get(cx, cy, cz) + " server " + b.get(cx, cy, cz) + " in " + serverChunk.getBlockState(pos.set(x * 16 + cx, s * 16 + cy, z * 16 + cz));
                                }
                            }
                        }
                    }
                }
            }
        }
        return chunks + " chunks compared, " + mismatches + " light mismatches, " + blocks + " block state mismatches" + (first == null ? "" : "; first: " + first);
    }

    private static int verify(CommandSourceStack source, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (minecraft.getSingleplayerServer() == null || level == null) {
            source.sendFailure(Component.literal("Needs an integrated server"));
            return 0;
        }
        String result = verify(minecraft, level, new ChunkPos(minecraft.player.blockPosition()), radius);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
