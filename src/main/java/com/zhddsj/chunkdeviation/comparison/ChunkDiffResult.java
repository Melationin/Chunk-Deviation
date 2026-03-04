package com.zhddsj.chunkdeviation.comparison;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;

/**
 * 保存一次区块对比的结果。
 * <p>
 * blockDiffMap: key 为 Pair&lt;生成的方块, 实际的方块&gt;，value 为出现次数。
 * 若生成方块 == 实际方块，则不计入此 map。
 */
public record ChunkDiffResult(
        int totalBlocks,
        long modifiedBlocks,
        Map<Pair<Block, Block>, Integer> blockDiffMap
) {
    /** 修改率，范围 [0.0, 1.0] */
    public double modificationRate() {
        return totalBlocks == 0 ? 0.0 : (double) modifiedBlocks / totalBlocks;
    }
}

