package com.zhddsj.chunkdeviation.utils;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.game.minecraft.launchwrapper.FabricServerTweaker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.FabricUtil;

import java.io.IOException;
import java.nio.file.Path;

import static net.minecraft.world.dimension.DimensionTypes.THE_NETHER;
public class ROFTool
{
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = false;
    public static boolean isNetherWorld(World world) {
        return world.getDimensionEntry().matchesKey(THE_NETHER);
    }
    public static Path getSavePath(ServerWorld world) {
       return   DimensionType.getSaveDirectory(world.getRegistryKey(), world.getServer().getSavePath(WorldSavePath.ROOT));
    }
    public static int fastHash(int x) {
        x = ((x >> 8) ^ x) * 0x119de1f3;
        x = ((x >> 8) ^ x) * 0x119de1f3;
        x = (x >> 8) ^ x;
        return x & 0xFFFF;
    }
    /** Decodes a packed chunk position (lower 32 bits = x, upper 32 bits = z). */
    public static ChunkPos getChunkPos(long value) {
        return new ChunkPos((int) (value & 0xFFFFFFFFL), (int) (value >>> 32));
    }

    public static String toString_(Vec3d vec) {
        return "x: %.4f, y: %.4f, z: %.4f".formatted(vec.x, vec.y, vec.z);
    }

    public static String toString_short(Vec3d vec) {
        return "(%.3f, %.3f, %.3f)".formatted(vec.x, vec.y, vec.z);
    }
    public static boolean canLoadAi(int id, int count, int max) {
        return (fastHash(id) % (count + 1)) <= max;
    }
    public static void rDEBUG(String message) {
        if (DEBUG) LOGGER.info("[ROF]{}", message);
    }


    public static void saveNBT2Data(ServerWorld world, String fileName, NbtCompound nbtCompound) {
        try {
            Path savePath = ROFTool.getSavePath(world).resolve("data").resolve(fileName);
            savePath.getParent().toFile().mkdirs();
            NbtIo.writeCompressed(nbtCompound, savePath);
        } catch (IOException e) {
            LOGGER.error("[ROF] Failed to save NBT data to {}: {}", fileName, e.getMessage());
        }
    }
}
