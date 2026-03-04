package com.zhddsj.chunkdeviation.comparison;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;

public  class BlockUnion{
    public Block block = null;
    public TagKey<Block> tag = null;

    public BlockUnion(Block block, TagKey<Block> tag)
    {
        this.block = block;
        this.tag = tag;
    }

    public static BlockUnion of(Block block){
        return new BlockUnion(block,null);
    }
    public static BlockUnion of(TagKey<Block> tag){
        return new BlockUnion(null,tag);
    }
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        BlockUnion other = (BlockUnion) o;

        if(block != null && other.block != null){
            return block == other.block;
        }
        if(tag != null && other.tag != null){
            return tag.equals(other.tag);
        }
        return false;
    }

    public String toString(){
        if(block != null) return Registries.BLOCK.getId(block).toString();
        if(tag != null) return "#"+tag.id().toString();
        return "null";
    }

    public String getString (){
        if(block != null) return block.getName().getString();
        if(tag != null) return "#"+tag.id().toString();
        return "null";
    }
}
