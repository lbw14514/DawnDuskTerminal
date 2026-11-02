package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.DiracOreBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 晨昏线自己那三种矿
 *
 * <p>这里曾经还有 29 种 {@code chrono_ore_*}，是把原版矿石换个命名空间再注册一遍，模型直接继承
 * {@code minecraft:block/<矿石>}、战利品表指向 {@code minecraft:blocks/<矿石>}，也就是原版矿石的别名，
 * 没有任何调色板替换。它们既没有世界生成入口也没有语言条目，已删除
 */
public final class ModChronoOres {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredBlock<Block> DEEP_SCARLET = BLOCKS.registerSimpleBlock(
            "charnockite_scarlet_molybdenum_ore",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .requiresCorrectToolForDrops()
                    .strength(5F, 9.0F)
                    .sound(SoundType.STONE));

    public static final DeferredBlock<Block> CARID_ORE = BLOCKS.registerSimpleBlock(
            "carid_ore",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.STONE));

    public static final DeferredBlock<DiracOreBlock> DIRAC_ORE = BLOCKS.registerBlock(
            "dirac_ore",
            DiracOreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(6.0F, 9.0F)
                    .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> DEEP_SCARLET_ITEM = ITEMS.registerSimpleBlockItem(DEEP_SCARLET);
    public static final DeferredItem<BlockItem> CARID_ORE_ITEM = ITEMS.registerSimpleBlockItem(CARID_ORE);
    public static final DeferredItem<BlockItem> DIRAC_ORE_ITEM = ITEMS.registerSimpleBlockItem(DIRAC_ORE);

    private ModChronoOres() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
