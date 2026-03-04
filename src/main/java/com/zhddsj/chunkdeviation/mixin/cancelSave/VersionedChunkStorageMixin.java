package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(VersionedChunkStorage.class)
public abstract class VersionedChunkStorageMixin {

    @Shadow
    protected abstract StorageKey getStorageKey();

    @Inject(method = "setNbt", at = @At("HEAD"), cancellable = true)
    private void chunkModify$cancelSetNbt(ChunkPos chunkPos, Supplier<NbtCompound> nbtSupplier, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        StorageKey storageKey = this.getStorageKey();
        String dimensionPath = storageKey.toString();

        // 检查是否是 mirror_ 开头的维度
        if (dimensionPath.contains("mirror_")) {
            //System.out.println("阻止保存区块到磁盘: " + dimensionPath + " at " + chunkPos);
            // 返回一个已完成的空 Future，假装保存成功
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}

