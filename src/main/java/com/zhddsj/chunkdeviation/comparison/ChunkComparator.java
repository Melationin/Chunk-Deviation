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
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
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

        ArrayList<CompletableFuture<Map.Entry<ChunkPos, ChunkDiffResult>>> regionFutures = new ArrayList<>();
        Map<ChunkPos,SerializedChunk> serializedChunkMap = new HashMap<>();



        ROFIO.loadFromRegion(regionMeta.path, regionMeta.x, regionMeta.z, world, chunkData ->
        {
            SerializedChunk serializedChunk = SerializedChunk.fromNbt(world, world.getRegistryManager(), chunkData);
            if (serializedChunk == null) {
                System.err.println("miss chunk data");
                return;
            }
            if (predicateChunk != null && !predicateChunk.test(serializedChunk.chunkPos()))
                return;
            if (serializedChunk.chunkStatus() != ChunkStatus.FULL) {
                System.err.println("Failed to parse chunk data");
                regionFutures.add(CompletableFuture.completedFuture(
                        Map.entry(serializedChunk.chunkPos(), new ChunkDiffResult(-1, 0, null))));
                return;
            }
            for (var section : serializedChunk.sectionData()) {
                if (section.chunkSection() == null)
                    continue;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState act = section.chunkSection().getBlockState(x, y, z);
                            if(config.isMark(act.getBlock())) {
                                regionFutures.add(CompletableFuture.completedFuture(
                                        Map.entry(serializedChunk.chunkPos(), new ChunkDiffResult(-1, 114514, null))));
                                return;
                            }
                        }
                    }
                }
            }
            serializedChunkMap.put(serializedChunk.chunkPos(), serializedChunk);

        });


        mirrorWorld.getServer().execute(() ->
        {
            for(Map.Entry<ChunkPos, SerializedChunk> entry : serializedChunkMap.entrySet()){
                mirrorWorld.getChunkManager().addTicket(
                        new ChunkTicket(ChunkTicketType.FORCED, ChunkLevels.getLevelFromType(ChunkLevelType.FULL)),
                        entry.getKey());
            }
        });
        for (SerializedChunk serializedChunk : serializedChunkMap.values()) {
            ChunkPos chunkPos = serializedChunk.chunkPos();
            CompletableFuture<Chunk> chunkFuture =
                    mirrorWorld.getChunkManager().getChunkFutureSyncOnMainThread(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false).thenApply(
                            optionalChunk -> optionalChunk.orElse(null)
                    );

            regionFutures.add(
                  chunkFuture.thenApply(chunk -> {
                      int totalBlocks = 0;
                      long modifiedBlocks = 0;
                      int bottomY = mirrorWorld.getBottomY();
                      Map<Pair<Block, Block>, Integer> diffMap = new HashMap<>();
                      BlockPos.Mutable mutable = new BlockPos.Mutable();

                      int i22 = 0;
                      checkChunkLoop:
                      for (var section : serializedChunk.sectionData()) {
                          if (section.chunkSection() == null)
                              continue;
                          int sy = i22 * 16 + bottomY;
                          i22++;
                          for (int y = 0; y < 16; y++) {
                              for (int x = 0; x < 16; x++) {
                                  for (int z = 0; z < 16; z++) {
                                      mutable.set(x + chunkPos.x * 16, y + sy, z + chunkPos.z * 16);
                                      BlockState gen = chunk.getBlockState(mutable);
                                      BlockState act = section.chunkSection().getBlockState(x, y, z);
                                      if (gen.isAir() && act.isAir())
                                          continue;
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
                      mirrorWorld.getServer().execute(() ->
                      {
                          mirrorWorld.getChunkManager().removeTicket(ChunkTicketType.FORCED, chunkPos, 1);
                      });
                      return Map.entry(chunkPos, new ChunkDiffResult(totalBlocks, modifiedBlocks, diffMap));
                  }
                  )
            );

        }

        return CompletableFuture.allOf(regionFutures.toArray(new CompletableFuture[0])).thenApply(v ->
        {
            Map<ChunkPos, ChunkDiffResult> regionResult = new HashMap<>();
            for (CompletableFuture<Map.Entry<ChunkPos, ChunkDiffResult>> future : regionFutures) {
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

