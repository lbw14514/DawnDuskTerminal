package com.dawnduskterminal.server;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.network.ChronoLinePayload;
import com.dawnduskterminal.portal.PortalBlock;
import com.dawnduskterminal.portal.PortalTeleporter;
import com.dawnduskterminal.portal.PortalTrigger;
import com.dawnduskterminal.progression.BossProgressTracker;
import com.dawnduskterminal.structure.StructureExclusions;
import com.dawnduskterminal.structure.StructurePlacements;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class DdtServerEvents {
    private static final double ITEM_SCAN_RADIUS = 12.0D;

    private DdtServerEvents() {}

    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal overworld is not available, the chrono line stays at its fallback value");
            return;
        }
        ChronoLineState state = ChronoLineState.get(overworld);
        ChronoLineState.setServerLine(state.line());
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal chrono line origin=({}, {}) normal=({}, {})",
            state.line().originX(), state.line().originZ(), state.line().normalX(), state.line().normalZ());

        Registry<Structure> structures = event.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE);
        StructurePlacements.validate(structures);
        StructureExclusions.validate(structures);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensureLine(player.server);
            PacketDistributor.sendToPlayer(player, new ChronoLinePayload(ChronoLineState.serverLine()));
        }
    }

    private static void ensureLine(MinecraftServer server) {
        if (ChronoLineState.hasServerLine()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        ChronoLineState.setServerLine(ChronoLineState.get(overworld).line());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PortalTeleporter.tickCooldown(player);
        if (PortalTrigger.isChrono(player)) {
            if (ChronoLineState.hasServerLine() && player.tickCount % 20 == 0) {
                double param = ChronoLineState.serverLine().param(player.getX(), player.getZ());
                com.dawnduskterminal.advancement.ModCriteria.lineParam().trigger(player, param);
            }
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
