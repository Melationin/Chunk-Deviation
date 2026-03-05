package com.zhddsj.chunkdeviation.comparison;

import com.mojang.datafixers.util.Pair;
import com.zhddsj.chunkdeviation.MirrorWorld;
import com.zhddsj.chunkdeviation.ProtoWorld;
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
    MirrorWorld mirrorWorld;
    ChunkComparatorConfig config;


    public ChunkComparator(ServerWorld serverWorld, MirrorWorld mirrorWorld, ChunkComparatorConfig config)
    {
        this.world = serverWorld;
        this.mirrorWorld = mirrorWorld;
        this.config = config;
    }


    public record RegionMeta(Path path, int x, int z)
    {
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

        //ServerWorld mirrorWorld = this.mirrorWorld.getMirrorWorld();

        ProtoWorld world1=new ProtoWorld(world);

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
        System.out.println("Region " + regionMeta.x + "," + regionMeta.z + " parsed. " +
                "Total: " + rawNbtList.size() +
                ", To Load: " + serializedChunkMap.size() +
                ", Early Results: " + earlyResults.size());
        // ── 阶段 2：批量注册 ticket + 获取所有区块 future ──
        // 关键优化：一次性在主线程批量注册所有 ticket 并获取所有 future，
        // 避免逐个提交导致主线程队列串行瓶颈。

        List<CompletableFuture<Map.Entry<ChunkPos, ChunkDiffResult>>> allFutures = new ArrayList<>();

        // 早期结果直接封装
        for (Map.Entry<ChunkPos, ChunkDiffResult> e : earlyResults.entrySet()) {
            allFutures.add(CompletableFuture.completedFuture(Map.entry(e.getKey(), e.getValue())));
        }

        // 收集所有需要加载的区块
        List<SerializedChunk> chunksToLoad = new ArrayList<>(serializedChunkMap.values());

        if (!chunksToLoad.isEmpty()) {
            // 一次性在主线程批量注册所有 ticket + 获取所有 future
            // 用一个
            /*
            for (SerializedChunk sc : chunksToLoad) {
                ChunkPos cp = sc.chunkPos();
                mirrorWorld.getChunkManager().addTicket(ChunkTicketType.FORCED, cp,1);
            }
            mirrorWorld.getChunkManager().chunkLoadingManager.updateChunks();

            for (SerializedChunk sc : chunksToLoad) {
                ChunkPos cp = sc.chunkPos();
                // 直接在主线程调用 getChunkFuture（走主线程路径，立即执行）
                CompletableFuture<Chunk> cf = mirrorWorld.getChunkManager().getChunkFutureSyncOnMainThread(
                        cp.x,
                        cp.z,
                        ChunkStatus.FULL,
                        true

                ).thenApply(chunk ->
                    chunk.orElse(null));

                chunkFutures2.add(cf);
            }
            */
            List<CompletableFuture<Chunk>> chunkFutures2 = new ArrayList<>(chunksToLoad.size());
            for (SerializedChunk sc : chunksToLoad) {
                ChunkPos cp = sc.chunkPos();

                chunkFutures2.add( CompletableFuture.supplyAsync(() ->{
                    return world1.generate(cp, ChunkStatus.FEATURES);

                }).thenApply(chunk->{
                    System.out.println("chunk " + cp + " generated");
                    return chunk;
                }));
            }
            //this.mirrorWorld.mirrorExecutor.

            // ── 阶段 3：所有区块已加载，并行计算 diff ──
            for (int idx = 0; idx < chunksToLoad.size(); idx++) {
                SerializedChunk serializedChunk = chunksToLoad.get(idx);
                CompletableFuture<Chunk> chunkFut = chunkFutures2.get(idx);
                ChunkPos chunkPos = serializedChunk.chunkPos();

                allFutures.add(chunkFut.thenApplyAsync(chunk -> {
                    if (chunk == null) {
                        System.err.println("miss chunk " + chunkPos);
                    }

                    int totalBlocks = 0;
                    long modifiedBlocks = 0;
                    int bottomY = world.getBottomY();
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

                    return Map.entry(chunkPos, new ChunkDiffResult(totalBlocks, modifiedBlocks, diffMap));
                }));
            }
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



    public static ChunkComparator getFromWorld(ServerWorld serverWorld)
    {

        if (serverWorld.getRegistryKey().getValue().toString().startsWith("chunk-deviation")) {
            return null;
        }
        if (serverWorld.getRegistryKey() == World.OVERWORLD) {
            var config = new ChunkComparatorConfigs.NetherConfig();
            config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
            return new ChunkComparator(serverWorld, null, config);
        } else if (serverWorld.getRegistryKey() == World.END) {
            var config = new ChunkComparatorConfigs.NetherConfig();
            config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
            return new ChunkComparator(serverWorld, MirrorWorld.createEndMirror(serverWorld.getServer()), config);
        } else if (serverWorld.getRegistryKey() == World.NETHER) {
            var config = new ChunkComparatorConfigs.NetherConfig();
            config.loadFromConfig(serverWorld.getRegistryKey().getValue().toString());
            return new ChunkComparator(serverWorld, null, config);
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

