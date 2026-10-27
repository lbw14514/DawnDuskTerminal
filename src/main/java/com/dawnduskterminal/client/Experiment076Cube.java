package com.dawnduskterminal.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/**
 * 试验物品 076 那个六面随机的立方体：候选贴图、抽取规则、六面几何，世界里的方块（
 * {@link Experiment076Renderer}）与物品栏里那个图标（{@link Experiment076ItemRenderer}）共用这一份
 *
 * <p><b>做法照暮色森林的反应堆碎片</b>（{@code twilightforest:reactor_debris}）：那一块是方块实体渲染器
 * 隔一阵换一批贴图，贴图按 ID 从方块图集里现取、画在实体层上。照抄过来最要紧的是三条：
 *
 * <ul>
 *   <li><b>不能用 {@code RenderType.translucent()}</b>。那是区块层，它的顶点着色器里带
 *       {@code ChunkOffset} 这个 uniform（区块段偏移），方块实体里画会被上一次区块渲染残留的偏移整块挪走。
 *       实体层没有这些，{@code entityTranslucentCull} 自带背面剔除与深度写入</li>
 *   <li>每帧现画，不进区块网格。区块网格是烘好缓存的，方块模型不可能每刻变样</li>
 *   <li><b>六个面一律按满亮画</b>（{@link LightTexture#FULL_BRIGHT}），不吃世界的光图。
 *       方块本身也是 15 级光源（见 {@code ModBlocks#EXPERIMENT_076}），两头对齐：它是一块到处都亮的异常方块，
 *       摆在洞里、夜里也是一个亮块，不会因为环境光变成一片黑</li>
 * </ul>
 *
 * <p>每 tick 换一次（{@code Level#getGameTime} 就是 tick 数），六个面各抽各的。种子只由「时间 + 坐标」算，
 * 所以同一刻所有客户端看到的是同一个样子；方块实体里一个字都不用存，也不用发网络包
 */
final class Experiment076Cube {
    /**
     * 六个面画在这一层：实体半透明层，带背面剔除、写深度，与暮色森林反应堆碎片用的是同一层
     *
     * <p>{@code RenderType.entityTranslucentCull} 内部按贴图 ID 记忆化，所以这个字段是稳定的单例，
     * 不会每帧新建一个 RenderType
     */
    static final RenderType LAYER = RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);

    /** 这六个面不吃环境光，一律按满亮画。方块自己也是 15 级光源，两边一致 */
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    /** 四张候选，按「地狱岩 黑曜石 基岩 传送门」的顺序排，全是原版方块图集里的贴图 */
    private static final ResourceLocation[] CHOICES = {
        ResourceLocation.withDefaultNamespace("block/netherrack"),
        ResourceLocation.withDefaultNamespace("block/obsidian"),
        ResourceLocation.withDefaultNamespace("block/bedrock"),
        ResourceLocation.withDefaultNamespace("block/nether_portal"),
    };

    /** 单位立方体六个面的四个角，按「从外面看过去 左下 右下 右上 左上」排 */
    private static final float[][][] CORNERS = buildCorners();

    private Experiment076Cube() {}

    /** 六个面全画，物品栏与手持用这个 */
    static void drawCube(VertexConsumer buffer, Matrix4f matrix, long seed, int overlay) {
        for (Direction face : Direction.values()) {
            drawFace(buffer, matrix, face, seed, overlay);
        }
    }

    /** 画一个面：这张面上抽到哪张图由 {@code seed} 与该面的序号定 */
    static void drawFace(VertexConsumer buffer, Matrix4f matrix, Direction face, long seed, int overlay) {
        int pick = (int) (mix(seed + face.ordinal()) & 3L);
        TextureAtlasSprite sprite = sprite(CHOICES[pick]);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float[][] corners = CORNERS[face.ordinal()];
        for (int corner = 0; corner < 4; corner++) {
            float[] xyz = corners[corner];
            // 左下与右下在贴图底边，右上与左上是顶边
            float u = corner == 1 || corner == 2 ? u1 : u0;
            float v = corner <= 1 ? v1 : v0;
            // 属性顺序照暮色森林反应堆碎片那一份：位置 贴图 光图 叠加层 法线 颜色
            buffer.addVertex(matrix, xyz[0], xyz[1], xyz[2])
                .setUv(u, v)
                .setLight(LIGHT)
                .setOverlay(overlay)
                .setNormal(face.getStepX(), face.getStepY(), face.getStepZ())
                .setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** 图集里的贴图。每帧现取，换资源包之后不用清任何缓存 */
    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /** splitmix64 的收尾混合：把 tick 与坐标搅散，低两位拿来四选一 */
    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    /**
     * 六个面各自四个角的坐标
     *
     * <p>面朝外时屏幕的右与上由「视线方向叉上世界上方」定出来，不是随便排的：排错了贴图会上下颠倒
     * 或者左右镜像，而且 {@code entityTranslucentCull} 是按顶点绕序剔背面的，顺序反了这个面直接不画
     *
     * <p>逐个面：下=右+X 上+Z，上=右+X 上-Z，北=右-X 上+Y，南=右+X 上+Y，西=右+Z 上+Y，东=右-Z 上+Y
     */
    private static float[][][] buildCorners() {
        float l = 0.0F;
        float h = 1.0F;
        float[][][] corners = new float[6][4][3];
        corners[Direction.DOWN.ordinal()] = new float[][] {{l, l, l}, {h, l, l}, {h, l, h}, {l, l, h}};
        corners[Direction.UP.ordinal()] = new float[][] {{l, h, h}, {h, h, h}, {h, h, l}, {l, h, l}};
        corners[Direction.NORTH.ordinal()] = new float[][] {{h, l, l}, {l, l, l}, {l, h, l}, {h, h, l}};
        corners[Direction.SOUTH.ordinal()] = new float[][] {{l, l, h}, {h, l, h}, {h, h, h}, {l, h, h}};
        corners[Direction.WEST.ordinal()] = new float[][] {{l, l, l}, {l, l, h}, {l, h, h}, {l, h, l}};
        corners[Direction.EAST.ordinal()] = new float[][] {{h, l, h}, {h, l, l}, {h, h, l}, {h, h, h}};
        return corners;
    }
}
