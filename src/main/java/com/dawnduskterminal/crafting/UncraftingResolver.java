package com.dawnduskterminal.crafting;

import com.dawnduskterminal.compat.TwilightUncrafting;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

/**
 * 给定一格原料，算出试验物品 051 该拆出什么
 *
 * <p>判定顺序照抄暮色森林的 {@code UncraftingMenu#getRecipesFor(ItemStack, Level)}，只是把「轮换到第几条」
 * 固定成第 0 条（本方块没有换配方的按钮）：
 *
 * <ol>
 *   <li>先扫全部配方找能<b>反查</b>的合成配方：产物就是这格原料、原料数量够、3x3 放得下、配方不是半成品，
 *       再过滤掉配置里禁拆的那些。暮色森林那边这类配方排在数组前面，所以只要存在就一定是显示出来的第一条
 *   <li>没有再找拆解台自己的 {@code twilightforest:uncrafting} 配方，按注册顺序取第一条能吃的
 *   <li>都没有，看有没有熔炼配方，有就烧
 * </ol>
 *
 * <p>产物一律取每个 {@link Ingredient} 的第一个非空选项、数量压成 1（拆解台同一位置就是这样）：
 * 一个格子的 {@code getItems()} 可能是整个标签，暮色森林用一个「换原料」按钮在候选里轮换，
 * 本方块没有那个按钮，于是固定拿第 0 个
 *
 * <p>带容器剩余物的产物（比如牛奶桶）与原版的屏障被暮色森林标记成「问题原料」，那一格在拆解台里看得见但拿不走；
 * 这里是自动出料，拿不走的东西不能真塞进格子，所以直接留空
 */
public final class UncraftingResolver {
    private static final int GRID = 3;

    /**
     * 算这一次加工。原料为空、没有任何配方可走时给 {@link UncraftingPlan#none()}
     *
     * <p>这是「全部配方 × 条件」的一次扫描，比熔炉那套 {@code CachedCheck} 贵得多，调用方必须自己缓存
     * （见 {@code Experiment051BlockEntity}），别每 tick 都调
     */
    public static UncraftingPlan resolve(Level level, ItemStack input) {
        if (input.isEmpty()) {
            return UncraftingPlan.none();
        }
        TwilightUncrafting.Config config = TwilightUncrafting.config();

        RecipeHolder<?> reversed = findReversedCrafting(level, input, config);
        if (reversed != null) {
            return fromRecipe(level, reversed.value());
        }

        RecipeHolder<?> uncrafting = findUncrafting(level, input);
        if (uncrafting != null) {
            return fromRecipe(level, uncrafting.value());
        }

        return fromSmelting(level, input);
    }

    /**
     * 反查：哪条合成配方的产物就是这格原料
     *
     * <p>逐条照抄暮色森林的筛选条件，连顺序都一样，只把「黑名单配置」换成
     * {@link TwilightUncrafting.Config#allows(net.minecraft.resources.ResourceLocation)}
     */
    @Nullable
    private static RecipeHolder<?> findReversedCrafting(
            Level level, ItemStack input, TwilightUncrafting.Config config) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!isSupported(recipe, config)) {
                continue;
            }
            if (recipe.isIncomplete()) {
                continue;
            }
            if (!recipe.canCraftInDimensions(GRID, GRID)) {
                continue;
            }
            if (recipe.getIngredients().isEmpty()) {
                continue;
            }
            if (!matches(input, recipe.getResultItem(level.registryAccess()))) {
                continue;
            }
            if (!config.allows(holder.id())) {
                continue;
            }
            return holder;
        }
        return null;
    }

    /** 反查允许的配方种类：默认只认有形配方，暮色森林开了无形拆解就一并认 */
    private static boolean isSupported(Recipe<?> recipe, TwilightUncrafting.Config config) {
        return config.allowShapeless() ? recipe instanceof CraftingRecipe : recipe instanceof ShapedRecipe;
    }

    /** 原料够不够拆出这个产物：同一个物品，且数量不少于这条配方的产物数量 */
    private static boolean matches(ItemStack input, ItemStack result) {
        return !result.isEmpty() && input.is(result.getItem()) && input.getCount() >= result.getCount();
    }

    /** 拆解台专用配方，按注册顺序取第一条能吃的 */
    @Nullable
    private static RecipeHolder<?> findUncrafting(Level level, ItemStack input) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!TwilightUncrafting.isUncraftingRecipe(holder.value())) {
                continue;
            }
            if (TwilightUncrafting.isIngredient(holder.value(), input)) {
                return holder;
            }
        }
        return null;
    }

    /**
     * 把一条配方翻成产物
     *
     * <p>拆解配方与反查配方走同一段：物品从 {@code getIngredients()} 来，位置从宽高来，
     * 吃几个原料则分两种——拆解配方写着自己的 {@code count}，反查配方按它自己的产物数量吃
     */
    private static UncraftingPlan fromRecipe(Level level, Recipe<?> recipe) {
        boolean uncrafting = TwilightUncrafting.isUncraftingRecipe(recipe);
        int inputCount = uncrafting
                ? TwilightUncrafting.count(recipe)
                : Math.max(1, recipe.getResultItem(level.registryAccess()).getCount());
        UncraftingPlan plan = new UncraftingPlan(
                UncraftingPlan.productsOf(layout(recipe)), inputCount, UncraftingPlan.DEFAULT_COOK_TIME);
        return plan.producesAnything() ? plan : UncraftingPlan.none();
    }

    /**
     * 按 3x3 摆产物
     *
     * <p>有形的配方照宽高铺：原料数组的第 {@code col + row * width} 个落到产物格的第 {@code col + row * 3} 个，
     * 两边步长不同（配方可能只有 2 格宽），所以不能拿一个 3 抄到底。无形配方没有位置，从 0 顺着排
     */
    private static ItemStack[] layout(Recipe<?> recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        ItemStack[] products = new ItemStack[UncraftingPlan.PRODUCT_SLOTS];
        boolean shaped = recipe instanceof ShapedRecipe;
        int width = shaped ? ((ShapedRecipe) recipe).getWidth() : UncraftingPlan.PRODUCT_SLOTS;
        int height = shaped ? ((ShapedRecipe) recipe).getHeight() : 1;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int source = shaped ? col + row * width : col;
                int target = shaped ? col + row * GRID : col;
                if (source >= ingredients.size() || target >= UncraftingPlan.PRODUCT_SLOTS) {
                    continue;
                }
                products[target] = firstOption(ingredients.get(source));
            }
        }
        return products;
    }

    /**
     * 一个格子的产物：候选里第一个非空的，数量压成 1
     *
     * <p>{@link Ingredient#getItems()} 给的是整个标签展开后的候选列表。问题原料（带容器剩余物的、屏障）
     * 在这里就丢掉，见类注释
     */
    private static ItemStack firstOption(Ingredient ingredient) {
        for (ItemStack option : ingredient.getItems()) {
            if (option.isEmpty()) {
                continue;
            }
            ItemStack product = option.copy();
            product.setCount(1);
            if (isProblematic(product)) {
                return ItemStack.EMPTY;
            }
            return product;
        }
        return ItemStack.EMPTY;
    }

    /** 暮色森林的 {@code UncraftingMenu#isIngredientProblematic}：带剩余物或者屏障，产物拿不走 */
    private static boolean isProblematic(ItemStack stack) {
        return stack.getItem().hasCraftingRemainingItem(stack) || stack.is(Items.BARRIER);
    }

    /**
     * 既没有反查配方也没有拆解配方，退一步当熔炉用
     *
     * <p>时间就是这条熔炼配方自己的 {@code cookingTime}（原版矿石 200 tick），不折算。
     * 产物只有一个，摆在 3x3 的第一格
     */
    private static UncraftingPlan fromSmelting(Level level, ItemStack input) {
        Optional<RecipeHolder<SmeltingRecipe>> smelting =
                level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
        if (smelting.isEmpty()) {
            return UncraftingPlan.none();
        }
        SmeltingRecipe recipe = smelting.get().value();
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty() || isProblematic(result)) {
            return UncraftingPlan.none();
        }
        ItemStack[] products = new ItemStack[UncraftingPlan.PRODUCT_SLOTS];
        products[0] = result.copy();
        int cookTime = Math.max(1, recipe.getCookingTime());
        return new UncraftingPlan(UncraftingPlan.productsOf(products), 1, cookTime);
    }

    private UncraftingResolver() {}
}
