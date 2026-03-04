package com.zhddsj.chunkdeviation.comparison;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;

public class ChunkComparatorConfigs
{
    public static class EndConfig extends ChunkComparatorConfig{


        @Override
        protected void loadDefaultConfig()
        {
            addBlockMap(Blocks.CHORUS_FLOWER,Blocks.AIR);
            addBlockMap(Blocks.CHORUS_PLANT,Blocks.AIR);
            addBlockMap(Blocks.CAVE_AIR,Blocks.AIR);
        }
    }

    public static class OverWorldConfig extends ChunkComparatorConfig
    {
        @Override
        protected void loadDefaultConfig()
        {
            addTagMap(BlockTags.LEAVES, Blocks.AIR);
            addTagMap(BlockTags.LOGS, Blocks.AIR);
            addTagMap(BlockTags.REPLACEABLE, Blocks.AIR);
            addTagMap(BlockTags.FLOWERS, Blocks.AIR);
            addTagMap(BlockTags.OVERWORLD_CARVER_REPLACEABLES, Blocks.STONE);
            addBlockMap(Blocks.CAVE_AIR, Blocks.AIR);
        }
    }

    public static class NetherConfig extends ChunkComparatorConfig {
        @Override
        protected void loadDefaultConfig()
        {
            // === 地形基底统一 ===
            addBlockMap(Blocks.SOUL_SAND, Blocks.NETHERRACK);
            addBlockMap(Blocks.SOUL_SOIL, Blocks.NETHERRACK);
            addBlockMap(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHERRACK);
            addBlockMap(Blocks.NETHER_GOLD_ORE, Blocks.NETHERRACK);
            addBlockMap(Blocks.ANCIENT_DEBRIS, Blocks.NETHERRACK);
            addTagMap(BlockTags.BASE_STONE_NETHER, Blocks.NETHERRACK);
            addBlockMap(Blocks.BASALT, Blocks.NETHERRACK);

            addBlockMap(Blocks.CRIMSON_STEM, Blocks.AIR);
            addBlockMap(Blocks.WARPED_STEM, Blocks.AIR);
            addBlockMap(Blocks.CRIMSON_HYPHAE, Blocks.AIR);
            addBlockMap(Blocks.WARPED_HYPHAE, Blocks.AIR);
            addBlockMap(Blocks.NETHER_WART_BLOCK, Blocks.AIR);
            addBlockMap(Blocks.WARPED_WART_BLOCK, Blocks.AIR);
            addBlockMap(Blocks.SHROOMLIGHT, Blocks.AIR);

            addBlockMap(Blocks.CRIMSON_FUNGUS, Blocks.AIR);
            addBlockMap(Blocks.WARPED_FUNGUS, Blocks.AIR);
            addBlockMap(Blocks.CRIMSON_ROOTS, Blocks.AIR);
            addBlockMap(Blocks.WARPED_ROOTS, Blocks.AIR);
            addBlockMap(Blocks.NETHER_SPROUTS, Blocks.AIR);
            addBlockMap(Blocks.TWISTING_VINES, Blocks.AIR);
            addBlockMap(Blocks.TWISTING_VINES_PLANT, Blocks.AIR);
            addBlockMap(Blocks.WEEPING_VINES, Blocks.AIR);
            addBlockMap(Blocks.WEEPING_VINES_PLANT, Blocks.AIR);
            addBlockMap(Blocks.GLOWSTONE, Blocks.AIR);
            addBlockMap(Blocks.BROWN_MUSHROOM, Blocks.AIR);
            addBlockMap(Blocks.RED_MUSHROOM, Blocks.AIR);

            addBlockMap(Blocks.FIRE, Blocks.AIR);
            addBlockMap(Blocks.LAVA, Blocks.AIR);
            addBlockMap(Blocks.CAVE_AIR, Blocks.AIR);
        }
    }
}
