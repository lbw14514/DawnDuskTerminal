package com.dawnduskterminal.portal;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModAttachments;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * 暮色森林式的即时传送。碰到传送门流体的第一个 tick 就直传另一维度（没有搜寻蓄力 没有冷却 没有中毒），
 * 到达时播原版下界传送门音效
 */
public final class PortalTeleporter {
    private static final int MAX_CHUNK_LOADS = 64;
    /**
     * 落点搜索跑在主线程 每个未加载的区块都是一次同步生成
     * 只限区块数挡不住单块很慢的情况 再加一层软时间预算 超时就铺兜底平台
     */
    private static final long LANDING_SEARCH_BUDGET_NANOS = 8_000_000L;
    private static final long ARRIVAL_SEARCH_BUDGET_NANOS = 4_000_000L;
    public static final int LANDING_SEARCH_RADIUS = 320;
    public static final int LANDING_MIN_Y = 0;
    public static final int LANDING_MAX_Y = 128;

    private PortalTeleporter() {}

    public static void teleportEntity(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel current)) {
            return;
        }
        boolean entering = !current.dimension().equals(ModDimensions.CHRONO);
        ServerLevel target = entering
            ? current.getServer().getLevel(ModDimensions.CHRONO)
            : current.getServer().overworld();
        if (target == null) {
            return;
        }
        Vec3 landing = findSafeLanding(target, entity.getX(), entity.getZ(), (int) entity.getY());
        if (entering) {
            if (entity instanceof Player player) {
                // 记下从哪来，回程按这个坐标找落点，而不是按晨昏线里当前站的位置重新搜表面
                PortalBuilder.rememberOrigin(player);
            }
            Vec3 arrived = buildArrivalPortal(target, landing);
            if (arrived != null) {
                landing = arrived;
            }
        } else if (entity instanceof Player player) {
            PortalState state = player.getData(ModAttachments.PORTAL_STATE);
            if (state.hasReturn() && state.returnDim().equals(target.dimension().location().toString())) {
                landing = findSafeLanding(target, state.x(), state.z(), Mth.floor(state.y()));
            }
        }
        entity.teleportTo(target, landing.x, landing.y, landing.z, Set.of(), entity.getYRot(), entity.getXRot());
        target.playSound(null, landing.x, landing.y, landing.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static Vec3 findSafeLanding(ServerLevel level, double originX, double originZ, int originY) {
        int baseX = Mth.floor(originX);
        int baseZ = Mth.floor(originZ);
        long deadline = System.nanoTime() + LANDING_SEARCH_BUDGET_NANOS;
        Set<Long> loaded = new HashSet<>();
        loaded.add(ChunkPos.asLong(baseX >> 4, baseZ >> 4));
        level.getChunk(baseX >> 4, baseZ >> 4);
        Vec3 here = scanColumn(level, baseX, baseZ, originY);
        if (here != null) {
            return here;
        }
        search:
        for (int ring = 16; ring <= LANDING_SEARCH_RADIUS; ring += 16) {
            int step = 16;
            for (int dx = -ring; dx <= ring; dx += step) {
                for (int dz = -ring; dz <= ring; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < ring) {
                        continue;
                    }
                    if (System.nanoTime() >= deadline) {
                        break search;
                    }
                    long key = ChunkPos.asLong((baseX + dx) >> 4, (baseZ + dz) >> 4);
                    // 预算在这里严格检查：已经加载过的列不占新预算，否则一环内会远超上限
                    if (!loaded.contains(key) && loaded.size() >= MAX_CHUNK_LOADS) {
                        break search;
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

    private static Vec3 probe(ServerLevel level, Set<Long> loaded, int x, int z, int originY) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (loaded.add(ChunkPos.asLong(chunkX, chunkZ))) {
            level.getChunk(chunkX, chunkZ);
        }
        return scanColumn(level, x, z, originY);
    }

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

    private static Vec3 buildFallbackPlatform(ServerLevel level, int x, int y, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floor = Math.max(level.getMinBuildHeight() + 4, LANDING_MIN_Y);
        // 自然落点仍受 LANDING_MAX_Y 限制，但兜底平台要落在真实地表之上
        // 再按 128 夹一次会把人塞进高山内部，然后清出一个洞
        // 上界只按维度实际建造高度留 5 格净空，防止平台与清空循环越界
        int ceiling = Math.max(floor, level.getMaxBuildHeight() - 5);
        int landed = Mth.clamp(surface + 1, floor, ceiling);
        if (landed < LANDING_MIN_Y + 4) {
            landed = Mth.clamp(y, floor, ceiling);
        }
        BlockPos center = new BlockPos(x, landed, z);
        int radius = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                level.setBlockAndUpdate(center.offset(dx, -1, dz), ModTerrainBlocks.ROSY_GRASS.get().defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, -2, dz), ModTerrainBlocks.ROSY_DIRT.get().defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, -3, dz), ModTerrainBlocks.CANOPY_STONE.get().defaultBlockState());
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

    public static Vec3 buildArrivalPortal(ServerLevel level, Vec3 landing) {
        int reqX = Mth.floor(landing.x) + 2;
        int reqZ = Mth.floor(landing.z);
        int reqFloor = Mth.floor(landing.y) - 1;
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int sx = -6; sx <= 6; sx++) {
            for (int sy = -4; sy <= 4; sy++) {
                for (int sz = -6; sz <= 6; sz++) {
                    scan.set(reqX + sx, reqFloor + 1 + sy, reqZ + sz);
                    if (level.getBlockState(scan).is(ModBlocks.PORTAL_FLUID.get())) {
                        return landing;
                    }
                }
            }
        }
        int minFloor = Math.max(level.getMinBuildHeight() + 2, LANDING_MIN_Y);
        int chosenX = Integer.MIN_VALUE;
        int chosenZ = 0;
        int chosenFloor = 0;
        long deadline = System.nanoTime() + ARRIVAL_SEARCH_BUDGET_NANOS;
        for (int ring = 0; ring <= 24 && chosenX == Integer.MIN_VALUE; ring += 4) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            for (int dx = -ring; dx <= ring && chosenX == Integer.MIN_VALUE; dx += 4) {
                for (int dz = -ring; dz <= ring; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    if (System.nanoTime() >= deadline) {
                        break;
                    }
                    int x = reqX + dx;
                    int z = reqZ + dz;
                    level.getChunk(x >> 4, z >> 4);
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (ground < minFloor || ground > reqFloor + 1) {
                        continue;
                    }
                    BlockPos feet = new BlockPos(x, ground + 1, z);
                    if (!level.getBlockState(feet).getFluidState().isEmpty()
                        || !level.getBlockState(feet.above()).getFluidState().isEmpty()) {
                        continue;
                    }
                    chosenX = x;
                    chosenZ = z;
                    chosenFloor = ground;
                    break;
                }
            }
        }
        if (chosenX == Integer.MIN_VALUE) {
            chosenX = reqX;
            chosenZ = reqZ;
            chosenFloor = Math.max(reqFloor, minFloor);
        }
        int baseX = chosenX;
        int baseZ = chosenZ;
        int floorY = chosenFloor;
        BlockState portal = ModBlocks.PORTAL_FLUID.get().defaultBlockState();
        BlockState pad = ModTerrainBlocks.CANOPY_STONE.get().defaultBlockState();
        BlockState turf = ModTerrainBlocks.ROSY_GRASS.get().defaultBlockState();
        BlockState soil = ModTerrainBlocks.ROSY_DIRT.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int r = 4;
        for (int dx = -r; dx <= 2 + r; dx++) {
            for (int dz = -r; dz <= 2 + r; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos clear = new BlockPos(baseX + dx, floorY + dy, baseZ + dz);
                    if (!level.getBlockState(clear).isAir()) {
                        level.setBlockAndUpdate(clear, air);
                    }
                }
                BlockPos pos = new BlockPos(baseX + dx, floorY, baseZ + dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, pad);
                }
                if (level.getBlockState(pos.below()).isAir()) {
                    level.setBlockAndUpdate(pos.below(), pad);
                }
                if (level.getBlockState(pos.below(2)).isAir()) {
                    level.setBlockAndUpdate(pos.below(2), pad);
                }
            }
        }
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos top = new BlockPos(baseX + dx, floorY, baseZ + dz);
                level.setBlockAndUpdate(top, air);
                level.setBlockAndUpdate(top.below(), pad);
                level.setBlockAndUpdate(top.below(2), pad);
                level.setBlockAndUpdate(top, portal);
                level.setBlockAndUpdate(top.above(), air);
            }
        }
        for (int dx = -r; dx <= 2 + r; dx++) {
            for (int dz = -r; dz <= 2 + r; dz++) {
                if (dx >= 0 && dx < 2 && dz >= 0 && dz < 2) {
                    continue;
                }
                BlockPos pos = new BlockPos(baseX + dx, floorY, baseZ + dz);
                if (!level.getBlockState(pos.above()).isAir()) {
                    continue;
                }
                if (level.getBlockState(pos).is(pad.getBlock())) {
                    level.setBlockAndUpdate(pos, turf);
                    BlockPos below = pos.below();
                    if (level.getBlockState(below).is(pad.getBlock())) {
                        level.setBlockAndUpdate(below, soil);
                    }
                }
            }
        }
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal arrival portal built at {} {} {}", baseX, floorY, baseZ);
        return new Vec3(baseX - 1.5D, floorY + 1, baseZ + 1.0D);
    }
}
