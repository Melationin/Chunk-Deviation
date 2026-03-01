package com.zhddsj.chunkcheck.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists a map of ChunkPos -> diff block count using PersistentState + Codec.
 * Saved to: world/data/chunk_diff_data.dat
 */
public class ChunkDiffStorage extends PersistentState {

    public static final String ID = "chunk_diff_data";

    // Codec for Map<ChunkPos, Integer> stored as Map<String "x,z", Integer>
    private static final Codec<Map<ChunkPos, Integer>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.INT
    ).xmap(
            stringMap -> {
                Map<ChunkPos, Integer> result = new HashMap<>();
                stringMap.forEach((key, val) -> {
                    String[] parts = key.split(",");
                    if (parts.length == 2) {
                        try {
                            result.put(new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])), val);
                        } catch (NumberFormatException ignored) {}
                    }
                });
                return result;
            },
            chunkMap -> {
                Map<String, Integer> result = new HashMap<>();
                chunkMap.forEach((pos, val) -> result.put(pos.x + "," + pos.z, val));
                return result;
            }
    );

    private static final Codec<ChunkDiffStorage> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MAP_CODEC.fieldOf("data").forGetter(s -> s.diffMap)
            ).apply(instance, ChunkDiffStorage::new)
    );

    public static final PersistentStateType<ChunkDiffStorage> STATE_TYPE = new PersistentStateType<>(
            ID,
            () -> new ChunkDiffStorage(new HashMap<>()),
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<ChunkPos, Integer> diffMap;

    public ChunkDiffStorage(Map<ChunkPos, Integer> diffMap) {
        this.diffMap = new HashMap<>(diffMap);
    }

    public void setDiff(ChunkPos pos, int diff) {
        diffMap.put(pos, diff);
        markDirty();
    }

    public int getDiff(ChunkPos pos) {
        return diffMap.getOrDefault(pos, -1);
    }

    public Map<ChunkPos, Integer> getDiffMap() {
        return diffMap;
    }

    public static ChunkDiffStorage getOrCreate(PersistentStateManager manager) {
        return manager.getOrCreate(STATE_TYPE);
    }
}
