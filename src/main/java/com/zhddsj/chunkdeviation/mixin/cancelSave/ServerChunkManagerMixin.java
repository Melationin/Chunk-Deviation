package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public class ServerChunkManagerMixin
{
    @Inject(method = "save(Z)V", at = @At(value = "HEAD"), cancellable = true)
    private void save(CallbackInfo ci)
    {
        if(((ChunkManager)(Object)this).getWorld() instanceof ServerWorld serverWorld){
            if(serverWorld.getRegistryKey() .getValue().toString().contains("mirror_")){
                ci.cancel();
            }
        }
    }

}
