package com.dawnduskterminal.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 试验物品 012 摆下来的样子：一格最多三只杯子，横着错开摆
 *
 * <p>模型 {@code models/block/experiment_012_1.json} 是一只 4x6x4 的杯子（{@code from [6,0,6] to [10,6,10]}），
 * 另有一张悬在 y=5 的朝上面，那是杯子的内底
 *
 * <p><b>多只杯子是错开摆的，不是往上一只压一只</b>：摆位照原版海泡菜那三份模型
 * （{@code sea_pickle} / {@code two_sea_pickles} / {@code three_sea_pickles}）的 from 角，
 * 第二只落在 (3,3) 与 (8,8)，第三只是 (6,9)、(2,2)、(8,4)，全部坐在格子底面上，
 * 杯口一样高。三只 4x4 的脚印在 16x16 里互不重叠，挤在一起像一撮杯子，而不是一根叠起来的塔
 *
 * <p>加杯子照原版 {@link net.minecraft.world.level.block.SeaPickleBlock} 那条路：
 * {@link #getStateForPlacement} 认「点在自己身上」，把手上的 {@code cups} 加一；
 * {@link #canBeReplaced} 放开「同一件物品点在自己身上」这条，于是拿着 012 点自己就是在加一只杯子
 *
 * <p>杯子的判定与摆法挂在 {@link BushBlock} 上：底下的方块没了自己消失，站不到实心面顶上也放不下来。
 * 这一条对半砖同样成立——{@link #mayPlaceOn} 看的是碰撞箱的朝上面，不是「是不是满方块」
 */
public class Experiment012Block extends BushBlock {
    public static final MapCodec<Experiment012Block> CODEC = simpleCodec(Experiment012Block::new);

    /** 一格最多放下的杯子数 */
    public static final int MAX_CUPS = 3;

    /** 杯子的个数，1~3。名字沿用模型的 {@code cups} 字段，方块状态里就是 {@code cups=1} 这种写法 */
    public static final IntegerProperty CUPS = IntegerProperty.create("cups", 1, MAX_CUPS);

    /** 杯子是 4x4 高 6 像素的一只，摆位只差 from 角 */
    private static final double CUP_SIZE = 4.0D;

    private static final double CUP_HEIGHT = 6.0D;

    /**
     * 每只杯子的脚印，顺序与模型里 elements 的顺序无关，只要集合一致
     *
     * <p>三份摆位抄的是原版 {@code sea_pickle}、{@code two_sea_pickles}、{@code three_sea_pickles}：
     * 一杯在正中，两杯走对角，三杯是一撮。三组都没有重叠
     */
    private static final int[][] SPOTS = {{6, 6}, {3, 3, 8, 8}, {6, 9, 2, 2, 8, 4}};

    /** 三份碰撞箱：每只杯子一个盒子，多只取并集 */
    private static final VoxelShape[] SHAPES = buildShapes();

    public Experiment012Block(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CUPS, 1));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    /**
     * 摆在自己身上就再加一只：{@code cups} 加一，封顶 {@value #MAX_CUPS}
     *
     * <p>其余情况照 {@link BushBlock} 的默认值，也就是这只杯子摆成 1 只
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(this)) {
            return state.setValue(CUPS, Math.min(MAX_CUPS, state.getValue(CUPS) + 1));
        }
        return super.getStateForPlacement(context).setValue(CUPS, 1);
    }

    /**
     * 手上拿着杯子、点到的也是杯子、而且还没满三只时，这一格算「可以替换」
     *
     * <p>不加这一条，{@link net.minecraft.world.item.BlockItem} 会在「位置上已经有方块」这一步就放弃，
     * 加杯子的路根本走不到 {@link #getStateForPlacement}。潜行时不算，留给「对着杯子放别的方块」
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive()
                        && context.getItemInHand().is(this.asItem())
                        && state.getValue(CUPS) < MAX_CUPS
                || super.canBeReplaced(state, context);
    }

    /** 底下要有面朝上的实心面：满方块、半砖、台阶的顶面都算，竖着的栅栏、告示牌不算 */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getCollisionShape(level, pos).getFaceShape(Direction.UP).isEmpty()
                || state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return this.mayPlaceOn(level.getBlockState(below), level, below);
    }

    /** 碰撞箱：这只杯子摆在哪，盒子就在哪，多只取并集，没有一只高过 6 像素 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(CUPS) - 1];
    }

    /** 三份碰撞箱：从 {@link #SPOTS} 里每两个数取一个杯子脚印 */
    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[MAX_CUPS];
        for (int cups = 1; cups <= MAX_CUPS; cups++) {
            int[] spots = SPOTS[cups - 1];
            VoxelShape shape = Shapes.empty();
            for (int i = 0; i < spots.length; i += 2) {
                double x = spots[i];
                double z = spots[i + 1];
                shape = Shapes.or(shape, box(x, 0.0D, z, x + CUP_SIZE, CUP_HEIGHT, z + CUP_SIZE));
            }
            shapes[cups - 1] = shape;
        }
        return shapes;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CUPS);
    }
}
