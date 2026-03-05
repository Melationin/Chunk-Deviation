package com.zhddsj.chunkdeviation;

import com.google.common.collect.ImmutableList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 创建并管理一个镜像 ServerWorld。
 *
 * 关键设计：
 * 1. 独立的 workerExecutor — 镜像世界区块生成不占用主世界线程池
 * 2. 独立的 Session — 指向临时目录，从根源隔离存储，即使写入也不影响主存档
 * 3. 共享主世界的 ChunkGenerator 实例 — 保证生成结果完全一致
 */
public class MirrorWorld{

    private final ServerWorld mirrorWorld;
    private final ServerWorld sourceWorld;
    public final RegistryKey<World> mirrorKey;
    public final ExecutorService mirrorExecutor;
    public final LevelStorage.Session mirrorSession;
    public final Path tempDir;

    public MirrorWorld(ServerWorld sourceWorld, Identifier mirrorId) {
        this.sourceWorld = sourceWorld;
        this.mirrorKey = RegistryKey.of(RegistryKeys.WORLD, mirrorId);

        MinecraftServer server = sourceWorld.getServer();

        // === 1. 独立的 workerExecutor ===
        // 用满所有核心：区块生成是 CPU 密集型，线程越多并行度越高
        int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());
        this.mirrorExecutor = Executors.newFixedThreadPool(threadCount);


        // === 2. 独立的 Session（指向临时目录）===
        try {
            this.tempDir = Files.createTempDirectory("mirror_" + mirrorId.getPath() + "_");
            // 在临时目录中创建一个 LevelStorage，然后打开 session
            LevelStorage tempStorage = LevelStorage.create(this.tempDir);
            this.mirrorSession = tempStorage.createSessionWithoutSymlinkCheck(mirrorId.getPath());
        } catch (IOException e) {
            throw new RuntimeException("[ChunkDeviation] 无法为镜像世界创建临时 session", e);
        }

        // === 3. 共享主世界的 ChunkGenerator 实例 ===
        DimensionOptions dimensionOptions = new DimensionOptions(
                sourceWorld.getDimensionEntry(),
                sourceWorld.getChunkManager().getChunkGenerator()  // 同一个实例！
        );

        ServerWorldProperties mainWorldProperties = server.getSaveProperties().getMainWorldProperties();
        UnmodifiableLevelProperties mirrorProperties = new UnmodifiableLevelProperties(
                server.getSaveProperties(), mainWorldProperties
        );

        long seed = BiomeAccess.hashSeed(server.getSaveProperties().getGeneratorOptions().getSeed());

        WorldGenerationProgressListener progressListener = new WorldGenerationProgressListener() {
            @Override public void start(ChunkPos spawnPos) {}
            @Override public void setChunkStatus(ChunkPos pos, @Nullable ChunkStatus status) {}
            @Override public void start() {}
            @Override public void stop() {}
        };

        var randomSequences = server.getOverworld().getRandomSequences();

        // 创建 ServerWorld — 用独立 executor 和独立 session
        this.mirrorWorld = new ServerWorld(
                server,
                this.mirrorExecutor,        // 独立线程池
                this.mirrorSession,          // 独立临时 session
                mirrorProperties,
                this.mirrorKey,
                dimensionOptions,            // 共享主世界 ChunkGenerator
                progressListener,
                false,
                seed,
                ImmutableList.of(),
                false,
                randomSequences
        );

        this.mirrorWorld.savingDisabled = true;
        ChunkDeviation.LOGGER.info("[ChunkDeviation] 镜像世界 {} 已创建 (独立executor, 临时session: {})",
                mirrorId, this.tempDir);
    }



    /**
     * 关闭镜像世界（释放线程池、关闭 session、清理临时目录）
     */
    public void close() {
        try {
            mirrorExecutor.shutdown();
            mirrorSession.close();
            // 清理临时目录
            deleteDirectory(tempDir);
            ChunkDeviation.LOGGER.info("[ChunkDeviation] 镜像世界 {} 已关闭并清理", mirrorKey.getValue());
        } catch (IOException e) {
            ChunkDeviation.LOGGER.warn("[ChunkDeviation] 关闭镜像世界 {} 时出错", mirrorKey.getValue(), e);
        }
    }

    private static void deleteDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }

    public ServerWorld getMirrorWorld() { return mirrorWorld; }
    public ServerWorld getSourceWorld() { return sourceWorld; }
    public RegistryKey<World> getMirrorKey() { return mirrorKey; }

    public static MirrorWorld createOverworldMirror(MinecraftServer server) {
        return new MirrorWorld(server.getOverworld(), Identifier.of("chunk-deviation", "mirror_overworld"));
    }

    public static MirrorWorld createNetherMirror(MinecraftServer server) {
        ServerWorld nether = server.getWorld(World.NETHER);
        if (nether == null) return null;
        return new MirrorWorld(nether, Identifier.of("chunk-deviation", "mirror_the_nether"));
    }

    public static MirrorWorld createEndMirror(MinecraftServer server) {
        ServerWorld end = server.getWorld(World.END);
        if (end == null) return null;
        return new MirrorWorld(end, Identifier.of("chunk-deviation", "mirror_the_end"));
    }
}
