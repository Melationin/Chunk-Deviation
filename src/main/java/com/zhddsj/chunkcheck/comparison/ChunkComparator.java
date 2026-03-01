package com.zhddsj.chunkcheck.comparison;

import com.zhddsj.chunkcheck.world.MirrorWorldManager;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChunkComparator {

    /**
     * Must be called on the SERVER MAIN THREAD.
     * Drives mirrorWorld's MainThreadExecutor.runTasks() loop internally.
     */
    public static Map<String, Integer> compare(
            MinecraftServer server, ServerWorld overworld, ChunkPos chunkPos) {

        // 1. Snapshot live chunk
        WorldChunk liveChunk = overworld.getChunk(chunkPos.x, chunkPos.z);
        int minY   = overworld.getBottomY();
        int maxY   = overworld.getTopYInclusive();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int height = maxY - minY + 1;
        BlockState[] liveStates = snapshotChunk(liveChunk, startX, startZ, minY, height);

        // 2. Mirror world: create → generate → close, all on main thread
        MirrorWorldManager mirror;
        try {
            mirror = MirrorWorldManager.create(server);
        } catch (IOException e) {
            throw new RuntimeException("Mirror world creation failed: " + e.getMessage(), e);
        }

        WorldChunk refChunk;
        try {
            refChunk = mirror.generateChunk(chunkPos);
        } finally {
            mirror.close();
        }

        // 3. Exact comparison
        Map<String, Integer> diffMap = new HashMap<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int dy = 0; dy < height; dy++) {
                    int y = minY + dy;
                    pos.set(startX + x, y, startZ + z);
                    BlockState live = liveStates[x * 16 * height + z * height + dy];
                    BlockState ref  = refChunk.getBlockState(pos);
                    if (live.getBlock() == ref.getBlock()) continue;
                    String liveId = Registries.BLOCK.getId(live.getBlock()).toString();
                    String refId  = Registries.BLOCK.getId(ref.getBlock()).toString();
                    diffMap.merge("now=" + liveId + "  was=" + refId, 1, Integer::sum);
                }
            }
        }
        return diffMap;
    }

    private static BlockState[] snapshotChunk(
            WorldChunk chunk, int startX, int startZ, int minY, int height) {
        BlockState[] states = new BlockState[16 * 16 * height];
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int dy = 0; dy < height; dy++) {
                    pos.set(startX + x, minY + dy, startZ + z);
                    states[x * 16 * height + z * height + dy] = chunk.getBlockState(pos);
                }
            }
        }
        return states;
    }
}
