package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.ChunkPosKeyedStorage;
import net.minecraft.world.storage.StorageKey;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkPosKeyedStorage.class)
public class ChunkPosKeyedStorageMixin {

    @Shadow
    public StorageKey getStorageKey() {
        throw new AssertionError();
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void chunkModify$cancelSet(ChunkPos pos, @Nullable NbtCompound nbt, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        StorageKey storageKey = this.getStorageKey();
        String dimensionPath = storageKey.toString();

        // 检查是否是 mirror_ 开头的维度
        if (dimensionPath.contains("mirror_")) {
            //System.out.println("阻止 ChunkPosKeyedStorage 写入数据: " + dimensionPath + " at " + pos);
            // 返回一个已完成的空 Future，假装保存成功
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}

