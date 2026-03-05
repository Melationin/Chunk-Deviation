package com.zhddsj.chunkdeviation.comparison;

import com.google.gson.JsonObject;
import com.zhddsj.chunkdeviation.Config;
import com.zhddsj.chunkdeviation.IBlockID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChunkComparatorConfig
{
    private Block[] id2BlockArray = null;
    private boolean[] id2BlockModifiedArray = null;
    private boolean[] id2BlockIgnoredArray = null;

    private final Map<BlockUnion,Block> unionMap  = new HashMap<>();
    private final Set<Block> blockModifiedSet  = new HashSet<>(); //这个表示有此方块的区块视为已修改
    private final Set<Block> blockIgnoredSet  = new HashSet<>(); //这个表示有忽视此方块的改动

    public final void addBlockMap(Block from, Block to){
        unionMap.put(BlockUnion.of(from),to);
    }
    public final void removeBlockMap(Block from){
        unionMap.remove(BlockUnion.of(from));
    }


    public final void addTagMap(TagKey<Block> from, Block to){
        unionMap.put(BlockUnion.of(from),to);
    }

    public final void removeTagMap(TagKey<Block> from)
    {
        unionMap.remove(BlockUnion.of(from));
    }
    public final void addBlockModified(Block block)
    {
        blockModifiedSet.add(block);
    }

    public final void removeBlockModified(Block block)
    {
        blockModifiedSet.remove(block);
    }
    public final void addBlockIgnored(Block block)
    {
        blockIgnoredSet.add(block);
    }
    public final void removeBlockIgnored(Block block)
    {
        blockIgnoredSet.remove(block);
    }

    public void load(){
        id2BlockArray = new Block[IBlockID.ZCM$BlockCount.get()];
        id2BlockModifiedArray = new boolean[IBlockID.ZCM$BlockCount.get()];
        id2BlockIgnoredArray = new boolean[IBlockID.ZCM$BlockCount.get()];
        for (Block block : Registries.BLOCK) {

            int id = ((IBlockID)block).ZCM$getID();

            for(var entry : unionMap.entrySet()){

                BlockUnion union = entry.getKey();
                Block target = entry.getValue();

                if(union.block != null){
                    if(block == union.block){
                        id2BlockArray[id] = target;
                    }
                }
                else if(union.tag != null){
                    if(block.getDefaultState().isIn(union.tag)){
                        id2BlockArray[id] = target;
                    }
                }
            }
        }

        for(var block :  blockModifiedSet){
            id2BlockModifiedArray[((IBlockID)block).ZCM$getID()] = true;
        }

        for(var block :  blockIgnoredSet){
            id2BlockIgnoredArray[((IBlockID)block).ZCM$getID()] = true;
        }
    }

    public long diffCount(Block mirror, Block real)
    {
        if(id2BlockIgnoredArray[((IBlockID)real).ZCM$getID()]) return 0; // 这个值应该足够大了
        if(id2BlockArray[((IBlockID)real).ZCM$getID()]!=null) real=id2BlockArray[((IBlockID)real).ZCM$getID()];
        if(id2BlockArray[((IBlockID)mirror).ZCM$getID()]!=null) mirror=id2BlockArray[((IBlockID)mirror).ZCM$getID()];

        if(real == mirror) return 0;
        else return 1;

    }
    public boolean isMark(Block real)
    {
        if(id2BlockModifiedArray[((IBlockID)real).ZCM$getID()]) return true; // 这个值应该足够大了
        return false;
    }
    protected void loadDefaultConfig(){};

    public void loadFromConfig(String worldId){
         var json =  Config.config.get("worlds").getAsJsonObject();
        if(!json.has(worldId)){

            loadDefaultConfig();
            json.add(worldId, new JsonObject());
            saveToJson(json.getAsJsonObject(worldId));
            loadFromJson(Config.config.getAsJsonObject("worlds").getAsJsonObject("common"));
            load();
            return;
        }
         loadFromJson(json.getAsJsonObject(worldId));
         if(json.has("common")){
             loadFromJson(json.getAsJsonObject("common"));
         }
         load();
    }
    // 不会加载，可以叠加
    private void loadFromJson(JsonObject json)
    {

        if(json.has("map")){
            JsonObject obj = json.getAsJsonObject("map");

            for(var entry : obj.entrySet()){

                String rawKey = entry.getKey();
                Identifier toId = Identifier.of(entry.getValue().getAsString());
                Block toBlock = Registries.BLOCK.get(toId);


                BlockUnion union = new BlockUnion(null,null);

                if(rawKey.startsWith("#")){
                    Identifier tagId = Identifier.of(rawKey.substring(1));
                    union.tag =TagKey.of(RegistryKeys.BLOCK, tagId);
                }else{
                    Identifier blockId = Identifier.of(rawKey);
                    union.block = Registries.BLOCK.get(blockId);
                }

                unionMap.put(union,toBlock);
            }
        }

        if(json.has("marker")){
            for(var e : json.getAsJsonObject("marker").asMap().entrySet()){
                Identifier id = Identifier.of(e.getKey());
                Block block = Registries.BLOCK.get(id);
                if(block != Blocks.AIR){
                    blockModifiedSet.add(block);
                }
            }
        }
        if(json.has("ignored")){
            for(var e : json.getAsJsonObject("ignored").asMap().entrySet()){
                Identifier id = Identifier.of(e.getKey());
                Block block = Registries.BLOCK.get(id);
                if(block != Blocks.AIR){
                    blockIgnoredSet.add(block);
                }
            }
        }
    }




    public void saveToJson(JsonObject json)
    {
        JsonObject rules = new JsonObject();

        for(var entry : unionMap.entrySet()){
            BlockUnion union = entry.getKey();
            Block target = entry.getValue();
            String key;
            if(union.block != null){
                key = Registries.BLOCK.getId(union.block).toString();
            }else{
                key = "#" + union.tag.id().toString();
            }
            rules.addProperty(key, Registries.BLOCK.getId(target).toString());
        }

        json.add("map",rules);

        var arr = new com.google.gson.JsonObject();
        for(var block : blockModifiedSet){
            arr.addProperty(Registries.BLOCK.getId(block).toString(),true);
        }
        json.add("marker",arr);

        var arr2 = new com.google.gson.JsonObject();
        for(var block : blockIgnoredSet){
            arr2.addProperty(Registries.BLOCK.getId(block).toString(),true);
        }
        json.add("ignored",arr2);
    }
}
