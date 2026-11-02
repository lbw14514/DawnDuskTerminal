package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.Experiment051BlockEntity;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 试验物品 051：拆解台那台机器
 *
 * <p>方块属性逐条照抄暮色森林的 {@code twilightforest:uncrafting_table} 的注册参数：
 * {@code of().ignitedByLava().mapColor(MapColor.FIRE).sound(SoundType.WOOD).strength(2.5F)}，
 * 也一起进 {@code #minecraft:mineable/axe}。与原版熔炉一样只有一个布尔状态，这里叫 {@code active}：
 * 有燃料在烧时换成 {@code _active} 那张贴图，同时推进度
 *
 * <p>拆解台本体没有状态、也不是容器，所以比较器与漏斗都是本方块额外加的：比较器按容器填充度给信号
 * （见 {@code AbstractContainerMenu#getRedstoneSignalFromBlockEntity}），漏斗按顶面原料、侧面燃料、底面产物走
 */
public class Experiment051Block extends BaseEntityBlock {
    /** 有燃料在烧。与熔炉的 {@code lit} 同义，只是换了名字去对上 {@code _active} 贴图 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public Experiment051Block(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(Experiment051Block::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Experiment051BlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 只有服务端刻：配方与进度都在服务端算，客户端拿 {@code ContainerData} 显示 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModBlockEntities.EXPERIMENT_051.get(), Experiment051BlockEntity::serverTick);
    }

    /**
     * 空手右键开界面
     *
     * <p>拆解台原版是用 {@code getMenuProvider} 让玩家点开的，这里同样走 {@link BaseEntityBlock#getMenuProvider}，
     * 拿到的就是方块实体自己；坐标由它的 {@code writeClientSideData} 带给客户端
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof Experiment051BlockEntity entity) {
            player.openMenu(entity);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 敲掉时把 11 格里的东西撒出来
     *
     * <p>方块本体掉自己走的是 {@code loot_table/blocks/experiment_051.json}，这里只管容器内容物。
     * 写法与原版的熔炉、箱子一致：换了方块才撒，只是被换成别的状态不算
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Experiment051BlockEntity entity) {
                Containers.dropContents(level, pos, entity);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    /**
     * 烧着的时候放熔炉那一声火响，逐行照原版 {@code FurnaceBlock#animateTick} 的音效那一段
     *
     * <p>只抄音效，不抄它后面那对烟与火粒子：原版是从 {@code FACING} 那一面喷出去的，
     * 本方块四面对称、没有朝向状态，粒子没有「正脸」可喷
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        if (random.nextDouble() < 0.1D) {
            level.playLocalSound(
                    pos.getX() + 0.5D,
                    pos.getY(),
                    pos.getZ() + 0.5D,
                    SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F,
                    false);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * 比较器读容器填充度。{@code BlockBehaviour} 的默认实现固定返回 0，光声明
     * {@link #hasAnalogOutputSignal} 只能让比较器认为这个方块有输出，读数仍然是空的
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
