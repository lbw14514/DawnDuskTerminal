package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.Experiment012Block;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 012 那只杯子：一格最多三只
 *
 * <p>钉住四件事：物品就是那方块的 {@link BlockItem}（摆下来的是方块本身）、拿着杯子点杯子每次都多一只、
 * 加满 {@value Experiment012Block#MAX_CUPS} 只之后这一格不再涨、敲下来按杯子数掉 1~3 个
 *
 * <p>「加一只」走的是真的右键那一条路（{@link BlockItem#useOn}），不是直接调
 * {@code setValue(CUPS, ...)}：能不能加上去其实卡在 {@code canBeReplaced} 上，
 * 少写那一条时这只杯子会「点自己没反应」，直接改状态位测不出这个错
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment012Tests {
    private static final String BLOCK_ID = "dawnduskterminal:experiment_012";

    @GameTest(template = "ddt_empty")
    public static void itemPlacesTheCupBlock(GameTestHelper helper) {
        Block block = ModBlocks.EXPERIMENT_012.get();
        helper.assertTrue(
                ModItems.EXPERIMENT_012.get() instanceof BlockItem, BLOCK_ID + " must be placed by a BlockItem");
        helper.assertTrue(
                ((BlockItem) ModItems.EXPERIMENT_012.get()).getBlock() == block,
                "the block item must place " + BLOCK_ID);
        helper.assertTrue(
                ModItems.EXPERIMENT_012.get().getDescriptionId().equals("block." + BLOCK_ID.replace(':', '.')),
                "a block item must take its name from the block, got "
                        + ModItems.EXPERIMENT_012.get().getDescriptionId());
        // 植物那一层给的是「底下的方块没了自己消失」，杯子要的正是这一条
        helper.assertTrue(
                block instanceof BushBlock,
                BLOCK_ID + " must extend BushBlock so it vanishes with its support, got "
                        + block.getClass().getSimpleName());
        helper.succeed();
    }

    /**
     * 三次点击同一格：杯子从 1 只加到 3 只，第四次这一格不再变
     *
     * <p>加的那几只是**横着错开**摆的，不是往上摞：三只的碰撞箱是三块 4x6x4、
     * 全部落在格子底面上，谁都不压谁（见 {@link #threeCupsSpreadOutInsteadOfStackingUp}）
     */
    @GameTest(template = "ddt_empty")
    public static void cupsAddUpToThreeInOneBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());

        for (int cups = 1; cups <= Experiment012Block.MAX_CUPS; cups++) {
            // 第一次点石头顶面，之后每次点的都是已经摆下的杯子本身
            BlockPos clicked = cups == 1 ? support : support.above();
            InteractionResult result = place(helper, level, clicked, Direction.UP);
            helper.assertTrue(result.consumesAction(), "click " + cups + " must place a cup, got " + result);
            BlockState state = level.getBlockState(support.above());
            helper.assertTrue(
                    state.is(ModBlocks.EXPERIMENT_012.get()),
                    "click " + cups + " must leave a cup above the stone, got " + state);
            helper.assertTrue(
                    state.getValue(Experiment012Block.CUPS) == cups,
                    "click " + cups + " must give " + cups + " cups, got " + state.getValue(Experiment012Block.CUPS));
        }

        // 第四下：这一格已经满了，再点不会变多
        place(helper, level, support.above(), Direction.UP);
        BlockState full = level.getBlockState(support.above());
        helper.assertTrue(
                full.getValue(Experiment012Block.CUPS) == Experiment012Block.MAX_CUPS,
                "a fourth cup must not push the pile past " + Experiment012Block.MAX_CUPS + ", got "
                        + full.getValue(Experiment012Block.CUPS));
        helper.succeed();
    }

    /**
     * 三只是横着错开的，不是往上摞的
     *
     * <p>照原版海泡菜那三份模型的做法：每只杯子各占一块 4x4 的脚印，都坐在格子底面上。
     * 摞起来那种写法会把碰撞箱顶到 16 像素、三块盒子在 y 上彼此压住，这条断言正好把它挡在门外
     */
    @GameTest(template = "ddt_empty")
    public static void threeCupsSpreadOutInsteadOfStackingUp(GameTestHelper helper) {
        Block block = ModBlocks.EXPERIMENT_012.get();
        BlockState three = block.defaultBlockState().setValue(Experiment012Block.CUPS, 3);
        VoxelShape shape = three.getShape(helper.getLevel(), BlockPos.ZERO);
        List<AABB> boxes = shape.toAabbs();
        helper.assertTrue(boxes.size() == 3, "three cups must be three boxes, got " + boxes);
        for (AABB box : boxes) {
            helper.assertTrue(
                    Math.abs(box.maxY - 0.375D) < 1.0E-6D,
                    "every cup must stand on the block floor and be 6 pixels tall, got " + box);
            helper.assertTrue(
                    Math.abs(box.maxX - box.minX - 0.25D) < 1.0E-6D && Math.abs(box.maxZ - box.minZ - 0.25D) < 1.0E-6D,
                    "every cup must be 4x4 pixels, got " + box);
        }
        // 三块脚印两两不相交：摞起来的话三块盒子会在 x/z 上完全重合
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                helper.assertFalse(
                        boxes.get(i).intersects(boxes.get(j)),
                        "cups must not overlap: " + boxes.get(i) + " vs " + boxes.get(j));
            }
        }
        helper.succeed();
    }

    /** 敲下来按杯子数掉：1 只掉 1 个、3 只掉 3 个 */
    @GameTest(template = "ddt_empty")
    public static void dropsOneItemPerCup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        for (int cups = 1; cups <= Experiment012Block.MAX_CUPS; cups++) {
            BlockPos pos = support.above();
            level.setBlockAndUpdate(
                    pos, ModBlocks.EXPERIMENT_012.get().defaultBlockState().setValue(Experiment012Block.CUPS, cups));
            List<ItemStack> drops = Block.getDrops(level.getBlockState(pos), level, pos, null);
            int total = drops.stream().mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(
                    total == cups,
                    "a pile of " + cups + " cups must drop " + cups + " items, got " + total + " from " + drops);
            helper.assertTrue(
                    drops.stream().allMatch(stack -> stack.is(ModItems.EXPERIMENT_012.get())),
                    "a cup pile must drop cups, got " + drops);
        }
        helper.succeed();
    }

    /** 底下的方块被挖掉，杯子跟着消失 */
    @GameTest(template = "ddt_empty")
    public static void vanishesWithItsSupport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(support.above(), ModBlocks.EXPERIMENT_012.get().defaultBlockState());
        helper.assertTrue(
                level.getBlockState(support.above()).is(ModBlocks.EXPERIMENT_012.get()),
                "sanity: the cup must sit on the stone");
        level.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
        helper.assertTrue(
                level.getBlockState(support.above()).isAir(),
                BLOCK_ID + " must vanish when its support is gone, got " + level.getBlockState(support.above()));
        helper.succeed();
    }

    /** 拿着杯子点某一格：走的是真的 {@link BlockItem#useOn}，手上那一栈每次重新给一支 */
    private static InteractionResult place(GameTestHelper helper, ServerLevel level, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(ModItems.EXPERIMENT_012.get());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
        UseOnContext context = new UseOnContext(
                level, helper.makeMockPlayer(GameType.SURVIVAL), InteractionHand.MAIN_HAND, stack, hit);
        return ModItems.EXPERIMENT_012.get().useOn(context);
    }

    private Experiment012Tests() {}
}
