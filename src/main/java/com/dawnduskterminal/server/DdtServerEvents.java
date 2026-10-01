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
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
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

        ServerLevel chrono = event.getServer().getLevel(com.dawnduskterminal.registry.ModDimensions.CHRONO);
        if (chrono != null) {
            profileChrono(chrono);
        }
    }

    private static void profileChrono(ServerLevel chrono) {
        int[][] spots = {{0, 0}, {2000, 2000}, {60000, 60000}, {-6000, 6000}};
        for (int[] spot : spots) {
            int surface = 0;
            java.util.Map<Integer, Integer> hist = new java.util.TreeMap<>();
            int samples = 0;
            for (int x = spot[0]; x < spot[0] + 64; x += 8) {
                for (int z = spot[1]; z < spot[1] + 64; z += 8) {
                    chrono.getChunk(x >> 4, z >> 4);
                    int h = chrono.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    hist.merge(h / 16 * 16, 1, Integer::sum);
                    surface += h;
                    samples++;
                }
            }
            DawnDuskTerminal.LOGGER.info("DDT_SPOT {} {} avgSurface={} hist={}", spot[0], spot[1],
                samples == 0 ? 0 : surface / samples, hist);
        }
        StringBuilder tc = new StringBuilder("DDT_TOP ");
        java.util.Map<String, Integer> topKinds = new java.util.HashMap<>();
        for (int x = -6000; x < -6000 + 256; x += 8) {
            for (int z = 6000; z < 6000 + 256; z += 8) {
                chrono.getChunk(x >> 4, z >> 4);
                int h = chrono.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                var st = chrono.getBlockState(new BlockPos(x, h - 1, z));
                topKinds.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(st.getBlock()).toString(), 1, Integer::sum);
            }
        }
        tc.append(topKinds);
        DawnDuskTerminal.LOGGER.info(tc.toString());

        StringBuilder sb = new StringBuilder("DDT_PROFILE ");
        int[] ys = {10, 30, 40, 48, 62, 80, 110, 128, 192, 196, 200, 208, 230, 260};
        for (int y : ys) {
            int solid = 0;
            int total = 0;
            java.util.Map<String, Integer> kinds = new java.util.HashMap<>();
            for (int x = -6000; x < -6000 + 64; x += 4) {
                for (int z = 6000; z < 6000 + 64; z += 4) {
                    chrono.getChunk(x >> 4, z >> 4);
                    total++;
                    var st = chrono.getBlockState(new BlockPos(x, y, z));
                    if (!st.isAir()) {
                        solid++;
                        kinds.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(st.getBlock()).toString(), 1, Integer::sum);
                    }
                }
            }
            sb.append(String.format("%d=%d%%%s ", y, total == 0 ? 0 : solid * 100 / total, kinds));
        }
        DawnDuskTerminal.LOGGER.info(sb.toString());
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
            item -> PortalBlock.isCatalyst(item.getItem()));
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

    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!SpawnRules.allow(level, event.getPos(), event.getEntityType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }
}
