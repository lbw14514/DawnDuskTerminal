package com.ysm.server;

import com.ysm.Ysm;
import com.ysm.network.ChronoLinePayload;
import com.ysm.portal.PortalBlock;
import com.ysm.portal.PortalTeleporter;
import com.ysm.portal.PortalTrigger;
import com.ysm.progression.BossProgressTracker;
import com.ysm.world.ChronoLineState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class YsmServerEvents {
    private static final double ITEM_SCAN_RADIUS = 12.0D;

    private YsmServerEvents() {}

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        ChronoLineState state = ChronoLineState.get(overworld);
        ChronoLineState.setServerLine(state.line());
        Ysm.LOGGER.info("YSM chrono line origin=({}, {}) normal=({}, {})",
            state.line().originX(), state.line().originZ(), state.line().normalX(), state.line().normalZ());

        Registry<Structure> structures = event.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE);
        StructurePlacements.validate(structures);
        StructureExclusions.validate(structures);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ChronoLinePayload(ChronoLineState.serverLine()));
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PortalTeleporter.tickCooldown(player);
        if (PortalTrigger.isChrono(player)) {
            if (player.tickCount % 5 == 0) {
                BlockPos pos = player.blockPosition();
                if (PortalTrigger.shouldTeleport(player, pos)) {
                    PortalTeleporter.teleport(player);
                }
            }
            return;
        }
        if (player.tickCount % 10 == 0) {
            scanFuelItems(player);
        }
    }

    private static void scanFuelItems(ServerPlayer player) {
        List<ItemEntity> items = player.level().getEntitiesOfClass(
            ItemEntity.class,
            player.getBoundingBox().inflate(ITEM_SCAN_RADIUS),
            item -> PortalBlock.isFuel(item.getItem()));
        for (ItemEntity item : items) {
            BlockPos pos = item.blockPosition();
            if (item.level().getBlockState(pos).is(Blocks.WATER)) {
                PortalBlock.tryIgnite(item.level(), pos, item);
            }
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.getKillCredit() instanceof Player killer)) {
            return;
        }
        BossProgressTracker.onBossKilled(dead, killer);
    }
}
