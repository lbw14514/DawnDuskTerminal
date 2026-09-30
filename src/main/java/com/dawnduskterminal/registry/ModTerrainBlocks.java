package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTerrainBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredBlock<Block> EMBER_SOIL = BLOCKS.registerSimpleBlock(
        "ember_soil", soft(MapColor.TERRACOTTA_ORANGE, SoundType.GRASS, 0.6F));

    public static final DeferredBlock<Block> SKY_STONE = BLOCKS.registerSimpleBlock(
        "sky_stone", stone(MapColor.STONE));

    public static final DeferredBlock<Block> OCHRE = BLOCKS.registerSimpleBlock(
        "ochre", stone(MapColor.TERRACOTTA_ORANGE));

    public static final DeferredBlock<Block> GREENSCHIST = BLOCKS.registerSimpleBlock(
        "greenschist", stone(MapColor.TERRACOTTA_GREEN));

    public static final DeferredBlock<Block> ECLOGITE = BLOCKS.registerSimpleBlock(
        "eclogite", stone(MapColor.TERRACOTTA_PURPLE));

    public static final DeferredBlock<Block> PERIDOTITE = BLOCKS.registerSimpleBlock(
        "peridotite", stone(MapColor.DEEPSLATE));

    public static final DeferredBlock<Block> TWILIGHT_STONE = BLOCKS.registerSimpleBlock(
        "twilight_stone", stone(MapColor.TERRACOTTA_GRAY));

    public static final DeferredBlock<Block> ROAD_SAND = BLOCKS.registerSimpleBlock(
        "road_sand", soft(MapColor.SAND, SoundType.SAND, 0.5F));

    public static final DeferredBlock<Block> SLEEPLESS_SAND = BLOCKS.registerSimpleBlock(
        "sleepless_sand", soft(MapColor.TERRACOTTA_ORANGE, SoundType.SAND, 0.5F));

    public static final DeferredBlock<Block> SILTSTONE = BLOCKS.registerSimpleBlock(
        "siltstone", stone(MapColor.TERRACOTTA_WHITE));

    public static final DeferredBlock<Block> FROZEN_CANOPY = BLOCKS.registerSimpleBlock(
        "frozen_canopy", BlockBehaviour.Properties.of()
            .mapColor(MapColor.ICE)
            .strength(0.5F)
            .sound(SoundType.GLASS)
            .friction(0.98F));

    public static final DeferredBlock<Block> FRAGILE_CANOPY = BLOCKS.registerSimpleBlock(
        "fragile_canopy", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(0.4F)
            .sound(SoundType.GLASS));

    public static final DeferredBlock<Block> CHAOS_STONE = BLOCKS.registerSimpleBlock(
        "chaos_stone", stone(MapColor.COLOR_BLACK));

    public static final DeferredBlock<Block> AURORA_BLOCK = BLOCKS.registerSimpleBlock(
        "aurora_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(1.0F, 6.0F)
            .lightLevel(state -> 12)
            .sound(SoundType.AMETHYST));

    public static final DeferredBlock<Block> BLAZING_BLOCK = BLOCKS.registerSimpleBlock(
        "blazing_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.5F, 6.0F)
            .lightLevel(state -> 15)
            .sound(SoundType.BASALT));

    private ModTerrainBlocks() {}

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F);
    }

    private static BlockBehaviour.Properties soft(MapColor color, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .strength(strength)
            .sound(sound);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
