package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.E115RegeneratorBlockEntity;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import twilightforest.init.TFItems;
import twilightforest.init.TFBlocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 试验物品 115 再生机：顶上摆一块撒过红石粉的完整试验物品 115，侧面来红石信号时，
 * 按 115 自己补片的速度（随机、3/4096 每 tick）往自己那一格里产试验物品 115
 *
 * <p>两个条件同时成立才掷骰子：
 * <ol>
 *   <li><b>侧面有红石信号</b>：只看四个水平方向，见 {@link #isSidePowered}。顶面不算——
 *       顶上那块 115 自己就是信号源（{@code getSignal} 满片 15 级），它往下发的信号拿来当条件的话，
 *       这个条件就永远成立了
 *   <li><b>顶上是一块完整的、撒过红石粉的 115</b>：{@code omnomnom=0} 且 {@code regenerate=true}，
 *       见 {@link #hasSprinkledFullSource}。只认暮色森林那件 {@code twilightforest:experiment_115}，
 *       本模组的 114 顶上去不算
 * </ol>
 *
 * <p>顶上那块 115 <b>不会被消耗</b>，也不需要它处在「吃到一半、正在补片」的状态：满片 + 撒粉就是引子，
 * 掷中的那一下产<b>新的</b> 115 物品并放一声顶块的放置音效，概率与算法见
 * {@link E115RegeneratorBlockEntity} 的类注释
 *
 * <p>干着活的时候在顶上那一格里飘余烬（{@code particle_carminite_ember}），落点随机、位置见
 * {@link #animateTick}
 *
 * <p>玩家拿着试验物品 115 点过来不吞交互：{@link #useItemOn} 把这一下让给物品自己，顶面照常摆到方块上面
 * （{@code SKIP_DEFAULT_BLOCK_INTERACTION} 只是拦住「摆放失败还顺势开界面」那一条）；
 * 空手点才开界面，见 {@link #useWithoutItem}
 *
 * <p>方块本身没有状态：干不干活全在方块实体里。形状是普通整块，三个贴图（顶、侧、底）走
 * {@code cube_bottom_top} 模型
 *
 * <p>容器那一面：只有一格产物格，漏斗从底面抽，其余五个面既不进也不出（见
 * {@link E115RegeneratorBlockEntity#getSlotsForFace}）；比较器按填充度输出，写法与 051 相同
 *
 * <p>掉自身走 {@code data/dawnduskterminal/loot_table/blocks/e_115_regenerator.json}，
 * 格子里的东西由 {@link #onRemove} 撒出来
 */
public class E115RegeneratorBlock extends BaseEntityBlock {
    /** 115 的「咬了几口」状态名，0 是满的 8 片 */
    public static final String SOURCE_BITES_PROPERTY = "omnomnom";

    /** 115 的「撒过红石粉」状态名 */
    public static final String SOURCE_REGENERATE_PROPERTY = "regenerate";

    /** 侧面四个方向。顶面故意不在里面，理由见类注释 */
    private static final Direction[] SIDES = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** 引子方块，第一次判定时按 ID 取一次就缓存下来 */
    @Nullable
    private static Block cachedSourceBlock;

    /** 引子物品，同上 */
    @Nullable
    private static Item cachedSourceItem;

    public E115RegeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(E115RegeneratorBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new E115RegeneratorBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 只有服务端刻：骰子与产出都在服务端算 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.E_115_REGENERATOR.get(),
            E115RegeneratorBlockEntity::serverTick);
    }

    /**
     * 拿着试验物品 115 点过来时把这一下留给物品自己
     *
     * <p>原版的顺序是「物品的 {@code useOn} 先跑、摆成功就结束」，所以顶面摆放本来就走不到这里；
     * 走到这里只剩「摆不下」那一种情形，那时返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION} 而不是默认的
     * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}，免得玩家本来想往上摆一层、却顺手开了界面。
     * 别的物品照默认走，空手点照常开界面
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(sourceItem())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 空手右键开界面，写法与 051 相同：坐标由方块实体的 {@code writeClientSideData} 带给客户端 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof E115RegeneratorBlockEntity entity) {
            player.openMenu(entity);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 干着活的时候在顶上那一格里飘余烬，落点随机
     *
     * <p>每次 {@code animateTick} 撒一颗、不叠概率：客户端的 {@code animateTick} 本来就不是每 tick 都轮到
     * 这个方块（{@code ClientLevel} 每 tick 只在玩家周围随机点若干位置），单个方块平均一秒才被点到两三次，
     * 再乘一层概率就几乎看不见了
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!isSidePowered(level, pos) || !hasSprinkledFullSource(level, pos)) {
            return;
        }
        BlockPos above = pos.above();
        level.addParticle(ModParticles.CARMINITE_EMBER.get(),
            above.getX() + random.nextDouble(),
            above.getY() + random.nextDouble(),
            above.getZ() + random.nextDouble(),
            (random.nextDouble() - 0.5D) * 0.02D,
            0.2D + random.nextDouble() * 0.05D,
            (random.nextDouble() - 0.5D) * 0.02D);
    }

    /** 敲掉时把格子里攒下的 115 撒出来，方块本体掉自己走战利品表 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof E115RegeneratorBlockEntity entity) {
                Containers.dropContents(level, pos, entity);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    /**
     * 四个水平方向里有没有一个给着红石信号
     *
     * <p>逐面问的是「那一格朝我这里发多少」，所以红石粉、红石块、对着我的中继器与比较器、
     * 贴在我侧面的拉杆都算，而顶上那块 115 往下发的 15 级<b>不算</b>：
     * {@code Level#hasNeighborSignal} 会把顶面也算进去，这里不能用它
     */
    public static boolean isSidePowered(Level level, BlockPos pos) {
        for (Direction side : SIDES) {
            if (level.getSignal(pos.relative(side), side.getOpposite()) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 顶上那块是不是「完整的、撒过红石粉的试验物品 115」
     *
     * <p>状态名只能按字符串找（{@code omnomnom} 与 {@code regenerate}）：那是方块状态属性名，
     * 没有可引用的编译期常量。方块对得上但少了这两个属性就当不是
     */
    public static boolean hasSprinkledFullSource(Level level, BlockPos pos) {
        Block source = sourceBlock();
        if (source == Blocks.AIR) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        if (!above.is(source)) {
            return false;
        }
        IntegerProperty bites = null;
        BooleanProperty regenerate = null;
        for (Property<?> property : above.getProperties()) {
            if (property instanceof IntegerProperty integer && integer.getName().equals(SOURCE_BITES_PROPERTY)) {
                bites = integer;
            } else if (property instanceof BooleanProperty bool
                && bool.getName().equals(SOURCE_REGENERATE_PROPERTY)) {
                regenerate = bool;
            }
        }
        return bites != null && regenerate != null
            && above.getValue(bites) == 0
            && above.getValue(regenerate);
    }

    /**
     * 顶上那块 115 的放置音效：115 是羊毛音效，所以这就是 {@code block.wool.place}
     *
     * <p>取的是那一格方块自己的音效表（{@code BlockState#getSoundType()}），不是写死成羊毛：
     * 暮色森林哪天换了音效，这里跟着换。那句无参 {@code getSoundType} 被 NeoForge 标了废弃，
     * 可它后面那层 {@code BlockBehaviour#getSoundType} 是 protected 的，外部只能走这一句，照原版蛋糕
     * 与 114 那边用 {@code isSolid} 的办法一样标掉
     */
    @SuppressWarnings("deprecation") // 上面那段注释说明为什么只能走这条废弃入口
    public static SoundEvent sourcePlaceSound(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).getSoundType().getPlaceSound();
    }

    /** 暮色森林的试验物品 115 方块 */
    public static Block sourceBlock() {
        return TFBlocks.EXPERIMENT_115.get();
    }

    /** 暮色森林的试验物品 115 物品 */
    public static Item sourceItem() {
        return TFItems.EXPERIMENT_115.get();
    }

    /** 产出的那一份：一个暮色森林的试验物品 115 */
    public static ItemStack newSourceStack() {
        return new ItemStack(sourceItem());
    }
}
