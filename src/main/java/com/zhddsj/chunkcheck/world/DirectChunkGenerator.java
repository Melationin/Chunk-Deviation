package com.zhddsj.chunkcheck.world;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.*;

import java.util.concurrent.CompletableFuture;

/**
 * Generates a single chunk to FEATURES status entirely in-memory on the
 * calling thread — no ServerChunkManager, no tickets, no IO.
 *
 * Every neighbour slot is a fresh ProtoChunk pre-populated by copying all
 * ChunkSection data from the live overworld WorldChunk.  This means:
 *   - getBlockState()  returns the real overworld data  (correct input for
 *                      Aquifer, Beardifier, surface builder, etc.)
 *   - setBlockState()  writes to the copy, not the real world  (carver
 *                      cross-chunk writes succeed without corrupting anything)
 *   - getSectionArray() is consistent with getBlockState()
 *
 * The target ProtoChunk runs BIOMES → NOISE → SURFACE → CARVERS → FEATURES.
 * STRUCTURE_STARTS/REFERENCES are skipped; data is copied from the overworld
 * so the decorator-seed counter m is identical to the original generation.
 */
public final class DirectChunkGenerator {

    private DirectChunkGenerator() {}

    public static ProtoChunk generate(ServerWorld overworld, ChunkPos targetPos) {
        var clm = overworld.getChunkManager().chunkLoadingManager;
        ChunkGenerationContext ctx = clm.generationContext;

        // Fresh ProtoChunk for the target position
        ProtoChunk proto = new ProtoChunk(
                targetPos, UpgradeData.NO_UPGRADE_DATA,
                overworld,
                overworld.getRegistryManager().getOrThrow(RegistryKeys.BIOME),
                null);

        // Copy structure data so generateFeatures sees the same starts as the
        // original world → m-counter is identical
        WorldChunk owTarget = overworld.getChunk(targetPos.x, targetPos.z);
        proto.setStructureStarts(owTarget.getStructureStarts());
        proto.setStructureReferences(owTarget.getStructureReferences());

        // Build holder array (radius 8 covers all step dependencies)
        final int RADIUS = 8;
        BoundedRegionArray<AbstractChunkHolder> holders =
                BoundedRegionArray.create(targetPos.x, targetPos.z, RADIUS,
                        (hx, hz) -> {
                            ChunkPos p = new ChunkPos(hx, hz);
                            if (p.equals(targetPos)) {
                                return new StoredHolder(p, proto);
                            }
                            // For every neighbour: copy section data from overworld
                            // into a fresh ProtoChunk so reads are correct and
                            // cross-chunk writes (carvers) don't corrupt the world.
                            WorldChunk nb = overworld.getChunk(hx, hz);
                            ProtoChunk copy = new ProtoChunk(
                                    p, UpgradeData.NO_UPGRADE_DATA,
                                    overworld,
                                    overworld.getRegistryManager().getOrThrow(RegistryKeys.BIOME),
                                    null);
                            // Copy every section
                            ChunkSection[] srcSections = nb.getSectionArray();
                            for (int i = 0; i < srcSections.length; i++) {
                                copy.getSectionArray()[i] = srcSections[i].copy();
                            }
                            // Copy structure data for correct cross-chunk feature placement
                            copy.setStructureStarts(nb.getStructureStarts());
                            copy.setStructureReferences(nb.getStructureReferences());
                            // Mark as already at FEATURES so no step re-generates it
                            copy.setStatus(ChunkStatus.FEATURES);
                            return new StoredHolder(p, copy);
                        });

        // Run generation steps synchronously on the calling thread
        for (ChunkGenerationStep step : ChunkGenerationSteps.GENERATION.steps()) {
            ChunkStatus status = step.targetStatus();
            if (status == ChunkStatus.STRUCTURE_STARTS
                    || status == ChunkStatus.STRUCTURE_REFERENCES) continue;
            if (status.isLaterThan(ChunkStatus.FEATURES)) break;
            step.run(ctx, holders, proto).join();
        }

        return proto;
    }

    // Minimal AbstractChunkHolder that just holds a pre-existing chunk
    private static final class StoredHolder extends AbstractChunkHolder {
        private final Chunk chunk;

        StoredHolder(ChunkPos pos, Chunk chunk) {
            super(pos);
            this.chunk = chunk;
        }

        @Override public Chunk getUncheckedOrNull(ChunkStatus s) { return chunk; }
        @Override protected void combineSavingFuture(CompletableFuture<?> f) {}
        @Override public int getLevel()          { return 0; }
        @Override public int getCompletedLevel() { return 0; }
    }
}
