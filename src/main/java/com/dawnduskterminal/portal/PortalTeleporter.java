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

    private static final class SearchTask {
        final ServerLevel level;
        final double baseX;
        final double baseZ;
        final int originY;
        final PortalState remembered;
        final boolean returning;
        final ServerLevel homeLevel;
        final int maxRadius;
        final Set<Long> loaded = new HashSet<>();
        int ring = 0;
        int index = 0;
        boolean originTried = false;
        boolean searching = false;
        int budget = 0;        int charge = 0;
        SearchTask(ServerLevel level, double baseX, double baseZ, int originY,
                   PortalState remembered, boolean returning, ServerLevel homeLevel, int maxRadius) {
            this.level = level;
            this.baseX = baseX;
            this.baseZ = baseZ;
            this.originY = originY;
            this.remembered = remembered;
            this.returning = returning;
            this.homeLevel = homeLevel;
            this.maxRadius = maxRadius;
        }
    }

    private static final java.util.Map<java.util.UUID, SearchTask> SEARCHES = new java.util.HashMap<>();

    public static void tick(ServerPlayer player) {
        java.util.UUID id = player.getUUID();
        SearchTask task = SEARCHES.get(id);
        boolean inPortal = PortalTrigger.shouldTeleport(player, player.blockPosition());
        if (task == null && !inPortal && cooldownActive(player)
            && PortalTrigger.isInsidePortal(player, player.blockPosition())) {
            if (player.tickCount % 20 == 0) {
                int left = player.getData(ModAttachments.PORTAL_STATE).cooldown();
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "portal cooldown " + (left / 20 + 1) + "s"), true);
            }
            return;
        }
        if (task == null) {
            if (inPortal) {
                SearchTask created = createTask(player);
                if (created != null) {
                    created.searching = true;
                    SEARCHES.put(id, created);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.dawnduskterminal.network.PortalSearchPayload(true));
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.dawnduskterminal.searching"), true);
                }
            }
            return;
        }
        if (!inPortal) {
            task.charge = 0;
            if (task.searching) {
                task.searching = false;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.dawnduskterminal.network.PortalSearchPayload(false));
            }
            return;
        }
        if (!task.searching) {
            task.searching = true;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.dawnduskterminal.network.PortalSearchPayload(true));
        }
        task.budget = 2;
        if (task.charge < 100) {
            task.charge++;
            return;
        }
        Vec3 found = advance(player, task);
        if (found == null && task.ring > task.maxRadius) {
            found = buildFallbackPlatform(task.level, Mth.floor(task.baseX), task.originY, Mth.floor(task.baseZ));
        }
        if (found == null && player.tickCount % 20 == 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "searching radius " + Math.min(task.ring, task.maxRadius) + " / " + task.maxRadius), true);
        }
        if (found == null) {
            return;
        }
        SEARCHES.remove(id);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.dawnduskterminal.network.PortalSearchPayload(false));
        finish(player, task, found);
    }

    @Nullable
    private static SearchTask createTask(ServerPlayer player) {
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
            return new SearchTask(target, originX, originZ, originY, state, true, target, 48);
        }
        ServerLevel chrono = player.server.getLevel(ModDimensions.CHRONO);
        if (chrono == null) {
            return null;
        }
        PortalState remembered = state.withReturn(
            current.dimension().location().toString(), player.getX(), player.getY(), player.getZ());
        return new SearchTask(chrono, player.getX(), player.getZ(), Mth.floor(player.getY()),
            remembered, false, current, LANDING_SEARCH_RADIUS);
    }

    @Nullable
    private static Vec3 advance(ServerPlayer player, SearchTask task) {
        if (task.returning) {
            Vec3 spot = returnSpot(task.level, task.baseX, task.originY, task.baseZ);
            if (spot != null) {
                return spot;
            }
            return new Vec3(task.baseX, task.originY, task.baseZ);
        }
        if (!task.originTried) {
            Vec3 here = probe(task, Mth.floor(task.baseX), Mth.floor(task.baseZ));
            if (here == null && task.budget <= 0) {
                return null;
            }
            task.originTried = true;
            if (here != null) {
                return here;
            }
        }
        while (task.ring <= task.maxRadius) {
            if (task.ring == 0) {
                task.ring = 16;
                task.index = 0;
                continue;
            }
            int ring = task.ring;
            int step = Math.max(16, ring / 6);
            int perSide = (2 * ring) / step;
            if (perSide < 1) {
                perSide = 1;
            }
            int total = 4 * perSide;
            if (task.index >= total) {
                task.ring += step;
                task.index = 0;
                continue;
            }
            int i = task.index;
            int side = i / perSide;
            int off = i % perSide;
            int dx;
            int dz;
            if (side == 0) {
                dx = -ring + off * step;
                dz = -ring;
            } else if (side == 1) {
                dx = ring;
                dz = -ring + off * step;
            } else if (side == 2) {
                dx = ring - off * step;
                dz = ring;
            } else {
                dx = -ring;
                dz = ring - off * step;
            }
            int px = Mth.floor(task.baseX) + dx;
            int pz = Mth.floor(task.baseZ) + dz;
            int cx = px >> 4;
            int cz = pz >> 4;
            long key = ChunkPos.asLong(cx, cz);
            if (!task.loaded.contains(key)) {
                if (task.budget <= 0) {
                    return null;
                }
                task.budget--;
                task.loaded.add(key);
                task.level.getChunk(cx, cz);
            }
            Vec3 found = scanColumn(task.level, px, pz, task.originY);
            if (found != null) {
                return found;
            }
            task.index++;
        }
        return null;
    }

    @Nullable
    private static Vec3 returnSpot(ServerLevel level, double x, int y, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        level.getChunk(bx >> 4, bz >> 4);
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        for (int dy = 0; dy >= -1; dy--) {
            for (int[] o : offsets) {
                BlockPos feet = new BlockPos(bx + o[0], y + dy, bz + o[1]);
                if (level.isOutsideBuildHeight(feet)) {
                    continue;
                }
                if (isSafeStanding(level, feet)) {
                    return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                }
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 probe(SearchTask task, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (!task.loaded.contains(key)) {
            if (task.budget <= 0) {
                return null;
            }
            task.budget--;
            task.loaded.add(key);
            task.level.getChunk(chunkX, chunkZ);
        }
        return scanColumn(task.level, x, z, task.originY);
    }

    private static void finish(ServerPlayer player, SearchTask task, Vec3 landing) {
        int cooldown = Math.max(DdtConfig.portalCooldownTicks(), 40);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.POISON, 300, 0));
        if (task.returning) {
            player.teleportTo(task.level, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
            player.setData(ModAttachments.PORTAL_STATE,
                player.getData(ModAttachments.PORTAL_STATE).withCooldown(cooldown));
            playSound(player, ModSounds.PORTAL_EXIT);
            return;
        }
        player.teleportTo(task.level, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        buildArrivalPortal(task.level, landing);
        player.setData(ModAttachments.PORTAL_STATE, task.remembered.withCooldown(cooldown));
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
                level.setBlockAndUpdate(center.offset(dx, -2, dz),
                    com.dawnduskterminal.registry.ModTerrainBlocks.EMBER_SOIL.get().defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, -3, dz),
                    com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get().defaultBlockState());
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
