package com.zhddsj.chunkcheck.mixin;

import com.zhddsj.chunkcheck.world.MirrorWorldManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After every MinecraftServer.runOneTask(), also pump mirrorWorld's
 * ServerChunkManager if a mirror session is active.
 * This is a safety net — the main pump loop in generateChunk() handles it too.
 */
@Mixin(MinecraftServer.class)
public class ServerTickMixin {

    @Inject(method = "runOneTask", at = @At("RETURN"))
    private void onRunOneTask(CallbackInfoReturnable<Boolean> cir) {
        ServerWorld mirror = MirrorWorldManager.getMirrorWorld();
        if (mirror != null) {
            mirror.getChunkManager().executeQueuedTasks();
        }
    }
}
