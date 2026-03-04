package com.zhddsj.chunkdeviation.storage;

import com.zhddsj.chunkdeviation.comparison.ChunkDiffResult;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存最近分析过的区块对比结果，上限 64 个（LRU 策略）。
 * <p>
 * 每个 ServerWorld 可单独持有一个实例，或全局共享一个实例。
 */
public class ChunkDiffState {

    private static final int MAX_SIZE = 64;

    /** LRU Map：访问顺序，满时自动淘汰最旧条目 */
    private final Map<Long, ChunkDiffResult> cache = new LinkedHashMap<>(MAX_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ChunkDiffResult> eldest) {
            return size() > MAX_SIZE;
        }
    };

    /** 单例（服务端全局使用） */
    public static final ChunkDiffState INSTANCE = new ChunkDiffState();

    private ChunkDiffState() {}

    public synchronized void put(ChunkPos pos, ChunkDiffResult result) {
        cache.put(pos.toLong(), result);
    }

    public synchronized ChunkDiffResult get(ChunkPos pos) {
        return cache.get(pos.toLong());
    }

    /** 返回修改率最高的前 N 个结果（降序） */
    public synchronized List<ChunkDiffResult> getTopModified(int n) {
        return cache.values().stream()
                .sorted(Comparator.comparingDouble(ChunkDiffResult::modificationRate).reversed())
                .limit(n)
                .toList();
    }

    public synchronized void clear() {
        cache.clear();
    }
}

