package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.Experiment114Block;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModItems;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import twilightforest.init.TFBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 114 的规格
 *
 * <p>方块这一半要钉住的是「照抄 115」：状态集、补满与撒粉的交互、红石与比较器输出，都按 115 的数值断言；
 * 装了暮色森林时再直接拿它的 {@code twilightforest:experiment_115} 比一遍状态名。
 *
 * <p>与 115 的差别也各有断言：摆下来就是满的 8 片（115 默认剩 1 片）、补片一次补满、只有全满的收得走、
 * 吃一片走 4 营养 0.3 饱和度、没有随机刻（115 的随机刻用来给 {@code regenerate} 版补片，我们那版改成响一声）
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment114Tests {
    private static final int SLICE_NUTRITION = 4;
    private static final float SLICE_SATURATION = 0.3F;

    @GameTest(template = "ddt_empty")
    public static void itemEqualsEightExperiment115(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.EXPERIMENT_114.get());
        FoodProperties food = stack.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "experiment_114 must be edible, but it carries no food component");
        helper.assertTrue(food.nutrition() == 8 * SLICE_NUTRITION,
            "experiment_114 must be worth 8 experiment_115, nutrition should be " + 8 * SLICE_NUTRITION
                + ", got " + food.nutrition());
        helper.assertTrue(Math.abs(food.saturation() - 8 * SLICE_NUTRITION * SLICE_SATURATION * 2.0F) < 1.0E-4F,
            "experiment_114 saturation should be 8 slices worth "
                + (8 * SLICE_NUTRITION * SLICE_SATURATION * 2.0F) + ", got " + food.saturation());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void itemPlacesTheCakeBlock(GameTestHelper helper) {
        Item item = ModItems.EXPERIMENT_114.get();
        helper.assertTrue(item instanceof BlockItem, "experiment_114 must be placeable, but it is not a BlockItem");
        helper.assertTrue(((BlockItem) item).getBlock() == ModBlocks.EXPERIMENT_114.get(),
            "experiment_114 must place dawnduskterminal:experiment_114, got "
                + ((BlockItem) item).getBlock());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void statesCopyExperiment115(GameTestHelper helper) {
        Block block = ModBlocks.EXPERIMENT_114.get();
        BlockState placed = block.defaultBlockState();
        helper.assertTrue(placed.getValue(Experiment114Block.BITES_TAKEN) == 0,
            "a placed experiment_114 must be the full 8 slices, got omnomnom="
                + placed.getValue(Experiment114Block.BITES_TAKEN));
        helper.assertFalse(placed.getValue(Experiment114Block.REGENERATE),
            "a placed experiment_114 must not be the regenerating variant");
        helper.assertTrue(Experiment114Block.BITES_TAKEN.getPossibleValues().size() == 8,
            "omnomnom must run 0..7, got " + Experiment114Block.BITES_TAKEN.getPossibleValues());
        helper.assertTrue(Experiment114Block.BITES_TAKEN.getName().equals("omnomnom"),
            "the bites property must keep 115's name omnomnom, got " + Experiment114Block.BITES_TAKEN.getName());

        // 状态名逐字比一遍：114 的方块照 115 写，属性少一个就说明没跟上
        {
            Block theirs = TFBlocks.EXPERIMENT_115.get();
            Set<String> ours = propertyNames(placed);
            Set<String> expected = propertyNames(theirs.defaultBlockState());
            helper.assertTrue(ours.equals(expected),
                "experiment_114 must carry the same block states as twilightforest:experiment_115, "
                    + ours + " vs " + expected);
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void redstoneTurnsTheFullBlockIntoTheSignalVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Player player = mockPlayerAt(helper, pos);
        BlockHitResult hit = hitOn(pos);

        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_114.get().defaultBlockState());
        ItemStack dust = new ItemStack(Items.REDSTONE);
        ItemInteractionResult applied = level.getBlockState(pos)
            .useItemOn(dust, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(applied.consumesAction(),
            "sprinkling redstone on a full experiment_114 must be consumed, got " + applied);
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.EXPERIMENT_114.get()),
            "sprinkling redstone must stay on the same block, only the regenerate state flips");
        helper.assertTrue(level.getBlockState(pos).getValue(Experiment114Block.REGENERATE),
            "sprinkling redstone must set regenerate=true");
        helper.assertTrue(dust.isEmpty(),
            "sprinkling redstone must consume one dust, " + dust.getCount() + " left");

        // 已经撒过的那版再撒不重复生效
        ItemStack again = new ItemStack(Items.REDSTONE);
        ItemInteractionResult repeated = level.getBlockState(pos)
            .useItemOn(again, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertFalse(repeated.consumesAction(), "a regenerating experiment_114 must not take a second dust");

        // 吃到一半的撒不上粉
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_114.get().defaultBlockState()
            .setValue(Experiment114Block.BITES_TAKEN, 3));
        ItemStack refused = new ItemStack(Items.REDSTONE);
        ItemInteractionResult notApplied = level.getBlockState(pos)
            .useItemOn(refused, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertFalse(notApplied.consumesAction(), "a bitten experiment_114 must not take redstone");
        helper.assertTrue(refused.getCount() == 1, "the refused dust must stay in hand");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void bittenBlockRefillsToFullInOneGo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Player player = mockPlayerAt(helper, pos);
        BlockHitResult hit = hitOn(pos);

        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_114.get().defaultBlockState()
            .setValue(Experiment114Block.BITES_TAKEN, 3));
        ItemStack cake = new ItemStack(ModItems.EXPERIMENT_114.get());
        ItemInteractionResult refilled = level.getBlockState(pos)
            .useItemOn(cake, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(refilled.consumesAction(),
            "right clicking a bitten experiment_114 with another experiment_114 must refill it, got " + refilled);
        helper.assertTrue(level.getBlockState(pos).getValue(Experiment114Block.BITES_TAKEN) == 0,
            "one experiment_114 must refill the whole block, omnomnom should be 0, got "
                + level.getBlockState(pos).getValue(Experiment114Block.BITES_TAKEN));
        helper.assertTrue(cake.isEmpty(), "refilling must consume the item");

        // 满的那块补不了
        ItemStack full = new ItemStack(ModItems.EXPERIMENT_114.get());
        ItemInteractionResult refused = level.getBlockState(pos)
            .useItemOn(full, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(refused == ItemInteractionResult.FAIL,
            "a full experiment_114 must refuse another slice, got " + refused);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void onlyAFullBlockCanBePickedUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Player player = mockPlayerAt(helper, pos);
        player.setShiftKeyDown(true);
        BlockHitResult hit = hitOn(pos);

        // 吃到一半的收不走
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_114.get().defaultBlockState()
            .setValue(Experiment114Block.BITES_TAKEN, 2));
        ItemInteractionResult bitten = level.getBlockState(pos)
            .useItemOn(new ItemStack(Items.STICK), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertFalse(bitten.consumesAction(), "a bitten experiment_114 must not be picked up, got " + bitten);
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.EXPERIMENT_114.get()),
            "a bitten experiment_114 must stay in the world");

        // 全满的收得走，方块消失，物品进背包
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_114.get().defaultBlockState());
        ItemInteractionResult picked = level.getBlockState(pos)
            .useItemOn(new ItemStack(Items.STICK), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(picked.consumesAction(), "a full experiment_114 must be picked up, got " + picked);
        helper.assertTrue(level.getBlockState(pos).isAir(),
            "picking up a full experiment_114 must leave air behind, got " + level.getBlockState(pos));
        helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.EXPERIMENT_114.get())),
            "picking up a full experiment_114 must hand back the item");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void eatingASliceFeedsOneExperiment115Slice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_114.get().defaultBlockState()
            .setValue(Experiment114Block.BITES_TAKEN, 3));
        Player eater = mockPlayerAt(helper, pos);
        eater.getFoodData().setFoodLevel(10);
        eater.getFoodData().setSaturation(0.0F);

        InteractionResult eaten = level.getBlockState(pos).useWithoutItem(level, eater, hitOn(pos));
        helper.assertTrue(eaten.consumesAction(), "an empty hand right click must eat a slice, got " + eaten);
        helper.assertTrue(level.getBlockState(pos).getValue(Experiment114Block.BITES_TAKEN) == 4,
            "one bite must move omnomnom from 3 to 4, got "
                + level.getBlockState(pos).getValue(Experiment114Block.BITES_TAKEN));
        helper.assertTrue(eater.getFoodData().getFoodLevel() == 10 + SLICE_NUTRITION,
            "one slice must be " + SLICE_NUTRITION + " nutrition, food level is "
                + eater.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(eater.getFoodData().getSaturationLevel()
                - SLICE_NUTRITION * SLICE_SATURATION * 2.0F) < 1.0E-4F,
            "one slice must add " + (SLICE_NUTRITION * SLICE_SATURATION * 2.0F) + " saturation, got "
                + eater.getFoodData().getSaturationLevel());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void regeneratingVariantKeepsTheRedstoneOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState plain = ModBlocks.EXPERIMENT_114.get().defaultBlockState();
        BlockState signal = plain.setValue(Experiment114Block.REGENERATE, true);

        // 115 的公式：比较器 8 - omnomnom + (regenerate ? 7 : 0)，直连信号 15 - omnomnom * 2
        helper.assertTrue(plain.hasAnalogOutputSignal(), "experiment_114 must drive a comparator");
        helper.assertTrue(plain.getAnalogOutputSignal(level, pos) == 8,
            "a full plain experiment_114 must read 8 on a comparator, got " + plain.getAnalogOutputSignal(level, pos));
        helper.assertTrue(signal.getAnalogOutputSignal(level, pos) == 15,
            "a full regenerating experiment_114 must read 15 on a comparator, got "
                + signal.getAnalogOutputSignal(level, pos));
        helper.assertFalse(plain.isSignalSource(), "the plain variant must not emit redstone");
        helper.assertTrue(signal.isSignalSource(), "the regenerating variant must emit redstone");
        helper.assertTrue(signal.getSignal(level, pos, Direction.UP) == 15,
            "a full regenerating experiment_114 must emit 15, got " + signal.getSignal(level, pos, Direction.UP));
        BlockState lastSlice = signal.setValue(Experiment114Block.BITES_TAKEN, 7);
        helper.assertTrue(lastSlice.getSignal(level, pos, Direction.UP) == 1,
            "the last slice must emit 1, got " + lastSlice.getSignal(level, pos, Direction.UP));

        // 再生换成响声：两版都不开随机刻
        helper.assertFalse(plain.isRandomlyTicking(),
            "experiment_114 must not tick randomly, 115's random tick only fed regeneration");
        helper.succeed();
    }

    private static Set<String> propertyNames(BlockState state) {
        return state.getProperties().stream().map(Property::getName).collect(Collectors.toSet());
    }

    private static Player mockPlayerAt(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    private static BlockHitResult hitOn(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private Experiment114Tests() {}
}
