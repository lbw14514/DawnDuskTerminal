package com.ysm.portal;

import com.ysm.config.YsmConfig;
import com.ysm.progression.BossProgressTracker;
import com.ysm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PortalTrigger {
    private PortalTrigger() {}

    public static boolean isPortalBlock(BlockState state) {
        if (state.is(ModBlocks.PORTAL_FLUID.get())) {
            return true;
        }
        return YsmConfig.waterIsPortal() && state.is(Blocks.WATER);
    }

    public static boolean isChrono(Entity entity) {
        return entity.level().dimension().equals(com.ysm.registry.ModDimensions.CHRONO);
    }

    public static boolean isActivePortal(Entity entity, BlockPos pos) {
        BlockState state = entity.level().getBlockState(pos);
        if (isChrono(entity)) {
            return isPortalBlock(state);
        }
        return state.is(ModBlocks.PORTAL_FLUID.get());
    }

    public static boolean shouldTeleport(Entity entity, BlockPos pos) {
        if (!isActivePortal(entity, pos)) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return true;
        }
        if (PortalTeleporter.cooldownActive(player)) {
            return false;
        }
        if (player.isSwimming() || player.isVisuallySwimming()) {
            return false;
        }
        if (!player.isShiftKeyDown()) {
            return false;
        }
        return player.getUseItem().isEmpty();
    }
}
