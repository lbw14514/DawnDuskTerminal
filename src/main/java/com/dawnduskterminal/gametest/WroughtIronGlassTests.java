package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 钉住锻铁玻璃的三件事：它确实是原版玻璃那一档方块、竖直叠起来时中间的面会被剔除、以及它那条无序配方
 *
 * <p>连接纹理那部分是纯客户端的（{@code WroughtIronGlassModel} 在烘焙模型里换 UV），服务端测不到；
 * 这里测的是它依赖的前提——同种方块之间的内部面被 {@code skipRendering} 剔掉，柱子只剩最外一圈，
 * 上中下三格贴图才能接成一条连续的边
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class WroughtIronGlassTests {
    private static final BlockPos LOWER = new BlockPos(1, 2, 1);
    private static final BlockPos UPPER = new BlockPos(1, 3, 1);
    private static final BlockPos EAST = new BlockPos(2, 2, 1);

    /**
     * 方块档位：与 {@code Blocks.GLASS} 是同一个类，属性也逐条对上
     *
     * <p>「是一种玻璃」不是靠名字，是靠这几条：{@link TransparentBlock} 给空视觉形状、天光直穿，
     * {@code noOcclusion} 让邻居的面照常画，四条 never 关掉刷怪 红石导通 窒息 遮挡视线
     */
    @GameTest(template = "ddt_empty")
    public static void wroughtIronGlassIsVanillaGlassClass(GameTestHelper helper) {
        Block glass = ModTerrainBlocks.WROUGHT_IRON_GLASS.get();
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(LOWER);
        BlockState state = glass.defaultBlockState();

        helper.assertTrue(glass.getClass() == Blocks.GLASS.getClass(),
            "锻铁玻璃要与原版玻璃用同一个方块类（TransparentBlock），行为才可能一模一样，实际是 "
                + glass.getClass().getName());
        helper.assertFalse(state.canOcclude(),
            "半透明整方块必须 noOcclusion，否则它会剔除邻居的面，没画出来的面就是一个洞");
        helper.assertFalse(state.isRedstoneConductor(level, pos), "玻璃不能当红石导体");
        helper.assertFalse(state.isSuffocating(level, pos), "玻璃不窒息");
        helper.assertFalse(state.isViewBlocking(level, pos), "玻璃不遮挡视线");
        helper.assertTrue(state.propagatesSkylightDown(level, pos), "玻璃让天光直穿，原版玻璃也是这样");
        helper.assertTrue(glass.defaultDestroyTime() == 0.3F,
            "硬度照原版玻璃是 0.3，实际是 " + glass.defaultDestroyTime());
        helper.succeed();
    }

    /**
     * 竖直叠两根时中间那个面必须剔除，最上面那一块的顶面必须留下
     *
     * <p>剔除来自 {@code HalfTransparentBlock#skipRendering}，{@link Block#shouldRenderFace} 最先问它
     * ——这与 {@link TranslucentFaceCullingTests} 对苍穹方块断言的是同一件事，只是这里要的是竖直方向
     */
    @GameTest(template = "ddt_empty")
    public static void stackedGlassCullsTheFaceBetweenThem(GameTestHelper helper) {
        Block glass = ModTerrainBlocks.WROUGHT_IRON_GLASS.get();
        helper.setBlock(LOWER, glass);
        helper.setBlock(UPPER, glass);
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(LOWER);
        BlockPos upper = helper.absolutePos(UPPER);

        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(lower), level, lower, Direction.UP, upper),
            "两根玻璃竖直挨着时，下面那块的顶面必须剔除，不然一根柱子里会一层层画顶面");

        // 对照：最上面那一块上方是空气，顶面照常画，这一格就是贴图里第二行第一列那张
        BlockPos aboveUpper = upper.above();
        helper.assertTrue(Block.shouldRenderFace(level.getBlockState(upper), level, upper, Direction.UP, aboveUpper),
            "柱子最上面那一块的顶面必须保留，那是贴图里顶面那一格");

        // 水平方向同理：并排两块之间的侧面也剔除
        helper.setBlock(EAST, glass);
        BlockPos east = helper.absolutePos(EAST);
        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(lower), level, lower, Direction.EAST, east),
            "并排两块玻璃之间的侧面也必须剔除");
        helper.succeed();
    }

    /** 它是玻璃，所以进通用玻璃方块标签，别的模组按 {@code c:glass_blocks} 找玻璃时才认得它 */
    @GameTest(template = "ddt_empty")
    public static void wroughtIronGlassIsInTheCommonGlassTag(GameTestHelper helper) {
        TagKey<Block> tag = BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
        helper.assertTrue(ModTerrainBlocks.WROUGHT_IRON_GLASS.get().defaultBlockState().is(tag),
            "dawnduskterminal:wrought_iron_glass 必须在 #c:glass_blocks 里");
        helper.succeed();
    }

    /**
     * 无序配方：8 个原版玻璃 + 1 个暮色森林锻铁锭 = 8 个锻铁玻璃
     *
     * <p>九格必须正好是这九件东西，所以验的是「配方管理器里最终生效的那一版」：一条无序配方、九个原料格、
     * 其中八格吃 {@code minecraft:glass}、一格吃 {@code twilightforest:wrought_iron_bar}，产物八件。
     * 暮色森林的物品只用字符串 ID 取，代码里不 import 它的类
     */
    @GameTest(template = "ddt_empty")
    public static void wroughtIronGlassCraftsShapelesslyFromGlassAndWroughtIron(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getRecipeManager();
        ResourceLocation id = DawnDuskTerminal.id("wrought_iron_glass");
        RecipeHolder<?> holder = manager.byKey(id).orElse(null);
        helper.assertTrue(holder != null, "配方 " + id + " 必须在配方管理器里，说明 JSON 解析失败或者文件放错了目录");
        helper.assertTrue(holder.value() instanceof ShapelessRecipe,
            "锻铁玻璃的配方必须是无序合成，实际是 " + holder.value().getClass().getName());

        ItemStack wroughtIron = foreignItem("twilightforest:wrought_iron_bar");
        helper.assertFalse(wroughtIron.isEmpty(),
            "暮色森林的锻铁锭（twilightforest:wrought_iron_bar）必须存在，本模组运行要求装暮色森林");

        ItemStack glass = new ItemStack(Items.GLASS);
        var ingredients = holder.value().getIngredients();
        long glassSlots = ingredients.stream().filter(ingredient -> ingredient.test(glass)).count();
        long ingotSlots = ingredients.stream().filter(ingredient -> ingredient.test(wroughtIron)).count();
        helper.assertTrue(ingredients.size() == 9, "配方要正好用满九格，实际有 " + ingredients.size() + " 格");
        helper.assertTrue(glassSlots == 8, "配方里要有 8 个原版玻璃，实际有 " + glassSlots + " 格吃玻璃");
        helper.assertTrue(ingotSlots == 1, "配方里要有 1 个锻铁锭，实际有 " + ingotSlots + " 格吃锻铁锭");

        ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModTerrainBlocks.WROUGHT_IRON_GLASS_ITEM.get()),
            "产物必须是 dawnduskterminal:wrought_iron_glass，实际是 " + result);
        helper.assertTrue(result.getCount() == 8, "产物要一次给 8 个，实际给 " + result.getCount() + " 个");
        helper.succeed();
    }

    /** 取别的模组的物品，取不到给空栈：代码里只用字符串 ID 引用暮色森林，缺 jar 也要能起 */
    private static ItemStack foreignItem(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private WroughtIronGlassTests() {}
}
