package com.zhddsj.chunkdeviation.comparison;

import java.util.concurrent.atomic.AtomicInteger;

public class ChunkComparatorResult
{


    //每个Region 完成后增加1

    public AtomicInteger progressRegion = new AtomicInteger(0);
    public int totalChunks;

    public  int totalRegions;

}
