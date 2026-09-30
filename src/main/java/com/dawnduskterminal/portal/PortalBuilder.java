package com.dawnduskterminal.portal;

import com.dawnduskterminal.registry.ModAttachments;
import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class PortalBuilder {
    private PortalBuilder() {}

    public static Optional<PortalShape> probe(Level level, BlockPos seed) {
        return PortalShape.find(level, seed);
    }

    public static boolean build(ServerLevel level, BlockPos seed) {
        Optional<PortalShape> found = PortalShape.find(level, seed);
        if (found.isEmpty()) {
            return false;
        }
        BlockState portal = ModBlocks.PORTAL_FLUID.get().defaultBlockState();
        for (BlockPos pos : found.get().waterBlocks()) {
            BlockState existing = level.getBlockState(pos);
            if (PortalShape.isPortalFluid(existing)) {
                continue;
            }
            level.setBlock(pos, portal, 3);
        }
        return true;
    }

    public static void rememberOrigin(net.minecraft.world.entity.player.Player player) {
        PortalState state = player.getData(ModAttachments.PORTAL_STATE);
        player.setData(ModAttachments.PORTAL_STATE, state.withReturn(
            player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ()));
    }
}
