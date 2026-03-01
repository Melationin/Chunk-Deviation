package com.zhddsj.chunkcheck.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores a virgin block-state snapshot for every overworld chunk the first
 * time it is loaded into the world (i.e. first FULL promotion).
 *
 * Persistence: world/data/chunk_snapshots.dat (PersistentState + Codec).
 *
 * Encoding: each chunk's blocks are stored as an int[] of Block.getRawIdFromState()
 * values, indexed [x*16*height + z*height + dy].  The int array is serialised as a
 * string (base-64-like compact form would be nicer but plain comma-separated is fine
 * for correctness) via a Map<String "cx,cz", long[]> Codec.
 *
 * We use long[] instead of int[] because Codec has a built-in LONG_STREAM; each long
 * packs two consecutive rawIds (upper 16 bits = rawId[i], lower 16 bits = rawId[i+1]).
 */
public class ChunkSnapshotStorage extends PersistentState {

    public static final String ID = "chunk_snapshots";

    // ── Codec ──────────────────────────────────────────────────────────────
    // Map<"cx,cz" -> int[] of rawIds>  stored as Map<String, long[]>
    // Each long packs two consecutive ints (upper/lower 16 bits each).
    private static final Codec<Map<String, long[]>> RAW_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING,
                    Codec.LONG_STREAM.xmap(
                            ls -> ls.toArray(),
                            la -> Arrays.stream(la)));

    private static final Codec<ChunkSnapshotStorage> CODEC =
            RecordCodecBuilder.create(inst -> inst.group(
                    RAW_MAP_CODEC.fieldOf("data").forGetter(s -> s.encoded)
            ).apply(inst, ChunkSnapshotStorage::new));

    public static final PersistentStateType<ChunkSnapshotStorage> STATE_TYPE =
            new PersistentStateType<>(ID,
                    ChunkSnapshotStorage::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    // ── State ───────────────────────────────────────────────────────────────
    // Stored as encoded longs so Codec serialisation is trivial.
    private final Map<String, long[]> encoded; // key = "cx,cz"

    private ChunkSnapshotStorage() {
        this.encoded = new HashMap<>();
    }

    private ChunkSnapshotStorage(Map<String, long[]> encoded) {
        this.encoded = new HashMap<>(encoded);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private static String key(ChunkPos pos) { return pos.x + "," + pos.z; }

    /** Pack int[] rawIds into long[] (two ints per long, big-endian). */
    private static long[] pack(int[] rawIds) {
        int len = (rawIds.length + 1) / 2;
        long[] longs = new long[len];
        for (int i = 0; i < rawIds.length; i += 2) {
            long hi = rawIds[i] & 0xFFFFL;
            long lo = (i + 1 < rawIds.length) ? (rawIds[i + 1] & 0xFFFFL) : 0L;
            longs[i / 2] = (hi << 16) | lo;
        }
        return longs;
    }

    /** Unpack long[] back to int[] rawIds. */
    private static int[] unpack(long[] longs, int count) {
        int[] rawIds = new int[count];
        for (int i = 0; i < count; i++) {
            long l = longs[i / 2];
            rawIds[i] = (int) ((i % 2 == 0) ? ((l >> 16) & 0xFFFFL) : (l & 0xFFFFL));
        }
        return rawIds;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public boolean hasSnapshot(ChunkPos pos) {
        return encoded.containsKey(key(pos));
    }

    /** Record a virgin snapshot of the chunk.  Called at most once per chunk. */
    public void recordSnapshot(WorldChunk chunk) {
        String k = key(chunk.getPos());
        if (encoded.containsKey(k)) return;

        int minY  = chunk.getBottomY();
        int height = chunk.getHeight();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int[] rawIds = new int[16 * 16 * height];

        BlockPos.Mutable bp = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int dy = 0; dy < height; dy++) {
                    bp.set(startX + x, minY + dy, startZ + z);
                    rawIds[x * 16 * height + z * height + dy] =
                            Block.getRawIdFromState(chunk.getBlockState(bp));
                }
            }
        }

        encoded.put(k, pack(rawIds));
        markDirty();
    }

    /**
     * Compare the live chunk against its stored snapshot.
     * Returns diff map, or null if no snapshot exists.
     */
    public Map<String, Integer> compareWithSnapshot(WorldChunk liveChunk) {
        String k = key(liveChunk.getPos());
        long[] longs = encoded.get(k);
        if (longs == null) return null;

        int minY   = liveChunk.getBottomY();
        int height = liveChunk.getHeight();
        int total  = 16 * 16 * height;
        if (longs.length != (total + 1) / 2) return null; // height changed?

        int[] refRawIds = unpack(longs, total);
        int startX = liveChunk.getPos().getStartX();
        int startZ = liveChunk.getPos().getStartZ();

        Map<String, Integer> diffMap = new HashMap<>();
        BlockPos.Mutable bp = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int dy = 0; dy < height; dy++) {
                    bp.set(startX + x, minY + dy, startZ + z);
                    BlockState live = liveChunk.getBlockState(bp);
                    int liveRaw = Block.getRawIdFromState(live);
                    int refRaw  = refRawIds[x * 16 * height + z * height + dy];
                    if (liveRaw == refRaw) continue;

                    BlockState refState = Block.getStateFromRawId(refRaw);
                    String liveId = Registries.BLOCK.getId(live.getBlock()).toString();
                    String refId  = Registries.BLOCK.getId(refState.getBlock()).toString();
                    if (liveId.equals(refId)) continue; // same block, different state — ignore
                    diffMap.merge("now=" + liveId + "  was=" + refId, 1, Integer::sum);
                }
            }
        }
        return diffMap;
    }

    public static ChunkSnapshotStorage getOrCreate(PersistentStateManager manager) {
        return manager.getOrCreate(STATE_TYPE);
    }
}

