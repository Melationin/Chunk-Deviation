package com.zhddsj.chunkdeviation.mixin.cancelSave;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin extends World
{
    @Shadow public boolean savingDisabled;

    protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates)
    {
        super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed,
                maxChainedNeighborUpdates);
    }

    @Inject(method = "<init>",at = @At(value = "TAIL"))
    void init(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci){
        if(this.getRegistryKey().getValue().toString().contains("mirror_")){
            savingDisabled = true;
        }
    }

    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At(value = "HEAD"), cancellable = true)
    private void save(ProgressListener progressListener, boolean flush, boolean savingDisabled, CallbackInfo ci)
    {
        if(this.getRegistryKey().getValue().toString().contains("mirror_")){
            ci.cancel();
        }

    }
    @Inject(method = "savePersistentState",at = @At(value = "HEAD"), cancellable = true)
    private void savePersistentState(CallbackInfo ci){
        if(this.getRegistryKey().getValue().toString().contains("mirror_")){
            ci.cancel();
        }
    }
}
