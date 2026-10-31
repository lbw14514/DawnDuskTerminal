package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import twilightforest.init.TFItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.SortedMap;
import java.util.TreeMap;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dawnduskterminal.main"))
            .icon(() -> new ItemStack(ModItems.ENCASED_DUST.get()))
            .displayItems((params, output) -> {
                accept(output, ModItems.HYDRA_TROPHY.get());
                accept(output, ModItems.UR_GHAST_TROPHY.get());
                accept(output, ModItems.SNOW_QUEEN_TROPHY.get());
                accept(output, ModItems.PAINTING_GHAST_1.get());
                accept(output, ModItems.ENCASED_DUST.get());
                accept(output, ModItems.CANOCAN.get());
                accept(output, ModItems.PORTAL_BUCKET.get());
                accept(output, ModScorchingTwilight.BUCKET.get());
                accept(output, ModItems.STABILITY_POTION.get());
                accept(output, ModItems.CHRONO_CRUST.get());
                accept(output, ModTerrainBlocks.ROSY_GRASS_ITEM.get());
                accept(output, ModTerrainBlocks.ROSY_DIRT_ITEM.get());
                accept(output, ModTerrainBlocks.CANOPY_STONE_ITEM.get());
                accept(output, ModTerrainBlocks.OCHRE_ITEM.get());
                accept(output, ModTerrainBlocks.GREENSCHIST_ITEM.get());
                accept(output, ModTerrainBlocks.ECLOGITE_ITEM.get());
                accept(output, ModTerrainBlocks.CHARNOCKITE_ITEM.get());
                accept(output, ModTerrainBlocks.TWILIGHT_STONE_ITEM.get());
                accept(output, ModTerrainBlocks.ROAD_SAND_ITEM.get());
                accept(output, ModTerrainBlocks.UNCONSCIOUS_SAND_ITEM.get());
                accept(output, ModTerrainBlocks.SILTSTONE_ITEM.get());
                accept(output, ModTerrainBlocks.FROZEN_CANOPY_ITEM.get());
                accept(output, ModTerrainBlocks.FRAGILE_CANOPY_ITEM.get());
                accept(output, ModTerrainBlocks.CANOPY_FROST_ITEM.get());
                accept(output, ModTerrainBlocks.CANOPY_FROST_BLOCK_ITEM.get());
                accept(output, ModTerrainBlocks.CHAOS_STONE_ITEM.get());
                accept(output, ModTerrainBlocks.BLAZING_BLOCK_ITEM.get());
                accept(output, ModTerrainBlocks.MOUNTED_TOWERWOOD_ITEM.get());
                accept(output, ModTerrainBlocks.ENCASED_TOWERBEAM_ITEM.get());
                accept(output, ModTerrainBlocks.CHISELED_TOWERBEAM_ITEM.get());
                accept(output, ModTerrainBlocks.WARNING_TOWERWOOD_ITEM.get());
                accept(output, ModTerrainBlocks.DARK_LOG_COUNTERTOP_ITEM.get());
                accept(output, ModTerrainBlocks.STRIPPED_DARK_LOG_COUNTERTOP_ITEM.get());
                accept(output, ModTerrainBlocks.WROUGHT_IRON_GLASS_ITEM.get());
                accept(output, ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR_ITEM.get());
                accept(output, ModTerrainBlocks.SKYGATE_PINE_LOG_ITEM.get());
                accept(output, ModTerrainBlocks.STRIPPED_SKYGATE_PINE_LOG_ITEM.get());
                accept(output, ModTerrainBlocks.SKYGATE_PINE_LEAVES_ITEM.get());
                accept(output, ModTerrainBlocks.SKYGATE_PINE_SAPLING_ITEM.get());
                accept(output, ModTerrainBlocks.SCARLET_MOLYBDENUM_ORE_ITEM.get());
                accept(output, ModChronoOres.DEEP_SCARLET_ITEM.get());
                accept(output, ModChronoOres.CARID_ORE_ITEM.get());
                accept(output, ModChronoOres.DIRAC_ORE_ITEM.get());
                accept(output, ModItems.RAW_SCARLET_MOLYBDENUM.get());
                accept(output, ModItems.GARNET.get());
                accept(output, ModItems.SKY_SLICES.get());
                accept(output, ModItems.OCHRE.get());
                accept(output, ModItems.CANOPY_FROST_BALL.get());
                accept(output, ModItems.ILLUSTRIOUS_POWDER.get());
                accept(output, ModItems.CARID.get());
                accept(output, ModItems.AOI.get());
                accept(output, ModItems.CANOPY_INGOT.get());
                accept(output, ModItems.DIRAC.get());
                accept(output, ModItems.MIXED_CARMINITE.get());
                accept(output, ModItems.CARMINITE_INGOT.get());
                accept(output, ModItems.SCARLET_MOLYBDENUM_INGOT.get());
                accept(output, ModItems.HUI_INGOT.get());
                accept(output, ModItems.DIRAC_INGOT.get());
                accept(output, ModItems.CARMINITE_GHAST_LEATHER.get());
                accept(output, ModItems.CARMINITED_LEATHER.get());
                accept(output, ModItems.CARMINITE_GHAST_MEAT.get());
                accept(output, ModItems.COOKED_CARMIN_GHAST_MEAT.get());
                // 115 的七种衍生物：一片 6 饥饿 4 饱和（血橙与红丝绒是 8 与 8），摆下来那块不用撒红石粉也会自己补片
                ModItems.susCakes().values().forEach(cake -> accept(output, cake.get()));
                accept(output, ModItems.CARMINITE_BALL.get());
                accept(output, ModItems.STRING.get());
                // 115 再生机不是有号的试验物品，排在主栏目这一档机器里
                accept(output, ModItems.E_115_REGENERATOR.get());
                accept(output, ModItems.TOWER_GHAST_SPAWN_EGG.get());
                accept(output, ModItems.STRING_SPAWN_EGG.get());
                accept(output, ModItems.GHASTFRUITY_SPAWN_EGG.get());
                accept(output, ModItems.GHAST_ELITE_SPAWN_EGG.get());
                ModEquipment.sets().values().forEach(set -> set.items().forEach(item -> accept(output, item.get())));
            })
            .build());

    /**
     * 试验物品栏：本模组 001~051 里所有有号的试验物品，再加 114 与暮色森林的试验物品 115
     *
     * <p>全部按序号升序排，靠一张 {@link TreeMap} 排出来，不走手写顺序：001 是方块，004 是照熔炉工作的那台机器，
     * 007 与 017 是那两把近战
     * 伤害 4、攻速 5 的裸武器，008 是那件 5 点饥饿 5 点饱和度的食物，027 是那支画红石点的笔，032/033 是两支长矛，
     * 036~038 与 040~042 是高塔工具与简单物品（039 号空着），046 是权杖，051 是拆解台那台机器，065 是压出「打不出伤害」的场的机器，
     * 066 是那本恶魂陷阱书，069 是压出「更新不处理」的场的机器，061/070/074 是三块不明肉块，104 是那个食物，
     * 107 是整块的红丝绒蛋糕，114 是能吃能摆的那一块，
     * 其余空号是 {@link ModExperimentItems#placeholders()} 补的占位物品
     *
     * <p>图标用暮色森林的试验物品 115，没装暮色森林时退化成试验物品 046
     *
     * <p>栏目位置靠 {@code withTabsBefore} 钉在主栏目的后面：NeoForge 给创造栏排序时，没写
     * {@code tabsBefore}/{@code tabsAfter} 的栏目统一按注册名做字母序（见 {@code CreativeModeTabRegistry}），
     * 于是别的模组的栏目会插到本模组两个栏目中间，注册先后在这里不算数
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXPERIMENT_ITEMS = TABS.register("experiment_items",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dawnduskterminal.experiment_items"))
            .withTabsBefore(DawnDuskTerminal.id("main"))
            .icon(() -> new ItemStack(TFItems.EXPERIMENT_115.get()))
            .displayItems((params, output) -> {
                SortedMap<Integer, ItemLike> sequence = new TreeMap<>();
                sequence.put(1, ModItems.EXPERIMENT_001.get());
                sequence.put(4, ModItems.EXPERIMENT_004.get());
                ModExperimentItems.placeholders().forEach((number, item) -> sequence.put(number, item.get()));
                sequence.put(7, ModExperimentItems.EXPERIMENT_007.get());
                sequence.put(8, ModExperimentItems.EXPERIMENT_008.get());
                sequence.put(17, ModExperimentItems.EXPERIMENT_017.get());
                sequence.put(19, ModItems.EXPERIMENT_019.get());
                sequence.put(20, ModExperimentItems.EXPERIMENT_020.get());
                sequence.put(27, ModExperimentItems.EXPERIMENT_027.get());
                sequence.put(32, ModExperimentItems.EXPERIMENT_032.get());
                sequence.put(33, ModExperimentItems.EXPERIMENT_033.get());
                sequence.put(36, ModExperimentItems.EXPERIMENT_036.get());
                sequence.put(37, ModExperimentItems.EXPERIMENT_037.get());
                sequence.put(38, ModExperimentItems.EXPERIMENT_038.get());
                sequence.put(40, ModExperimentItems.EXPERIMENT_040.get());
                sequence.put(41, ModExperimentItems.EXPERIMENT_041.get());
                sequence.put(42, ModExperimentItems.EXPERIMENT_042.get());
                sequence.put(43, ModExperimentItems.EXPERIMENT_043.get());
                sequence.put(45, ModExperimentItems.EXPERIMENT_045.get());
                sequence.put(46, ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get());
                sequence.put(47, ModExperimentItems.EXPERIMENT_047.get());
                sequence.put(48, ModExperimentItems.EXPERIMENT_048.get());
                sequence.put(49, ModExperimentItems.EXPERIMENT_049.get());
                sequence.put(50, ModExperimentItems.EXPERIMENT_050.get());
                sequence.put(51, ModItems.EXPERIMENT_051.get());
                sequence.put(52, ModItems.EXPERIMENT_052.get());
                sequence.put(61, ModExperimentItems.EXPERIMENT_061.get());
                sequence.put(65, ModItems.EXPERIMENT_065.get());
                sequence.put(66, ModExperimentItems.EXPERIMENT_066.get());
                sequence.put(69, ModItems.EXPERIMENT_069.get());
                sequence.put(70, ModExperimentItems.EXPERIMENT_070.get());
                sequence.put(74, ModExperimentItems.EXPERIMENT_074.get());
                sequence.put(76, ModItems.EXPERIMENT_076.get());
                sequence.put(104, ModItems.EXPERIMENT_104.get());
                sequence.put(107, ModItems.EXPERIMENT_107.get());
                sequence.put(114, ModItems.EXPERIMENT_114.get());
                sequence.forEach((number, item) -> accept(output, item));
                output.accept(TFItems.EXPERIMENT_115.get());
            })
            .build());

    private static void accept(CreativeModeTab.Output output, ItemLike item) {
        if (item == null || item.asItem() == Items.AIR) {
            DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal creative entry skipped, no item is registered for {}", item);
            return;
        }
        output.accept(item);
    }

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
