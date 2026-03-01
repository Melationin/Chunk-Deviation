package com.zhddsj.chunkcheck.world;

import com.google.common.collect.ImmutableList;
import com.zhddsj.chunkcheck.mixin.MinecraftServerAccessor;
import com.zhddsj.chunkcheck.mixin.ServerChunkManagerAccessor;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkLevels;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class MirrorWorldManager {

    /** Read by MirrorStructuresMixin. */
    private static volatile ServerWorld activeOverworld   = null;
    /** Read by ServerTickMixin to pump chunk manager each server task. */
    private static volatile ServerWorld activeMirrorWorld  = null;
    /** The IO worker of the real overworld — used by MirrorChunkIoMixin to read real .mca data */
    private static volatile StorageIoWorker realIoWorker  = null;
    /** The chunk being regenerated — MirrorChunkIoMixin returns empty for this position */
    private static volatile ChunkPos targetChunkPos       = null;

    public static ServerWorld getSourceOverworld()   { return activeOverworld;   }
    public static ServerWorld getMirrorWorld()       { return activeMirrorWorld;  }
    public static StorageIoWorker getRealIoWorker()  { return realIoWorker;       }
    public static ChunkPos getTargetChunkPos()       { return targetChunkPos;     }

    private final ServerWorld mirrorWorld;
    private final Path tempDir;
    private final LevelStorage.Session tempSession;

    // ── Factory (main thread) ────────────────────────────────────────────────

    public static MirrorWorldManager create(MinecraftServer server) throws IOException {
        Path tempDir = Files.createTempDirectory("chunk_compare_mirror_");
        LevelStorage tempStorage = LevelStorage.create(tempDir.getParent());
        LevelStorage.Session tempSession =
                tempStorage.createSessionWithoutSymlinkCheck(tempDir.getFileName().toString());

        DimensionOptions dimensionOptions = server.getCombinedDynamicRegistries()
                .getCombinedRegistryManager()
                .getOrThrow(RegistryKeys.DIMENSION)
                .get(DimensionOptions.OVERWORLD);

        ServerWorldProperties mirrorProps = new UnmodifiableLevelProperties(
                server.getSaveProperties(),
                server.getSaveProperties().getMainWorldProperties());

        ServerWorld mirrorWorld = new ServerWorld(
                server, Util.getMainWorkerExecutor(), tempSession, mirrorProps,
                World.OVERWORLD, dimensionOptions,
                new SilentProgress(),
                false, server.getOverworld().getSeed(),
                ImmutableList.of(), false, null);

        mirrorWorld.getChunkManager().chunkLoadingManager.structurePlacementCalculator =
                server.getOverworld().getChunkManager().getStructurePlacementCalculator();

        activeOverworld   = server.getOverworld();
        activeMirrorWorld = mirrorWorld;
        // Expose the real world's IO worker so MirrorChunkIoMixin can forward reads
        realIoWorker = (StorageIoWorker) server.getOverworld()
                .getChunkManager().chunkLoadingManager.getWorker();

        return new MirrorWorldManager(mirrorWorld, tempDir, tempSession);
    }

    private MirrorWorldManager(ServerWorld mirrorWorld, Path tempDir, LevelStorage.Session session) {
        this.mirrorWorld = mirrorWorld;
        this.tempDir     = tempDir;
        this.tempSession = session;
    }

    // ── Generation (main thread) ─────────────────────────────────────────────

    /**
     * Must be called on the server main thread.
     *
     * We manually pump both executors in a tight loop:
     *  - scm.executeQueuedTasks()        drives mirrorWorld's MainThreadExecutor
     *    (updateChunks + lightingProvider.tick + queue drain)
     *  - server.invokeRunTask()          drives MinecraftServer's own queue
     *    (needed for any CF callbacks scheduled on the server executor,
     *     e.g. loadChunk's thenApplyAsync(..., mainThreadExecutor))
     *
     * We use getChunkFutureSyncOnMainThread() to obtain a CompletableFuture,
     * then poll isDone() each iteration — this is truly non-blocking and avoids
     * the deadlock caused by the old scm.getChunk() which blocked inside runTasks().
     */
    public WorldChunk generateChunk(ChunkPos chunkPos) {
        var scm    = mirrorWorld.getChunkManager();
        var scmAcc = (ServerChunkManagerAccessor) scm;
        var server = (MinecraftServerAccessor) mirrorWorld.getServer();
        int radius = ChunkLevels.FULL_GENERATION_REQUIRED_LEVEL;

        targetChunkPos = chunkPos;
        try {
            scm.addTicket(ChunkTicketType.FORCED, chunkPos, radius);

            long deadline = System.currentTimeMillis() + 60_000;
            while (true) {
                // Drive mirrorWorld pipeline (non-blocking, one task at a time)
                scm.executeQueuedTasks();
                scmAcc.invokeUpdateChunks();
                server.invokeRunTask();

                // Non-blocking check via holder directly — no runTasks() triggered
                ChunkHolder holder = scmAcc.invokeGetChunkHolder(chunkPos.toLong());
                if (holder != null) {
                    Chunk c = holder.getOrNull(ChunkStatus.FULL);
                    if (c instanceof WorldChunk wc) return wc;
                }

                if (System.currentTimeMillis() > deadline) {
                    throw new RuntimeException("Timed out waiting for mirror chunk generation");
                }
            }
        } finally {
            targetChunkPos = null;
        }
    }

    // ── Cleanup (main thread) ────────────────────────────────────────────────

    public void close() {
        activeOverworld   = null;
        activeMirrorWorld = null;
        realIoWorker      = null;
        targetChunkPos    = null;
        try { mirrorWorld.getChunkManager().close(); } catch (IOException ignored) {}
        try { tempSession.close(); }                  catch (IOException ignored) {}
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private static class SilentProgress implements WorldGenerationProgressListener {
        @Override public void start(ChunkPos p) {}
        @Override public void start() {}
        @Override public void setChunkStatus(ChunkPos p, @Nullable ChunkStatus s) {}
        @Override public void stop() {}
    }
}
