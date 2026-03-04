package com.zhddsj.chunkdeviation.mixin;

import com.mojang.datafixers.DataFixer;
import com.zhddsj.chunkdeviation.Config;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.util.ApiServices;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin
{
    @Inject(method = "loadWorld",at = @At(value = "TAIL"))
    private void onInit(CallbackInfo ci)
    {
        Config.load((MinecraftServer) (Object)this);
    }
    @Inject(method = "save",at = @At(value = "HEAD"))
    private void save(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir){
        Config.save((MinecraftServer) (Object)this);
    }

}
