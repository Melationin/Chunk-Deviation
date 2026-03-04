package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkLoadingManager.class)
public abstract class ServerChunkLoadingManagerMixin {
    @Shadow @Final private ServerWorld world;

    @Inject(method = "save(Z)V", at = @At("HEAD"), cancellable = true)
    private void chunkModify$skipSave(boolean flush, CallbackInfo ci) {
        if (world.getRegistryKey().getValue().toString().contains("mirror_")) {
            ci.cancel();
        }
    }
}

