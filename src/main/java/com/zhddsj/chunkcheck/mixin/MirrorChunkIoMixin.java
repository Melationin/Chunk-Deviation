package com.zhddsj.chunkcheck.mixin;

import com.zhddsj.chunkcheck.world.MirrorWorldManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.StorageIoWorker;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Intercepts IO on the mirror world's VersionedChunkStorage (i.e. its
 * ServerChunkLoadingManager, which extends VersionedChunkStorage):
 *
 *  - getNbt: forward reads to the REAL world's StorageIoWorker so that
 *            neighbour chunks are loaded from the live .mca files instead
 *            of being re-generated from scratch.
 *            For the target chunk itself, return Optional.empty() so the
 *            chunk pipeline falls through to the GENERATION path.
 *
 *  - setNbt: discard all writes — we never want to pollute the real save.
 */
@Mixin(VersionedChunkStorage.class)
public abstract class MirrorChunkIoMixin {

    @Final
    @Shadow
    private StorageIoWorker worker;

    // ── getNbt ───────────────────────────────────────────────────────────────

    @Inject(method = "getNbt", at = @At("HEAD"), cancellable = true)
    private void onGetNbt(ChunkPos chunkPos,
                          CallbackInfoReturnable<CompletableFuture<Optional<NbtCompound>>> cir) {
        // Only intercept the mirror world's storage, not the real world's
        StorageIoWorker realWorker = MirrorWorldManager.getRealIoWorker();
        if (realWorker == null || this.worker == realWorker) return;

        // Check if this storage belongs to the mirror world's chunk loading manager
        if (MirrorWorldManager.getMirrorWorld() == null) return;

        ChunkPos target = MirrorWorldManager.getTargetChunkPos();
        if (target != null && chunkPos.x == target.x && chunkPos.z == target.z) {
            // Target chunk: return empty so it gets GENERATED fresh
            cir.setReturnValue(CompletableFuture.completedFuture(Optional.empty()));
        } else {
            // Neighbour chunk: read from the real world's .mca — no re-generation needed
            cir.setReturnValue(realWorker.readChunkData(chunkPos));
        }
    }

    // ── setNbt ───────────────────────────────────────────────────────────────

    @Inject(method = "setNbt", at = @At("HEAD"), cancellable = true)
    private void onSetNbt(ChunkPos chunkPos, Supplier<NbtCompound> nbtSupplier,
                          CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        StorageIoWorker realWorker = MirrorWorldManager.getRealIoWorker();
        if (realWorker == null || this.worker == realWorker) return;
        if (MirrorWorldManager.getMirrorWorld() == null) return;

        // Discard all writes from the mirror world
        cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}

