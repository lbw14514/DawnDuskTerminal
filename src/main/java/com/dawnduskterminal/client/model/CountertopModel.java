package com.dawnduskterminal.client.model;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.CountertopBlock;
import java.util.ArrayList;
import java.util.List;
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
 * 「黑木台面」的顶面连接纹理模型
 *
 * <p>两块台面（黑木、去皮黑木）共用同一张顶面：贴的是封装塔木梁那张 128x128 的 8x8 图集
 * （{@code block/encased_towerbeam_connected}），侧面与底面各是两张自己的 16x16 单图。所以这里只把
 * <b>顶面</b>那四个顶点重映射进图集的一格，其余面原样放行——判据是这一格的 sprite 就是图集本身
 * （{@link #SHEET}），与 {@link WroughtIronGlassModel} 挑九宫图是同一个办法，比按面序号判更稳：
 * 侧面哪天换成别的方块借来的模型也不会被误裁
 *
 * <p>连接判定是「邻居是不是台面」（{@link CountertopBlock#isCountertop}），不是「邻居是不是同一种方块」：
 * 两种台面顶面相同，混着摆也要连成一片。方块本身不存连接状态，邻居在渲染时从世界读，与
 * {@link MountedTowerWoodModel} 一个路子
 *
 * <p>物品图标不在这条链上：{@code models/item/<id>.json} 是它自己的 {@code cube_bottom_top}，顶面贴的是
 * 拆出来的 16x16 单图 {@code block/countertop_top}，没有图集可裁，{@code DdtClient} 也只包方块那一处键
 */
public class CountertopModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Integer> TOP_INDEX = new ModelProperty<>();

    /** 顶面那张 8x8 图集本身。只有用这张图的面才裁格 */
    private static final ResourceLocation SHEET = DawnDuskTerminal.id("block/encased_towerbeam_connected");

    // DefaultVertexFormat.BLOCK：每个顶点 8 个 int（3 位置 + 1 颜色 + 2 UV + 1 光照 + 1 法线）
    private static final int VERTEX_STRIDE = 8;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;

    public CountertopModel(BakedModel original) {
        super(original);
    }

    /**
     * 顶面用哪一格
     *
     * <p>台面是整方块，所以顶面只看同一层的八个邻居：横竖两条加上四角，正是 47 格图集要的那一圈
     */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        int index = ConnectedTexture47.index(level, pos, Direction.UP, CountertopBlock::isCountertop);
        return ModelData.of(TOP_INDEX, index);
    }

    /**
     * 没有 {@code ModelData} 的那条路兜底
     *
     * <p>{@code BakedModelWrapper} 的三参数 {@code getQuads} 直接把调用转给原模型、<b>不经过</b>子类覆写的
     * 五参数版本，所以只覆写五参数的话，任何不带方块上下文的调用都会把整张 8x8 图集糊在顶面上。这里转回
     * 五参数并给空的 {@link ModelData}，落到孤立方块那一格（四边四角都不接 = 序号 0）
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (quads.isEmpty()) {
            return quads;
        }

        Integer index = data.get(TOP_INDEX);
        // 孤立方块的那一格是序号 0：四边四角都不接，正是平地上单独一块台面的顶面
        int tile = index == null ? 0 : index;

        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            out.add(usesSheet(quad) ? remap(quad, tile) : quad);
        }
        return out;
    }

    private static boolean usesSheet(BakedQuad quad) {
        return quad.getSprite().contents().name().equals(SHEET);
    }

    private static BakedQuad remap(BakedQuad quad, int index) {
        int col = index % ConnectedTexture47.SHEET_SIZE;
        int row = index / ConnectedTexture47.SHEET_SIZE;
        var sprite = quad.getSprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * VERTEX_STRIDE;
            // 顶点 UV 是贴图在整个图集里的区间（u0 到 u1）。先换算成贴图内 0 到 16，重映射到目标格，
            // 再换算回图集坐标
            float u = Float.intBitsToFloat(vertices[offset + U_OFFSET]);
            float v = Float.intBitsToFloat(vertices[offset + V_OFFSET]);
            float u16 = (u - u0) / (u1 - u0) * 16f;
            float v16 = (v - v0) / (v1 - v0) * 16f;
            float tileU = (u16 + col * 16f) / ConnectedTexture47.SHEET_SIZE;
            float tileV = (v16 + row * 16f) / ConnectedTexture47.SHEET_SIZE;
            vertices[offset + U_OFFSET] = Float.floatToRawIntBits(u0 + tileU / 16f * (u1 - u0));
            vertices[offset + V_OFFSET] = Float.floatToRawIntBits(v0 + tileV / 16f * (v1 - v0));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }
}
