package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.storage.ChunkDataList;
import net.minecraft.world.storage.EntityChunkDataAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityChunkDataAccess.class)
public class EntityChunkDataAccessMixin {

    @Shadow @Final
    private ServerWorld world;

    @Inject(method = "writeChunkData", at = @At("HEAD"), cancellable = true)
    private void chunkModify$cancelEntityWrite(ChunkDataList<Entity> dataList, CallbackInfo ci) {
        if (world.getRegistryKey().getValue().toString().contains("mirror_")) {
            //System.out.println("阻止保存实体数据到磁盘: " + world.getRegistryKey().getValue() + " at " + dataList.getChunkPos());
            ci.cancel();
        }
    }
}

