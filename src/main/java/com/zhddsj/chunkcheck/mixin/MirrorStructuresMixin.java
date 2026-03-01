package com.zhddsj.chunkcheck.mixin;

import com.zhddsj.chunkcheck.world.MirrorWorldManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * When the mirror world runs STRUCTURE_STARTS for any chunk, skip the expensive
 * re-calculation and instead copy structure data directly from the overworld chunk
 * (if that chunk already exists there at FULL status).
 *
 * This guarantees that generateFeatures sees exactly the same structure starts as
 * the original world generation, so the decorator-seed counter m is always correct.
 */
@Mixin(ChunkGenerating.class)
public class MirrorStructuresMixin {

    @Inject(
        method = "generateStructures",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onGenerateStructures(
            ChunkGenerationContext context,
            ChunkGenerationStep step,
            BoundedRegionArray<AbstractChunkHolder> chunks,
            Chunk chunk,
            CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {

        ServerWorld overworld = MirrorWorldManager.getSourceOverworld();
        if (overworld == null) return;                   // not inside a mirror session
        if (context.world() == overworld) return;        // safety: never intercept the real world

        // Try to find the chunk in the overworld
        Chunk owChunk = overworld.getChunkManager()
                .getChunk(chunk.getPos().x, chunk.getPos().z,
                          ChunkStatus.STRUCTURE_STARTS, false);
        if (owChunk == null) return;  // overworld doesn't have it yet — fall through to normal gen

        // Copy structure starts from the overworld chunk
        chunk.setStructureStarts(owChunk.getStructureStarts());

        // cacheStructures must still be called so downstream lookups work
        context.world().cacheStructures(chunk);

        cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
}

