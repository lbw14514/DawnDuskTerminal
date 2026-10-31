package com.dawnduskterminal.block;

import com.dawnduskterminal.entity.CarminiteShrapnelEntity;
import com.dawnduskterminal.registry.ModSounds;
import com.dawnduskterminal.world.CarminiteBlast;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 试验物品 052 摆下来的样子：照砷铅铁反应堆做的那颗方雷
 *
 * <p>收到<b>任意一个</b>红石信号就点火：状态换成 {@code active}（模型换 {@code experiment_052_glow}、
 * 发 15 级光），放一声 {@code experiment_052_ignite}，然后 {@value #FUSE_TICKS} tick（3 秒）后炸开
 *
 * <p>点着之后断电也停不下来：状态已经是 {@code active}，这里没有「信号没了就取消」的分支，
 * 与砷铅铁反应堆一样，点着以后只剩倒计时
 *
 * <p>爆炸本体在 {@link CarminiteBlast#large}：威力 8、破坏方块，粒子与音效都换成
 * {@code particle_carminite_effect} 与 {@code experiment_052_explode}，一颗原版爆炸粒子都没有；
 * 炸完再甩出 0~{@value #MAX_SHRAPNEL} 个 {@link CarminiteShrapnelEntity}
 *
 * <p>触发只认「从没点着变成点着」这一次：{@link #neighborChanged} 会连着来好几轮，
 * {@link #onPlace} 还会在摆下的那一刻再问一次（摆在一片已经通着电的位置上应当立刻点火）
 */
public class Experiment052Block extends Block {
    /** 点着的那一版：换模型、发 15 级光 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** 红石信号进来之后等多久炸：3 秒 */
    public static final int FUSE_TICKS = 60;

    /** 一次爆炸最多甩出几个射弹 */
    public static final int MAX_SHRAPNEL = 15;

    public Experiment052Block(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /**
     * 摆到一片已经通着电的位置上要立刻点火
     *
     * <p>只在这一格原本不是本方块时才问（{@code oldState} 判断）：点火那一下自己也会走 {@code onPlace}，
     * 不挡的话会重入
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || oldState.is(this)) {
            return;
        }
        tryIgnite(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block fromBlock, BlockPos fromPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, fromBlock, fromPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        tryIgnite(state, level, pos);
    }

    /**
     * 有信号就点火
     *
     * <p>{@code state} 取的是最新的那版（{@code BlockStateBase} 在调用前现取），所以「已经点着」
     * 在这一行就挡住了，同一个方块只会安排一次引信。信号从哪一面来都算，红石粉、红石块、
     * 贴面拉杆、中继器走的都是 {@link Level#hasNeighborSignal}
     */
    private void tryIgnite(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(ACTIVE) || !level.hasNeighborSignal(pos)) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(ACTIVE, true));
        level.playSound(null, pos, ModSounds.EXPERIMENT_052_IGNITE.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
        level.scheduleTick(pos, this, FUSE_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        detonate(level, pos);
    }

    /** 炸：先撤掉方块，再把 052 那一整份后果（{@link #detonateAt}）摊出去 */
    private void detonate(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        level.removeBlock(pos, false);
        detonateAt(level, x, y, z);
    }

    /**
     * 052 爆炸的全部后果，落在什么位置由调用方决定
     *
     * <p>抽出来是为了让持块变体（{@code GhastFruityEntity.HeldBlock#EXPERIMENT_052}）炸的和方块一模一样：
     * 主爆炸（{@link CarminiteBlast#large}，含粒子、音效、连锁与刷新群系）在前，
     * 再甩出 0~{@value #MAX_SHRAPNEL} 个 {@link CarminiteShrapnelEntity}
     *
     * <p>射弹排在爆炸之后：先炸掉方块，碎片才不会被自己的爆炸推歪
     */
    public static void detonateAt(ServerLevel level, double x, double y, double z) {
        CarminiteBlast.large(level, x, y, z);
        launchShrapnel(level, x, y, z);
    }

    /** 0~15 个射弹，初速方向与快慢都由实体自己摇 */
    private static void launchShrapnel(ServerLevel level, double x, double y, double z) {
        int count = Mth.nextInt(level.getRandom(), 0, MAX_SHRAPNEL);
        for (int i = 0; i < count; i++) {
            CarminiteShrapnelEntity.launch(level, x, y, z);
        }
    }
}
