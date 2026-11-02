package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 001 的规格：属性和工作台一致，但不是原版工作台
 *
 * <p>重点是那两条 {@code stillValid} 断言。原版 {@code CraftingMenu#stillValid} 把判定写死成
 * {@code Blocks.CRAFTING_TABLE}，不接管的话本方块开合成界面会被服务端当场关掉；接管方式见
 * {@code mixin/CraftingMenuMixin}，它读 {@link ModBlocks#CRAFTING_TABLES}。直接问菜单要答案，
 * 比开一个真界面再等它关靠谱
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class ExperimentBlockTests {
    private static final String SUBJECT = "dawnduskterminal:experiment_001";

    @GameTest(template = "ddt_empty")
    @SuppressWarnings("deprecation") // 爆炸抗性只有这个没带 Explosion 的取值入口，NeoForge 把它标了废弃
    public static void propertiesMatchCraftingTable(GameTestHelper helper) {
        Block ours = ModBlocks.EXPERIMENT_001.get();
        helper.assertTrue(
                ours instanceof CraftingTableBlock,
                SUBJECT + " must be a CraftingTableBlock, otherwise right click cannot open the crafting menu");
        helper.assertTrue(
                ours.defaultDestroyTime() == Blocks.CRAFTING_TABLE.defaultDestroyTime(),
                SUBJECT + " destroy time must be " + Blocks.CRAFTING_TABLE.defaultDestroyTime() + ", got "
                        + ours.defaultDestroyTime());
        helper.assertTrue(
                ours.getExplosionResistance() == Blocks.CRAFTING_TABLE.getExplosionResistance(),
                SUBJECT + " explosion resistance must be " + Blocks.CRAFTING_TABLE.getExplosionResistance() + ", got "
                        + ours.getExplosionResistance());
        helper.assertTrue(
                ours.defaultMapColor() == Blocks.CRAFTING_TABLE.defaultMapColor(),
                SUBJECT + " map color must be " + Blocks.CRAFTING_TABLE.defaultMapColor() + ", got "
                        + ours.defaultMapColor());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void craftingTableTagHoldsBoth(GameTestHelper helper) {
        helper.assertTrue(
                ModBlocks.EXPERIMENT_001.get().defaultBlockState().is(ModBlocks.CRAFTING_TABLES),
                SUBJECT + " must be in #dawnduskterminal:crafting_tables, otherwise its crafting menu closes at once");
        helper.assertTrue(
                Blocks.CRAFTING_TABLE.defaultBlockState().is(ModBlocks.CRAFTING_TABLES),
                "minecraft:crafting_table must stay in #dawnduskterminal:crafting_tables, "
                        + "otherwise the vanilla crafting table breaks");
        helper.assertFalse(
                Blocks.DIRT.defaultBlockState().is(ModBlocks.CRAFTING_TABLES),
                "minecraft:dirt must not be in #dawnduskterminal:crafting_tables");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void craftingMenuStaysValidOnExperimentBlock(GameTestHelper helper) {
        assertCraftingMenuValidity(
                helper,
                new BlockPos(1, 1, 1),
                ModBlocks.EXPERIMENT_001.get(),
                true,
                "a crafting menu opened on " + SUBJECT + " must stay valid");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void craftingMenuRejectsOtherBlocks(GameTestHelper helper) {
        assertCraftingMenuValidity(
                helper,
                new BlockPos(2, 1, 2),
                Blocks.DIRT,
                false,
                "a crafting menu opened on minecraft:dirt must not stay valid");
        helper.succeed();
    }

    /** 摆块、站到旁边、开一个合成台菜单，直接问它的 would-close 判定 */
    private static void assertCraftingMenuValidity(
            GameTestHelper helper, BlockPos relative, Block block, boolean expected, String message) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(relative, block);
        BlockPos pos = helper.absolutePos(relative);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // 假玩家默认在原点生成，离结构几十万格远，先挪到方块旁边，否则距离判定先否掉
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        CraftingMenu menu = new CraftingMenu(0, player.getInventory(), ContainerLevelAccess.create(level, pos));
        boolean valid = menu.stillValid(player);
        helper.assertTrue(
                valid == expected, message + " (stillValid returned " + valid + ", expected " + expected + ")");
    }

    private ExperimentBlockTests() {}
}
