package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.CampCarminiteFireBlockEntity;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.world.CampCarminiteFireAura;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * 红篝火：一团砷铅铁火焰压在一堆红木头上，就是砷铅铁火焰那只火盆
 *
 * <p>形状、朝向、点着/熄灭、含水、烧烤这几件事照原版篝火那一套，但<b>不继承</b> {@code CampfireBlock}：
 * 那一类的 {@code codec()} 写死了 {@code CampfireBlock::new}，子类没法换成自己的（泛型不协变），
 * 方块编解码转一圈会变回原版篝火。这里自己实现一份，只留用得上的部分：{@link #LIT}、{@link #WATERLOGGED}、
 * {@link #FACING}，没有原版的 {@code SIGNAL_FIRE}（那一位只管蜂箱冒不冒信号烟）
 *
 * <p><b>它是一台篝火，能烤东西。</b>方块实体自带原版篝火那套数据（四格食材、进度、冷却、配方查询，见
 * {@link CampCarminiteFireBlockEntity}）：{@link #useItemOn} 照抄原版 {@code CampfireBlock#useItemOn}，
 * 点着时每 tick 跑 {@code cookTick}、熄灭时跑 {@code cooldownTick}，客户端的
 * {@code client/CampCarminiteFireRenderer} 把四格食材摆到火堆上，烤好了照原版吐在方块脚下。
 * 不直接用原版那个方块实体，是因为它的构造器把类型写死成 {@code BlockEntityType.CAMPFIRE}，
 * 那个类型不认本方块，一构造就抛 {@code Invalid block entity}
 *
 * <p>点燃与熄灭靠 NeoForge 那两条工具动作，不自己写 {@code useItemOn} 里的点火分支：
 * {@code FIRESTARTER_LIGHT} 的默认实现判的是 {@code CampfireBlock.canLight}，那条只认「在
 * {@code #minecraft:campfires} 标签里、有 {@code LIT} 与 {@code WATERLOGGED} 两个属性、没点着、没泡水」，
 * 本方块照样满足（标签见 {@code data/minecraft/tags/block/campfires.json}）；
 * {@code SHOVEL_DOUSE} 的默认实现判的是 {@code instanceof CampfireBlock}，本方块不是它的子类，
 * 所以在 {@link #getToolModifiedState} 里自己接一条
 *
 * <p><b>接触伤害与粒子同砷铅铁火焰</b>：踩上去每 0.5 秒掉 2 点血，伤害类型是本模组自己的
 * {@code carminite_fire}（不进 {@code #minecraft:is_fire}，火焰免疫的也照烧），不点火；
 * 粒子是 {@code particle_carminite_ember} 那三颗余烬，位置、速度都与 {@code CarminiteFireBlock} 同档，
 * 另外照原版篝火那样时不时响一声 {@code block.campfire.crackle}——它本来就是篝火
 *
 * <p>16 格内玩家的消耗度规则见 {@link CampCarminiteFireAura}：小增量取反，见底补一点饥饿值再把消耗度压回 4
 *
 * <p>挖掉掉自己，走 {@code data/dawnduskterminal/loot_table/blocks/campcarminitefire.json}，
 * 表里没有工具条件，空手也掉；火堆上还摆着的食材一起掉出来
 */
public class CampCarminiteFireBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<CampCarminiteFireBlock> CODEC = simpleCodec(CampCarminiteFireBlock::new);

    /** 与原版篝火同一个碰撞箱：底下 7 格高的那堆木头 */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

    /** 接触伤害：与 {@code CarminiteFireBlock} 同一条，每吃一次 2 点 */
    private static final float DAMAGE = 2.0F;

    /** 每次 {@code animateTick} 撒几颗余烬，与 {@code CarminiteFireBlock} 同量 */
    private static final int EMBER_PARTICLES = 3;

    /** 原版篝火的噼啪声：每次被点到有十分之一的概率响一声 */
    private static final int CRACKLE_CHANCE = 10;

    public CampCarminiteFireBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition
                .any()
                .setValue(LIT, true)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, WATERLOGGED, FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CampCarminiteFireBlockEntity(pos, state);
    }

    /**
     * 服务端：点着时烧烤、熄灭时冷却，与原版篝火同一套（{@code CampCarminiteFireBlockEntity} 那两个静态 tick）
     *
     * <p>客户端不要 ticker：原版那条客户端 ticker 只负责冒 {@code campfire_cosy_smoke} 那类烟，
     * 本方块要的是余烬，撒在 {@link #animateTick} 里；四格食材由 {@code CampfireRenderer} 画，与 ticker 无关
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return state.getValue(LIT)
                ? createTickerHelper(
                        type, ModBlockEntities.CAMP_CARMINITE_FIRE.get(), CampCarminiteFireBlockEntity::cookTick)
                : createTickerHelper(
                        type, ModBlockEntities.CAMP_CARMINITE_FIRE.get(), CampCarminiteFireBlockEntity::cooldownTick);
    }

    /**
     * 放食材：照抄原版 {@code CampfireBlock#useItemOn}
     *
     * <p>本方块的方块实体自带四格食材与「这一份能不能烤」的查询，所以这一支与原件逐行一致
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof CampCarminiteFireBlockEntity campfire) {
            ItemStack held = player.getItemInHand(hand);
            Optional<RecipeHolder<CampfireCookingRecipe>> recipe = campfire.getCookableRecipe(held);
            if (recipe.isPresent()) {
                if (!level.isClientSide
                        && campfire.placeFood(player, held, recipe.get().value().getCookingTime())) {
                    player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                    return ItemInteractionResult.SUCCESS;
                }
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction facing,
            BlockState facingState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, facing, facingState, level, pos, facingPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water =
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return this.defaultBlockState()
                .setValue(WATERLOGGED, water)
                .setValue(LIT, !water)
                .setValue(FACING, context.getHorizontalDirection());
    }

    /**
     * 泡水即灭：原版篝火那条的简化版，先熄火（放一声熄灭音）再把水灌进去
     *
     * <p>{@code SimpleWaterloggedBlock} 的默认实现只写 {@code WATERLOGGED}，不会熄火，
     * 也不会把火堆上还摆着的食材挪出来（食材跟着方块实体走，挖掉时才掉）
     */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (state.getValue(WATERLOGGED) || fluidState.getType() != Fluids.WATER) {
            return false;
        }
        if (state.getValue(LIT) && !level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.setBlock(pos, state.setValue(WATERLOGGED, true).setValue(LIT, false), Block.UPDATE_ALL);
        level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
        return true;
    }

    /**
     * 铲子灭火
     *
     * <p>NeoForge 那条 {@code SHOVEL_DOUSE} 的默认实现判的是 {@code instanceof CampfireBlock}，
     * 本方块不是它的子类，这里自己接。声音与方块更新由 {@code ShovelItem} 那边负责，这里只给新状态
     *
     * <p>其余的（打火石点燃、斧头刮皮之类）原样交回默认实现
     */
    @Override
    public BlockState getToolModifiedState(
            BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
        if (itemAbility == ItemAbilities.SHOVEL_DOUSE && state.getValue(LIT) && !state.getValue(WATERLOGGED)) {
            return state.setValue(LIT, false);
        }
        return super.getToolModifiedState(state, context, itemAbility, simulate);
    }

    /** 接触就掉 2 点，不点火：与 {@code CarminiteFireBlock#entityInside} 同一条，只在点着的时候 */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !state.getValue(LIT)) {
            return;
        }
        entity.hurt(level.damageSources().source(ModDamageTypes.CARMINITE_FIRE), DAMAGE);
    }

    /**
     * 点着时响一声噼啪、撒三颗砷铅铁余烬
     *
     * <p>噼啪声照原版篝火：本 tick 被点到时十分之一的概率放 {@code block.campfire.crackle}，
     * 音量 0.5~1.5、音高 0.6~1.3。粒子换成本模组那套——位置取方块中间那一圈（原版篝火的火舌就在正中），
     * 竖直速度沿用 {@code CarminiteFireBlock} 那一档（0.2~0.25），横向只留一点抖动；
     * 之后的上升加速度与横向阻尼在 {@code client/CarminiteEmberParticle} 里
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(CRACKLE_CHANCE) == 0) {
            level.playLocalSound(
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS,
                    0.5F + random.nextFloat(),
                    random.nextFloat() * 0.7F + 0.6F,
                    false);
        }
        for (int i = 0; i < EMBER_PARTICLES; i++) {
            level.addParticle(
                    ModParticles.CARMINITE_EMBER.get(),
                    pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                    pos.getY() + 0.35D + random.nextDouble() * 0.35D,
                    pos.getZ() + 0.3D + random.nextDouble() * 0.4D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    0.2D + random.nextDouble() * 0.05D,
                    (random.nextDouble() - 0.5D) * 0.02D);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            CampCarminiteFireAura.track(level, pos);
        }
    }

    /** 挖掉或推走：撤掉气场，火堆上还摆着的食材一起掉出来（与原版篝火同一条） */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            CampCarminiteFireAura.untrack(level, pos);
            if (level.getBlockEntity(pos) instanceof CampCarminiteFireBlockEntity campfire) {
                Containers.dropContents(level, pos, campfire.getItems());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(facing).rotate(facing));
    }
}
