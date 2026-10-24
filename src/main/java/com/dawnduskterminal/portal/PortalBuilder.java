package com.dawnduskterminal.portal;

import com.dawnduskterminal.registry.ModAttachments;
import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
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
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : found.get().waterBlocks()) {
            if (!PortalShape.isPortalFluid(level.getBlockState(pos))) {
                targets.add(pos);
            }
        }
        // 先把整池水清空再灌传送门流体。不能边清边灌：传送门流体一碰到水就被 FluidReactions 变成
        // fragile_canopy（走 LiquidBlock#onPlace 立即生效），先灌好的那些邻居还是水，会被反应吃掉
        for (BlockPos pos : targets) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        // 池面不平时高列的水还在门平面之上，反应会扫正上方，灌门前必须把这整列水也清掉
        // 每列最多清 16 格，与 PortalShape.find 找地板的上限一致
        for (BlockPos pos : targets) {
            BlockPos above = pos.above();
            int guard = 0;
            while (level.getBlockState(above).is(Blocks.WATER) && guard++ < 16) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
                above = above.above();
            }
        }
        BlockState portal = ModBlocks.PORTAL_FLUID.get().defaultBlockState();
        for (BlockPos pos : targets) {
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
