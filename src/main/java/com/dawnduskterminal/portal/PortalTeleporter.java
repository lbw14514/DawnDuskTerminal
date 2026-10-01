package com.dawnduskterminal.portal;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.registry.ModAttachments;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public final class PortalTeleporter {
    private static final int MAX_CHUNK_LOADS = 64;
    private static final int OPEN_SKY_HEIGHT = 32;
    public static final int LANDING_SEARCH_RADIUS = 320;
    public static final int COLUMN_SCAN_DEPTH = 48;
    public static final int LANDING_MIN_Y = 0;
    public static final int LANDING_MAX_Y = 128;

    private PortalTeleporter() {}

    public static boolean cooldownActive(Player player) {
        return player.getData(ModAttachments.PORTAL_STATE).cooldown() > 0;
    }

    public static void tickCooldown(Player player) {
        PortalState state = player.getData(ModAttachments.PORTAL_STATE);
        if (state.cooldown() > 0) {
            player.setData(ModAttachments.PORTAL_STATE, state.tick());
        }
    }

    @Nullable
    private static ServerLevel resolve(MinecraftServer server, String dimensionId) {
        if (dimensionId.isEmpty()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    public static void teleport(ServerPlayer player) {
        int cooldown = DdtConfig.portalCooldownTicks();
        PortalState state = player.getData(ModAttachments.PORTAL_STATE);
        ServerLevel current = player.serverLevel();

        if (current.dimension().equals(ModDimensions.CHRONO)) {
            ServerLevel target = resolve(player.server, state.returnDim());
            if (target == null) {
                target = player.server.overworld();
            }
            double originX = state.hasReturn() ? state.x() : target.getSharedSpawnPos().getX() + 0.5D;
            double originZ = state.hasReturn() ? state.z() : target.getSharedSpawnPos().getZ() + 0.5D;
            int originY = state.hasReturn() ? Mth.floor(state.y()) : target.getSharedSpawnPos().getY();
            Vec3 destination = findSafeLanding(target, originX, originZ, originY);
            player.teleportTo(target, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
            player.setData(ModAttachments.PORTAL_STATE,
                player.getData(ModAttachments.PORTAL_STATE).withCooldown(cooldown));
            playSound(player, ModSounds.PORTAL_EXIT);
            return;
        }

        ServerLevel chrono = player.server.getLevel(ModDimensions.CHRONO);
        if (chrono == null) {
            return;
        }
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("message.dawnduskterminal.searching"), false);
        Vec3 landing = findSafeLanding(chrono, player.getX(), player.getZ(), Mth.floor(player.getY()));
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal teleport from {} {} {} to chrono {} {} {}",
            (int) player.getX(), (int) player.getY(), (int) player.getZ(),
            (int) landing.x, (int) landing.y, (int) landing.z);
        PortalState remembered = state.withReturn(
            current.dimension().location().toString(), player.getX(), player.getY(), player.getZ());
        player.teleportTo(chrono, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        buildArrivalPortal(chrono, landing);
        player.setData(ModAttachments.PORTAL_STATE, remembered.withCooldown(cooldown));
        playSound(player, ModSounds.PORTAL_ENTER);
    }

    public static Vec3 findSafeLanding(ServerLevel level, double originX, double originZ, int originY) {
        int baseX = Mth.floor(originX);
        int baseZ = Mth.floor(originZ);
        Set<Long> loaded = new HashSet<>();
        loaded.add(ChunkPos.asLong(baseX >> 4, baseZ >> 4));
        level.getChunk(baseX >> 4, baseZ >> 4);
        Vec3 here = scanColumn(level, baseX, baseZ, originY);
        if (here != null) {
            return here;
        }
        for (int ring = 16; ring <= LANDING_SEARCH_RADIUS; ring += 16) {
            if (loaded.size() > MAX_CHUNK_LOADS) {
                break;
            }
            int step = 16;
            for (int dx = -ring; dx <= ring; dx += step) {
                for (int dz = -ring; dz <= ring; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < ring) {
                        continue;
                    }
                    Vec3 found = probe(level, loaded, baseX + dx, baseZ + dz, originY);
                    if (found != null) {
                        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal landing found at {} {} after {} chunks",
                            (int) found.x, (int) found.y, loaded.size());
                        return found;
                    }
                }
            }
        }
        DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal landing search found nothing, building a platform");
        return buildFallbackPlatform(level, baseX, originY, baseZ);
    }

    @Nullable
    private static Vec3 probe(ServerLevel level, Set<Long> loaded, int x, int z, int originY) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (loaded.add(ChunkPos.asLong(chunkX, chunkZ))) {
            level.getChunk(chunkX, chunkZ);
        }
        return scanColumn(level, x, z, originY);
    }

    @Nullable
    private static Vec3 scanColumn(ServerLevel level, int x, int z, int originY) {
        int minY = level.getMinBuildHeight() + 1;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surface <= minY) {
            return null;
        }
        if (surface < LANDING_MIN_Y || surface > LANDING_MAX_Y) {
            return null;
        }
        BlockPos pos = new BlockPos(x, surface, z);
        BlockPos ground = new BlockPos(x, surface - 1, z);
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
            return null;
        }
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return null;
        }
        if (!level.getBlockState(pos).getFluidState().isEmpty()) {
            return null;
        }
        return new Vec3(x + 0.5D, surface, z + 0.5D);
    }

    private static boolean isSafeStanding(ServerLevel level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (!below.isFaceSturdy(level, belowPos, Direction.UP)) {
            return false;
        }
        if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CACTUS) || below.is(Blocks.FIRE)
            || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SWEET_BERRY_BUSH)
            || below.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        BlockPos headPos = pos.above();
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(headPos);
        if (!feet.getCollisionShape(level, pos).isEmpty() || !head.getCollisionShape(level, headPos).isEmpty()) {
            return false;
        }
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    private static Vec3 buildFallbackPlatform(ServerLevel level, int x, int y, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int landed = Mth.clamp(surface + 1, Math.max(level.getMinBuildHeight() + 4, LANDING_MIN_Y), LANDING_MAX_Y);
        if (landed < LANDING_MIN_Y + 4) {
            landed = Mth.clamp(y, Math.max(level.getMinBuildHeight() + 4, LANDING_MIN_Y), LANDING_MAX_Y);
        }
        BlockPos center = new BlockPos(x, landed, z);
        int radius = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                level.setBlockAndUpdate(center.offset(dx, -1, dz), ModBlocks.SKY_SOIL.get().defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, -2, dz), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, -3, dz), Blocks.STONE.defaultBlockState());
            }
        }
        for (int dy = 0; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        return new Vec3(x + 0.5D, landed, z + 0.5D);
    }

    private static void playSound(ServerPlayer player, net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound.get(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static boolean buildArrivalPortal(ServerLevel level, Vec3 landing) {
        int baseX = Mth.floor(landing.x) + 2;
        int baseZ = Mth.floor(landing.z);
        int y = Mth.floor(landing.y);
        BlockPos probe = new BlockPos(baseX, y - 1, baseZ);
        if (!level.getBlockState(probe).isFaceSturdy(level, probe, Direction.UP)) {
            return false;
        }
        BlockState portal = ModBlocks.PORTAL_FLUID.get().defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState turf = ModBlocks.SKY_SOIL.get().defaultBlockState();
        BlockState soil = com.dawnduskterminal.registry.ModTerrainBlocks.EMBER_SOIL.get().defaultBlockState();
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos top = new BlockPos(baseX + dx, y - 1, baseZ + dz);
                level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(top.below(), bedrock);
                level.setBlockAndUpdate(top.below(2), bedrock);
                level.setBlockAndUpdate(top, portal);
                level.setBlockAndUpdate(top.above(), Blocks.AIR.defaultBlockState());
            }
        }
        int r = 4;
        for (int dx = -r; dx <= 2 + r; dx++) {
            for (int dz = -r; dz <= 2 + r; dz++) {
                if (dx >= 0 && dx < 2 && dz >= 0 && dz < 2) {
                    continue;
                }
                BlockPos pos = new BlockPos(baseX + dx, y - 1, baseZ + dz);
                BlockState at = level.getBlockState(pos);
                if (at.isAir()) {
                    continue;
                }
                if (!level.getBlockState(pos.above()).isAir()) {
                    continue;
                }
                if (at.is(com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get())) {
                    level.setBlockAndUpdate(pos, turf);
                    BlockPos below = pos.below();
                    if (level.getBlockState(below).is(com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get())) {
                        level.setBlockAndUpdate(below, soil);
                    }
                }
            }
        }
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal arrival portal built at {} {} {}", baseX, y, baseZ);
        return true;
    }

    public static boolean isChronoLevel(Level level) {
        return level.dimension().equals(ModDimensions.CHRONO);
    }
}
