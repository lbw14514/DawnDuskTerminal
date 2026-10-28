package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.SusCakeBlock;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 七种可疑蛋糕（{@code sus_<口味>_cake}）的规格
 *
 * <p>它们逐条对标暮色森林的试验物品 115：物品是一片、摆下来是只剩 1 片的方块、拿物品点缺口一次补一片、
 * 潜行点缺口一次取回一片（最后一片整块取回）、空手点吃一片、状态 {@code omnomnom} 0~7、
 * 比较器公式 8 - 片数；无序配方的原料里都有试验物品 115。与 115 的差别：一片默认 6 点饥饿 4 点饱和度
 * （血橙与红丝绒是 8 与 8，115 是 4 与 2.4）；方块不用撒红石粉，吃到一半的随机刻会自己补一片
 * （115 要先撒粉成 {@code regenerate} 那版才补）。红丝绒暂无配方，所以配方那条只覆盖其余六种
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SusCakeTests {
    private static final int NUTRITION = 6;

    private static final int EIGHT_NUTRITION = 8;

    private static final int SUS_CAKE_COUNT = 7;

    private static final String CHEESE = "sus_cheese_cake";
    private static final String LAMINGTON = "sus_lamington_cake";
    private static final String PAVLOVA = "sus_pavlova_cake";
    private static final String PINEAPPLE = "sus_pineapple_cake";
    private static final String BLOODORANGE = "sus_bloodorange_cake";
    private static final String REDVELVET = "sus_redvelvet_cake";

    /**
     * 有配方的那六种：无序配方的原料，第一条都是暮色森林的试验物品 115
     *
     * <p>顺序不参与匹配（无序配方本来就只看集合），这里只是把「一条配方吃什么」写死。
     * 红丝绒暂无配方，所以不在这张表里
     */
    private static final Map<String, List<String>> RECIPE_INGREDIENTS = Map.of(
        BLOODORANGE, List.of("twilightforest:experiment_115", "dawnduskterminal:experiment_008"),
        CHEESE, List.of("twilightforest:experiment_115", "minecraft:milk_bucket"),
        "sus_cherrycheese_cake", List.of("twilightforest:experiment_115", "minecraft:milk_bucket",
            "minecraft:sweet_berries"),
        LAMINGTON, List.of("twilightforest:experiment_115", "minecraft:cocoa_beans", "minecraft:bone_meal"),
        PAVLOVA, List.of("twilightforest:experiment_115", "minecraft:egg"),
        PINEAPPLE, List.of("twilightforest:experiment_115", "minecraft:cactus"));

    @GameTest(template = "ddt_empty")
    public static void everyFlavourFeedsItsOwnServing(GameTestHelper helper) {
        Map<String, Block> blocks = new LinkedHashMap<>();
        ModBlocks.susCakes().forEach((id, holder) -> blocks.put(id, holder.get()));
        Map<String, Item> items = new LinkedHashMap<>();
        ModItems.susCakes().forEach((id, holder) -> items.put(id, holder.get()));

        helper.assertTrue(blocks.size() == SUS_CAKE_COUNT,
            "there must be " + SUS_CAKE_COUNT + " sus cakes, got " + blocks.keySet());
        helper.assertTrue(blocks.keySet().equals(items.keySet()),
            "blocks and items must use the same ids, got " + blocks.keySet() + " vs " + items.keySet());

        items.forEach((id, item) -> {
            helper.assertTrue(item instanceof BlockItem, id + " must be placeable, but it is not a BlockItem");
            helper.assertTrue(((BlockItem) item).getBlock() == blocks.get(id),
                id + " must place " + id + ", got " + ((BlockItem) item).getBlock());

            FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
            helper.assertTrue(food != null, id + " must be edible, but it carries no food component");
            boolean eight = id.equals(BLOODORANGE) || id.equals(REDVELVET);
            int nutrition = eight ? EIGHT_NUTRITION : NUTRITION;
            float saturation = eight ? 8.0F : 4.0F;
            helper.assertTrue(food.nutrition() == nutrition,
                id + " must feed " + nutrition + " hunger per slice, got " + food.nutrition());
            helper.assertTrue(Math.abs(food.saturation() - saturation) < 1.0E-4F,
                id + " must feed " + saturation + " saturation per slice, got " + food.saturation());
        });
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void eachItemPlacesASingleSliceLikeExperiment115(GameTestHelper helper) {
        ModBlocks.susCakes().forEach((id, block) -> {
            BlockState placed = block.get().defaultBlockState();
            // 115 的默认态就是 7：摆一次只放一片
            helper.assertTrue(placed.getValue(SusCakeBlock.BITES_TAKEN) == 7,
                "a placed " + id + " must be the single last slice like experiment_115, got omnomnom="
                    + placed.getValue(SusCakeBlock.BITES_TAKEN));
            helper.assertTrue(SusCakeBlock.BITES_TAKEN.getName().equals("omnomnom"),
                "the bites property must keep 115's name omnomnom, got " + SusCakeBlock.BITES_TAKEN.getName());
            helper.assertTrue(SusCakeBlock.BITES_TAKEN.getPossibleValues().size() == 8,
                "omnomnom must run 0..7, got " + SusCakeBlock.BITES_TAKEN.getPossibleValues());
            // 没有 regenerate 那种第二个状态：这一版不需要撒红石粉
            helper.assertTrue(placed.getProperties().size() == 1,
                id + " must carry omnomnom and nothing else, got " + placed.getProperties());
            helper.assertTrue(placed.isRandomlyTicking(), id + " must tick randomly, that is how it regrows");
        });
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void cakeItemAddsExactlyOneSlice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Player player = mockPlayerAt(helper, pos);
        BlockHitResult hit = hitOn(pos);
        Block lamingtonCake = ModBlocks.susCakes().get(LAMINGTON).get();

        level.setBlockAndUpdate(pos, lamingtonCake.defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 3));
        ItemStack lamington = new ItemStack(ModItems.susCakes().get(LAMINGTON).get());
        ItemInteractionResult added = level.getBlockState(pos)
            .useItemOn(lamington, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(added.consumesAction(),
            "right clicking a bitten sus_lamington_cake with another one must add a slice, got " + added);
        helper.assertTrue(level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN) == 2,
            "one cake must add exactly one slice, omnomnom should be 2, got "
                + level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN));
        helper.assertTrue(lamington.isEmpty(), "adding a slice must consume the item");

        // 别的口味补不上
        level.setBlockAndUpdate(pos, lamingtonCake.defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 3));
        ItemStack cheese = new ItemStack(ModItems.susCakes().get(CHEESE).get());
        ItemInteractionResult refused = level.getBlockState(pos)
            .useItemOn(cheese, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertFalse(refused.consumesAction(), "another flavour must not refill this cake, got " + refused);
        helper.assertTrue(level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN) == 3,
            "the refused flavour must leave the bites alone, got "
                + level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN));
        helper.assertTrue(cheese.getCount() == 1, "the refused cake must stay in hand");

        // 满的那块再点不给补
        level.setBlockAndUpdate(pos, lamingtonCake.defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 0));
        ItemInteractionResult full = level.getBlockState(pos)
            .useItemOn(new ItemStack(ModItems.susCakes().get(LAMINGTON).get()), level, player,
                InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(full == ItemInteractionResult.FAIL,
            "a full cake must refuse another slice, got " + full);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void sneakingTakesOneSliceBackLikeExperiment115(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Player player = mockPlayerAt(helper, pos);
        player.setShiftKeyDown(true);
        BlockHitResult hit = hitOn(pos);
        Block pineappleCake = ModBlocks.susCakes().get(PINEAPPLE).get();
        Item pineapple = ModItems.susCakes().get(PINEAPPLE).get();

        // 吃到一半也能取，一次取回一片：3 -> 4
        level.setBlockAndUpdate(pos, pineappleCake.defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 3));
        ItemInteractionResult taken = level.getBlockState(pos)
            .useItemOn(new ItemStack(Items.STICK), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(taken.consumesAction(), "sneak right clicking must take a slice back, got " + taken);
        helper.assertTrue(level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN) == 4,
            "taking a slice must move omnomnom from 3 to 4, got "
                + level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN));
        helper.assertTrue(player.getInventory().contains(new ItemStack(pineapple)),
            "taking a slice must hand back one cake item");

        // 只剩 1 片时整块取走
        level.setBlockAndUpdate(pos, pineappleCake.defaultBlockState());
        ItemInteractionResult last = level.getBlockState(pos)
            .useItemOn(new ItemStack(Items.STICK), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(last.consumesAction(), "taking the last slice must report success, got " + last);
        helper.assertTrue(level.getBlockState(pos).isAir(),
            "taking the last slice must clear the block, got " + level.getBlockState(pos));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void eatingASliceFeedsItsServing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState full = ModBlocks.susCakes().get(CHEESE).get().defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 0);
        level.setBlockAndUpdate(pos, full);

        Player eater = mockPlayerAt(helper, pos);
        eater.getFoodData().setFoodLevel(10);
        eater.getFoodData().setSaturation(0.0F);

        InteractionResult eaten = level.getBlockState(pos).useWithoutItem(level, eater, hitOn(pos));
        helper.assertTrue(eaten.consumesAction(), "an empty hand right click must eat a slice, got " + eaten);
        helper.assertTrue(level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN) == 1,
            "one bite must move omnomnom from 0 to 1, got "
                + level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN));
        helper.assertTrue(eater.getFoodData().getFoodLevel() == 10 + NUTRITION,
            "one slice must be " + NUTRITION + " nutrition, food level is "
                + eater.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(eater.getFoodData().getSaturationLevel() - 4.0F) < 1.0E-4F,
            "one slice must add 4.0 saturation, got " + eater.getFoodData().getSaturationLevel());

        // 血橙那一块是配方单独指定的 8 点饥饿、8 点饱和度
        level.setBlockAndUpdate(pos, ModBlocks.susCakes().get(BLOODORANGE).get().defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 0));
        eater.getFoodData().setFoodLevel(10);
        eater.getFoodData().setSaturation(0.0F);
        InteractionResult bloodorange = level.getBlockState(pos).useWithoutItem(level, eater, hitOn(pos));
        helper.assertTrue(bloodorange.consumesAction(),
            "an empty hand right click must eat a blood orange slice, got " + bloodorange);
        helper.assertTrue(eater.getFoodData().getFoodLevel() == 10 + EIGHT_NUTRITION,
            "a blood orange slice must be " + EIGHT_NUTRITION + " nutrition, food level is "
                + eater.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(eater.getFoodData().getSaturationLevel() - 8.0F) < 1.0E-4F,
            "a blood orange slice must add 8.0 saturation, got " + eater.getFoodData().getSaturationLevel());

        // 最后一片吃完方块消失
        level.setBlockAndUpdate(pos, full.setValue(SusCakeBlock.BITES_TAKEN, 7));
        eater.getFoodData().setFoodLevel(10);
        InteractionResult last = level.getBlockState(pos).useWithoutItem(level, eater, hitOn(pos));
        helper.assertTrue(last.consumesAction(), "eating the last slice must report success, got " + last);
        helper.assertTrue(level.getBlockState(pos).isAir(),
            "eating the last slice must clear the block, got " + level.getBlockState(pos));
        helper.succeed();
    }

    /**
     * 有配方那六种的无序配方都在：原料里都有暮色森林的试验物品 115，产出各自那一种蛋糕，一次一件
     *
     * <p>问的是 {@code RecipeManager}（数据包覆盖之后真正生效的那一版），不是「我们写了什么文件」
     */
    @GameTest(template = "ddt_empty")
    public static void everyCakeHasItsShapelessRecipe(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getRecipeManager();
        RECIPE_INGREDIENTS.forEach((id, expected) -> {
            ResourceLocation recipeId = DawnDuskTerminal.id(id);
            RecipeHolder<?> holder = manager.byKey(recipeId).orElse(null);
            helper.assertTrue(holder != null,
                "recipe " + recipeId + " must be loaded, it is the shapeless one of " + id);
            helper.assertTrue(holder.value() instanceof ShapelessRecipe,
                "recipe " + recipeId + " must be shapeless, got " + holder.value().getType());

            ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is(ModItems.susCakes().get(id).get()),
                "recipe " + recipeId + " must produce " + id + ", got " + result);
            helper.assertTrue(result.getCount() == 1,
                "recipe " + recipeId + " must produce one cake, got " + result.getCount());

            List<Ingredient> ingredients = holder.value().getIngredients();
            helper.assertTrue(ingredients.size() == expected.size(),
                "recipe " + recipeId + " must take " + expected.size() + " ingredients, got " + ingredients.size());
            for (String ingredientId : expected) {
                ItemStack stack = foreignItem(ingredientId);
                helper.assertTrue(!stack.isEmpty(), "ingredient id " + ingredientId + " must resolve to an item");
                helper.assertTrue(ingredients.stream().anyMatch(ingredient -> ingredient.test(stack)),
                    "recipe " + recipeId + " must accept " + ingredientId);
            }
        });
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void randomTicksRegrowWithoutRedstone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Block block = ModBlocks.susCakes().get(PAVLOVA).get();

        level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(SusCakeBlock.BITES_TAKEN, 5));
        level.getBlockState(pos).randomTick(level, pos, level.getRandom());
        helper.assertTrue(level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN) == 4,
            "one random tick must put one slice back, omnomnom should be 4, got "
                + level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN));

        // 满片时随机刻什么都不做
        level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(SusCakeBlock.BITES_TAKEN, 0));
        level.getBlockState(pos).randomTick(level, pos, level.getRandom());
        helper.assertTrue(level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN) == 0,
            "a full cake must stay full, got " + level.getBlockState(pos).getValue(SusCakeBlock.BITES_TAKEN));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void comparatorReadsRemainingSlicesAndNeverEmitsRedstone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState full = ModBlocks.susCakes().get(PINEAPPLE).get().defaultBlockState()
            .setValue(SusCakeBlock.BITES_TAKEN, 0);
        helper.assertTrue(full.hasAnalogOutputSignal(), "a sus cake must drive a comparator");
        helper.assertTrue(full.getAnalogOutputSignal(level, pos) == 8,
            "a full sus cake must read 8 on a comparator, got " + full.getAnalogOutputSignal(level, pos));
        helper.assertTrue(full.setValue(SusCakeBlock.BITES_TAKEN, 7).getAnalogOutputSignal(level, pos) == 1,
            "the last slice must read 1 on a comparator, got "
                + full.setValue(SusCakeBlock.BITES_TAKEN, 7).getAnalogOutputSignal(level, pos));
        helper.assertFalse(full.isSignalSource(), "a sus cake must not emit redstone, there is no regenerate state");
        helper.succeed();
    }

    private static Player mockPlayerAt(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    /** 按 ID 取一件物品，取不到给空栈（原料表里有别的模组的物品，代码里只用字符串引用） */
    private static ItemStack foreignItem(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static BlockHitResult hitOn(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private SusCakeTests() {}
}
