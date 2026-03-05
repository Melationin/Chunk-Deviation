package com.zhddsj.chunkdeviation.comparison;

import net.minecraft.block.Block;
import net.minecraft.block.BlockTypes;
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

            addBlockMap(Blocks.CAVE_AIR, Blocks.AIR);
            addBlockMap(Blocks.WATER, Blocks.AIR);
            addTagMap(BlockTags.COAL_ORES, Blocks.STONE);
            addBlockMap(Blocks.SCULK,Blocks.STONE);
            addBlockMap(Blocks.SCULK_VEIN,Blocks.AIR);
            addTagMap(BlockTags.CAVE_VINES,Blocks.AIR);
            addBlockMap(Blocks.CLAY,Blocks.STONE);

            addTagMap(BlockTags.CORAL_BLOCKS,Blocks.AIR);
            addTagMap(BlockTags.CORALS,Blocks.AIR);

            addBlockMap(Blocks.MOSS_BLOCK,Blocks.STONE);
            addBlockMap(Blocks.MOSS_CARPET,Blocks.AIR);
            addBlockMap(Blocks.PALE_MOSS_CARPET,Blocks.AIR);
            addBlockMap(Blocks.KELP,Blocks.AIR);
            addBlockMap(Blocks.KELP_PLANT,Blocks.AIR);
            addBlockMap(Blocks.TALL_SEAGRASS,Blocks.AIR);
            addBlockMap(Blocks.SEAGRASS,Blocks.AIR);
            addBlockMap(Blocks.BAMBOO,Blocks.AIR);
            addBlockMap(Blocks.DRIPSTONE_BLOCK,Blocks.STONE);
            addBlockMap(Blocks.POINTED_DRIPSTONE,Blocks.AIR);
            addBlockMap(Blocks.HORN_CORAL_WALL_FAN,Blocks.AIR);
            addBlockMap(Blocks.BUBBLE_CORAL_WALL_FAN,Blocks.AIR);
            addBlockMap(Blocks.BRAIN_CORAL_WALL_FAN,Blocks.AIR);
            addBlockMap(Blocks.FIRE_CORAL_WALL_FAN,Blocks.AIR);
            addBlockMap(Blocks.TUBE_CORAL_WALL_FAN,Blocks.AIR);

            addBlockMap(Blocks.SEA_PICKLE,Blocks.AIR);
            addBlockMap(Blocks.BIG_DRIPLEAF_STEM,Blocks.AIR);
            addBlockMap(Blocks.BIG_DRIPLEAF,Blocks.AIR);
            addBlockMap(Blocks.SMALL_DRIPLEAF,Blocks.AIR);


            addBlockMap(Blocks.AZALEA,Blocks.AIR);
            addBlockMap(Blocks.SCULK_SENSOR,Blocks.AIR);
            addBlockMap(Blocks.BROWN_MUSHROOM,Blocks.AIR);
            addBlockMap(Blocks.COBWEB,Blocks.AIR);
            addBlockMap(Blocks.COCOA,Blocks.AIR);
            addBlockMap(Blocks.MOSSY_COBBLESTONE,Blocks.AIR);
            addTagMap(BlockTags.DIAMOND_ORES,Blocks.STONE);
            addTagMap(BlockTags.LAPIS_ORES,Blocks.STONE);
            addTagMap(BlockTags.REDSTONE_ORES,Blocks.STONE);
            addTagMap(BlockTags.GOLD_ORES,Blocks.STONE);

            addTagMap(BlockTags.BASE_STONE_OVERWORLD,Blocks.STONE);
            addBlockMap(Blocks.SNOW, Blocks.AIR);
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
