package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.ClimbableHollowLogBlock;
import com.dawnduskterminal.block.HollowLogVariants;
import com.dawnduskterminal.block.HorizontalHollowLogBlock;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 天门松木家族：木的去皮映射，以及空心原木「一个物品对应三块方块」的放置规则
 *
 * <p>三块空心原木共用一件物品，因此放置方向必须由点击面决定：点顶面放竖向那块，
 * 点侧面放横向那块、轴向跟着点击面走。挖下来三块都还原成同一件物品，
 * 这条由 {@code getBlock().asItem()} 断言，它走的是 {@code BlockItem.registerBlocks} 建立的反查表。
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SkygateWoodTests {
    private static final int PLACEMENT_LIFT = 30;

    @GameTest(template = "ddt_empty")
    public static void hollowLogItemPlacesBothOrientations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_ITEM.get();
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);

        BlockPos ground = helper.absolutePos(BlockPos.ZERO).above(PLACEMENT_LIFT);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        placer.moveTo(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D);

        ItemStack stack = new ItemStack(item);
        InteractionResult clickedTop = stack.useOn(new BlockPlaceContext(placer, InteractionHand.MAIN_HAND, stack,
            new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
        helper.assertTrue(clickedTop.consumesAction(),
            "placing the hollow log onto a top face must succeed, got " + clickedTop);

        BlockPos verticalPos = ground.above();
        helper.assertTrue(level.getBlockState(verticalPos).is(ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_VERTICAL.get()),
            "a top face click must place the vertical hollow log, got " + level.getBlockState(verticalPos));

        BlockPos wall = ground.offset(3, 0, 0);
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(wall.north(), Blocks.AIR.defaultBlockState());
        ItemStack sideStack = new ItemStack(item);
        InteractionResult clickedSide = sideStack.useOn(new BlockPlaceContext(placer, InteractionHand.MAIN_HAND, sideStack,
            new BlockHitResult(Vec3.atCenterOf(wall), Direction.NORTH, wall, false)));
        helper.assertTrue(clickedSide.consumesAction(),
            "placing the hollow log onto a side face must succeed, got " + clickedSide);

        BlockState horizontal = level.getBlockState(wall.north());
        helper.assertTrue(horizontal.is(ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_HORIZONTAL.get()),
            "a side face click must place the horizontal hollow log, got " + horizontal);
        helper.assertTrue(horizontal.getValue(HorizontalHollowLogBlock.HORIZONTAL_AXIS) == Direction.Axis.Z,
            "the horizontal hollow log must follow the clicked face axis, got "
                + horizontal.getValue(HorizontalHollowLogBlock.HORIZONTAL_AXIS));

        helper.assertTrue(horizontal.getBlock().asItem() == item,
            "the horizontal hollow log must drop the shared hollow log item, got " + horizontal.getBlock().asItem());
        helper.assertTrue(level.getBlockState(verticalPos).getBlock().asItem() == item,
            "the vertical hollow log must drop the shared hollow log item, got "
                + level.getBlockState(verticalPos).getBlock().asItem());
        helper.succeed();
    }

    /** 塞梯子要换成攀爬版，而且攀爬版必须进 {@code minecraft:climbable}，否则梯子只是画上去的 */
    @GameTest(template = "ddt_empty")
    public static void hollowLogTakesALadderAndBecomesClimbable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(PLACEMENT_LIFT);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

        BlockState vertical = ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_VERTICAL.get().defaultBlockState();
        level.setBlockAndUpdate(pos, vertical);

        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        placer.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        ItemStack ladder = new ItemStack(Items.LADDER);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);

        ItemInteractionResult placed = vertical.useItemOn(ladder, level, placer, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(placed.consumesAction(), "putting a ladder into the hollow log must succeed, got " + placed);

        BlockState climbed = level.getBlockState(pos);
        helper.assertTrue(climbed.is(ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_CLIMBABLE.get()),
            "a ladder must turn the hollow log into its climbable block, got " + climbed);
        helper.assertTrue(climbed.getValue(ClimbableHollowLogBlock.VARIANT) == HollowLogVariants.Climbable.LADDER,
            "a ladder must pick the ladder variant, got " + climbed.getValue(ClimbableHollowLogBlock.VARIANT));
        helper.assertTrue(climbed.is(BlockTags.CLIMBABLE),
            "the climbable hollow log must be in minecraft:climbable, otherwise it cannot be climbed");
        helper.succeed();
    }

    /** 去皮走 NeoForge 数据图，木不能只在代码里注册、映射漏写 */
    @GameTest(template = "ddt_empty")
    public static void axeStripsTheWoodKeepingTheAxis(GameTestHelper helper) {
        BlockState wood = ModTerrainBlocks.SKYGATE_PINE_WOOD.get().defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);

        BlockState stripped = AxeItem.getAxeStrippingState(wood);

        helper.assertTrue(stripped != null,
            "the strippables data map must map skygate_pine_wood to a stripped block, AxeItem returned null");
        helper.assertTrue(stripped.is(ModTerrainBlocks.STRIPPED_SKYGATE_PINE_WOOD.get()),
            "skygate_pine_wood must strip into stripped_skygate_pine_wood, got " + stripped);
        helper.assertTrue(stripped.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z,
            "stripping must keep the log axis, got " + stripped);
        helper.succeed();
    }
}
