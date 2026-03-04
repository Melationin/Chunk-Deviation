package com.zhddsj.chunkdeviation.mixin;

import com.zhddsj.chunkdeviation.ChunkDeviation;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 强制镜像维度使用与对应原始维度完全相同的：
 * - ChunkGenerator 实例（包含 BiomeSource、indexedFeaturesList、generationSettingsGetter）
 * - NoiseConfig 实例（包含噪声采样器、随机数分裂器、表面构建器）
 * - StructurePlacementCalculator 实例
 *
 * 这是解决树木/地物差异的关键：ChunkGenerator.generateFeatures() 中使用
 * this.biomeSource.getBiomes() 和 this.indexedFeaturesListSupplier 来决定
 * 哪些 feature 以什么顺序生成。两个独立创建的 ChunkGenerator 实例的
 * BiomeSource 内部的 RegistryEntry 对象集合可能有不同的迭代顺序或引用，
 * 导致 feature 索引映射不同，从而产生不同的 decorator seed。
 *
 * 通过直接共享主世界的 ChunkGenerator 实例，彻底消除此问题。
 */
@Mixin(ServerChunkLoadingManager.class)
public abstract class MirrorDimensionSyncMixin {

    @Shadow @Final
    ServerWorld world;

    @Shadow @Final @Mutable
    private NoiseConfig noiseConfig;

    @Shadow @Final @Mutable
    private StructurePlacementCalculator structurePlacementCalculator;

    @Shadow @Final @Mutable
    private ChunkGenerationContext generationContext;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chunkDeviation$syncMirrorInstances(CallbackInfo ci) {
        String dimName = this.world.getRegistryKey().getValue().toString();
        if (!dimName.contains("mirror_")) {
            return;
        }

        // 根据镜像维度名称确定对应的原始维度
        ServerWorld sourceWorld = null;
        if (dimName.contains("overworld")) {
            sourceWorld = this.world.getServer().getWorld(World.OVERWORLD);
        } else if (dimName.contains("nether")) {
            sourceWorld = this.world.getServer().getWorld(World.NETHER);
        } else if (dimName.contains("end")) {
            sourceWorld = this.world.getServer().getWorld(World.END);
        }

        if (sourceWorld == null) {
            return;
        }

        // 1. 同步 NoiseConfig — 控制噪声、地形、含水层、矿石随机等
        NoiseConfig sourceNoiseConfig = sourceWorld.getChunkManager().getNoiseConfig();
        if (sourceNoiseConfig != null) {
            this.noiseConfig = sourceNoiseConfig;
            ChunkDeviation.LOGGER.info("[ChunkDeviation] 镜像维度 {} 同步 NoiseConfig <- {}",
                    dimName, sourceWorld.getRegistryKey().getValue());
        }

        // 2. 同步 StructurePlacementCalculator — 控制结构放置位置
        StructurePlacementCalculator sourcePlacementCalc = sourceWorld.getChunkManager().getStructurePlacementCalculator();
        if (sourcePlacementCalc != null) {
            this.structurePlacementCalculator = sourcePlacementCalc;
            ChunkDeviation.LOGGER.info("[ChunkDeviation] 镜像维度 {} 同步 StructurePlacementCalculator <- {}",
                    dimName, sourceWorld.getRegistryKey().getValue());
        }

        // 3. 同步 ChunkGenerator（通过替换 generationContext）
        // 这是解决树木/地物差异的关键！ChunkGenerator 实例内含：
        //   - biomeSource: 决定 getBiomes() 返回哪些 RegistryEntry<Biome>
        //   - indexedFeaturesListSupplier: 决定 feature 的全局索引映射
        //   - generationSettingsGetter: 决定 biome -> feature 列表的映射
        // 这三者共同决定了每个区块生成哪些 feature 以及 decoratorSeed
        ChunkGenerator sourceGenerator = sourceWorld.getChunkManager().getChunkGenerator();
        if (sourceGenerator != null) {
            // 重建 generationContext，替换 generator 为主世界的，保留镜像维度自己的 world 和其他组件
            this.generationContext = new ChunkGenerationContext(
                    this.generationContext.world(),            // 保持镜像维度的 ServerWorld
                    sourceGenerator,                           // 使用主世界的 ChunkGenerator
                    this.generationContext.structureManager(),  // 保持自己的结构模板管理器
                    this.generationContext.lightingProvider(),  // 保持自己的光照提供器
                    this.generationContext.mainThreadExecutor(),// 保持自己的主线程执行器
                    this.generationContext.unsavedListener()    // 保持自己的未保存监听器
            );
            ChunkDeviation.LOGGER.info("[ChunkDeviation] 镜像维度 {} 同步 ChunkGenerator <- {}",
                    dimName, sourceWorld.getRegistryKey().getValue());
        }
    }
}

