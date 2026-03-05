package com.zhddsj.chunkdeviation.mixin;

import com.zhddsj.chunkdeviation.Config;
import com.zhddsj.chunkdeviation.MirrorWorld;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin
{
    @Inject(method = "loadWorld",at = @At(value = "TAIL"))
    private void onInit(CallbackInfo ci)
    {
        MinecraftServer server = (MinecraftServer) (Object) this;
        Config.load(server);

        // 创建镜像世界 — 直接复用主世界的 ChunkGenerator 实例

    }
    @Inject(method = "save",at = @At(value = "HEAD"))
    private void save(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir){
        Config.save((MinecraftServer) (Object)this);
    }

}
