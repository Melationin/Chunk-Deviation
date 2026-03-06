package com.zhddsj.chunkdeviation;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ChunkLevels;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 独立原型世界：完全绕开 ServerChunkManager，
 * 自己手动驱动 ChunkGenerator 流水线生成 ProtoChunk。
 */
public class ProtoWorld {

    final ServerWorld world;

    /**
     * 全局 ProtoChunk 缓存。
     * 使用 ConcurrentHashMap：多个线程可能并发调用 generate()。
     */
    final ConcurrentHashMap<ChunkPos, SimpleChunkHolder> cache = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ChunkPos, ProtoChunk> cacheB = new ConcurrentHashMap<>();
    public ProtoWorld(ServerWorld world) {
        this.world = world;
    }

    // ────────────────────────────────────────────────
    // 公开 API
    // ────────────────────────────────────────────────

    /**
     * 同步生成指定区块至 targetStatus（含）。
     * 不要传入 FULL（会转为 WorldChunk）。
     */
    public ProtoChunk generate(ChunkPos center, ChunkStatus targetStatus) {
        ensureStatus(center, targetStatus);
        return cache.get(center);
    }

    public void invalidate(ChunkPos pos) { cache.remove(pos); }
    public void invalidateAll() { cache.clear(); }

    // ────────────────────────────────────────────────
    // 内部：递归确保 pos 达到 targetStatus
    // ────────────────────────────────────────────────

    /**
     * 确保 pos 处的区块已达到 targetStatus。
     * 对每个需要推进的步骤：
     *   1. 先递归确保所有 directDependencies 范围内的周边块达到该步骤要求的状态
     *   2. 再对 pos 自身执行该步骤
     *
     * synchronized 保证同一个 ProtoWorld 实例不会并发写同一个区块。
     */
    private  void ensureStatus(ChunkPos pos, ChunkStatus targetStatus) {
        ProtoChunk pc = getOrCreate(pos).chunk;
        BoundedRegionArray<AbstractChunkHolder> holders = BoundedRegionArray.create(
                pos.x, pos.z, 8,
                (hx, hz) -> getOrCreate(new ChunkPos(hx, hz))
        );

        if (pc.getStatus().isAtLeast(targetStatus)) return;

        for (ChunkStatus status : ChunkStatus.createOrderedList()) {
            if (pc.getStatus().isAtLeast(status)) continue; // 已完成该步骤，跳过
            if (status == ChunkStatus.FULL) break;

            ChunkGenerationStep step = ChunkGenerationSteps.GENERATION.get(status);
            int depSize = step.directDependencies().size();

            // 先递归确保所有周边依赖块达到该步骤要求的最低状态
            // directDependencies.get(dist) = 距离 dist 处的块需要达到的状态
            for (int dx = -(depSize - 1); dx <= (depSize - 1); dx++) {
                for (int dz = -(depSize - 1); dz <= (depSize - 1); dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int dist = Math.max(Math.abs(dx), Math.abs(dz));
                    if (dist >= depSize) continue;
                    ChunkStatus depStatus = step.directDependencies().get(dist);
                    ensureStatus(new ChunkPos(pos.x + dx, pos.z + dz), depStatus);
                }
            }
            runStep(step, pos, pc, holders);
            cache.put(new ChunkPos(pos.x, pos.z), pc);
            if (status == targetStatus) break;
        }
    }

    private void ensureStatus(Set<ChunkPos> map, ChunkStatus targetStatus)
    {
        Map<ChunkPos, BoundedRegionArray<AbstractChunkHolder>> regions = new HashMap<>();

        for (ChunkStatus status : ChunkStatus.createOrderedList()) {
            ChunkGenerationStep step = ChunkGenerationSteps.GENERATION.get(status);
            int depSize = step.directDependencies().size();
        }
    }

    private SimpleChunkHolder getOrCreate(ChunkPos pos) {
        return cache.computeIfAbsent(pos, p ->
                new SimpleChunkHolder(pos,new ProtoChunk(
                        p, UpgradeData.NO_UPGRADE_DATA, world,
                        world.getRegistryManager().getOrThrow(RegistryKeys.BIOME), null))
                );
    }

    // ────────────────────────────────────────────────
    // 内部：执行单个生成步骤
    // ────────────────────────────────────────────────

    private void runStep(ChunkGenerationStep step, ChunkPos pos,
                         ProtoChunk chunk, BoundedRegionArray<AbstractChunkHolder> holders) {
        ChunkStatus status = step.targetStatus();
        ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
        NoiseConfig noise = world.getChunkManager().getNoiseConfig();

        try {
            if (status == ChunkStatus.STRUCTURE_STARTS) {
                if (world.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures()) {
                    gen.setStructureStarts(
                            world.getRegistryManager(),
                            world.getChunkManager().getStructurePlacementCalculator(),
                            world.getStructureAccessor(),
                            chunk,
                            world.getServer().getStructureTemplateManager(),
                            world.getRegistryKey());
                }
                world.cacheStructures(chunk);

            } else if (status == ChunkStatus.STRUCTURE_REFERENCES) {
                ChunkRegion region = new ChunkRegion(world, holders, step, chunk);
                gen.addStructureReferences(region, world.getStructureAccessor().forRegion(region), chunk);

            } else if (status == ChunkStatus.BIOMES) {
                ChunkRegion region = new ChunkRegion(world, holders, step, chunk);
                gen.populateBiomes(noise, Blender.getBlender(region),
                        world.getStructureAccessor().forRegion(region), chunk).join();

            } else if (status == ChunkStatus.NOISE) {
                ChunkRegion region = new ChunkRegion(world, holders, step, chunk);
                gen.populateNoise(Blender.getBlender(region), noise,
                        world.getStructureAccessor().forRegion(region), chunk).join();
                BelowZeroRetrogen retrogen = chunk.getBelowZeroRetrogen();
                if (retrogen != null) {
                    BelowZeroRetrogen.replaceOldBedrock(chunk);
                    if (retrogen.hasMissingBedrock()) retrogen.fillColumnsWithAirIfMissingBedrock(chunk);
                }

            } else if (status == ChunkStatus.SURFACE) {
                ChunkRegion region = new ChunkRegion(world, holders, step, chunk);
                gen.buildSurface(region, world.getStructureAccessor().forRegion(region), noise, chunk);

            } else if (status == ChunkStatus.CARVERS) {
                ChunkRegion region = new ChunkRegion(world, holders, step, chunk);
                Blender.createCarvingMasks(region, chunk);
                gen.carve(region, world.getSeed(), noise,
                        world.getBiomeAccess(), world.getStructureAccessor().forRegion(region), chunk);

            } else if (status == ChunkStatus.FEATURES) {
                Heightmap.populateHeightmaps(chunk, EnumSet.of(
                        Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        Heightmap.Type.OCEAN_FLOOR, Heightmap.Type.WORLD_SURFACE));
                ChunkRegion region = new ChunkRegion(world, holders, step, chunk);
                gen.generateFeatures(region, chunk, world.getStructureAccessor().forRegion(region));
                Blender.tickLeavesAndFluids(region, chunk);
            }
            // EMPTY / INITIALIZE_LIGHT / LIGHT / SPAWN 无需操作

            chunk.setStatus(status);

        } catch (Exception e) {
            ChunkDeviation.LOGGER.error("[ProtoWorld] step={} pos={} failed", status.getId(), pos, e);
        }
    }

    // ────────────────────────────────────────────────
    // 内部类：AbstractChunkHolder 最简实现
    // ────────────────────────────────────────────────

    /**
     * 持有一个 ProtoChunk，让 ChunkRegion 的 getUncheckedOrNull() 能查到它。
     * 构造时将同一个 done-future 注入所有槽位（0 … SPAWN），
     * 使 ChunkRegion 对任意 status 的查询都能命中。
     */
    static final class SimpleChunkHolder extends AbstractChunkHolder {

        final ProtoChunk chunk;

        SimpleChunkHolder(ChunkPos pos, ProtoChunk chunk) {
            super(pos);
            this.chunk = chunk;
            CompletableFuture<OptionalChunk<Chunk>> done =
                    CompletableFuture.completedFuture(OptionalChunk.of(chunk));
            int maxIdx = ChunkStatus.FULL.getIndex() - 1; // SPAWN = index 10
            for (int i = 0; i <= maxIdx; i++) {
                chunkFuturesByStatus.compareAndSet(i, null, done);
            }
            this.status  = ChunkStatus.FULL; // cannotBeLoaded() 永远返回 false
        }

        @Override public int getLevel() { return ChunkLevels.getLevelFromType(ChunkLevelType.FULL); }
        @Override public int getCompletedLevel() { return getLevel(); }
        @Override protected void combineSavingFuture(CompletableFuture<?> f) { /* 不保存 */ }
    }
    static final class SimpleChunkLoader {
        SimpleChunkHolder holder;


        SimpleChunkLoader(SimpleChunkHolder holder) {
            this.holder = holder;
        }


    }
}
