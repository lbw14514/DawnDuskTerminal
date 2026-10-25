package com.dawnduskterminal.item;

import com.dawnduskterminal.entity.StringEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 「弦」的刷怪蛋：只肯放在传送门流体边上
 *
 * <p>发放路径有三条，漏一条就等于没拦，所以三条都在这里盖住：
 * <ul>
 *   <li>{@link #useOn} —— 对着方块点。落点按原版规则算：点击格碰撞形状为空就是它自己，
 *       否则朝点击面偏移一格（实体其实落在那个空格里，判定看的就是这个空格）</li>
 *   <li>{@link #use} —— 对着流体源格点。原版只认 {@code LiquidBlock}，这里再要求它必须是传送门流体，
 *       于是拿蛋点水也放不出来</li>
 *   <li>{@link #createDispenseBehavior} —— 发射器。默认行为在 {@link DeferredSpawnEggItem} 里是私有的，
 *       照着它重写一份，只在落点合格时发射并消耗</li>
 * </ul>
 *
 * <p>判定统一走 {@link StringEntity#canSpawnAt}：落点那一格是传送门流体，或者它正下方是
 */
public final class StringSpawnEggItem extends DeferredSpawnEggItem {
    public StringSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor,
                              int highlightColor, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!StringEntity.canSpawnAt(level, spawnPos(context))) {
            // FAIL 而不是 PASS：这一下明确不算用出去，蛋不消耗、也不往下传给别人
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK
            && !StringEntity.canSpawnAt(level, hit.getBlockPos())) {
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Nullable
    @Override
    protected DispenseItemBehavior createDispenseBehavior() {
        return (source, stack) -> {
            Direction face = source.state().getValue(DispenserBlock.FACING);
            BlockPos target = source.pos().relative(face);
            if (!StringEntity.canSpawnAt(source.level(), target)) {
                // 不发也不消耗：发射器里那一格还留着蛋，玩家可以搬去池子边再射
                return stack;
            }
            spawnFromDispenser(source, stack, target, face);
            return stack;
        };
    }

    /** 与 {@link DeferredSpawnEggItem} 里那份默认行为一致，只是落点先过一遍判定 */
    private static void spawnFromDispenser(BlockSource source, ItemStack stack, BlockPos target,
                                           Direction face) {
        EntityType<?> type = ((SpawnEggItem) stack.getItem()).getType(stack);
        if (type.spawn(source.level(), stack, null, target, MobSpawnType.DISPENSER,
            face != Direction.UP, false) == null) {
            return;
        }
        stack.shrink(1);
        source.level().gameEvent(GameEvent.ENTITY_PLACE, source.pos(),
            GameEvent.Context.of(source.state()));
    }

    /** 原版 {@link SpawnEggItem#useOn} 算落点的规则 */
    private static BlockPos spawnPos(UseOnContext context) {
        BlockPos clicked = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(clicked);
        if (state.getCollisionShape(context.getLevel(), clicked).isEmpty()) {
            return clicked;
        }
        return clicked.relative(context.getClickedFace());
    }
}
