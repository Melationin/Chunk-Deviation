package com.zhddsj.chunkdeviation.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 ChunkGenerator.generateFeatures() 中对 world.getSeed() 的调用。
 *
 * 在 generateFeatures 中，关键的种子使用：
 * long l = chunkRandom.setPopulationSeed(world.getSeed(), blockPos.getX(), blockPos.getZ());
 *
 * 虽然 ServerWorld.getSeed() 理论上对所有维度返回相同值（全局种子），
 * 但 StructureWorldAccess.getSeed() 实际上由 ChunkRegion.getSeed() 实现，
 * 它使用 ChunkRegion 构造时缓存的 seed 字段。
 *
 * 此 mixin 作为最后一道保险，确保即使 ChunkRegion 的 seed 出现意外差异，
 * 特征生成仍然使用正确的种子。
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorFeatureMixin {

    @Redirect(
            method = "generateFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/StructureWorldAccess;getSeed()J"
            )
    )
    private long chunkDeviation$useMirrorSeedForFeatures(StructureWorldAccess world) {
        if (world instanceof net.minecraft.world.ChunkRegion chunkRegion) {
            ServerWorld serverWorld = chunkRegion.toServerWorld();
            String dimName = serverWorld.getRegistryKey().getValue().toString();
            if (dimName.contains("mirror_")) {
                ServerWorld sourceWorld = null;
                if (dimName.contains("overworld")) {
                    sourceWorld = serverWorld.getServer().getWorld(World.OVERWORLD);
                } else if (dimName.contains("nether")) {
                    sourceWorld = serverWorld.getServer().getWorld(World.NETHER);
                } else if (dimName.contains("end")) {
                    sourceWorld = serverWorld.getServer().getWorld(World.END);
                }
                if (sourceWorld != null) {
                    return sourceWorld.getSeed();
                }
            }
        }
        return world.getSeed();
    }
}

