package com.dawnduskterminal.portal;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class PortalTeleporter {
    public static final int LANDING_SEARCH_RADIUS = 16;
    public static final int COLUMN_SCAN_DEPTH = 48;

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
        Vec3 landing = findSafeLanding(chrono, player.getX(), player.getZ(), Mth.floor(player.getY()));
        PortalState remembered = state.withReturn(
            current.dimension().location().toString(), player.getX(), player.getY(), player.getZ());
        player.teleportTo(chrono, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        player.setData(ModAttachments.PORTAL_STATE, remembered.withCooldown(cooldown));
        playSound(player, ModSounds.PORTAL_ENTER);
    }

    public static Vec3 findSafeLanding(ServerLevel level, double originX, double originZ, int originY) {
        int baseX = Mth.floor(originX);
        int baseZ = Mth.floor(originZ);
        for (int radius = 0; radius <= LANDING_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int squared = dx * dx + dz * dz;
                    if (squared > radius * radius) {
                        continue;
                    }
                    if (radius > 0 && squared <= (radius - 1) * (radius - 1)) {
                        continue;
                    }
                    Vec3 found = scanColumn(level, baseX + dx, baseZ + dz, originY);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return buildFallbackPlatform(level, baseX, originY, baseZ);
    }

    @Nullable
    private static Vec3 scanColumn(ServerLevel level, int x, int z, int originY) {
        int minY = level.getMinBuildHeight() + 1;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surface <= minY) {
            return null;
        }
        int floor = Math.max(minY, surface - COLUMN_SCAN_DEPTH);
        for (int y = surface; y >= floor; y--) {
            if (isSafeStanding(level, new BlockPos(x, y, z))) {
                return new Vec3(x + 0.5D, y, z + 0.5D);
            }
        }
        if (originY > surface) {
            int ceiling = Math.min(level.getMaxBuildHeight() - 2, originY);
            for (int y = ceiling; y > surface; y--) {
                if (isSafeStanding(level, new BlockPos(x, y, z))) {
                    return new Vec3(x + 0.5D, y, z + 0.5D);
                }
            }
        }
        return null;
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
        int landed = Mth.clamp(y, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 4);
        BlockPos center = new BlockPos(x, landed, z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(center.offset(dx, -1, dz), ModBlocks.SKY_SOIL.get().defaultBlockState());
            }
        }
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
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

    public static boolean isChronoLevel(Level level) {
        return level.dimension().equals(ModDimensions.CHRONO);
    }
}
