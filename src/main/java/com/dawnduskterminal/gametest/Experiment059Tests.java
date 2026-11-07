package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.Experiment059Block;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 059 铂催化网格的规格：玻璃的渲染、铁栅栏的音效与挖掘、3x3 图组按坐标取格
 *
 * <p>前半段钉的是「两边各借了一半」这件事本身：方块是 {@link TransparentBlock}（玻璃那一支，
 * 内部面互相剔除）、{@code noOcclusion}，而硬度、爆炸抗性、音效与「要镐子」三条全照着原版铁栅栏
 * 逐项比对——写死数字容易被一次无心的调参抹平，比着 {@code Blocks.IRON_BARS} 才是「照铁栅栏」的意思
 *
 * <p>后半段是格子号 {@link Experiment059Block#tileAt}：<b>每个面</b>在它自己那个平面上，
 * 九格在 3x3 的方块阵里正好各出现一次、周期是 3、面法线那条轴不参与；更要紧的是
 * 行与列都按「从外面看这一面」的方向走，不许取反——否则每一面都是镜像，
 * 上一版就是顶面对、其余五面全反
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment059Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_059";

    @GameTest(template = "ddt_empty")
    @SuppressWarnings("deprecation") // 音效与爆炸抗性在 1.21.1 只有这几个没带上下文的取值入口，NeoForge 标了废弃
    public static void glassRenderingWithIronBarsMining(GameTestHelper helper) {
        var ours = ModBlocks.EXPERIMENT_059.get();
        BlockState state = ours.defaultBlockState();
        helper.assertTrue(
                ours instanceof TransparentBlock,
                SUBJECT + " must extend TransparentBlock like vanilla glass, otherwise the faces inside a wall of them"
                        + " stay drawn, got " + ours.getClass().getSimpleName());
        helper.assertFalse(
                state.canOcclude(), SUBJECT + " must be noOcclusion, otherwise it hides its neighbours' faces");

        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        helper.assertTrue(
                ours.defaultDestroyTime() == bars.getBlock().defaultDestroyTime(),
                SUBJECT + " destroy time must match iron bars ("
                        + bars.getBlock().defaultDestroyTime() + "), got " + ours.defaultDestroyTime());
        helper.assertTrue(
                ours.getExplosionResistance() == bars.getBlock().getExplosionResistance(),
                SUBJECT + " explosion resistance must match iron bars ("
                        + bars.getBlock().getExplosionResistance() + "), got " + ours.getExplosionResistance());
        helper.assertTrue(
                state.getSoundType() == bars.getSoundType(),
                SUBJECT + " must use the iron bars sound type (" + bars.getSoundType() + "), got "
                        + state.getSoundType());
        helper.assertTrue(
                state.requiresCorrectToolForDrops(),
                SUBJECT + " must require the correct tool like iron bars, otherwise bare hands get the block");
        helper.succeed();
    }

    /**
     * 挖掘那一档的标签：进 {@code #minecraft:mineable/pickaxe}（镐子挖得快、且算「正确工具」），
     * 但<b>不</b>进 {@code #minecraft:needs_iron_tool}——铁栅栏木镐也挖得动，两件工具的档位要一起对上
     */
    @GameTest(template = "ddt_empty")
    public static void minedWithAnyPickaxe(GameTestHelper helper) {
        BlockState state = ModBlocks.EXPERIMENT_059.get().defaultBlockState();
        helper.assertTrue(
                state.is(BlockTags.MINEABLE_WITH_PICKAXE),
                SUBJECT + " must sit in #minecraft:mineable/pickaxe, otherwise a pickaxe neither speeds it up nor"
                        + " counts as the correct tool");
        helper.assertFalse(
                state.is(BlockTags.NEEDS_IRON_TOOL),
                SUBJECT + " must stay out of #minecraft:needs_iron_tool: iron bars can be mined with a wooden pickaxe");
        helper.assertTrue(
                Blocks.IRON_BARS.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "sanity: vanilla iron bars must be in #minecraft:mineable/pickaxe");
        helper.succeed();
    }

    /**
     * 格子号：<b>每一个面</b>都按「从外面看这一面」的方向铺 3x3，行、列都不许取反
     *
     * <p>两层要求，缺一层就会重演那个 bug：
     * <ul>
     *   <li>铺得开：每个面在它自己那两条轴上取 3x3 的方块阵，正好铺满九格；两条轴各自 +3 回到同一格，
     *       面法线那条轴不参与。只做到这一层，竖墙上就不会退化成三条竖带</li>
     *   <li>不镜像：沿<b>屏幕右</b>挪一格，格子号只有列 +1；沿<b>屏幕下</b>挪一格，只有行 +1。
     *       图组里每一格的斜纹是有方向的，行或列一取反那一面就是镜像。只做到上一条而不做这条，
     *       就是上一版的现场：顶面正常，底面与四个侧面全反</li>
     * </ul>
     *
     * <p>屏幕方向用 {@code screenRight} / {@code screenDown} 两张表钉住：从方块外面看，
     * 顶面以北为上（右 +X、下 +Z），底面从下面看南在上（右 +X、下 −Z），
     * 四面的上都是 +Y（右：南 +X、北 −X、东 −Z、西 +Z；下都是 −Y）
     *
     * <p>基准点故意带负坐标（{@code -7, -5, 3}）：{@code %} 在负半轴拿到负数，
     * 图集下标会跑到 0 以下，区块一边的半个世界就全糊在同一格上
     */
    @GameTest(template = "ddt_empty")
    public static void everyFaceTilesThreeByThreeTheSameWayUp(GameTestHelper helper) {
        int sheet = Experiment059Block.SHEET_SIZE;
        BlockPos base = new BlockPos(-7, -5, 3);
        for (Direction face : Direction.values()) {
            Direction right = screenRight(face);
            Direction down = screenDown(face);
            String where = "face " + face + " (right " + right + ", down " + down + ")";

            Set<Integer> seen = new HashSet<>();
            for (int column = 0; column < sheet; column++) {
                for (int row = 0; row < sheet; row++) {
                    BlockPos pos = base.relative(right, column).relative(down, row);
                    int tile = Experiment059Block.tileAt(pos, face);
                    helper.assertTrue(
                            tile >= 0 && tile < sheet * sheet,
                            where + ": tile index must stay inside the 3x3 sheet, got " + tile + " at " + pos);
                    helper.assertTrue(
                            seen.add(tile), where + ": a 3x3 patch must hit every tile once, " + tile + " repeated");
                }
            }
            helper.assertTrue(seen.size() == sheet * sheet, where + ": must use all nine tiles, got " + seen);

            int tile = Experiment059Block.tileAt(base, face);
            helper.assertTrue(
                    tile == Experiment059Block.tileAt(base.relative(right, sheet), face),
                    where + ": the pattern must repeat every 3 blocks along the screen-right");
            helper.assertTrue(
                    tile == Experiment059Block.tileAt(base.relative(down, sheet), face),
                    where + ": the pattern must repeat every 3 blocks along the screen-down");
            helper.assertTrue(
                    tile == Experiment059Block.tileAt(base.relative(face, 1), face),
                    where + ": the axis the face looks along must not change the tile, otherwise a wall turns into"
                            + " stripes");

            // 不镜像：沿屏幕右只有列动、沿屏幕下有行动，正反两个方向都要对上。
            // 反方向是 −1（模 3 之后是 +2），写成「也 +1」就自相矛盾了
            assertColumnStep(helper, base, face, right, 1, where);
            assertColumnStep(helper, base, face, right.getOpposite(), -1, where);
            assertRowStep(helper, base, face, down, 1, where);
            assertRowStep(helper, base, face, down.getOpposite(), -1, where);
        }
        helper.succeed();
    }

    /** 沿 {@code step} 走一格，格子号必须变成 {@code (col+delta, row)}，行一动都不许动 */
    private static void assertColumnStep(
            GameTestHelper helper, BlockPos base, Direction face, Direction step, int delta, String where) {
        int sheet = Experiment059Block.SHEET_SIZE;
        int before = Experiment059Block.tileAt(base, face);
        int after = Experiment059Block.tileAt(base.relative(step, 1), face);
        helper.assertTrue(
                Math.floorMod(after % sheet - before % sheet, sheet) == Math.floorMod(delta, sheet)
                        && after / sheet == before / sheet,
                where + ": walking one block to " + step + " must shift the sheet column by " + delta
                        + " and leave the row, " + before + " -> " + after);
    }

    /** 沿 {@code step} 走一格，格子号必须变成 {@code (col, row+delta)}，列一动都不许动 */
    private static void assertRowStep(
            GameTestHelper helper, BlockPos base, Direction face, Direction step, int delta, String where) {
        int sheet = Experiment059Block.SHEET_SIZE;
        int before = Experiment059Block.tileAt(base, face);
        int after = Experiment059Block.tileAt(base.relative(step, 1), face);
        helper.assertTrue(
                Math.floorMod(after / sheet - before / sheet, sheet) == Math.floorMod(delta, sheet)
                        && after % sheet == before % sheet,
                where + ": walking one block to " + step + " must shift the sheet row by " + delta
                        + " and leave the column, " + before + " -> " + after);
    }

    /**
     * 从方块外面看这个面时，屏幕向右是哪条世界方向
     *
     * <p>等于 {@code 视线方向 × 屏幕上}：顶面从上往下看、以北为上，右就是 +X；
     * 底面从下往上看、以南为上，右还是 +X；南北东西四面以上为 +Y，右分别是 +X、−X、−Z、+Z
     */
    private static Direction screenRight(Direction face) {
        return switch (face) {
            case UP, DOWN, SOUTH -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
        };
    }

    /** 从方块外面看这个面时，屏幕向下是哪条世界方向：顶面是 +Z，底面是 −Z，四面都是 −Y */
    private static Direction screenDown(Direction face) {
        return switch (face) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.DOWN;
        };
    }

    /**
     * 资源那一套：方块状态一个变体、{@code cube_all} 加半透明渲染层、{@code all} 指 3x3 图组、
     * {@code particle} 指单图、物品图标走 {@code item/generated} 加单图、战利品表掉自己、
     * 两张贴图都在 jar 里且图组确实是 3x3 张 16x16
     *
     * <p>assets 下的模型服务端不加载，构建也不会因为路径写错而失败，所以这里直接读 classpath 上的文件本身
     */
    @GameTest(template = "ddt_empty", batch = "ddt059_assets")
    public static void assetsMatchTheBlock(GameTestHelper helper) {
        JsonObject states = readJson(helper, "/assets/dawnduskterminal/blockstates/" + id() + ".json");
        JsonObject variants = states.getAsJsonObject("variants");
        helper.assertTrue(
                variants.size() == 1,
                SUBJECT + " has no state, its blockstates file must carry exactly one variant, got " + variants.size());
        helper.assertTrue(
                variants.getAsJsonObject("").get("model").getAsString().equals("dawnduskterminal:block/" + id()),
                "the only variant must point at block/" + id() + ", got " + variants.getAsJsonObject(""));

        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/block/" + id() + ".json");
        helper.assertTrue(
                model.get("parent").getAsString().equals("minecraft:block/cube_all"),
                "block/" + id() + " must stay a plain cube_all model, got " + model.get("parent"));
        helper.assertTrue(
                model.has("render_type")
                        && model.get("render_type").getAsString().equals("minecraft:translucent"),
                "the block must render like glass, translucent layer, got " + model.get("render_type"));
        JsonObject textures = model.getAsJsonObject("textures");
        helper.assertTrue(
                textures.get("all").getAsString().equals("dawnduskterminal:block/" + id() + "_loop"),
                "all faces must read the 3x3 sheet block/" + id() + "_loop, got " + textures.get("all"));
        helper.assertTrue(
                textures.get("particle").getAsString().equals("dawnduskterminal:block/" + id()),
                "break particles must read the single 16x16 picture, otherwise every chip is a third of the sheet, got "
                        + textures.get("particle"));

        JsonObject item = readJson(helper, "/assets/dawnduskterminal/models/item/" + id() + ".json");
        helper.assertTrue(
                item.get("parent").getAsString().equals("minecraft:item/generated"),
                "the item icon must stay a flat generated sprite, got " + item.get("parent"));
        helper.assertTrue(
                item.getAsJsonObject("textures").get("layer0").getAsString().equals("dawnduskterminal:block/" + id()),
                "the icon must read the single 16x16 picture block/" + id() + ", got "
                        + item.getAsJsonObject("textures"));

        JsonObject loot = readJson(helper, "/data/dawnduskterminal/loot_table/blocks/" + id() + ".json");
        helper.assertTrue(
                loot.getAsJsonArray("pools")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("entries")
                        .get(0)
                        .getAsJsonObject()
                        .get("name")
                        .getAsString()
                        .equals(SUBJECT),
                "the block must drop itself, got "
                        + loot.getAsJsonArray("pools").get(0));

        int[] single = pngSize(helper, "/assets/dawnduskterminal/textures/block/" + id() + ".png");
        helper.assertTrue(
                single[0] == 16 && single[1] == 16,
                "the icon and particle picture must stay 16x16, got " + single[0] + "x" + single[1]);
        int[] loop = pngSize(helper, "/assets/dawnduskterminal/textures/block/" + id() + "_loop.png");
        helper.assertTrue(
                loop[0] == 16 * Experiment059Block.SHEET_SIZE && loop[1] == 16 * Experiment059Block.SHEET_SIZE,
                "the sheet must be 3x3 pictures of 16x16 = " + (16 * Experiment059Block.SHEET_SIZE) + " square, got "
                        + loop[0] + "x" + loop[1]);

        // 名字挂在方块那一档：摆下来的这一件是 BlockItem，显示名取的是 block.<id>，
        // 少一条语言文件，创造栏里就是一串原始键
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            JsonObject words = readJson(helper, "/assets/dawnduskterminal/lang/" + lang + ".json");
            helper.assertTrue(
                    words.has("block.dawnduskterminal." + id()),
                    lang + " must name " + SUBJECT + ", otherwise the tab shows a raw key");
        }
        helper.succeed();
    }

    /** 方块自己的物品形态：放下来就是它，且只有它 */
    @GameTest(template = "ddt_empty")
    public static void placesTheSameBlock(GameTestHelper helper) {
        Item item = ModItems.EXPERIMENT_059.get();
        helper.assertTrue(item instanceof BlockItem, SUBJECT + " must have a BlockItem, got " + item.getClass());
        helper.assertTrue(
                ((BlockItem) item).getBlock() == ModBlocks.EXPERIMENT_059.get(), "the BlockItem must place " + SUBJECT);
        helper.succeed();
    }

    private static String id() {
        return "experiment_059";
    }

    /** 读 PNG 的 IHDR：宽高在 16..23 字节，大端 */
    private static int[] pngSize(GameTestHelper helper, String resource) {
        byte[] bytes = readBytes(helper, resource);
        helper.assertTrue(bytes.length > 24, resource + " is too short to be a Png");
        long width = 0;
        long height = 0;
        for (int i = 0; i < 4; i++) {
            width = width << 8 | (bytes[16 + i] & 0xFFL);
            height = height << 8 | (bytes[20 + i] & 0xFFL);
        }
        return new int[] {(int) width, (int) height};
    }

    private static byte[] readBytes(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment059Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return stream.readAllBytes();
        } catch (IOException problem) {
            throw new AssertionError("reading " + resource + " failed", problem);
        }
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        return JsonParser.parseString(new String(readBytes(helper, resource), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private Experiment059Tests() {}
}
