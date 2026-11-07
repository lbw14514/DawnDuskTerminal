package com.dawnduskterminal.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntBiFunction;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 「按坐标取图组里的某一格」的模型：一张 NxN 的图组，<b>每个面</b>按自己的位置挑一格贴上
 *
 * <p>做法与 {@link WroughtIronGlassModel} 同一套：方块模型里 {@code all} 指的就是那张 48x48 图组，
 * 模型烘焙出来每个面铺满整张图，这里再按「这个面该用哪一格」缩到 1/3 并平移过去
 *
 * <p>用哪一格不存进方块状态，而是渲染时现算：格子号来自 {@code tiles}（方块自己给的纯函数，
 * 参数是坐标与面的朝向），它在 {@link #getModelData} 里<b>六个面各算一次</b>，
 * 再由 {@link #getQuads} 按每个 quad 自己的朝向换成 UV。所以每个面都按自己那个平面铺 3x3，
 * 而不是只有朝上朝下的两面；活塞推走一块、{@code /setblock} 换坐标，图案也都跟着走
 *
 * <p>只裁 {@code sheet} 那一张图的面：模型里另指了 {@code particle} 的贴图（通常是那张 16x16 的单图），
 * 破坏粒子走的就是它，不会被裁成图组的一角
 */
public class LoopTileModel extends BakedModelWrapper<BakedModel> {
    /** 图组是 NxN 格。格子序号 = 列 + 行 * N */
    public static final int SHEET_SIZE = 3;

    /** 六个面各一格，下标就是 {@code Direction#ordinal} */
    private static final ModelProperty<int[]> TILES = new ModelProperty<>();

    // DefaultVertexFormat.BLOCK：每个顶点 8 个 int（3 位置 + 1 颜色 + 2 UV + 1 光照 + 1 法线）
    private static final int VERTEX_STRIDE = 8;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;

    /** 图组那张图本身。只有用这张图的面才裁格 */
    private final ResourceLocation sheet;

    /** 某个坐标的某个面该用哪一格，取值 {@code 0..SHEET_SIZE*SHEET_SIZE-1} */
    private final ToIntBiFunction<BlockPos, Direction> tiles;

    public LoopTileModel(BakedModel original, ResourceLocation sheet, ToIntBiFunction<BlockPos, Direction> tiles) {
        super(original);
        this.sheet = sheet;
        this.tiles = tiles;
    }

    /**
     * 六个面各取一格
     *
     * <p>区块网格在每块方块上只问一次这里，所以六个数一起算好塞进去，{@link #getQuads} 那边按朝向取
     */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        int[] indices = new int[Direction.values().length];
        for (Direction face : Direction.values()) {
            indices[face.ordinal()] = this.tiles.applyAsInt(pos, face);
        }
        return ModelData.of(TILES, indices);
    }

    /**
     * 没有 {@code ModelData} 的那条路兜底
     *
     * <p>{@code BakedModelWrapper} 的三参数 {@code getQuads} 直接把调用转给原模型，<b>不经过</b>子类覆写的
     * 五参数版本，所以只覆写五参数的话，任何不带方块上下文的调用都会把整张 48x48 糊在立方体每个面上。
     * 这里转回五参数并给空的 {@link ModelData}，一律落到第一格
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    /**
     * 按面重映射 UV：每个 quad 用自己朝向那一格
     *
     * <p>只用图组 {@link #sheet} 的那些面才裁格；{@code particle} 那张单图（朝向为 {@code null}）
     * 原样保留。拿不到格子表时说明这不是世界里画方块的那一次调用（手里、别模组借用这个模型），
     * 按第一格兜底
     */
    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData data,
            @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (quads.isEmpty()) {
            return quads;
        }

        int[] indices = data.get(TILES);
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (!usesSheet(quad)) {
                out.add(quad);
                continue;
            }
            Direction face = quad.getDirection();
            out.add(remap(quad, indices == null || face == null ? 0 : indices[face.ordinal()]));
        }
        return out;
    }

    private boolean usesSheet(BakedQuad quad) {
        return quad.getSprite().contents().name().equals(this.sheet);
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
}
