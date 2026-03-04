package com.zhddsj.chunkdeviation;

import java.util.concurrent.atomic.AtomicInteger;

public interface IBlockID
{
    public static AtomicInteger ZCM$BlockCount = new AtomicInteger(0);
    int ZCM$getID();
}
