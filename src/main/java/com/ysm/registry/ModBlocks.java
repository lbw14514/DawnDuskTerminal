package com.ysm.registry;

import com.ysm.Ysm;
import com.ysm.portal.PortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ysm.MOD_ID);

    public static final DeferredBlock<PortalBlock> PORTAL_FLUID = BLOCKS.registerBlock(
        "portal_fluid",
        props -> new PortalBlock(ModFluids.PORTAL_FLUID.get(), props),
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .replaceable()
            .noCollission()
            .randomTicks()
            .strength(100.0F)
            .lightLevel(state -> 15)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY));

    public static final DeferredBlock<Block> CHRONO_CRUST = BLOCKS.registerSimpleBlock(
        "chrono_crust",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .lightLevel(state -> 3));

    public static final DeferredBlock<Block> SKY_SOIL = BLOCKS.registerSimpleBlock(
        "sky_soil",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.GRASS)
            .strength(0.6F)
            .sound(SoundType.GRASS));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
