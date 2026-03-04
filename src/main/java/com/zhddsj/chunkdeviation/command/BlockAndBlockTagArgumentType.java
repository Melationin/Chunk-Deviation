package com.zhddsj.chunkdeviation.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zhddsj.chunkdeviation.comparison.BlockUnion;
import net.minecraft.block.Block;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class BlockAndBlockTagArgumentType implements ArgumentType<BlockUnion>
{
    RegistryWrapper<Block> registryWrapper;

    boolean tagAllowed;
   private   BlockAndBlockTagArgumentType(RegistryWrapper<Block> wrapper,boolean tagAllowed)
    {
        registryWrapper = wrapper;
        this.tagAllowed = tagAllowed;
    }

    @Override
    public BlockUnion parse(StringReader reader) throws CommandSyntaxException
    {
        if(tagAllowed) {
            var res = BlockArgumentParser.blockOrTag(registryWrapper, reader, false);
            if (res.left().isPresent()) {
                return BlockUnion.of(res.left().get().blockState().getBlock());
            }
            return BlockUnion.of(res.right().get().tag().getTagKey().get());
        }else {
            var res = BlockArgumentParser.block(registryWrapper, reader, false);
            return BlockUnion.of(res.blockState().getBlock());
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return BlockArgumentParser.getSuggestions(registryWrapper, builder, tagAllowed, false);
    }

    public static BlockAndBlockTagArgumentType blockOrTag(CommandRegistryAccess commandRegistryAccess)
    {
        return new BlockAndBlockTagArgumentType(commandRegistryAccess.getOrThrow(RegistryKeys.BLOCK),true);
    }
    public static BlockAndBlockTagArgumentType block(CommandRegistryAccess commandRegistryAccess)
    {
        return new BlockAndBlockTagArgumentType(commandRegistryAccess.getOrThrow(RegistryKeys.BLOCK),false);
    }
}
