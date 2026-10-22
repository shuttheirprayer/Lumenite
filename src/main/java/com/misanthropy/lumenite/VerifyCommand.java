package com.misanthropy.lumenite;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class VerifyCommand {
    private VerifyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lumenite")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("verify")
                        .executes(context -> verify(context.getSource(), 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(context -> verify(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("stats")
                        .executes(context -> {
                            String stats = ((LightingHost) context.getSource().getLevel().getChunkSource().getLightEngine()).lumenite$lighting().stats();
                            context.getSource().sendSuccess(() -> Component.literal(stats), false);
                            String uncached = uncachedBlocks();
                            context.getSource().sendSuccess(() -> Component.literal(uncached), false);
                            return 1;
                        }))
                .then(Commands.literal("relight")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(context -> relight(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), false))))
                .then(Commands.literal("recheck")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(context -> relight(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), true)))));
    }

    private static String uncachedBlocks() {
        Map<String, Integer> byMod = new TreeMap<>();
        int total = 0;
        int uncached = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            total++;
            if (!PositionIndependence.check(block)) {
                uncached++;
                byMod.merge(BuiltInRegistries.BLOCK.getKey(block).getNamespace(), 1, Integer::sum);
            }
        }
        return "uncached block light: " + uncached + " of " + total + " blocks " + byMod;
    }

    private static int relight(CommandSourceStack source, int radius, boolean keepLight) {
        ServerLevel level = source.getLevel();
        ServerChunkCache cache = level.getChunkSource();
        ChunkPos centre = new ChunkPos(BlockPos.containing(source.getPosition()));
        int count = 0;
        for (int x = centre.x - radius; x <= centre.x + radius; x++) {
            for (int z = centre.z - radius; z <= centre.z + radius; z++) {
                LevelChunk chunk = cache.getChunkNow(x, z);
                if (chunk != null) {
                    cache.getLightEngine().lightChunk(chunk, keepLight);
                    count++;
                }
            }
        }
        int total = count;
        source.sendSuccess(() -> Component.literal("relighting " + total + " chunks"), false);
        return count;
    }

    private static int verify(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        ServerChunkCache cache = level.getChunkSource();
        ChunkPos centre = new ChunkPos(BlockPos.containing(source.getPosition()));
        List<LevelChunk> chunks = new ArrayList<>();
        Set<Long> loaded = new HashSet<>();
        for (int x = centre.x - radius; x <= centre.x + radius; x++) {
            for (int z = centre.z - radius; z <= centre.z + radius; z++) {
                LevelChunk chunk = cache.getChunkNow(x, z);
                if (chunk != null) {
                    chunks.add(chunk);
                    loaded.add(ChunkPos.asLong(x, z));
                }
            }
        }
        LightChunkGetter getter = new LightChunkGetter() {
            @Override
            public LightChunk getChunkForLighting(int x, int z) {
                return cache.getChunkForLighting(x, z);
            }

            @Override
            public BlockGetter getLevel() {
                return level;
            }
        };
        long start = System.nanoTime();
        LevelLightEngine vanilla = new LevelLightEngine(getter, true, level.dimensionType().hasSkyLight());
        for (LevelChunk chunk : chunks) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].hasOnlyAir()) {
                    vanilla.updateSectionStatus(SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(i)), false);
                }
            }
        }
        for (LevelChunk chunk : chunks) {
            vanilla.propagateLightSources(chunk.getPos());
        }
        while (vanilla.hasLightWork()) {
            vanilla.runLightUpdates();
        }
        long oracleMillis = (System.nanoTime() - start) / 1_000_000;
        LevelLightEngine live = level.getLightEngine();
        int compared = 0;
        int[] mismatches = new int[2];
        List<String> samples = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (LevelChunk chunk : chunks) {
            ChunkPos cp = chunk.getPos();
            boolean interior = true;
            for (int dx = -1; dx <= 1 && interior; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!loaded.contains(ChunkPos.asLong(cp.x + dx, cp.z + dz))) {
                        interior = false;
                        break;
                    }
                }
            }
            if (!interior) {
                continue;
            }
            compared++;
            for (LightLayer layer : LightLayer.values()) {
                LayerLightEventListener ours = live.getLayerListener(layer);
                LayerLightEventListener theirs = vanilla.getLayerListener(layer);
                for (int y = level.getMinBuildHeight() - 16; y < level.getMaxBuildHeight() + 16; y++) {
                    for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
                            pos.set(x, y, z);
                            int a = ours.getLightValue(pos);
                            int b = theirs.getLightValue(pos);
                            if (a != b) {
                                if (mismatches[layer.ordinal()] < 5) {
                                    StringBuilder sample = new StringBuilder(layer + " " + x + "," + y + "," + z + " lumenite=" + a + " vanilla=" + b + " " + level.getBlockState(pos).getBlock().getName().getString());
                                    for (Direction direction : Direction.values()) {
                                        BlockPos n = pos.relative(direction);
                                        sample.append(" | ").append(direction.getName()).append(' ').append(ours.getLightValue(n)).append('/').append(theirs.getLightValue(n)).append(' ').append(level.getBlockState(n).getBlock().getName().getString());
                                    }
                                    SectionPos section = SectionPos.of(pos);
                                    sample.append(" | section live=").append(live.getDebugData(layer, section)).append(" oracle=").append(vanilla.getDebugData(layer, section));
                                    sample.append(" lowestSource=").append(chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15));
                                    if (mismatches[layer.ordinal()] == 0) {
                                        BlockPos cursor = pos.immutable();
                                        for (int step = 0; step < 16 && ours.getLightValue(cursor) != theirs.getLightValue(cursor); step++) {
                                            BlockPos best = cursor;
                                            for (Direction direction : Direction.values()) {
                                                BlockPos n = cursor.relative(direction);
                                                if (theirs.getLightValue(n) > theirs.getLightValue(best)) {
                                                    best = n;
                                                }
                                            }
                                            if (best.equals(cursor)) {
                                                break;
                                            }
                                            cursor = best;
                                            sample.append(" >> ").append(cursor.getX()).append(',').append(cursor.getY()).append(',').append(cursor.getZ()).append(' ').append(level.getBlockState(cursor).getBlock().getName().getString()).append(" opacity ").append(level.getBlockState(cursor).getLightBlock(level, cursor)).append(" emits ").append(level.getBlockState(cursor).getLightEmission(level, cursor)).append(' ').append(ours.getLightValue(cursor)).append('/').append(theirs.getLightValue(cursor)).append(" section ").append(live.getDebugData(layer, SectionPos.of(cursor)));
                                        }
                                    }
                                    samples.add(sample.toString());
                                }
                                mismatches[layer.ordinal()]++;
                            }
                        }
                    }
                }
            }
        }
        String summary = "vanilla oracle lit " + chunks.size() + " chunks in " + oracleMillis + " ms; compared " + compared + " chunks: block mismatches " + mismatches[LightLayer.BLOCK.ordinal()] + ", sky mismatches " + mismatches[LightLayer.SKY.ordinal()];
        source.sendSuccess(() -> Component.literal(summary), false);
        for (String sample : samples) {
            source.sendSuccess(() -> Component.literal(sample), false);
        }
        Lumenite.LOGGER.info(summary);
        samples.forEach(Lumenite.LOGGER::info);
        return mismatches[0] + mismatches[1];
    }
}
