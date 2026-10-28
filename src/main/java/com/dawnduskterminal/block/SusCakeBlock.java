package com.dawnduskterminal.block;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 可疑蛋糕摆下来的样子：暮色森林试验物品 115 方块的衍生物，交互逐条照 115
 *
 * <p><b>一块蛋糕是 8 片，物品就是一片</b>：摆下来是只剩 1 片的 {@code omnomnom=7}（115 的默认态也是 7），
 * 拿同一块蛋糕点一下缺口加一片、潜行点一下从缺口取回一片（只剩最后一片时整块取回），空手点吃一片。
 * 状态、切口形状、比较器公式（8 - {@code omnomnom}）全部照 115，模型是 115 的那八个原样复制过来的，
 * 只把顶、侧、底、切口四张贴图换成各自那份 {@code dawnduskterminal:block/sus_<口味>_cake_*}
 *
 * <p>与 115 的差别有两处：
 * <ol>
 *   <li>吃一片给 {@link Serving} 指定的量：默认 {@link #DEFAULT_SERVING} 是 6 点饥饿、4 点饱和度，
 *       血橙与红丝绒那一档是 {@link #EIGHT_SERVING} 的 8 点饥饿、8 点饱和度；115 是一片 4 点饥饿、2.4 点饱和度
 *   <li><b>不需要撒红石粉就会自己补片</b>：115 要先拿红石粉点成 {@code regenerate=true} 那版才在随机刻里补片，
 *       这里没有 {@code regenerate} 状态，吃到一半就被随机刻一片一片补回去，见 {@link #randomTick}
 * </ol>
 *
 * <p>补片速度就是原版的随机刻速度：每一个随机刻被抽中一次补一片，没有额外的骰子（115 那版也一样），
 * 默认 {@code randomTickSpeed=3} 下期望间隔 4096/3 ≈ 1365 tick（约 68 秒）
 *
 * <p>方块属性 {@code noLootTable}、{@code pushReaction(DESTROY)}、{@code sound(WOOL)}、{@code strength(0.5)}
 * 照搬 115；不同的一处是 {@code randomTicks()}，因为这里真的要靠随机刻补片。没有战利品表，
 * 敲下来什么都不掉（115 同样没有），要收回只能照 115 那样潜行一片一片点
 */
public class SusCakeBlock extends Block {
    /** 咬了几口：0 是满的 8 片，7 是只剩 1 片。名字与 115 一样 */
    public static final IntegerProperty BITES_TAKEN = IntegerProperty.create("omnomnom", 0, 7);

    /** 一片的饥饿点数与饱和度系数。115 是一片 4 与 0.3，这里按规格另给 */
    public record Serving(int nutrition, float saturationModifier) {
        public Serving {
            if (nutrition <= 0 || saturationModifier <= 0.0F) {
                throw new IllegalArgumentException("a serving must feed something, got "
                    + nutrition + " and " + saturationModifier);
            }
        }

        /** 这一份实际给的饱和度点数：{@code FoodData#eat} 存的是系数，点数 = 系数 × 饥饿 × 2 */
        public float saturation() {
            return nutrition * saturationModifier * 2.0F;
        }
    }

    /** 默认的一片：6 点饥饿、4 点饱和度（115 是 4 与 2.4） */
    public static final Serving DEFAULT_SERVING = new Serving(6, 1.0F / 3.0F);

    /** 8 点饥饿、8 点饱和度那一档：血橙（配方指定的值）与红丝绒（暂无配方，数值单给） */
    public static final Serving EIGHT_SERVING = new Serving(8, 0.5F);

    /** 满片时的片数 */
    public static final int FULL_SLICES = 8;

    /** 最后一片的状态值：{@code omnomnom} 的上界 */
    private static final int MAX_BITES = 7;

    /** 这一块的一片给多少，见 {@link #serving()} */
    private final Serving serving;

    private static final VoxelShape QUARTER_SHAPE = box(1.0D, 0.0D, 1.0D, 8.0D, 8.0D, 8.0D);
    private static final VoxelShape HALF_SHAPE = box(1.0D, 0.0D, 1.0D, 8.0D, 8.0D, 15.0D);
    private static final VoxelShape THREE_QUARTER_SHAPE = Shapes.join(
        HALF_SHAPE, box(8.0D, 0.0D, 8.0D, 15.0D, 8.0D, 15.0D), BooleanOp.OR);
    private static final VoxelShape FULL_SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D);

    public SusCakeBlock(Properties properties) {
        this(properties, DEFAULT_SERVING);
    }

    public SusCakeBlock(Properties properties, Serving serving) {
        super(properties);
        this.serving = serving;
        // 摆下来是只剩 1 片，与 115 的默认态一样：一块蛋糕要摆 8 次才满。没有 regenerate 之类的第二个状态
        this.registerDefaultState(this.stateDefinition.any().setValue(BITES_TAKEN, MAX_BITES));
    }

    /** 这一块的一片给多少：每种口味在 {@link com.dawnduskterminal.registry.ModBlocks} 里登记，默认 {@link #DEFAULT_SERVING} */
    public Serving serving() {
        return serving;
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
     * 拿同一块蛋糕点缺口加一片、潜行点一下取回一片，两条都照 115
     *
     * <p>补片与取片用的都是本方块自己的物品（{@link #asItem()}），每种口味只认自己那一种；满片时再点不给补，
     * 回 {@link ItemInteractionResult#FAIL}
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        int bitesTaken = state.getValue(BITES_TAKEN);
        if (!player.isSecondaryUseActive()) {
            if (stack.is(this.asItem())) {
                if (bitesTaken == 0) {
                    return ItemInteractionResult.FAIL;
                }
                // 一次只补一片，115 也是这么补的
                level.setBlockAndUpdate(pos, state.setValue(BITES_TAKEN, bitesTaken - 1));
                level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.consume(1, player);
                if (player instanceof ServerPlayer serverPlayer) {
                    CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        } else {
            // 潜行：从缺口取回一片，只剩 1 片时整块取走
            if (bitesTaken < MAX_BITES) {
                level.setBlockAndUpdate(pos, state.setValue(BITES_TAKEN, bitesTaken + 1));
            } else {
                level.removeBlock(pos, false);
            }
            player.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
            if (!player.isCreative()) {
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(this.asItem()));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 空手右键吃一片，逐行照 115：数量看这一块的 {@link #serving()} */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.canEat(false)) {
            return InteractionResult.PASS;
        }
        player.awardStat(Stats.EAT_CAKE_SLICE);
        player.getFoodData().eat(serving.nutrition(), serving.saturationModifier());
        level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
            0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        int bitesTaken = state.getValue(BITES_TAKEN);
        if (bitesTaken < MAX_BITES) {
            level.setBlock(pos, state.setValue(BITES_TAKEN, bitesTaken + 1), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, new ItemStack(this.asItem(), FULL_SLICES - bitesTaken));
            player.awardStat(Stats.ITEM_USED.get(this.asItem()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 与 115 的第二处差别：不用先撒红石粉，吃到一半就在随机刻里自己补一片
     *
     * <p>没有额外的骰子：随机刻每抽中这个方块一次就补一片，115 的 {@code regenerate} 那版也是这么写的
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int bitesTaken = state.getValue(BITES_TAKEN);
        if (bitesTaken == 0) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(BITES_TAKEN, bitesTaken - 1));
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
        builder.add(BITES_TAKEN);
    }

    /** 115 的公式去掉 {@code regenerate} 那半：8 - 片数 */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return FULL_SLICES - state.getValue(BITES_TAKEN);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }
}
