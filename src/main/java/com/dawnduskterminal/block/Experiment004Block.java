package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.Experiment004BlockEntity;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 004 摆下来的那一块：照熔炉工作的机器
 *
 * <p>方块本身直接继承原版熔炉的抽象层 {@link AbstractFurnaceBlock}，于是「正面朝向玩家」的摆放
 * （{@code getStateForPlacement}）、活塞旋转与镜像、比较器按容器填充度输出信号、空手右键开界面
 * 这几条都走原版，不用另写；状态也就是原版那两个 {@code FACING} 与 {@code LIT}
 *
 * <p>与熔炉的区别只有两条，都落在方块实体那边（见 {@link Experiment004BlockEntity}）：
 * 烧制食物快一倍、产出的东西不产生任何粒子
 *
 * <p>粒子：原版熔炉的烟与火写在 {@code FurnaceBlock#animateTick} 里，抽象层没有；本类也只补了那一声
 * 火响（{@code block.furnace.fire_crackle}，约一成概率），{@code addParticle} 一句都没有
 *
 * <p>敲掉时把三格里的东西撒出来、没结算的经验按方块中心撒出来，掉自身走
 * {@code data/dawnduskterminal/loot_table/blocks/experiment_004.json}
 */
public class Experiment004Block extends AbstractFurnaceBlock {
    public static final MapCodec<Experiment004Block> CODEC = simpleCodec(Experiment004Block::new);

    public Experiment004Block(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends AbstractFurnaceBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Experiment004BlockEntity(pos, state);
    }

    /** 只有服务端刻：进度、燃料、经验都在服务端算，客户端拿 {@code ContainerData} 显示 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.EXPERIMENT_004.get(),
            Experiment004BlockEntity::serverTick);
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof Experiment004BlockEntity entity) {
            player.openMenu(entity);
            player.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextDouble() < 0.1D) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Experiment004BlockEntity entity) {
                Containers.dropContents(level, pos, entity);
                if (level instanceof ServerLevel serverLevel) {
                    entity.popExperienceIntoWorld(serverLevel, Vec3.atCenterOf(pos));
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
