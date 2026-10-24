package com.dawnduskterminal.block;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModParticles;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 砷铅铁火焰：试验物品 052 炸出来的那种火
 *
 * <p>继承 {@link BaseFireBlock} 而不是原版 {@code FireBlock}，这件事本身就是一半的规格：
 * 蔓延、烧方块、按火烧掉可燃物全在 {@code FireBlock#tick} 里，{@code BaseFireBlock} 只留「火这个方块」
 * 该有的东西（形状、贴图位置、撞上去挨烧）。{@code canBurn} 再钉死成 {@code false}，
 * 于是它连给邻居画烟都不画
 *
 * <p><b>模型照原版火焰那一套</b>：五个朝向状态（{@link #NORTH} {@link #EAST} {@link #SOUTH}
 * {@link #WEST} {@link #UP}）+ {@link #AGE}，方块状态是一张与 {@code minecraft:fire} 同构的 multipart，
 * 地面十字、四面侧板、顶板各有自己那把模型；十张模型都继承原版
 * {@code template_fire_floor/side/side_alt/up/up_alt}，只把 {@code fire} 那个贴图变量换成本模组的
 * {@code carminite_fire_0/1} 两层。每面要不要画板子由邻居的实心面决定（{@link #connected}），
 * 所以火贴着墙、顶着天花板时会铺满整格，而不是只剩地上一个十字
 *
 * <p>不引燃实体：原版 {@code BaseFireBlock#entityInside} 先把 {@code remainingFireTicks} 加一、
 * 给到 8 秒着火，本类把这段整个换掉，只留下「接触就掉 2 点血」。掉血频率交给生物自己的受伤无敌时间，
 * 所以站在原地就是每 0.5 秒 2 点
 *
 * <p><b>熄灭照抄原版火焰</b>（{@code FireBlock#tick} 去掉蔓延那一段）：每 30~39 tick 一个计划刻，
 * 淋雨按 {@code 0.2 + age × 0.03} 的概率灭，{@code AGE} 每次长 0~1，没落在火源上又没有可燃邻居时
 * {@code age > 3} 就灭——也就是压在石头上的原版火焰的寿命。区别只有一条：本方块不往可燃邻居上蔓延
 *
 * <p>环境音不抄原版（{@code BaseFireBlock#animateTick} 会放 {@code block.fire.ambient}），改成时不时
 * 飘起三颗向上的红石粒子
 *
 * <p>伤害类型是本模组自己的 {@code carminite_fire}，只进 {@code #minecraft:no_knockback}，
 * <b>不进 {@code #minecraft:is_fire}</b>：进了那条就会被「火焰免疫」挡下来（烈焰人、炽足兽那一类），
 * 而砷铅铁火焰要能烧穿火焰免疫；不产生击退
 */
public class CarminiteFireBlock extends BaseFireBlock {
    /** 老化计数，借原版火焰那个同名的 0..15 */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;

    /** 四个侧面与顶面：哪一面贴着实心块，就画哪一面的板子 */
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;

    /** 方向到状态属性的映射，与 {@code FireBlock} 一样把 {@code DOWN} 剔掉 */
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() != Direction.DOWN)
        .collect(Util.toMap());

    private static final VoxelShape UP_AABB = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape WEST_AABB = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_AABB = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);

    /** 接触伤害：每吃一次 2 点 */
    private static final float DAMAGE = 2.0F;

    /** 原版火焰的熄灭判断门槛：没落在火源上、age 超过这个数就灭 */
    private static final int EXTINGUISH_AGE = 3;

    /** 老化计划刻的基准间隔，与原版 {@code FireBlock#getFireTickDelay} 一致 */
    private static final int TICK_DELAY_BASE = 30;
    private static final int TICK_DELAY_JITTER = 10;

    /** 每次 {@code animateTick} 撒几颗余烬，与先前提到的「原版火焰每次三颗烟」同量 */
    private static final int EMBER_PARTICLES = 3;

    private final Map<BlockState, VoxelShape> shapesCache;

    public CarminiteFireBlock(Properties properties) {
        super(properties, DAMAGE);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(AGE, 0)
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(UP, false));
        // 形状只看五个朝向，AGE 不参与，所以按 AGE=0 的那一批预先算好，与 FireBlock 的做法一致
        this.shapesCache = ImmutableMap.copyOf(this.stateDefinition.getPossibleStates().stream()
            .filter(state -> state.getValue(AGE) == 0)
            .collect(Collectors.toMap(Function.identity(), CarminiteFireBlock::calculateShape)));
    }

    /**
     * 能不能在某一格新放一团火：那一格是空的，底下一格有朝上的实心面
     *
     * <p>爆炸补火与射弹落地都用这一条，{@code level.isEmptyBlock} 就是「空旷」那个判断。
     * 已经摆在那里的火本身不空，所以「站不站得住」是另一回事，见 {@link #canSurvive}
     */
    public static boolean canSurviveAt(LevelReader level, BlockPos pos) {
        return level.isEmptyBlock(pos) && hasSupport(level, pos);
    }

    /** 底下那一格有没有朝上的实心面：新放与已经放着的火都只看这一条 */
    private static boolean hasSupport(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** 某一面要不要画板子：那个方向的邻居有一面朝着本格的实心面 */
    private static boolean connected(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos neighbour = pos.relative(direction);
        return level.getBlockState(neighbour).isFaceSturdy(level, neighbour, direction.getOpposite());
    }

    /**
     * 由五个朝向拼出状态：{@code FireBlock#getStateForPlacement} 的同一件事，
     * 只是「能不能着火」换成了「有没有实心面」
     */
    private BlockState sideState(LevelReader level, BlockPos pos, int age) {
        BlockState state = this.defaultBlockState().setValue(AGE, age);
        for (Direction direction : PROPERTY_BY_DIRECTION.keySet()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connected(level, pos, direction));
        }
        return state;
    }

    private static VoxelShape calculateShape(BlockState state) {
        VoxelShape shape = Shapes.empty();
        if (state.getValue(UP)) {
            shape = UP_AABB;
        }
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, NORTH_AABB);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, SOUTH_AABB);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, EAST_AABB);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, WEST_AABB);
        }
        return shape.isEmpty() ? DOWN_AABB : shape;
    }

    @Override
    protected MapCodec<? extends BaseFireBlock> codec() {
        return simpleCodec(CarminiteFireBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, NORTH, EAST, SOUTH, WEST, UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapesCache.get(state.setValue(AGE, 0));
    }

    /** 不烧方块：{@code BaseFireBlock} 只用它决定要不要给邻居画烟，这里连烟都不要 */
    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    /** 接触就掉 2 点，不点火、不加火烧时间 */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            return;
        }
        entity.hurt(level.damageSources().source(ModDamageTypes.CARMINITE_FIRE), DAMAGE);
    }

    /**
     * 摆下来：站不住就撤掉，站得住就把五面朝向算一遍，再排第一个计划刻
     *
     * <p>朝向要在这里补：本方块只有代码生成（爆炸、碎片落地），不走 {@code getStateForPlacement}，
     * 直接 {@code setBlockAndUpdate} 摆下来的默认态五个朝向位还是 {@code false}，得算一遍才画得出侧板与顶板
     *
     * <p>不调 {@code super.onPlace}：{@code BaseFireBlock#onPlace} 会在主世界与下界试着用这一格
     * 点出一个下界门（{@code PortalShape.findEmptyPortalShape}），砷铅铁火焰不该有这种副作用
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        if (!hasSupport(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        BlockState sided = sideState(level, pos, state.getValue(AGE));
        if (sided != state) {
            // 再进来一次时 sided == state，这一支就断在这里，不会递归
            level.setBlock(pos, sided, Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, getFireTickDelay(level.getRandom()));
    }

    /**
     * 熄灭照抄原版火焰去掉蔓延那一段：每个计划刻先把下一个排上，再判支撑、判淋雨、判年龄
     *
     * <p>本方块没有可燃邻居（{@link #canBurn} 恒为 {@code false}），所以原版那条
     * 「不在火源上、周围又没有可燃块」的分支永远成立：压在实心面上的时候 {@code AGE} 超过
     * {@value #EXTINGUISH_AGE} 就灭，撑不住的地方当场灭
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, getFireTickDelay(random));
        if (!state.canSurvive(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        BlockPos below = pos.below();
        boolean fireSource = level.getBlockState(below).isFireSource(level, below, Direction.UP);
        int age = state.getValue(AGE);
        if (!fireSource && level.isRaining() && isNearRain(level, pos)
            && random.nextFloat() < 0.2F + (float) age * 0.03F) {
            level.removeBlock(pos, false);
            return;
        }
        int nextAge = Math.min(15, age + random.nextInt(3) / 2);
        if (nextAge != age) {
            state = state.setValue(AGE, nextAge);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        if (!fireSource
            && (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || age > EXTINGUISH_AGE)) {
            level.removeBlock(pos, false);
        }
    }

    private static int getFireTickDelay(RandomSource random) {
        return TICK_DELAY_BASE + random.nextInt(TICK_DELAY_JITTER);
    }

    /** 与原版 {@code FireBlock#isNearRain} 一样，看这一格与四周有没有在下雨 */
    private static boolean isNearRain(ServerLevel level, BlockPos pos) {
        return level.isRainingAt(pos)
            || level.isRainingAt(pos.west())
            || level.isRainingAt(pos.east())
            || level.isRainingAt(pos.north())
            || level.isRainingAt(pos.south());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // 这里不能再要求「那一格是空的」：这个判定是给已经摆好的火用的
        return hasSupport(level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                     BlockPos currentPos, BlockPos facingPos) {
        return state.canSurvive(level, currentPos)
            ? sideState(level, currentPos, state.getValue(AGE))
            : Blocks.AIR.defaultBlockState();
    }

    /** 落下来那一下按周围的情况摆，五面各自判一次 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return sideState(context.getLevel(), context.getClickedPos(), 0);
    }

    /**
     * 每次被点到就撒三颗砷铅铁余烬（{@code particle_carminite_ember}）
     *
     * <p>为什么是「每次三颗」而不是带概率：客户端的 {@code animateTick} 不是每 tick 都轮到这个方块，
     * {@code Minecraft#tick} 每 tick 只在玩家周围随机点 667×2 个位置（{@code ClientLevel#animateTick}），
     * 单个方块平均一秒才被点到两三次。再叠一层概率就几乎看不见了——原版火焰也是靠「每次三颗烟」撑起来的
     *
     * <p>位置撒在方块内偏下的地方，竖直速度按原版红石粒子那一档给（约 0.1），横向只留一点抖动；
     * 之后的上升加速度与横向阻尼在 {@code client/CarminiteEmberParticle} 里
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < EMBER_PARTICLES; i++) {
            level.addParticle(ModParticles.CARMINITE_EMBER.get(),
                pos.getX() + 0.15D + random.nextDouble() * 0.7D,
                pos.getY() + 0.1D + random.nextDouble() * 0.5D,
                pos.getZ() + 0.15D + random.nextDouble() * 0.7D,
                (random.nextDouble() - 0.5D) * 0.02D,
                // 竖直速度与原版红石粒子那一档（原版构造会把总分速度归一到 0.4 上下、再抬 0.1），
                // 之前按 0.01~0.03 给，飘得几乎看不出往上
                0.2D + random.nextDouble() * 0.05D,
                (random.nextDouble() - 0.5D) * 0.02D);
        }
    }
}
