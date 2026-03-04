package com.zhddsj.chunkdeviation.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkGenerating;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 ChunkGenerating 中对 serverWorld.getSeed() 的调用，
 * 确保镜像维度使用原始维度的种子。
 * 关键拦截点：carve() 方法中的 serverWorld.getSeed() — 用于洞穴雕刻。
 */
@Mixin(ChunkGenerating.class)
public abstract class ChunkGeneratingMixin {

    /**
     * 拦截 carve() 方法中对 ServerWorld.getSeed() 的调用。
     * 如果是镜像维度，返回对应原始维度的种子。
     */
    @Redirect(
            method = "carve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getSeed()J"
            )
    )
    private static long chunkDeviation$useMirrorSeedForCarve(ServerWorld serverWorld) {
        return getSourceWorldSeed(serverWorld);
    }

    /**
     * 获取原始维度的种子。如果当前维度不是镜像维度，返回自身种子。
     */
    @Unique
    private static long getSourceWorldSeed(ServerWorld world) {
        String dimName = world.getRegistryKey().getValue().toString();
        if (!dimName.contains("mirror_")) {
            return world.getSeed();
        }

        ServerWorld sourceWorld = null;
        if (dimName.contains("overworld")) {
            sourceWorld = world.getServer().getWorld(World.OVERWORLD);
        } else if (dimName.contains("nether")) {
            sourceWorld = world.getServer().getWorld(World.NETHER);
        } else if (dimName.contains("end")) {
            sourceWorld = world.getServer().getWorld(World.END);
        }

        if (sourceWorld != null) {
            return sourceWorld.getSeed();
        }
        return world.getSeed();
    }
}

