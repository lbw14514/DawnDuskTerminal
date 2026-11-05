package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.Experiment011PlateEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModExperimentItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 011 那只盘子：14 像素见方、1 像素厚，只往朝上的那一面摆，高度跟着脚下的碰撞箱走
 *
 * <p>钉住五件事：尺寸就是 14/16 x 1/16、只能点在朝上的面、底下的碰撞箱顶面有多高就摆多高
 * （满方块 1.0、半砖 0.5）、顶上有方块压着时摆不下、右键只能摆一件而空手右键把那一件拿回来
 *
 * <p>「展示物画成方块还是面片」归客户端，这里测不到：{@code client/Experiment011PlateRenderer}
 * 那边按 {@code BlockItem} 判，服务端只负责那件物品本身
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment011Tests {
    private static final double EPSILON = 1.0E-6D;

    @GameTest(template = "ddt_empty")
    public static void sizeIsFourteenByOnePixels(GameTestHelper helper) {
        EntityType<Experiment011PlateEntity> type = ModEntities.EXPERIMENT_011_PLATE.get();
        helper.assertTrue(
                Math.abs(type.getWidth() - 14.0F / 16.0F) < EPSILON,
                "the plate must be 14 pixels wide, got " + type.getWidth());
        helper.assertTrue(
                Math.abs(type.getHeight() - 1.0F / 16.0F) < EPSILON,
                "the plate must be 1 pixel tall, got " + type.getHeight());
        helper.succeed();
    }

    /** 满方块上摆 1.0、半砖上摆 0.5：位置取的是脚下碰撞箱的顶面 */
    @GameTest(template = "ddt_empty")
    public static void sitsOnTopOfTheSupportCollisionBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        helper.assertTrue(
                place(level, stone, Direction.UP).consumesAction(), "a plate must go on top of a stone block");
        Experiment011PlateEntity onStone = plateAbove(helper, stone);
        helper.assertTrue(onStone != null, "no plate appeared on top of the stone block");
        helper.assertTrue(
                Math.abs(onStone.getY() - (stone.getY() + 1.0D)) < EPSILON,
                "a plate on a full block must sit at y=" + (stone.getY() + 1.0D) + ", got " + onStone.getY());

        BlockPos slab = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(slab, Blocks.STONE_SLAB.defaultBlockState());
        helper.assertTrue(place(level, slab, Direction.UP).consumesAction(), "a plate must go on top of a slab");
        Experiment011PlateEntity onSlab = plateAbove(helper, slab);
        helper.assertTrue(onSlab != null, "no plate appeared on top of the slab");
        helper.assertTrue(
                Math.abs(onSlab.getY() - (slab.getY() + 0.5D)) < EPSILON,
                "a plate on a slab must sit at y=" + (slab.getY() + 0.5D) + ", got " + onSlab.getY());
        helper.succeed();
    }

    /** 只认朝上的那一面：对着侧面点不摆 */
    @GameTest(template = "ddt_empty")
    public static void refusesEveryFaceButUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) {
                continue;
            }
            InteractionResult result = place(level, support, face);
            helper.assertFalse(
                    result.consumesAction(), "a plate must not be placed against " + face + ", got " + result);
            helper.assertTrue(
                    platesIn(helper, support).isEmpty(), "a plate must not appear when clicked against " + face);
        }
        // 反过来，朝上的那一面必须摆得下
        helper.assertTrue(place(level, support, Direction.UP).consumesAction(), "a plate must go on the up face");
        helper.assertTrue(platesIn(helper, support).size() == 1, "exactly one plate must appear on the up face");
        helper.succeed();
    }

    /** 顶上被方块压着就摆不下：盘子只有 1 像素厚，那 1 像素也得是空的 */
    @GameTest(template = "ddt_empty")
    public static void refusesWhenThePixelAboveIsBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(support.above(), Blocks.STONE.defaultBlockState());
        InteractionResult result = place(level, support, Direction.UP);
        helper.assertFalse(result.consumesAction(), "a plate must not squeeze into a full block, got " + result);
        helper.assertTrue(platesIn(helper, support).isEmpty(), "no plate must appear under a solid block");
        helper.succeed();
    }

    /** 盘子上一次只摆一件，空手右键把那件拿回来 */
    @GameTest(template = "ddt_empty")
    public static void holdsOneItemAndGivesItBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(10, 2, 10));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        helper.assertTrue(place(level, support, Direction.UP).consumesAction(), "sanity: the plate must be placed");
        Experiment011PlateEntity plate = plateAbove(helper, support);
        helper.assertTrue(plate != null, "sanity: the plate must exist");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
        helper.assertTrue(
                plate.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
                "right-clicking with an item must put it on the plate");
        helper.assertTrue(
                plate.displayed().is(Items.DIAMOND), "the plate must show the diamond, got " + plate.displayed());
        helper.assertTrue(plate.displayed().getCount() == 1, "the plate must show exactly one item");
        helper.assertTrue(
                player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "one diamond must leave the hand, got " + player.getItemInHand(InteractionHand.MAIN_HAND));

        // 第二件摆不上去：先拿走再放
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
        helper.assertFalse(
                plate.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
                "a plate must hold one item at a time");
        helper.assertTrue(plate.displayed().is(Items.DIAMOND), "the diamond must stay on the plate");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(
                plate.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
                "an empty hand must take the item back");
        helper.assertTrue(plate.displayed().isEmpty(), "the plate must be empty after taking the item back");
        helper.assertTrue(
                player.getInventory().contains(new ItemStack(Items.DIAMOND)),
                "the diamond must end up in the player's inventory, got " + player.getInventory());
        helper.succeed();
    }

    /** 砸掉：盘子与上面的东西一起掉出来 */
    @GameTest(template = "ddt_empty")
    public static void dropsItselfAndTheDisplayedItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(12, 2, 12));
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        helper.assertTrue(place(level, support, Direction.UP).consumesAction(), "sanity: the plate must be placed");
        Experiment011PlateEntity plate = plateAbove(helper, support);
        helper.assertTrue(plate != null, "sanity: the plate must exist");
        plate.setDisplayed(new ItemStack(Items.GOLD_INGOT));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(
                plate.hurt(level.damageSources().playerAttack(player), 1.0F), "a plate must be breakable by hand");
        helper.assertTrue(plate.isRemoved(), "a broken plate must be removed");
        helper.succeed();
    }

    /** 点某一格摆一只：走的是真的 {@link com.dawnduskterminal.item.Experiment011Item#useOn} */
    private static InteractionResult place(ServerLevel level, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_011.get());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, hit);
        return ModExperimentItems.EXPERIMENT_011.get().useOn(context);
    }

    /** 这一格上方有没有盘子；没有时返回 {@code null} */
    private static Experiment011PlateEntity plateAbove(GameTestHelper helper, BlockPos support) {
        List<Experiment011PlateEntity> plates = platesIn(helper, support);
        return plates.isEmpty() ? null : plates.get(0);
    }

    /** 这一格上方那一小块里的盘子 */
    private static List<Experiment011PlateEntity> platesIn(GameTestHelper helper, BlockPos support) {
        AABB area = new AABB(support).expandTowards(0.0D, 1.0D, 0.0D);
        return helper.getLevel().getEntitiesOfClass(Experiment011PlateEntity.class, area, plate -> !plate.isRemoved());
    }

    private Experiment011Tests() {}
}
