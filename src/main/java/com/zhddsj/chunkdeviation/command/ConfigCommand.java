package com.zhddsj.chunkdeviation.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.zhddsj.chunkdeviation.Config;
import com.zhddsj.chunkdeviation.comparison.BlockUnion;
import com.zhddsj.chunkdeviation.comparison.ChunkComparator;
import com.zhddsj.chunkdeviation.comparison.ChunkDiffResult;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.command.argument.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static net.minecraft.command.argument.BlockPosArgumentType.*;

/**
 * 配置管理命令
 * <pre>
 * /chunkconfig list [worldId]                          — 查看指定世界的配置（默认当前世界）
 * /chunkconfig map add <worldId> <from> <to>          — 添加方块映射规则（方块或标签）
 * /chunkconfig map remove <worldId> <from>            — 移除方块映射规则
 * /chunkconfig modified add <worldId> <block>         — 添加修改标记方块
 * /chunkconfig modified remove <worldId> <block>      — 移除修改标记方块
 * /chunkconfig ignored add <worldId> <block>          — 添加忽略标记方块
 * /chunkconfig ignored remove <worldId> <block>       — 移除忽略标记方块
 * /chunkconfig reload                                  — 重新加载配置文件
 * /chunkconfig save                                    — 保存当前配置到文件
 * </pre>
 */
public final class ConfigCommand
{

    private ConfigCommand()
    {
    }

    public static int helpCommand(CommandContext<ServerCommandSource> ctx){
        return 0;
    }

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)->{
            ROFCommandHelper<ServerCommandSource> helper = new ROFCommandHelper<>(dispatcher.getRoot(),registryAccess);
            helper.registerCommand("naturalChunkFilter{r}")
                    .rOp()
                    .command(ConfigCommand::helpCommand);

            helper.registerCommand("naturalChunkFilter com")
                    .command(context -> {
                        var chunkComparator = ChunkComparator.getFromWorld(context.getSource().getWorld());
                        if(chunkComparator == null){
                            context.getSource().sendError(Text.of("当前世界无法进行对比"));
                            return 0;
                        }

                       var regionMetas = chunkComparator.getRegionFilter((chunkPos)->true);

                        context.getSource().getServer().executeSync(()->{
                            AtomicInteger progress = new AtomicInteger(0);
                            int totalRegion = regionMetas.size();
                            Map<ChunkPos, ChunkDiffResult> results = new HashMap<>();
                            for(ChunkComparator.RegionMeta regionMeta : regionMetas){
                                results.putAll(
                                        chunkComparator.compareRegion(regionMeta,null).join()
                                );
                                progress.incrementAndGet();
                                context.getSource().sendFeedback(
                                        ()->Text.literal("已完成" + progress.get() +"/" + totalRegion ),
                                        false
                                );
                            }
                            Map<Pair<Block, Block>, Integer> res = new HashMap<>();

                            results.entrySet().stream().sorted(Comparator.comparingLong(entry->-entry.getValue().modifiedBlocks()))
                                    .forEach(
                                            entry -> {
                                                context.getSource().sendFeedback(()->Text.of(entry.getKey().toString() +": " +entry.getValue().modifiedBlocks()),false);
                                               if(entry.getValue().blockDiffMap() != null)
                                                    entry.getValue().blockDiffMap().forEach((blockPair,count)->{
                                                         res.merge(blockPair,count,Integer::sum);
                                                    });
                                            }
                                    );

                            res.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getValue))
                                    .forEach(
                                            entry -> {
                                                context.getSource().sendFeedback(()->Text.of(entry.getKey().getFirst().getName().getString() +"("
                                                        +entry.getKey().getFirst().getDefaultState().getRegistryEntry().getIdAsString()
                                                        +")"

                                                        + " -> " + entry.getKey().getSecond().getName().getString() + ": " + entry.getValue()),false);
                                            }
                                    )

                            ;


                        });



                        return 0;
                    });

            ROFCommandHelper<ServerCommandSource> configHelper = helper.getChild("naturalChunkFilter");
            registerConfigCommand(configHelper);

        });
    }


    public static int listConfig(CommandContext<ServerCommandSource> ctx){

        return 1;
    }

    public static void foreachBlock(CommandContext<ServerCommandSource> ctx, Consumer<Block> consumer) throws CommandSyntaxException
    {
        BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx,"from");
        BlockPos pos2 = BlockPosArgumentType.getLoadedBlockPos(ctx,"to");
        ServerWorld world = ctx.getSource().getWorld();
        for(BlockPos it: BlockPos.iterate(pos,pos2)){
            if(! world.isPosLoaded(it)){
                throw  UNLOADED_EXCEPTION.create();
            } if( world.isOutOfHeightLimit(it)){
                throw  OUT_OF_WORLD_EXCEPTION.create();
            }
            if(! world.getWorldBorder().contains(it)){
                throw  OUT_OF_BOUNDS_EXCEPTION.create();
            }
            if(world.getBlockState(pos).isAir()){
                continue;
            }
            Block block = world.getBlockState(it).getBlock();
            consumer.accept(block);
        }
    }
    public static <T extends JsonElement> T   getJson(CommandContext<ServerCommandSource> ctx, String key,T defaultValue) throws CommandSyntaxException
    {
        var json  = Config.config.getAsJsonObject("worlds");
        if(ROFCommandHelper.hasArgument(ctx,"dimension")){
            String worldId = DimensionArgumentType.getDimensionArgument(ctx,"dimension").getRegistryKey().getValue().toString();
            if(worldId.contains("mirror")){
                ctx.getSource().sendError(Text.of("非法的维度"));
                return null;
            }
            if(!json.has(worldId)){
                json.add(worldId, new JsonObject());
            }
            json = json.getAsJsonObject(worldId);
        }else {
            if(!json.has("common")){
                json.add("common",new JsonObject());

            }
            json = json.getAsJsonObject("common");
        }
        if(!json.has(key)){
            json.add(key,defaultValue);
        }
        return (T)json.get(key);
    }

    public static int changeMap(CommandContext<ServerCommandSource> ctx,boolean add,boolean quick) throws CommandSyntaxException
    {

        var json  = getJson(ctx,"map",new JsonObject());
        if(json == null) return 0;
        var mappedBlock = ctx.getArgument("mapped", BlockUnion.class);
        String blockMappedId =mappedBlock.toString();

        if(quick){
            foreachBlock(ctx, block->{
                String blockId = Registries.BLOCK.getId(block).toString();
                if(add){
                    json.addProperty(blockId,blockMappedId);
                    ctx.getSource().sendFeedback(()->Text.literal("已添加映射: "+block.getName().getString()+" -> "+mappedBlock.getString()),false);
                }
                else {
                    if(json.has(blockId)){
                        json.remove(blockId);
                        ctx.getSource().sendFeedback(()->Text.literal("已移除映射: "+block.getName().getString()).formatted(Formatting.RED),false);
                    }
                }

            });
        }else {
            BlockUnion blockUnion = ctx.getArgument("unmapped", BlockUnion.class);
            if(add){
                json.addProperty(blockUnion.toString(),blockMappedId);
                ctx.getSource().sendFeedback(()->Text.literal("已添加映射: "+blockUnion.getString()+" -> "+mappedBlock.getString()),false);
            }
            else {
                if(json.has(blockUnion.toString())){
                    json.remove(blockUnion.toString());
                    ctx.getSource().sendFeedback(()->Text.literal("已移除映射: "+blockUnion.getString()).formatted(Formatting.RED),false);
                }
            }
        }

        return 1;
    }

    public static int changeMarkerOrIgnore(CommandContext<ServerCommandSource> ctx,boolean add,boolean quick,boolean isMarker) throws CommandSyntaxException
    {


        String key = isMarker?"marker":"ignored";

        var json  = getJson(ctx,key,new JsonObject());
        if(json == null) return 0;
        try {
            if(quick){
                foreachBlock(ctx, block->{
                    String blockId = Registries.BLOCK.getId(block).toString();
                    if(add){
                        json.addProperty(blockId,true);
                        ctx.getSource().sendFeedback(()->Text.literal("已添加" +key+ "方块: "+block.getName().getString()),false);
                    }
                    else {
                        if(json.has(blockId)){
                            json.remove(blockId);
                            ctx.getSource().sendFeedback(()->Text.literal("已删除" +key+ "方块: "+block.getName().getString()).formatted(Formatting.RED),false);
                        }
                    }

                });
            }else {
                BlockUnion blockUnion = ctx.getArgument(key + "Block", BlockUnion.class);
                if(add){
                    json.addProperty(blockUnion.toString(),true);
                    ctx.getSource().sendFeedback(()->Text.literal("已添加" +key+ "方块:"+blockUnion.getString()),false);
                }
                else {
                    if(json.has(blockUnion.toString())){
                        json.remove(blockUnion.toString());
                        ctx.getSource().sendFeedback(()->Text.literal("已删除" +key+ "方块: "+blockUnion.getString()).formatted(Formatting.RED),false);
                    }
                }
            }

            return 1;
        }catch (Exception e){
            System.err.println(e.getMessage());
        }
       return 0;
    }

    public static void registerConfigCommand(ROFCommandHelper<ServerCommandSource> helper)
    {
        helper.registerCommand("config [dimension]")
                .arg(DimensionArgumentType.dimension())
                .command(ConfigCommand::listConfig);
        helper.registerCommand("config map add <unmapped> <mapped> [dimension]")

                .arg(BlockAndBlockTagArgumentType.blockOrTag(helper.commandRegistryAccess))
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMap(context,true,false));
        helper.registerCommand("config map addQuick <from> <to> <mapped> [dimension] ")

                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMap(context,true,true));
        helper.registerCommand("config map remove <unmapped> <mapped> [dimension] ")

                .arg(BlockAndBlockTagArgumentType.blockOrTag(helper.commandRegistryAccess))
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMap(context,false,false));
        helper.registerCommand("config map removeQuick <from> <to> <mapped> [dimension]  ")

                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockPosArgumentType.blockPos())
                .arg(DimensionArgumentType.dimension())
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .command(context -> changeMap(context,false,true));


        helper.registerCommand("config marker add <markerBlock> [dimension]")
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,true,false,true));
        helper.registerCommand("config marker remove <markerBlock> [dimension]")
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,false,false,true));
        helper.registerCommand("config marker addQuick <from> <to> [dimension]")
                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockPosArgumentType.blockPos())
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,true,true,true));
        helper.registerCommand("config marker removeQuick <from> <to> [dimension]")
                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockPosArgumentType.blockPos())
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,false,true,true));

        helper.registerCommand("config ignored add <ignoredBlock> [dimension]")
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,true,false,false));
        helper.registerCommand("config ignored remove <ignoredBlock> [dimension]")
                .arg(BlockAndBlockTagArgumentType.block(helper.commandRegistryAccess))
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,false,false,false));
        helper.registerCommand("config ignored addQuick <from> <to> [dimension]")
                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockPosArgumentType.blockPos())
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,true,true,false));
        helper.registerCommand("config ignored removeQuick <from> <to> [dimension]")
                .arg(BlockPosArgumentType.blockPos())
                .arg(BlockPosArgumentType.blockPos())
                .arg(DimensionArgumentType.dimension())
                .command(context -> changeMarkerOrIgnore(context,false,true,false));
    }


}