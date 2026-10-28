package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.CountertopBlock;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 塔木系列方块：石切机配方真的生效，台面两块的族标记也对
 *
 * <p>连接纹理是客户端画的，服务端测不到；这里钉的是服务端也得对的事：
 * <ol>
 *   <li>石切机配方进了最终生效的那份配方表，类型是石切机，产出就是那块方块，原料吃的是对应的黑木原木——
 *       配方文件里写错 id 只会被静默丢弃，光看文件在不在不能说明问题
 *   <li>两块台面属于同一族（{@link CountertopBlock#isCountertop}），顶面才会连成一片；塔木梁不是台面，
 *       它的顶面跟台面不是同一张图集，混进去就会把两种图案接在一起
 * </ol>
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class TowerWoodTests {
    /** 以黑木原木为原料的石切机配方，方块那边的路径与配方文件名同名 */
    private static final List<String> DARK_LOG_STONECUT_BLOCKS = List.of(
        "mounted_towerwood",
        "encased_towerbeam",
        "chiseled_towerbeam",
        "warning_towerwood",
        "dark_log_countertop");

    @GameTest(template = "ddt_empty")
    public static void bothCountertopsAreOneFamily(GameTestHelper helper) {
        helper.assertTrue(
            CountertopBlock.isCountertop(ModTerrainBlocks.DARK_LOG_COUNTERTOP.get().defaultBlockState()),
            "dawnduskterminal:dark_log_countertop must be a countertop");
        helper.assertTrue(
            CountertopBlock.isCountertop(ModTerrainBlocks.STRIPPED_DARK_LOG_COUNTERTOP.get().defaultBlockState()),
            "dawnduskterminal:stripped_dark_log_countertop must be a countertop");
        helper.assertFalse(
            CountertopBlock.isCountertop(ModTerrainBlocks.ENCASED_TOWERBEAM.get().defaultBlockState()),
            "dawnduskterminal:encased_towerbeam must not count as a countertop");
        helper.assertFalse(
            CountertopBlock.isCountertop(ModTerrainBlocks.CHISELED_TOWERBEAM.get().defaultBlockState()),
            "dawnduskterminal:chiseled_towerbeam must not count as a countertop");
        helper.assertFalse(
            CountertopBlock.isCountertop(ModTerrainBlocks.WARNING_TOWERWOOD.get().defaultBlockState()),
            "dawnduskterminal:warning_towerwood must not count as a countertop");
        helper.assertFalse(
            CountertopBlock.isCountertop(ModTerrainBlocks.MOUNTED_TOWERWOOD.get().defaultBlockState()),
            "dawnduskterminal:mounted_towerwood must not count as a countertop");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void towerWoodBlocksAreCutFromDarkLog(GameTestHelper helper) {
        for (String path : DARK_LOG_STONECUT_BLOCKS) {
            assertStonecutFrom(helper, path, "twilightforest:dark_log");
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void strippedCountertopIsCutFromStrippedDarkLog(GameTestHelper helper) {
        assertStonecutFrom(helper, "stripped_dark_log_countertop", "twilightforest:stripped_dark_log");
        helper.succeed();
    }

    /**
     * 配方在最终生效的表里，类型是石切机，产出就是那块方块，原料吃的是传进来的那种原木
     *
     * <p>配方 id 与文件名同名，所以这里直接用方块路径拼：写错一边必然对不上
     */
    private static void assertStonecutFrom(GameTestHelper helper, String blockPath, String inputId) {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("dawnduskterminal", blockPath);
        ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(inputId)));
        helper.assertFalse(input.isEmpty(), "item " + inputId + " must exist, install The Twilight Forest");

        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().getRecipes().stream()
            .filter(candidate -> candidate.id().equals(recipeId))
            .findFirst()
            .orElse(null);
        helper.assertTrue(holder != null, "stonecutting recipe " + recipeId + " is missing from the recipe manager");
        helper.assertTrue(holder.value() instanceof StonecutterRecipe,
            recipeId + " must be a stonecutting recipe, got " + holder.value().getType());
        helper.assertTrue(holder.value().getResultItem(helper.getLevel().registryAccess())
                .is(BuiltInRegistries.BLOCK.get(recipeId).asItem()),
            recipeId + " must produce the block of the same id");
        helper.assertTrue(holder.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(input)),
            recipeId + " must accept " + inputId);
    }

    private TowerWoodTests() {}
}
