package com.zhddsj.chunkcheck.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zhddsj.chunkcheck.comparison.ChunkComparator;
import com.zhddsj.chunkcheck.storage.ChunkDiffStorage;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

public class ChunkCompareCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("chunkcompare")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                                .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(ChunkCompareCommand::executeCompare)))
                        .then(CommandManager.literal("query")
                                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(ChunkCompareCommand::executeQuery))))
                        .then(CommandManager.literal("here")
                                .executes(ChunkCompareCommand::executeHere))
        );
    }

    private static int executeCompare(CommandContext<ServerCommandSource> ctx) {
        int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
        return runCompare(ctx.getSource(), chunkX, chunkZ);
    }

    private static int executeHere(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ChunkPos pos = player.getChunkPos();
            return runCompare(source, pos.x, pos.z);
        } catch (Exception e) {
            source.sendError(Text.literal("[ChunkCompare] This subcommand requires a player context."));
            return 0;
        }
    }

    private static int executeQuery(CommandContext<ServerCommandSource> ctx) {
        int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
        ServerCommandSource source = ctx.getSource();
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        ServerWorld overworld = source.getServer().getWorld(World.OVERWORLD);
        if (overworld == null) {
            source.sendError(Text.literal("[ChunkCompare] Overworld is not available."));
            return 0;
        }

        ChunkDiffStorage storage = ChunkDiffStorage.getOrCreate(overworld.getPersistentStateManager());
        int diff = storage.getDiff(chunkPos);
        if (diff < 0) {
            source.sendMessage(Text.literal(String.format(
                    "[ChunkCompare] Chunk (%d, %d) has no recorded diff. Run /chunkcompare %d %d first.",
                    chunkX, chunkZ, chunkX, chunkZ)));
        } else {
            source.sendMessage(Text.literal(String.format(
                    "[ChunkCompare] Chunk (%d, %d) last recorded diff: %d block(s) modified.",
                    chunkX, chunkZ, diff)));
        }
        return 1;
    }

    /**
     * Runs the comparison synchronously on the main thread.
     * The server will appear frozen for the duration (typically a few seconds).
     * Commands are always executed on the main thread, so this is safe.
     */
    private static int runCompare(ServerCommandSource source, int chunkX, int chunkZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        ServerWorld overworld = source.getServer().getWorld(World.OVERWORLD);
        if (overworld == null) {
            source.sendError(Text.literal("[ChunkCompare] Overworld is not available."));
            return 0;
        }

        source.sendMessage(Text.literal(String.format(
                "[ChunkCompare] Comparing chunk (%d, %d), please wait...", chunkX, chunkZ)));

        try {
            Map<String, Integer> diffMap =
                    ChunkComparator.compare(source.getServer(), overworld, chunkPos);

            int total = diffMap.values().stream().mapToInt(Integer::intValue).sum();

            ChunkDiffStorage storage = ChunkDiffStorage.getOrCreate(overworld.getPersistentStateManager());
            storage.setDiff(chunkPos, total);

            source.sendMessage(Text.literal(String.format(
                    "[ChunkCompare] Chunk (%d, %d): %d block(s) differ from original generation.",
                    chunkX, chunkZ, total)));

            if (total > 0) {
                List<Map.Entry<String, Integer>> sorted = diffMap.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .toList();
                for (Map.Entry<String, Integer> entry : sorted) {
                    source.sendMessage(Text.literal(
                            String.format("  x%-5d  %s", entry.getValue(), entry.getKey())));
                }
            }
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            source.sendError(Text.literal("[ChunkCompare] Error: " + cause.getMessage()));
            cause.printStackTrace();
            return 0;
        }

        return 1;
    }
}
