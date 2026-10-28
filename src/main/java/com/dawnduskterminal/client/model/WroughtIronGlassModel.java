package com.dawnduskterminal.client.model;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 「锻铁玻璃」的竖直连接纹理模型
 *
 * <p>贴图 {@code wrought_iron_glass.png} 是一张 48x48 的图，正好 3x3 张 16x16：
 * <ul>
 *   <li>第一行第一列：单个方块的侧面（{@link #TILE_SINGLE}）</li>
 *   <li>第二行第一列：顶面与底面（{@link #TILE_TOP_BOTTOM}）</li>
 *   <li>第二列第一行：竖直方向上最上那一段的侧面，上边收口、下边敞开（{@link #TILE_UPPER}）</li>
 *   <li>第二列第二行：中段的侧面，只有左右两条竖边（{@link #TILE_INNER}）</li>
 *   <li>第二列第三行：竖直方向上最下那一段的侧面，下边收口、上边敞开（{@link #TILE_LOWER}）</li>
 * </ul>
 * 第三列是空白的，只为凑成方图
 *
 * <p>做法是把整方块模型的顶点 UV 重映射进这张 3x3 图集的一格：方块模型里 {@code all} 指的就是这张 48x48，
 * 所以模型烘焙出来每个面铺满整张图，这里再按「这个面该用哪一格」缩到 1/3 并平移过去
 *
 * <p>连接状态不存进方块状态：邻居在渲染时从世界读（{@link #getModelData}），和
 * {@link MountedTowerWoodModel} 的「镶边塔木」是一个路子。区别只在于本方块只在竖直方向连：
 * 上面和下面各有没有同种方块，决定侧面用哪一格，水平方向不参与
 *
 * <p>竖直上升的裂缝已经被 {@code TransparentBlock} 继承来的 {@code skipRendering} 剔掉：
 * {@code HalfTransparentBlock} 在邻居是同一种方块时回答「跳过」，所以两根玻璃之间那道内部面根本不画，
 * 一根柱子的侧面只会留下最外一圈，上中下三格贴图才能接成一条连续的边
 */
public class WroughtIronGlassModel extends BakedModelWrapper<BakedModel> {
    /** 图集是 3x3 格。格子序号 = 列 + 行 * 3 */
    public static final int SHEET_SIZE = 3;

    /** 第一行第一列：单个方块的侧面 */
    private static final int TILE_SINGLE = tile(0, 0);
    /** 第二行第一列：顶面与底面共用这一格 */
    private static final int TILE_TOP_BOTTOM = tile(0, 1);
    /** 第二列第一行：竖直方向上最上那一段 */
    private static final int TILE_UPPER = tile(1, 0);
    /** 第二列第二行：中段 */
    private static final int TILE_INNER = tile(1, 1);
    /** 第二列第三行：竖直方向上最下那一段 */
    private static final int TILE_LOWER = tile(1, 2);

    private static final ModelProperty<int[]> CT_INDICES = new ModelProperty<>();

    /** 九宫图那张图本身。只有用这张图的面才裁格，薄边那几张单面图原样保留 */
    private static final ResourceLocation SHEET = DawnDuskTerminal.id("block/wrought_iron_glass");

    // DefaultVertexFormat.BLOCK：每个顶点 8 个 int（3 位置 + 1 颜色 + 2 UV + 1 光照 + 1 法线）
    private static final int VERTEX_STRIDE = 8;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;

    /** 上下两面单独用「顶/底」那一格 */
    private final boolean dedicatedTopBottom;

    /** 锻铁玻璃方块与它的物品图标：上下两面用 {@link #TILE_TOP_BOTTOM} */
    public WroughtIronGlassModel(BakedModel original) {
        this(original, true);
    }

    /**
     * @param dedicatedTopBottom 上下两面是否单独用「顶/底」那一格
     *
     * <p>锻铁玻璃方块、它的物品图标、锻铁玻璃活板门（关着时两块大面正是顶底面）都是 {@code true}；
     * 留一个 {@code false} 的口子给「只有侧面连接、没有顶底之分」的模型用
     */
    public WroughtIronGlassModel(BakedModel original, boolean dedicatedTopBottom) {
        super(original);
        this.dedicatedTopBottom = dedicatedTopBottom;
    }

    private static int tile(int col, int row) {
        return col + row * SHEET_SIZE;
    }

    /**
     * 六个面各用哪一格
     *
     * <p>上下两面永远是 {@link #TILE_TOP_BOTTOM}；四个侧面只看本方块上方与下方有没有同种方块：
     * 上下都有是柱子中段，只有下面是柱子最上段，只有上面是柱子最下段，上下都没有就是孤立的一块
     */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        Block block = state.getBlock();
        boolean above = connects(level, pos, block, Direction.UP);
        boolean below = connects(level, pos, block, Direction.DOWN);
        int side = above ? (below ? TILE_INNER : TILE_LOWER) : (below ? TILE_UPPER : TILE_SINGLE);

        int[] indices = new int[6];
        Arrays.fill(indices, side);
        if (this.dedicatedTopBottom) {
            indices[Direction.UP.ordinal()] = TILE_TOP_BOTTOM;
            indices[Direction.DOWN.ordinal()] = TILE_TOP_BOTTOM;
        }
        return ModelData.of(CT_INDICES, indices);
    }

    /**
     * 没有 {@code ModelData} 的那条路兜底
     *
     * <p>{@code BakedModelWrapper} 的三参数 {@code getQuads} 直接把调用转给原模型，**不经过**子类覆写的
     * 五参数版本，所以只覆写五参数的话，任何不带方块上下文的调用（手里、世界里某些预览、别模组借用这个模型）
     * 都会把整张 48x48 糊在立方体每个面上。这里转回五参数并给空的 {@link ModelData}，落到「单个方块」的兜底格
     *
     * <p>物品栏图标也走这条：物品模型 {@code parent} 方块模型、并且被 {@code DdtClient} 一起包上，
     * 于是图标同样是「上下顶/底那一格 + 四面单块侧面那一格」，不会把整张九宫格糊上去
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    /**
     * 按面重映射 UV
     *
     * <p>只用九宫图 {@link #SHEET} 的那些面才裁格：活板门的两块 16x16 大面是，四条薄边贴的是单面图
     * （{@code block/wrought_iron_glass_particle}），原样保留
     *
     * <p>{@code indices} 拿不到时说明这不是世界里画方块的那一次调用（物品栏、手里、别模组借用）：
     * 那一档按 {@link #defaultTile} 兜底——方块与活板门的「该是哪个面就是哪个面」在这里退化成
     * 「上下顶/底那一格 + 四面单块侧面那一格」
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (quads.isEmpty()) {
            return quads;
        }

        int[] indices = data.get(CT_INDICES);
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (!usesSheet(quad)) {
                out.add(quad);
                continue;
            }
            Direction face = quad.getDirection();
            out.add(remap(quad, indices == null || face == null ? defaultTile(face) : indices[face.ordinal()]));
        }
        return out;
    }

    private static boolean usesSheet(BakedQuad quad) {
        return quad.getSprite().contents().name().equals(SHEET);
    }

    private int defaultTile(@Nullable Direction face) {
        return this.dedicatedTopBottom && (face == Direction.UP || face == Direction.DOWN)
            ? TILE_TOP_BOTTOM
            : TILE_SINGLE;
    }

    private static BakedQuad remap(BakedQuad quad, int index) {
        var sprite = quad.getSprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int col = index % SHEET_SIZE;
        int row = index / SHEET_SIZE;
        float stepU = (u1 - u0) / SHEET_SIZE;
        float stepV = (v1 - v0) / SHEET_SIZE;
        float tileU = u0 + col * stepU;
        float tileV = v0 + row * stepV;

        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * VERTEX_STRIDE;
            // 顶点 UV 铺满整张 48x48，缩到 1/3 再平移到目标格：x 归一化后就是 0 到一个格宽，加上 tileU 即可
            float u = Float.intBitsToFloat(vertices[offset + U_OFFSET]);
            float v = Float.intBitsToFloat(vertices[offset + V_OFFSET]);
            vertices[offset + U_OFFSET] = Float.floatToRawIntBits(tileU + (u - u0) / SHEET_SIZE);
            vertices[offset + V_OFFSET] = Float.floatToRawIntBits(tileV + (v - v0) / SHEET_SIZE);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade());
    }

    private static boolean connects(BlockAndTintGetter level, BlockPos pos, Block block, Direction direction) {
        return level.getBlockState(pos.relative(direction)).is(block);
    }
}
