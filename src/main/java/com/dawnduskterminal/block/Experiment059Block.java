package com.dawnduskterminal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;

/**
 * 试验物品 059：铂催化网格，一片能摆在场地里的金属网
 *
 * <p>四条性质各自有出处：
 * <ul>
 *   <li>渲染照原版玻璃：{@link TransparentBlock} 继承来的 {@code skipRendering} 把同种方块之间的
 *       内部面剔掉，属性那边的 {@code noOcclusion} 与「不挡视线」那几条在
 *       {@link com.dawnduskterminal.registry.ModBlocks#EXPERIMENT_059} 里，模型那边是
 *       {@code render_type: minecraft:translucent}</li>
 *   <li>音效与挖掘照原版铁栅栏：{@code SoundType.METAL}、5.0 硬度、6.0 爆炸抗性、
 *       {@code requiresCorrectToolForDrops}，进 {@code #minecraft:mineable/pickaxe}，
 *       但不进 {@code #minecraft:needs_iron_tool}——木镐也挖得动，与铁栅栏一致</li>
 * </ul>
 *
 * <p>贴图 {@code experiment_059_loop.png} 是一张 48x48 的 3x3 图组。哪一格贴在哪一面上由
 * {@link #tileAt} 按<b>坐标</b>算，而且是<b>一个面算一次</b>：每个面只看自己平面上的两条轴，
 * 各自对 3 取模。于是横着铺一层屋顶、竖着砌一面墙，任何 3x3 的方块阵在它自己那个平面上
 * 都正好铺满九格，再往哪边都是同一个图案循环下去
 *
 * <p>规则放在方块这边而不是客户端那边，是因为服务端也要问同一个问题：{@code gametest} 跑在专用服务器上，
 * 客户端渲染模型 {@code client/model/LoopTileModel} 那类东西根本不在 classpath 里。
 * 渲染侧只负责把算出来的格子号换成 UV，见 {@code client/model/LoopTileModel}
 */
public class Experiment059Block extends TransparentBlock {
    /** 图组是 3x3 格，格子序号 = 列 + 行 * 3 */
    public static final int SHEET_SIZE = 3;

    public Experiment059Block(Properties properties) {
        super(properties);
    }

    /**
     * 这一面该贴图组里的哪一格，取值 {@code 0..8}
     *
     * <p>两条轴取的是<b>从方块外面看这一面时</b>的方向：列跟着屏幕向右、行跟着屏幕向下。
     * 六个面各自不同，不是把世界坐标按固定顺序塞进去就完事：
     * <ul>
     *   <li>顶面：右是 +X、下是 +Z（原版的顶面贴图以北为上）</li>
     *   <li>底面：右是 +X、下是 −Z（从下面看，南边在上，所以行要翻）</li>
     *   <li>南面：右是 +X、下是 −Y</li>
     *   <li>北面：右是 −X、下是 −Y</li>
     *   <li>东面：右是 −Z、下是 −Y</li>
     *   <li>西面：右是 +Z、下是 −Y</li>
     * </ul>
     *
     * <p>这一条就是「六个面的网线走向一致」的全部内容：图组里每一格的斜纹是有方向的，
     * 行或列取反，那一面就成了镜像，紧贴邻面时一眼看出接不上。所以只有顶面自然成立是不够的——
     * 六面各错反一次，摆一个 27 块的采样立方体就全暴露出来
     *
     * <p>用 {@code Math.floorMod} 而不是 {@code %}：负坐标在 {@code %} 下拿到负数，
     * 图集下标会跑到 0 以下，区块一边的半个世界就全糊成同一格
     */
    public static int tileAt(BlockPos pos, Direction face) {
        return switch (face) {
            case UP -> tile(pos.getX(), pos.getZ());
            case DOWN -> tile(pos.getX(), -pos.getZ());
            case SOUTH -> tile(pos.getX(), -pos.getY());
            case NORTH -> tile(-pos.getX(), -pos.getY());
            case EAST -> tile(-pos.getZ(), -pos.getY());
            case WEST -> tile(pos.getZ(), -pos.getY());
        };
    }

    private static int tile(int column, int row) {
        return Math.floorMod(column, SHEET_SIZE) + SHEET_SIZE * Math.floorMod(row, SHEET_SIZE);
    }
}
