package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModChronoOres {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredBlock<Block> COAL_LOWER = ore("chrono_ore_coal_lower");
    public static final DeferredBlock<Block> COAL_UPPER = ore("chrono_ore_coal_upper");
    public static final DeferredBlock<Block> IRON_SMALL = ore("chrono_ore_iron_small");
    public static final DeferredBlock<Block> IRON_MIDDLE = ore("chrono_ore_iron_middle");
    public static final DeferredBlock<Block> IRON_UPPER = ore("chrono_ore_iron_upper");
    public static final DeferredBlock<Block> COPPER = ore("chrono_ore_copper");
    public static final DeferredBlock<Block> COPPER_LARGE = ore("chrono_ore_copper_large");
    public static final DeferredBlock<Block> GOLD = ore("chrono_ore_gold");
    public static final DeferredBlock<Block> GOLD_LOWER = ore("chrono_ore_gold_lower");
    public static final DeferredBlock<Block> REDSTONE = ore("chrono_ore_redstone");
    public static final DeferredBlock<Block> REDSTONE_LOWER = ore("chrono_ore_redstone_lower");
    public static final DeferredBlock<Block> LAPIS = ore("chrono_ore_lapis");
    public static final DeferredBlock<Block> LAPIS_BURIED = ore("chrono_ore_lapis_buried");
    public static final DeferredBlock<Block> DIAMOND = ore("chrono_ore_diamond");
    public static final DeferredBlock<Block> DIAMOND_BURIED = ore("chrono_ore_diamond_buried");
    public static final DeferredBlock<Block> DIAMOND_LARGE = ore("chrono_ore_diamond_large");
    public static final DeferredBlock<Block> DIAMOND_MEDIUM = ore("chrono_ore_diamond_medium");
    public static final DeferredBlock<Block> EMERALD = ore("chrono_ore_emerald");
    public static final DeferredBlock<Block> GRANITE_UPPER = ore("chrono_ore_granite_upper");
    public static final DeferredBlock<Block> GRANITE_LOWER = ore("chrono_ore_granite_lower");
    public static final DeferredBlock<Block> DIORITE_UPPER = ore("chrono_ore_diorite_upper");
    public static final DeferredBlock<Block> DIORITE_LOWER = ore("chrono_ore_diorite_lower");
    public static final DeferredBlock<Block> ANDESITE_UPPER = ore("chrono_ore_andesite_upper");
    public static final DeferredBlock<Block> ANDESITE_LOWER = ore("chrono_ore_andesite_lower");
    public static final DeferredBlock<Block> TUFF = ore("chrono_ore_tuff");
    public static final DeferredBlock<Block> GRAVEL = ore("chrono_ore_gravel");
    public static final DeferredBlock<Block> CLAY = ore("chrono_ore_clay");
    public static final DeferredBlock<Block> INFESTED = ore("chrono_ore_infested");
    public static final DeferredBlock<Block> MAGMA = ore("chrono_ore_magma");

    public static final DeferredItem<BlockItem> COAL_LOWER_ITEM = ITEMS.registerSimpleBlockItem(COAL_LOWER);
    public static final DeferredItem<BlockItem> COAL_UPPER_ITEM = ITEMS.registerSimpleBlockItem(COAL_UPPER);
    public static final DeferredItem<BlockItem> IRON_SMALL_ITEM = ITEMS.registerSimpleBlockItem(IRON_SMALL);
    public static final DeferredItem<BlockItem> IRON_MIDDLE_ITEM = ITEMS.registerSimpleBlockItem(IRON_MIDDLE);
    public static final DeferredItem<BlockItem> IRON_UPPER_ITEM = ITEMS.registerSimpleBlockItem(IRON_UPPER);
    public static final DeferredItem<BlockItem> COPPER_ITEM = ITEMS.registerSimpleBlockItem(COPPER);
    public static final DeferredItem<BlockItem> COPPER_LARGE_ITEM = ITEMS.registerSimpleBlockItem(COPPER_LARGE);
    public static final DeferredItem<BlockItem> GOLD_ITEM = ITEMS.registerSimpleBlockItem(GOLD);
    public static final DeferredItem<BlockItem> GOLD_LOWER_ITEM = ITEMS.registerSimpleBlockItem(GOLD_LOWER);
    public static final DeferredItem<BlockItem> REDSTONE_ITEM = ITEMS.registerSimpleBlockItem(REDSTONE);
    public static final DeferredItem<BlockItem> REDSTONE_LOWER_ITEM = ITEMS.registerSimpleBlockItem(REDSTONE_LOWER);
    public static final DeferredItem<BlockItem> LAPIS_ITEM = ITEMS.registerSimpleBlockItem(LAPIS);
    public static final DeferredItem<BlockItem> LAPIS_BURIED_ITEM = ITEMS.registerSimpleBlockItem(LAPIS_BURIED);
    public static final DeferredItem<BlockItem> DIAMOND_ITEM = ITEMS.registerSimpleBlockItem(DIAMOND);
    public static final DeferredItem<BlockItem> DIAMOND_BURIED_ITEM = ITEMS.registerSimpleBlockItem(DIAMOND_BURIED);
    public static final DeferredItem<BlockItem> DIAMOND_LARGE_ITEM = ITEMS.registerSimpleBlockItem(DIAMOND_LARGE);
    public static final DeferredItem<BlockItem> DIAMOND_MEDIUM_ITEM = ITEMS.registerSimpleBlockItem(DIAMOND_MEDIUM);
    public static final DeferredItem<BlockItem> EMERALD_ITEM = ITEMS.registerSimpleBlockItem(EMERALD);
    public static final DeferredItem<BlockItem> GRANITE_UPPER_ITEM = ITEMS.registerSimpleBlockItem(GRANITE_UPPER);
    public static final DeferredItem<BlockItem> GRANITE_LOWER_ITEM = ITEMS.registerSimpleBlockItem(GRANITE_LOWER);
    public static final DeferredItem<BlockItem> DIORITE_UPPER_ITEM = ITEMS.registerSimpleBlockItem(DIORITE_UPPER);
    public static final DeferredItem<BlockItem> DIORITE_LOWER_ITEM = ITEMS.registerSimpleBlockItem(DIORITE_LOWER);
    public static final DeferredItem<BlockItem> ANDESITE_UPPER_ITEM = ITEMS.registerSimpleBlockItem(ANDESITE_UPPER);
    public static final DeferredItem<BlockItem> ANDESITE_LOWER_ITEM = ITEMS.registerSimpleBlockItem(ANDESITE_LOWER);
    public static final DeferredItem<BlockItem> TUFF_ITEM = ITEMS.registerSimpleBlockItem(TUFF);
    public static final DeferredItem<BlockItem> GRAVEL_ITEM = ITEMS.registerSimpleBlockItem(GRAVEL);
    public static final DeferredItem<BlockItem> CLAY_ITEM = ITEMS.registerSimpleBlockItem(CLAY);
    public static final DeferredItem<BlockItem> INFESTED_ITEM = ITEMS.registerSimpleBlockItem(INFESTED);
    public static final DeferredItem<BlockItem> MAGMA_ITEM = ITEMS.registerSimpleBlockItem(MAGMA);

    private static DeferredBlock<Block> ore(String name) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 3.0F)
            .sound(SoundType.STONE));
    }

    private ModChronoOres() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
