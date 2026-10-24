package com.dawnduskterminal.block;

import com.dawnduskterminal.item.Experiment114Item;
import com.dawnduskterminal.registry.ModItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 试验物品 114 摆下来的样子：整块照抄暮色森林的试验物品 115 方块（{@code twilightforest:experiment_115}）
 *
 * <p>同一套状态（{@code omnomnom} 0~7 与 {@code regenerate}）、同一套碰撞形状、同一套比较器与红石输出
 * （{@link #getAnalogOutputSignal} 与 {@link #getSignal}）。模型与侧、底、切口三张贴图直接用 115 的
 * （{@code twilightforest:block/experiment115/*}），顶面换成本模组自己的
 * {@code dawnduskterminal:block/experiment114_top}
 *
 * <p>与 115 的不同之处：
 * <ol>
 *   <li>摆下来就是满的 8 片：115 的默认态是 {@code omnomnom=7}（只剩 1 片），这里的默认态是 0
 *   <li>拿 114 点缺口是<b>一次补满</b>，115 一次只补一片，见 {@link #useItemOn}
 *   <li>潜行拿东西点一下收走只对全满的方块生效，吃到一半的收不走，见 {@link #useItemOn}
 *   <li>吃掉一片时额外随机放一声恶魂受伤，见 {@link #useWithoutItem}
 *   <li>撒过红石粉的那版（{@code regenerate=true}）不再自己补片，改成收到一次红石信号时随机放一声恶魂受伤，
 *       见 {@link #neighborChanged}。所以本方块没有 {@code randomTicks()}：115 的随机刻只用于给 {@code regenerate}
 *       那版补片，我们这版不补片，随机刻没有事可做
 * </ol>
 *
 * <p>没有战利品表，敲下来什么都不掉，和 115 一致
 */
public class Experiment114Block extends Block {
    /** 咬了几口：0 是满的 8 片，7 是只剩 1 片 */
    public static final IntegerProperty BITES_TAKEN = IntegerProperty.create("omnomnom", 0, 7);

    /** 撒过红石粉的那版。115 用它标记「会自己补片」，我们用它标记「会叫」 */
    public static final BooleanProperty REGENERATE = BooleanProperty.create("regenerate");

    /** 每片的营养，与 115 相同 */
    public static final int SLICE_NUTRITION = 4;

    /** 每片的饱和度系数，与 115 相同 */
    public static final float SLICE_SATURATION = 0.3F;

    /**
     * 一次红石信号会引来好几轮邻居更新，用一次方块刻把同一波压成一声
     *
     * <p>红石粉靠方块刻去重，不再多一个 {@code powered} 状态，状态集与 115 保持一样
     */
    private static final int SIGNAL_SOUND_DELAY = 4;

    private static final VoxelShape QUARTER_SHAPE = box(1.0D, 0.0D, 1.0D, 8.0D, 8.0D, 8.0D);
    private static final VoxelShape HALF_SHAPE = box(1.0D, 0.0D, 1.0D, 8.0D, 8.0D, 15.0D);
    private static final VoxelShape THREE_QUARTER_SHAPE = Shapes.join(
        HALF_SHAPE, box(8.0D, 0.0D, 8.0D, 15.0D, 8.0D, 15.0D), BooleanOp.OR);
    private static final VoxelShape FULL_SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D);

    public Experiment114Block(Properties properties) {
        super(properties);
        // 与 115 的第一处差别：摆下来就是满的 8 片（115 的默认态是 7）。BooleanProperty 的 any() 先取 true，
        // 所以 regenerate 必须显式写一遍
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(BITES_TAKEN, 0)
            .setValue(REGENERATE, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BITES_TAKEN)) {
            case 2, 3 -> THREE_QUARTER_SHAPE;
            case 4, 5 -> HALF_SHAPE;
            case 6, 7 -> QUARTER_SHAPE;
            default -> FULL_SHAPE;
        };
    }

    /**
     * 拿 114 补片、拿红石粉换成会叫的那版、潜行收走
     *
     * <p>骨架照 115，两处按本模组的规格改：拿 114 点缺口是**一次补满**（115 一次只补一片）；潜行收走只对
     * 全满的方块生效，吃到一半的收不走。补片与撒粉用的物品都是我们自己的 114 与红石粉
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        int bitesTaken = state.getValue(BITES_TAKEN);
        if (!player.isSecondaryUseActive()) {
            if (stack.is(ModItems.EXPERIMENT_114.get())) {
                if (bitesTaken == 0) {
                    return ItemInteractionResult.FAIL;
                }
                // 一次补满，不是补一片
                level.setBlockAndUpdate(pos, state.setValue(BITES_TAKEN, 0));
                level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.consume(1, player);
                if (player instanceof ServerPlayer serverPlayer) {
                    CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!state.getValue(REGENERATE) && bitesTaken == 0 && stack.is(Items.REDSTONE)) {
                level.setBlockAndUpdate(pos, state.setValue(REGENERATE, true));
                level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.consume(1, player);
                if (player instanceof ServerPlayer) {
                    player.awardStat(Stats.ITEM_USED.get(Items.REDSTONE));
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        } else if (!state.getValue(REGENERATE) && bitesTaken == 0) {
            // 只有全满的收得走，吃到一半的收不走
            level.removeBlock(pos, false);
            player.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
            if (!player.isCreative()) {
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.EXPERIMENT_114.get()));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 空手右键吃一片，逐行照 115，只有一处不同：吃完那一下额外随机放一声恶魂受伤
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.canEat(false)) {
            return InteractionResult.PASS;
        }
        player.awardStat(Stats.EAT_CAKE_SLICE);
        player.getFoodData().eat(SLICE_NUTRITION, SLICE_SATURATION);
        level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
            0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        // 与 115 的第二处差别
        if (!level.isClientSide) {
            Experiment114Item.playGhastHurt(level, pos);
        }
        int bitesTaken = state.getValue(BITES_TAKEN);
        if (bitesTaken < 7) {
            level.setBlock(pos, state.setValue(BITES_TAKEN, bitesTaken + 1), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer,
                new ItemStack(ModItems.EXPERIMENT_114.get(), 8 - bitesTaken));
            player.awardStat(Stats.ITEM_USED.get(ModItems.EXPERIMENT_114.get()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 与 115 的第三处差别：115 在这里给 {@code regenerate} 那版补片，我们那版不补片，改成收到一次红石信号
     * 随机放一声恶魂受伤
     *
     * <p>只有 {@code regenerate} 版会响，普通 114 收到信号没反应
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block fromBlock, BlockPos fromPos,
                                   boolean isMoving) {
        super.neighborChanged(state, level, pos, fromBlock, fromPos, isMoving);
        if (level.isClientSide || !state.getValue(REGENERATE) || !level.hasNeighborSignal(pos)) {
            return;
        }
        if (level.getBlockTicks().hasScheduledTick(pos, this)) {
            return;
        }
        // 排进去的方块刻只当去重标记用，不打算被处理；声音就在这里立刻放
        level.scheduleTick(pos, this, SIGNAL_SOUND_DELAY);
        Experiment114Item.playGhastHurt(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation") // isSolid 是这个判定的原版入口，NeoForge 给标了废弃，原版蛋糕也还在用
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                     BlockPos currentPos, BlockPos facingPos) {
        return facing == Direction.DOWN && !state.canSurvive(level, currentPos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BITES_TAKEN, REGENERATE);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return 8 - state.getValue(BITES_TAKEN) + (state.getValue(REGENERATE) ? 7 : 0);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(REGENERATE);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(REGENERATE) ? 15 - state.getValue(BITES_TAKEN) * 2 : 0;
    }
}
