package com.zhddsj.chunkdeviation.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerationStep;
import net.minecraft.util.collection.BoundedRegionArray;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 确保镜像维度的 ChunkRegion 使用与原始维度完全相同的种子来初始化 BiomeAccess。
 *
 * BiomeAccess 使用 hashSeed(seed) 来进行生物群系的抖动采样，
 * 如果 seed 不同，即使生物群系源相同，实际选中的生物群系也可能在边界处不同。
 *
 * ChunkRegion.seed 字段直接来自 world.getSeed()，
 * 而 ServerWorld.getSeed() 返回全局种子，所以理论上应该相同。
 * 但为了绝对保险，此 mixin 明确强制镜像维度使用原始维度的种子。
 */
@Mixin(ChunkRegion.class)
public abstract class ChunkRegionMixin {

    @Shadow @Final @Mutable
    private long seed;

    @Shadow @Final @Mutable
    private BiomeAccess biomeAccess;


    @Inject(method = "<init>", at = @At("TAIL"))
    private void chunkDeviation$forceMirrorSeed(ServerWorld world, BoundedRegionArray<AbstractChunkHolder> chunks,
                                                 ChunkGenerationStep generationStep, Chunk centerPos, CallbackInfo ci) {
        String dimName = world.getRegistryKey().getValue().toString();
        if (!dimName.contains("mirror_")) {
            return;
        }

        // 确定对应的原始维度
        ServerWorld sourceWorld = null;
        if (dimName.contains("overworld")) {
            sourceWorld = world.getServer().getWorld(World.OVERWORLD);
        } else if (dimName.contains("nether")) {
            sourceWorld = world.getServer().getWorld(World.NETHER);
        } else if (dimName.contains("end")) {
            sourceWorld = world.getServer().getWorld(World.END);
        }

        if (sourceWorld != null) {
            long sourceSeed = sourceWorld.getSeed();
            // 强制使用原始维度的种子
            if (this.seed != sourceSeed) {
                this.seed = sourceSeed;
                // 重建 BiomeAccess 以确保使用正确的 hashSeed
                this.biomeAccess = new BiomeAccess((ChunkRegion)(Object)this, BiomeAccess.hashSeed(sourceSeed));
            }
        }
    }
}

