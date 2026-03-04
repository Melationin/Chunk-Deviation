package com.zhddsj.chunkdeviation.comparison;

import com.mojang.datafixers.util.Pair;
import com.zhddsj.chunkdeviation.utils.ROFIO;
import com.zhddsj.chunkdeviation.utils.ROFTool;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

public class ChunkComparator
{

    ServerWorld world;
    ServerWorld mirrorWorld;
    ChunkComparatorConfig config;


    public ChunkComparator(ServerWorld serverWorld, ServerWorld mirrorWorld, ChunkComparatorConfig config)
    {
        this.world = serverWorld;
        this.mirrorWorld = mirrorWorld;
        this.config = config;
    }


    public record RegionMeta(Path path, int x, int z)
    {
    }

    public CompletableFuture<Map<ChunkPos, ChunkDiffResult>> compareWorld(Predicate<ChunkPos> predicateChunk, ChunkComparatorResult result)
    {
       ArrayList<RegionMeta> regionMetas = getRegionFilter(predicateChunk);

        result.totalRegions = regionMetas.size();

        ArrayList<CompletableFuture<Map<ChunkPos, ChunkDiffResult>>> futures = new ArrayList<>();

        for (RegionMeta regionMeta : regionMetas) {
            futures.add(compareRegion(regionMeta, predicateChunk));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v ->
        {
            Map<ChunkPos, ChunkDiffResult> finalResult = new HashMap<>();
            for (CompletableFuture<Map<ChunkPos, ChunkDiffResult>> future : futures) {
                try {
                    Map<ChunkPos, ChunkDiffResult> regionResult = future.get();
                    finalResult.putAll(regionResult);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return finalResult;
        });

    }

    public ArrayList<RegionMeta> getRegionFilter(Predicate<ChunkPos> predicateChunk){
        Path regionPath = ROFTool.getSavePath(world).resolve("region");
        if (!regionPath.toFile().isDirectory()) {
            return new ArrayList<>();
        }
        var folder = regionPath.toFile();
        String[] files = folder.list((dir, name) -> name.startsWith("r") && name.endsWith(".mca"));
        ArrayList<RegionMeta> regionMetas = new ArrayList<>();
        for (String fileName : files) {
            String[] split = fileName.split("\\.");

            if (split.length == 4 && split[0].equals("r") && split[3].equals("mca")) {
                int x = Integer.parseInt(split[1]);
                int z = Integer.parseInt(split[2]);
                if (predicateChunk == null||
                        predicateChunk.test(new ChunkPos(x * 32, z * 32)) || predicateChunk.test(
                        new ChunkPos(x * 32 + 31, z * 32)) || predicateChunk.test(
                        new ChunkPos(x * 32, z * 32 + 31)) || predicateChunk.test(new ChunkPos(x * 32 + 31, z * 32 + 31))) {
                    regionMetas.add(new RegionMeta(regionPath, x, z));
                }
            }
        }
        return regionMetas;
    }


    public CompletableFuture<Map<ChunkPos, ChunkDiffResult>> compareRegion(RegionMeta regionMeta, @Nullable Predicate<ChunkPos> predicateChunk)
    {
        // ── 阶段 1：顺序 I/O 读取 + 并行 NBT 解析 + 标记块扫描 ──
        // 整个阶段在调用线程（工作线程）上执行，I/O 不可并行，解析可以并行。
        // 不用外层 supplyAsync 包装，避免 ForkJoinPool worker 被 join() 堵死（线程饥饿死锁）。

        ConcurrentMap<ChunkPos, SerializedChunk> serializedChunkMap = new ConcurrentHashMap<>();
        ConcurrentMap<ChunkPos, ChunkDiffResult> earlyResults = new ConcurrentHashMap<>();

        // 顺序读取所有原始 NbtCompound（RegionFile 不是线程安全的）
        List<net.minecraft.nbt.NbtCompound> rawNbtList = new ArrayList<>(1024);
        ROFIO.loadFromRegion(regionMeta.path, regionMeta.x, regionMeta.z, world, rawNbtList::add);

        // 并行解析 NBT → SerializedChunk + 标记块扫描
        List<CompletableFuture<Void>> parseTasks = new ArrayList<>(rawNbtList.size());
        for (net.minecraft.nbt.NbtCompound chunkData : rawNbtList) {
            parseTasks.add(CompletableFuture.runAsync(() ->
            {
                SerializedChunk serializedChunk = SerializedChunk.fromNbt(world, world.getRegistryManager(), chunkData);
                if (serializedChunk == null) {
                    System.err.println("miss chunk data");
                    return;
                }
                if (predicateChunk != null && !predicateChunk.test(serializedChunk.chunkPos()))
                    return;
                if (serializedChunk.chunkStatus() != ChunkStatus.FULL) {
                    earlyResults.put(serializedChunk.chunkPos(), new ChunkDiffResult(-1, 0, null));
                    return;
                }
                // 标记块扫描：命中标记块则直接记录结果，跳过后续对比
                for (var section : serializedChunk.sectionData()) {
                    if (section.chunkSection() == null) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState act = section.chunkSection().getBlockState(x, y, z);
                                if (config.isMark(act.getBlock())) {
                                    earlyResults.put(serializedChunk.chunkPos(),
                                            new ChunkDiffResult(-1, 114514, null));
                                    return;
                                }
                            }
                        }
                    }
                }
                serializedChunkMap.put(serializedChunk.chunkPos(), serializedChunk);
            }, ForkJoinPool.commonPool()));
        }
        // 等待所有解析任务完成（此时在工作线程上 join，不占用主线程）
        CompletableFuture.allOf(parseTasks.toArray(new CompletableFuture[0])).join();

        // ── 阶段 2：注册 ticket + 获取区块 future ──
        // 关键：getChunkFutureSyncOnMainThread 必须从【非主线程】调用，
        // 它会把 getChunkFuture 提交到 mainThreadExecutor 并返回一个 pending future，
        // 主线程继续自由 tick 时会推进这些 future 完成。
        // 绝对不能在主线程任务内部调用并等待结果（会死锁）。
        //
        // ticket 注册也需要在主线程执行（ChunkTicketManager 非线程安全），
        // 但只需 fire-and-forget（execute），不需要等它完成再调 getChunkFutureSyncOnMainThread：
        // getChunkFutureSyncOnMainThread 非主线程路径本身会把 getChunkFuture 提交到主线程队列，
        // 该提交会排在 execute(addTicket) 之后，因此 ticket 一定先注册。

        List<CompletableFuture<Map.Entry<ChunkPos, ChunkDiffResult>>> allFutures = new ArrayList<>();

        // 早期结果直接封装
        for (Map.Entry<ChunkPos, ChunkDiffResult> e : earlyResults.entrySet()) {
            allFutures.add(CompletableFuture.completedFuture(Map.entry(e.getKey(), e.getValue())));
        }

        for (SerializedChunk serializedChunk : serializedChunkMap.values()) {
            ChunkPos chunkPos = serializedChunk.chunkPos();

            // fire-and-forget：在主线程注册 ticket
            mirrorWorld.getServer().execute(() ->
                    mirrorWorld.getChunkManager().addTicket(
                            new ChunkTicket(ChunkTicketType.FORCED,
                                    ChunkLevels.getLevelFromType(ChunkLevelType.FULL)),
                            chunkPos));

            // 从工作线程调用（非主线程路径）：把 getChunkFuture 提交到 mainThreadExecutor 队列，
            // 主线程在后续 tick 中会执行它并推进区块加载，不会阻塞主线程。
            CompletableFuture<Chunk> chunkFuture =
                    mirrorWorld.getChunkManager()
                            .getChunkFutureSyncOnMainThread(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true)
                            .thenApply(opt -> opt.orElse(null));

            // ── 阶段 3：区块加载完毕后在 ForkJoinPool 中并行计算 diff ──
            allFutures.add(chunkFuture.thenApplyAsync(chunk ->
            {
                if (chunk == null) {
                    System.err.println("miss chunk " + chunkPos);
                    mirrorWorld.getServer().execute(() ->
                            mirrorWorld.getChunkManager().removeTicket(
                                    ChunkTicketType.FORCED, chunkPos, 1));
                    return Map.entry(chunkPos, new ChunkDiffResult(0, 0, null));
                }

                int totalBlocks = 0;
                long modifiedBlocks = 0;
                int bottomY = mirrorWorld.getBottomY();
                Map<Pair<Block, Block>, Integer> diffMap = new HashMap<>();
                BlockPos.Mutable mutable = new BlockPos.Mutable();

                int sectionIdx = 0;
                checkChunkLoop:
                for (var section : serializedChunk.sectionData()) {
                    if (section.chunkSection() == null) {
                        sectionIdx++;
                        continue;
                    }
                    int sy = sectionIdx * 16 + bottomY;
                    sectionIdx++;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                mutable.set(x + chunkPos.x * 16, y + sy, z + chunkPos.z * 16);
                                BlockState gen = chunk.getBlockState(mutable);
                                BlockState act = section.chunkSection().getBlockState(x, y, z);
                                if (gen.isAir() && act.isAir()) continue;
                                totalBlocks++;
                                long diff = config.diffCount(gen.getBlock(), act.getBlock());
                                if (diff >= 16 * 16 * 16 * 32) {
                                    break checkChunkLoop;
                                }
                                if (diff > 0) {
                                    modifiedBlocks++;
                                    diffMap.merge(Pair.of(gen.getBlock(), act.getBlock()), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }

                // ticket 释放回主线程执行
                mirrorWorld.getServer().execute(() ->
                        mirrorWorld.getChunkManager().removeTicket(
                                ChunkTicketType.FORCED, chunkPos, 1));
                return Map.entry(chunkPos, new ChunkDiffResult(totalBlocks, modifiedBlocks, diffMap));
            }, ForkJoinPool.commonPool()));
        }

        // ── 阶段 4：汇总所有区块结果 ──
        return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                .thenApply(v ->
                {
                    Map<ChunkPos, ChunkDiffResult> regionResult = new HashMap<>();
                    for (var future : allFutures) {
                        try {
                            Map.Entry<ChunkPos, ChunkDiffResult> entry = future.get();
                            regionResult.put(entry.getKey(), entry.getValue());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return regionResult;
                });
    }


    public ChunkDiffResult compare(WorldChunk actual)
    {
        if (mirrorWorld == null)
            throw new NullPointerException("The mirror world is null");


        ChunkPos pos = actual.getPos();
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int bottomY = mirrorWorld.getBottomY();
        int topY = mirrorWorld.getTopYInclusive();

        Chunk original = mirrorWorld.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);

        int totalBlocks = 0;
        long modifiedBlocks = 0;
        Map<Pair<Block, Block>, Integer> diffMap = new HashMap<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottomY; y < topY; y++) {
                    mutable.set(startX + x, y, startZ + z);

                    BlockState gen = original.getBlockState(mutable);
                    BlockState act = actual.getBlockState(mutable);

                    if (gen.isAir() && act.isAir())
                        continue;
                    totalBlocks++;
                    long diff = config.diffCount(gen.getBlock(), act.getBlock());
                    if (diff >= 16 * 16 * 16 * 32) {
                        break;
                    }
                    if (diff > 0) {
                        modifiedBlocks++;
                        diffMap.merge(Pair.of(gen.getBlock(), act.getBlock()), 1, Integer::sum);
                    }
                }
            }
        }

        return new ChunkDiffResult(totalBlocks, modifiedBlocks, diffMap);
    }

    public static ChunkComparator getFromWorld(ServerWorld serverWorld)
    {

        if (serverWorld.getRegistryKey().getValue().toString().startsWith("chunk-deviation")) {
            return null;
        }
        if (serverWorld.getRegistryKey() == World.OVERWORLD) {
            for (ServerWorld serverWorld1 : serverWorld.getServer().getWorlds()) {
                if (serverWorld1.getRegistryKey().getValue().toString().contains("mirror") && serverWorld1.getRegistryKey().getValue().toString()
                        .contains("overworld")) {
                    var config = new ChunkComparatorConfigs.OverWorldConfig();
                    config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
                    return new ChunkComparator(serverWorld, serverWorld1, config);
                }
            }
        } else if (serverWorld.getRegistryKey() == World.END) {
            for (ServerWorld serverWorld1 : serverWorld.getServer().getWorlds()) {
                if (serverWorld1.getRegistryKey().getValue().toString().contains("mirror") && serverWorld1.getRegistryKey().getValue().toString().contains("end")) {
                    var config = new ChunkComparatorConfigs.EndConfig();
                    config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
                    return new ChunkComparator(serverWorld, serverWorld1, config);
                }
            }
        } else if (serverWorld.getRegistryKey() == World.NETHER) {
            for (ServerWorld serverWorld1 : serverWorld.getServer().getWorlds()) {
                if (serverWorld1.getRegistryKey().getValue().toString().contains("mirror") && serverWorld1.getRegistryKey().getValue().toString().contains("nether")) {
                    var config = new ChunkComparatorConfigs.NetherConfig();
                    config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
                    return new ChunkComparator(serverWorld, serverWorld1, config);
                }
            }
        }

        return null;
    }

    //在无配置时加载
    public static void init(MinecraftServer server)
    {
        for (ServerWorld serverWorld : server.getWorlds()) {
            System.out.println(ROFTool.getSavePath(serverWorld));
            if (serverWorld.getRegistryKey() == World.OVERWORLD) {
                var config = new ChunkComparatorConfigs.OverWorldConfig();
                config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
            } else if (serverWorld.getRegistryKey() == World.END) {
                var config = new ChunkComparatorConfigs.EndConfig();
                config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
            } else if (serverWorld.getRegistryKey() == World.NETHER) {
                var config = new ChunkComparatorConfigs.NetherConfig();
                config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
            }
        }
    }
}

