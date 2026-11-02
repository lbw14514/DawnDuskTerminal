package com.dawnduskterminal.item;

import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 「苍穹罐」是唯一允许舀起晨昏线维度两种特殊流体（portal_fluid 与 enthu_fluid）的容器
 *
 * 分别产出 {@code portal_bucket} 与 {@code enthu_fluid_bucket}，其它流体一律不动
 */
public class CanocanItem extends Item {
    public CanocanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.mayInteract(player, pos)) {
            return InteractionResultHolder.fail(stack);
        }
        BlockState blockState = level.getBlockState(pos);
        if (!(blockState.getBlock() instanceof BucketPickup bucketPickup)) {
            return InteractionResultHolder.pass(stack);
        }
        var fluidState = level.getFluidState(pos);
        if (!fluidState.isSource()
                || (fluidState.getType() != ModFluids.PORTAL_FLUID.get()
                        && fluidState.getType() != ModScorchingTwilight.SOURCE.get())) {
            return InteractionResultHolder.pass(stack);
        }
        ItemStack filled = bucketPickup.pickupBlock(player, level, pos, blockState);
        if (filled.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        bucketPickup.getPickupSound(blockState).ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return InteractionResultHolder.sidedSuccess(
                ItemUtils.createFilledResult(stack, player, filled), level.isClientSide());
    }
}
