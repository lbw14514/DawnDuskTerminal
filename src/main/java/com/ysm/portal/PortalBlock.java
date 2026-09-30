package com.ysm.portal;

import com.ysm.config.YsmConfig;
import com.ysm.progression.BossProgressTracker;
import com.ysm.registry.ModEffects;
import com.ysm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public class PortalBlock extends LiquidBlock {
    public PortalBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) {
            return;
        }
        if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
            if (YsmConfig.entityDebuff()) {
                living.addEffect(new MobEffectInstance(ModEffects.PORTAL_SICKNESS, 200, 0, false, true));
            }
            return;
        }
        if (entity instanceof Player player) {
            PortalTeleporter.tickCooldown(player);
            if (PortalTrigger.shouldTeleport(player, pos) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                PortalTeleporter.teleport(serverPlayer);
            }
            return;
        }
        if (entity instanceof ItemEntity item) {
            tryIgnite(level, pos, item);
        }
    }

    public static void tryIgnite(Level level, BlockPos pos, ItemEntity item) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack stack = item.getItem();
        if (!isFuel(stack)) {
            return;
        }
        Entity owner = item.getOwner();
        Player player = owner instanceof Player p ? p : null;
        if (player != null && !BossProgressTracker.unlocked(player)) {
            return;
        }
        if (!PortalBuilder.build(serverLevel, pos)) {
            return;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
        }
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(ModItems.HYDRA_TROPHY.get())
            || stack.is(ModItems.UR_GHAST_TROPHY.get())
            || stack.is(ModItems.SNOW_QUEEN_TROPHY.get());
    }
}
