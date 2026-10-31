package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModBlockSetTypes;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import twilightforest.init.TFItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

        helper.assertTrue(glass instanceof TransparentBlock,
            "锻铁玻璃必须是原版玻璃那一支（TransparentBlock 或它的子类），行为才可能一模一样，实际是 "
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

        ItemStack wroughtIron = new ItemStack(TFItems.WROUGHT_IRON_BAR.get());
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

    /**
     * 物品栏图标：自己一份 {@code cube_bottom_top}，上下贴顶/底那张、四面贴单块侧面那张
     *
     * <p>图标不许碰 48x48 九宫图（那样会把整张图糊在每个面上），也不许另存和 particle 逐像素相同的副本。
     * 顶/底那张是从九宫图第二行第一列拆出来的，与单块侧面那张逐像素不同
     */
    @GameTest(template = "ddt_empty")
    public static void glassItemIconUsesExtractedFaces(GameTestHelper helper) {
        String model = readResource("/assets/dawnduskterminal/models/item/wrought_iron_glass.json");
        helper.assertTrue(model != null, "物品模型 models/item/wrought_iron_glass.json 必须在 jar 里");
        helper.assertTrue(model.contains("cube_bottom_top"),
            "图标要分开给顶底与侧面，用 cube_bottom_top，实际内容：" + model);
        helper.assertFalse(model.contains("\"dawnduskterminal:block/wrought_iron_glass\""),
            "图标不许吃 48x48 九宫格：" + model);
        helper.assertTrue(model.contains("dawnduskterminal:block/wrought_iron_glass_top"),
            "上下两面要贴拆出来的 block/wrought_iron_glass_top：" + model);
        helper.assertTrue(model.contains("dawnduskterminal:block/wrought_iron_glass_particle"),
            "四个侧面要贴 block/wrought_iron_glass_particle：" + model);
        for (String texture : new String[] {
            "/assets/dawnduskterminal/textures/block/wrought_iron_glass_top.png",
            "/assets/dawnduskterminal/textures/block/wrought_iron_glass_particle.png"}) {
            helper.assertTrue(resourceExists(texture), "贴图必须在 jar 里：" + texture);
        }
        helper.assertFalse(resourceExists("/assets/dawnduskterminal/textures/block/wrought_iron_glass_single.png"),
            "不许再存一份和 particle 逐像素相同的单面图");
        helper.succeed();
    }

    /**
     * 活板门：原版 {@link TrapDoorBlock} 那一支，五个状态一个不少，空手点得开，九宫图那几张图各就各位
     *
     * <p>连接纹理与模型是客户端的，服务端钉住的是：方块类、状态集、物品形态、能不能空手开、
     * 三份模型与贴图在 jar 里、以及配方能合成
     */
    @GameTest(template = "ddt_empty")
    public static void trapdoorIsAVanillaTrapdoorWithItsOwnAssets(GameTestHelper helper) {
        Block trapdoor = ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR.get();
        helper.assertTrue(trapdoor instanceof TrapDoorBlock,
            "锻铁玻璃活板门必须继承原版 TrapDoorBlock，开合与红石逻辑才走原版，实际是 "
                + trapdoor.getClass().getName());

        BlockState state = trapdoor.defaultBlockState();
        // facing 是水平四向那个 HORIZONTAL_FACING，不是六向的 FACING，挨个比属性对象时要对上
        for (var property : new net.minecraft.world.level.block.state.properties.Property<?>[] {
            BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF, BlockStateProperties.OPEN,
            BlockStateProperties.POWERED, BlockStateProperties.WATERLOGGED}) {
            helper.assertTrue(state.hasProperty(property),
                "活板门要有原版那五个状态，缺 " + property.getName() + "，实际状态集 " + state.getProperties());
        }
        helper.assertTrue(state.is(ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR.get()),
            "默认状态必须还指向自己");

        helper.assertTrue(ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR_ITEM.get()
                .getBlock() == trapdoor,
            "活板门的物品必须是它自己的 BlockItem");

        // 空手点得开：原版铁那一档 canOpenByHand 是 false，用错了就是「活板门点不动」
        helper.assertTrue(ModBlockSetTypes.WROUGHT_IRON_GLASS.canOpenByHand(),
            "锻铁玻璃活板门那一档方块组必须 canOpenByHand，否则空手点不开");
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), trapdoor);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(level.getBlockState(pos).getValue(BlockStateProperties.OPEN),
            "空手右键必须能把活板门打开，实际 open=" + level.getBlockState(pos).getValue(BlockStateProperties.OPEN));

        for (String path : new String[] {
            "/assets/dawnduskterminal/blockstates/wrought_iron_glass_trapdoor.json",
            "/assets/dawnduskterminal/models/block/wrought_iron_glass_trapdoor_bottom.json",
            "/assets/dawnduskterminal/models/block/wrought_iron_glass_trapdoor_top.json",
            "/assets/dawnduskterminal/models/block/wrought_iron_glass_trapdoor_open.json",
            "/assets/dawnduskterminal/models/item/wrought_iron_glass_trapdoor.json",
            "/data/dawnduskterminal/loot_table/blocks/wrought_iron_glass_trapdoor.json",
            "/data/dawnduskterminal/recipe/wrought_iron_glass_trapdoor.json"}) {
            helper.assertTrue(resourceExists(path), "活板门缺资源：" + path);
        }

        // 两块 16x16 的大面贴顶/底那张图，薄边贴单块侧面那张；两张都不该是九宫图本身
        for (String piece : new String[] {"bottom", "top", "open"}) {
            String model = readResource(
                "/assets/dawnduskterminal/models/block/wrought_iron_glass_trapdoor_" + piece + ".json");
            helper.assertTrue(model != null, "缺模型 wrought_iron_glass_trapdoor_" + piece);
            helper.assertTrue(model.contains("\"dawnduskterminal:block/wrought_iron_glass\""),
                "活板门的两块 16x16 大面要贴九宫图，由包装器按面裁格：" + model);
            helper.assertTrue(model.contains("dawnduskterminal:block/wrought_iron_glass_particle"),
                "活板门的薄边要贴单面图 block/wrought_iron_glass_particle 的前几行：" + model);
            helper.assertFalse(model.contains("wrought_iron_glass_top"),
                "顶/底那张不该单独存文件，九宫图里就有：" + model);
        }
        helper.succeed();
    }

    /**
     * 活板门的物品栏图标：自己一份薄板模型，只用拆出来的两张 16x16，一个像素都不来自九宫图
     *
     * <p>方块模型那三份是「大面吃九宫图、由包装器按面裁格」，图标不能照抄它们——图标在物品栏里
     * 没有方块上下文，吃九宫图就是整张图糊上去（这条已经翻车两次，钉死）
     */
    @GameTest(template = "ddt_empty")
    public static void trapdoorItemIconUsesExtractedFaces(GameTestHelper helper) {
        String model = readResource("/assets/dawnduskterminal/models/item/wrought_iron_glass_trapdoor.json");
        helper.assertTrue(model != null, "物品模型 models/item/wrought_iron_glass_trapdoor.json 必须在 jar 里");
        helper.assertFalse(model.contains("\"dawnduskterminal:block/wrought_iron_glass\""),
            "活板门图标不许吃 48x48 九宫图：" + model);
        helper.assertFalse(model.contains("block/wrought_iron_glass_trapdoor_"),
            "活板门图标不能 parent 方块那三份模型（它们吃九宫图）：" + model);
        helper.assertTrue(model.contains("dawnduskterminal:block/wrought_iron_glass_top"),
            "大面要贴拆出来的 block/wrought_iron_glass_top：" + model);
        helper.assertTrue(model.contains("dawnduskterminal:block/wrought_iron_glass_particle"),
            "薄边要贴 block/wrought_iron_glass_particle：" + model);
        helper.succeed();
    }

    /** 配方：两块三格的锻铁玻璃压出两个活板门，与原版活板门同一个排法 */
    @GameTest(template = "ddt_empty")
    public static void trapdoorCraftsFromGlassBlocks(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getRecipeManager();
        ResourceLocation id = DawnDuskTerminal.id("wrought_iron_glass_trapdoor");
        RecipeHolder<?> holder = manager.byKey(id).orElse(null);
        helper.assertTrue(holder != null, "配方 " + id + " 必须在配方管理器里");
        helper.assertTrue(holder.value() instanceof ShapedRecipe,
            "活板门配方必须有形状，实际是 " + holder.value().getClass().getName());
        var ingredients = holder.value().getIngredients();
        helper.assertTrue(ingredients.size() == 6, "配方要正好六格，实际 " + ingredients.size() + " 格");
        ItemStack glass = new ItemStack(ModTerrainBlocks.WROUGHT_IRON_GLASS_ITEM.get());
        helper.assertTrue(ingredients.stream().allMatch(ingredient -> ingredient.test(glass)),
            "六格都要吃锻铁玻璃方块");
        ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR_ITEM.get()),
            "产物必须是锻铁玻璃活板门，实际是 " + result);
        helper.assertTrue(result.getCount() == 2, "产物要一次给 2 个，实际给 " + result.getCount() + " 个");
        helper.succeed();
    }

    /**
     * 内部面剔除：贴合的面不画，错开的面照画
     *
     * <p>半透明的贴图一旦叠两层就看得见「粗边 + 内影」，所以关着的活板门并排、活板门压在玻璃上
     * 这两种贴住的情形必须剔；上下两块下沿活板门之间隔着空气、关着的与打开的错位，这些都不能剔，
     * 剔了就是洞
     */
    @GameTest(template = "ddt_empty")
    public static void abuttingFacesAreCulled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block glass = ModTerrainBlocks.WROUGHT_IRON_GLASS.get();
        Block trapdoor = ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR.get();
        BlockState closedBottom = trapdoor.defaultBlockState()
            .setValue(TrapDoorBlock.OPEN, false)
            .setValue(TrapDoorBlock.HALF, Half.BOTTOM);

        // 并排两块关着的下沿活板门：相接的侧面剔除
        BlockPos left = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos right = left.east();
        level.setBlockAndUpdate(left, closedBottom);
        level.setBlockAndUpdate(right, closedBottom);
        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(left), level, left, Direction.EAST, right),
            "两块并排的关着活板门之间那一面必须剔除，否则半透明贴图叠成两层");
        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(right), level, right, Direction.WEST, left),
            "反过来那一面也一样要剔除");

        // 上下错开：下沿板只占 0~3，上一块底下那面在 3~16 之后，隔着空气，不许剔
        BlockPos stacked = left.above();
        level.setBlockAndUpdate(stacked, closedBottom);
        helper.assertTrue(Block.shouldRenderFace(level.getBlockState(left), level, left, Direction.UP, stacked),
            "上下两块关着的下沿活板门之间隔着空气，顶面不能剔");
        helper.assertTrue(
            Block.shouldRenderFace(level.getBlockState(stacked), level, stacked, Direction.DOWN, left),
            "上面那块的底面也不能剔");

        // 玻璃方块压着关着的下沿活板门：两侧贴合的面都剔
        BlockPos base = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos onTop = base.above();
        level.setBlockAndUpdate(base, glass.defaultBlockState());
        level.setBlockAndUpdate(onTop, closedBottom);
        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(base), level, base, Direction.UP, onTop),
            "玻璃顶上压着关着的活板门时，玻璃的顶面要剔除");
        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(onTop), level, onTop, Direction.DOWN, base),
            "活板门的底面贴在玻璃上时也要剔除");

        // 玻璃顶面空着：照画
        level.setBlockAndUpdate(onTop, Blocks.AIR.defaultBlockState());
        helper.assertTrue(Block.shouldRenderFace(level.getBlockState(base), level, base, Direction.UP, onTop),
            "玻璃顶上没东西时顶面必须画");

        // 打开的活板门：同一朝向并排时边缘剔除，朝向不同不剔
        BlockState openNorth = trapdoor.defaultBlockState()
            .setValue(TrapDoorBlock.OPEN, true)
            .setValue(TrapDoorBlock.FACING, Direction.NORTH);
        BlockPos openLeft = helper.absolutePos(new BlockPos(6, 2, 1));
        BlockPos openRight = openLeft.east();
        level.setBlockAndUpdate(openLeft, openNorth);
        level.setBlockAndUpdate(openRight, openNorth);
        helper.assertFalse(Block.shouldRenderFace(level.getBlockState(openLeft), level, openLeft,
            Direction.EAST, openRight), "两扇同朝向打开着的活板门并排时边缘要剔除");
        BlockState openEast = openNorth.setValue(TrapDoorBlock.FACING, Direction.EAST);
        level.setBlockAndUpdate(openRight, openEast);
        helper.assertTrue(Block.shouldRenderFace(level.getBlockState(openLeft), level, openLeft,
            Direction.EAST, openRight), "朝向不同的两扇打开活板门不贴合，不能剔");
        helper.succeed();
    }

    /** 从 jar 里读一份资源，读不到给 {@code null} */
    private static String readResource(String path) {
        try (InputStream stream = WroughtIronGlassTests.class.getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean resourceExists(String path) {
        try (InputStream stream = WroughtIronGlassTests.class.getResourceAsStream(path)) {
            return stream != null;
        } catch (IOException exception) {
            return false;
        }
    }

    /** 取别的模组的物品，取不到给空栈：代码里只用字符串 ID 引用暮色森林，缺 jar 也要能起 */
    private static ItemStack foreignItem(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private WroughtIronGlassTests() {}
}
