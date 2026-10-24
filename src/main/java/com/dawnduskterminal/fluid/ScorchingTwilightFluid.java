package com.dawnduskterminal.fluid;

import com.dawnduskterminal.registry.ModScorchingTwilight;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 「热情的暮光」(enthu_fluid) 流体。复用 minecraft:lava 的*属性*，但有意砍掉一切岩浆式*行为*：
 *
 * <ul>
 *   <li><b>不点燃生物</b>：{@code ScorchingTwilightBlock.entityInside} 不再调 {@code lavaHurt()}。
 *       （原版只会点燃 {@code NeoForgeMod.LAVA_TYPE} 里的实体，而本流体有自己的 {@link FluidType}，
 *       所以没有第二条燃烧路径）</li>
 *   <li><b>不引燃方块</b>：原版蔓延火焰的 {@code LavaFluid.randomTick} 没有被覆写，因此走基类
 *       {@code Fluid.randomTick} 的空实现。随机 tick 仍然开着（{@link #isRandomlyTicking()}），因为流
 *       体方块要靠它跑 {@code FluidReactions}</li>
 *   <li><b>不放岩浆粒子</b>：没有 {@code ParticleTypes.LAVA} / {@code LAVA_POP} / {@code LAVA_AMBIENT}
 *       动画，没有 {@code DRIPPING_LAVA} 滴落粒子，也没有 {@code LevelEvent.LAVA_FIZZ} 的方块销毁效果</li>
 * </ul>
 */
public abstract class ScorchingTwilightFluid extends FlowingFluid {
    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    @Override
    public FluidType getFluidType() {
        return ModScorchingTwilight.TYPE.value();
    }

    @Override
    public Fluid getFlowing() {
        return ModScorchingTwilight.FLOWING.get();
    }

    @Override
    public Fluid getSource() {
        return ModScorchingTwilight.SOURCE.get();
    }

    @Override
    public Item getBucket() {
        return ModScorchingTwilight.BUCKET.get();
    }

    @Override
    @Nullable
    public ParticleOptions getDripParticle() {
        return null;
    }

    /**
     * 在玩家附近播放岩浆的环境音，但不放岩浆粒子。这是 {@code LavaFluid#animateTick} 的音频那一半，本流
     * 体有意只留下它（见类注释）
     */
    @Override
    public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir() && !level.getBlockState(above).isSolidRender(level, above)) {
            if (random.nextInt(100) == 0) {
                double x = (double) pos.getX() + random.nextDouble();
                double y = (double) pos.getY() + 1.0;
                double z = (double) pos.getZ() + random.nextDouble();
                level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
            if (random.nextInt(200) == 0) {
                level.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(),
                    SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        // 有意留空。原版岩浆在这里触发 LevelEvent.LAVA_FIZZ (1501)，客户端会把它变成岩浆熄灭音加烟雾
        // 粒子，两者都是本流体不能产生的岩浆效果。FlowingFluid 把本方法声明为抽象，所以不能直接去掉
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 4 : 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModScorchingTwilight.BLOCK.get().defaultBlockState()
            .setValue(LiquidBlock.LEVEL, Integer.valueOf(getLegacyLevel(state)));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == ModScorchingTwilight.SOURCE.get() || fluid == ModScorchingTwilight.FLOWING.get();
    }

    @Override
    public int getDropOff(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid,
                                     Direction direction) {
        return state.getHeight(level, pos) >= MIN_LEVEL_CUTOFF && fluid.is(FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return level.dimensionType().ultraWarm() ? 10 : 30;
    }

    @Override
    public int getSpreadDelay(Level level, BlockPos pos, FluidState oldState, FluidState newState) {
        int delay = this.getTickDelay(level);
        if (!oldState.isEmpty() && !newState.isEmpty()
            && !oldState.getValue(FALLING) && !newState.getValue(FALLING)
            && newState.getHeight(level, pos) > oldState.getHeight(level, pos)
            && level.getRandom().nextInt(4) != 0) {
            delay *= 4;
        }
        return delay;
    }

    /**
     * 原版 {@code FlowingFluid} 把这个方法声明成抽象，必须实现。NeoForge 的源生成路径查的是
     * {@code FluidType.canConvertToSource}，两处都保持 false，不能再出现流体侧读岩浆规则 类型侧写 false 的矛盾
     */
    @Override
    protected boolean canConvertToSource(Level level) {
        return false;
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    public static class Source extends ScorchingTwilightFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends ScorchingTwilightFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
