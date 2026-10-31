package com.dawnduskterminal.compat;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import twilightforest.config.TFConfig;
import twilightforest.init.TFRecipes;
import twilightforest.item.recipe.UncraftingRecipe;

/**
 * 暮色森林拆解台的只读桥
 *
 * <p>暮色森林是必装依赖，这里直接引用它的配方类型与配置类：{@code TFRecipes.UNCRAFTING_RECIPE}
 * 是配方类型，{@code UncraftingRecipe} 上的两个方法给「这个物品能不能拆」与「拆一次吃掉几个」，
 * 筛选开关直接读 {@code TFConfig} 的同名字段。升级暮色森林时字段或方法一变，编译期就会报错
 *
 * <p>产物不走这一层：{@code UncraftingRecipe} 继承 {@code ShapedRecipe}，
 * 用原版的 {@link Recipe#getIngredients()} 加宽高就能摆出 3x3
 *
 * <p>配置字段与暮色森林一一对应：{@code allowShapelessUncrafting}（无形配方算不算可拆）、
 * {@code disableUncraftingRecipes} 与 {@code reverseRecipeBlacklist}（配方黑/白名单）、
 * {@code blacklistedUncraftingModIds} 与 {@code flipUncraftingModIdList}（按模组 id 的黑/白名单）。
 * 不在这里重新发明一套开关，改了暮色森林的配置，两块表的产物还是同一份
 */
public final class TwilightUncrafting {
    /**
     * 拆解产物的筛选配置，字段含义与暮色森林的 {@code TFConfig} 一一对应
     *
     * <p>{@link #allows(ResourceLocation)} 就是 {@code UncraftingMenu#getRecipesFor} 里那两个连续的
     * {@code if_icmpne}：名单默认是黑名单，所以「进名单」等于「不许拆」，{@code flip} 打开后反过来
     */
    public record Config(boolean allowShapeless,
                         boolean reverseRecipeBlacklist,
                         Set<String> disabledRecipes,
                         boolean flipModIdList,
                         Set<String> blacklistedModIds) {
        public static final Config DEFAULT = new Config(false, false, Set.of(), false, Set.of());

        public boolean allows(ResourceLocation recipeId) {
            boolean recipeListed = this.disabledRecipes.contains(recipeId.toString());
            if (this.reverseRecipeBlacklist != recipeListed) {
                return false;
            }
            boolean modListed = this.blacklistedModIds.contains(recipeId.getNamespace());
            return this.flipModIdList == modListed;
        }
    }

    /** 当前的配置快照，直接读暮色森林自己的字段 */
    public static Config config() {
        return new Config(
            TFConfig.allowShapelessUncrafting,
            TFConfig.reverseRecipeBlacklist,
            copyOf(TFConfig.disableUncraftingRecipes),
            TFConfig.flipUncraftingModIdList,
            copyOf(TFConfig.blacklistedUncraftingModIds));
    }

    /** 这个配方是不是暮色森林的拆解配方 */
    public static boolean isUncraftingRecipe(Recipe<?> recipe) {
        return recipe.getType() == TFRecipes.UNCRAFTING_RECIPE.get();
    }

    /** 这个物品能不能进这条拆解配方，等价于 {@code UncraftingRecipe#isItemStackAnIngredient} */
    public static boolean isIngredient(Recipe<?> recipe, ItemStack stack) {
        return recipe instanceof UncraftingRecipe uncrafting && uncrafting.isItemStackAnIngredient(stack);
    }

    /** 拆一次吃掉几个，等价于 {@code UncraftingRecipe#getCount} */
    public static int count(Recipe<?> recipe) {
        return recipe instanceof UncraftingRecipe uncrafting ? Math.max(1, uncrafting.getCount()) : 1;
    }

    /** 配方名单是 {@code List<? extends String>}，复制一份不可变集合，免得被别处改动 */
    private static Set<String> copyOf(List<? extends String> names) {
        return Set.copyOf(names);
    }

    private TwilightUncrafting() {}
}
