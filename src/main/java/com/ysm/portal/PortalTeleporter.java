package com.ysm.portal;

import com.ysm.config.YsmConfig;
import com.ysm.registry.ModAttachments;
import com.ysm.registry.ModBlocks;
import com.ysm.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class PortalTeleporter {
    public static final int LANDING_X = 8;
    public static final int LANDING_Z = 8;
    public static final int LANDING_Y = 160;

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
        int cooldown = YsmConfig.portalCooldownTicks();
        PortalState state = player.getData(ModAttachments.PORTAL_STATE);
        ServerLevel current = player.serverLevel();

        if (current.dimension().equals(ModDimensions.CHRONO)) {
            ServerLevel target = resolve(player.server, state.returnDim());
            if (target == null) {
                target = player.server.overworld();
            }
            Vec3 destination = state.hasReturn()
                ? new Vec3(state.x(), state.y(), state.z())
                : Vec3.atBottomCenterOf(target.getSharedSpawnPos());
            player.teleportTo(target, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
            player.setData(ModAttachments.PORTAL_STATE,
                player.getData(ModAttachments.PORTAL_STATE).withCooldown(cooldown));
            return;
        }

        ServerLevel chrono = player.server.getLevel(ModDimensions.CHRONO);
        if (chrono == null) {
            return;
        }
        Vec3 landing = ensureLanding(chrono);
        PortalState remembered = state.withReturn(
            current.dimension().location().toString(), player.getX(), player.getY(), player.getZ());
        player.teleportTo(chrono, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        player.setData(ModAttachments.PORTAL_STATE, remembered.withCooldown(cooldown));
    }

    private static Vec3 ensureLanding(ServerLevel chrono) {
        BlockPos center = new BlockPos(LANDING_X, LANDING_Y, LANDING_Z);
        if (chrono.getBlockState(center.below()).isAir()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    chrono.setBlockAndUpdate(center.offset(dx, -1, dz), ModBlocks.SKY_SOIL.get().defaultBlockState());
                }
            }
            for (int dy = 0; dy <= 3; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!chrono.getBlockState(pos).isAir()) {
                            chrono.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
        return new Vec3(LANDING_X + 0.5D, LANDING_Y, LANDING_Z + 0.5D);
    }

    public static boolean isChronoLevel(Level level) {
        return level.dimension().equals(ModDimensions.CHRONO);
    }
}
