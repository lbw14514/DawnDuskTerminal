package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 苍穹石在合成里顶替石头与圆石
 *
 * <p>原版配方把「石头」「圆石」当<b>物品</b>写死在 {@code key}/{@code ingredient} 里，而不是引用标签：
 * 1.21.1 的物品标签里根本没有 {@code minecraft:stone} 与 {@code minecraft:cobblestone}，只有
 * {@code stone_crafting_materials}（熔炉、酿造台）和 {@code stone_tool_materials}（石制工具）两条。
 * 所以只往自造标签里塞一个方块不会有任何效果——标签必须先被配方引用。这里钉住的是最终行为：
 *
 * <ol>
 *   <li>四条 {@code minecraft:} 标签都认得苍穹石：两条自造的（{@code minecraft:stone}、
 *       {@code minecraft:cobblestone}，供 {@code data/minecraft/recipe} 下那些改写过的原版配方引用），
 *       两条原版就有的材料标签
 *   <li>NeoForge 的两条通用标签也认得它：NeoForge 自己会覆盖一部分原版配方并把原料换成
 *       {@code c:cobblestones/normal}、{@code c:stones}，而<b>它的数据包压得住模组的数据包</b>，
 *       那批配方我们改写也没用，只能把苍穹石塞进它引用的标签里
 *   <li>{@code data/minecraft/recipe} 里改写过的每一条配方，至少有一个原料格吃苍穹石
 *   <li>石切机自己那条原版配方被 NeoForge 覆盖成写死 {@code minecraft:stone} 的版本，压不过，
 *       所以由 {@code dawnduskterminal:canopy_stone_stonecutter} 补一条
 * </ol>
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class RecipeTagTests {
    /** 改写过的原版配方，以石头为原料的那些（不含石切机，那条单独验） */
    private static final List<String> STONE_RECIPES = List.of(
        "stone_bricks",
        "stone_bricks_from_stone_stonecutting",
        "stone_brick_slab_from_stone_stonecutting",
        "stone_brick_stairs_from_stone_stonecutting",
        "stone_brick_walls_from_stone_stonecutting",
        "chiseled_stone_bricks_stone_from_stonecutting",
        "stone_slab",
        "stone_slab_from_stone_stonecutting",
        "stone_stairs",
        "stone_stairs_from_stone_stonecutting",
        "stone_button",
        "stone_pressure_plate",
        "comparator",
        "repeater",
        "smooth_stone");

    /** 以圆石为原料的那些：前七条模组自己改写，后半段是 NeoForge 覆盖、只能靠通用标签 */
    private static final List<String> COBBLESTONE_RECIPES = List.of(
        "cobblestone_slab",
        "cobblestone_slab_from_cobblestone_stonecutting",
        "cobblestone_stairs",
        "cobblestone_stairs_from_cobblestone_stonecutting",
        "cobblestone_wall",
        "cobblestone_wall_from_cobblestone_stonecutting",
        "stone",
        "mossy_cobblestone_from_vine",
        "mossy_cobblestone_from_moss_block",
        "andesite",
        "diorite",
        "dispenser",
        "dropper",
        "lever",
        "observer",
        "piston",
        "coast_armor_trim_smithing_template",
        "sentry_armor_trim_smithing_template",
        "vex_armor_trim_smithing_template");

    @GameTest(template = "ddt_empty")
    public static void canopyStoneSitsInStoneTags(GameTestHelper helper) {
        Item canopyStone = ModTerrainBlocks.CANOPY_STONE.get().asItem();
        assertTagged(helper, canopyStone, "minecraft", "stone");
        assertTagged(helper, canopyStone, "minecraft", "cobblestone");
        assertTagged(helper, canopyStone, "minecraft", "stone_crafting_materials");
        assertTagged(helper, canopyStone, "minecraft", "stone_tool_materials");
        assertTagged(helper, canopyStone, "c", "stones");
        assertTagged(helper, canopyStone, "c", "cobblestones/normal");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void vanillaStoneRecipesAcceptCanopyStone(GameTestHelper helper) {
        assertIngredientsAccept(helper, STONE_RECIPES, "stone");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void vanillaCobblestoneRecipesAcceptCanopyStone(GameTestHelper helper) {
        assertIngredientsAccept(helper, COBBLESTONE_RECIPES, "cobblestone");
        helper.succeed();
    }

    /** 石切机：不看是哪条配方，只要求「有办法拿苍穹石做出来」 */
    @GameTest(template = "ddt_empty")
    public static void canopyStoneCraftsAStonecutter(GameTestHelper helper) {
        ItemStack canopyStone = new ItemStack(ModTerrainBlocks.CANOPY_STONE.get());
        boolean any = helper.getLevel().getRecipeManager().getRecipes().stream()
            .anyMatch(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()).is(Items.STONECUTTER)
                && accepts(holder, canopyStone));
        helper.assertTrue(any, "some recipe producing minecraft:stonecutter must accept dawnduskterminal:canopy_stone");
        helper.succeed();
    }

    private static void assertTagged(GameTestHelper helper, Item item, String namespace, String path) {
        TagKey<Item> tag = ItemTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
        helper.assertTrue(new ItemStack(item).is(tag),
            "dawnduskterminal:canopy_stone must be in #" + namespace + ":" + path);
    }

    /**
     * 每条配方都必须在档案里，且至少有一个原料格吃苍穹石
     *
     * <p>{@code RecipeManager#getRecipes} 给的是全量配方（含数据包覆盖后的那版），所以这是在验最终生效的那一版，
     * 而不是「我们写了什么文件」
     */
    private static void assertIngredientsAccept(GameTestHelper helper, List<String> recipePaths, String group) {
        RecipeManager manager = helper.getLevel().getRecipeManager();
        ItemStack canopyStone = new ItemStack(ModTerrainBlocks.CANOPY_STONE.get());
        for (String path : recipePaths) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", path);
            RecipeHolder<?> holder = manager.getRecipes().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElse(null);
            helper.assertTrue(holder != null, "recipe " + id + " (" + group + ") is missing from the recipe manager");
            helper.assertTrue(accepts(holder, canopyStone),
                "recipe " + id + " (" + group + ") must accept dawnduskterminal:canopy_stone");
        }
    }

    private static boolean accepts(RecipeHolder<?> holder, ItemStack stack) {
        return holder.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack));
    }

    private RecipeTagTests() {}
}
