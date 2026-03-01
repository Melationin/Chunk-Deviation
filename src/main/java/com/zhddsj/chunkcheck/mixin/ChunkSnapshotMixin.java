package com.zhddsj.chunkcheck.mixin;

import com.zhddsj.chunkcheck.storage.ChunkSnapshotStorage;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures a virgin snapshot of every overworld chunk the first time it is
 * loaded into the world (i.e., first time setLoadedToWorld(true) is called).
 *
 * WorldChunk.setLoadedToWorld(true) is called exactly once during
 * ChunkGenerating.convertToFullChunk(), which runs on the main thread.
 */
@Mixin(WorldChunk.class)
public class ChunkSnapshotMixin {

    @Shadow private boolean loadedToWorld;

    @Inject(method = "setLoadedToWorld", at = @At("HEAD"))
    private void onSetLoadedToWorld(boolean loaded, CallbackInfo ci) {
        if (!loaded) return;
        if (this.loadedToWorld) return; // already was loaded — not the first time

        WorldChunk self = (WorldChunk)(Object)this;
        if (!(self.getWorld() instanceof ServerWorld sw)) return;
        // Only snapshot the overworld (or any dimension you care about)
        // Here we snapshot all server worlds; filter by dimension key if needed.

        ChunkSnapshotStorage storage = ChunkSnapshotStorage.getOrCreate(
                sw.getPersistentStateManager());
        if (!storage.hasSnapshot(self.getPos())) {
            storage.recordSnapshot(self);
        }
    }
}

