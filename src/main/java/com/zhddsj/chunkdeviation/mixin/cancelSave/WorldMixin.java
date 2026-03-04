package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "isSavingDisabled", at = @At("HEAD"), cancellable = true)
    private void chunkModify$disableSaving(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ServerWorld serverWorld) {
            if (serverWorld.getRegistryKey().getValue().toString().contains("mirror_")) {
                cir.setReturnValue(true);
            }
        }
    }
}

