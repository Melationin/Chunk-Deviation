package com.zhddsj.chunkdeviation.mixin;

import com.zhddsj.chunkdeviation.IBlockID;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockIDMixin implements IBlockID
{

    @Unique
    int rofId = 0;


    @Inject(method = "<init>",
            at = @At(value = "TAIL"))
    private void onInit(CallbackInfo ci)
    {
        rofId = ZCM$BlockCount.getAndAdd(1);
    }


    @Override
    public int ZCM$getID()
    {
        return rofId;
    }
}
