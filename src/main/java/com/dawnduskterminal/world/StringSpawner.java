package com.dawnduskterminal.world;

import com.dawnduskterminal.entity.StringEntity;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * 「弦」的自然生成：在不稳定液体的液面上、头顶是空气的位置刷出
 *
 * <h2>为什么不用原版刷怪器</h2>
 *
 * <p>原版地面刷怪的落点来自固体地面高度图，水生刷怪只认 {@code minecraft:water} 标签；
 * 传送门流体既不是固体也不在原版水标签里，两种刷怪器都不会看它一眼。修改
 * {@code MobSpawnSettings} 也只能让实体出现在岸边，不会出现在水面，所以这里自己扫列。
 *
 * <h2>扫描方式</h2>
 *
 * <p>{@link Heightmap.Types#WORLD_SURFACE} 把流体也算作非空气，它的高度正好是液面之上的第一格空气。
 * 因此取一列的高度、检查下面那一格是传送门流体、这一格是空气，就是一个合法的生成点。
 * 每 5 秒对每个玩家最多找十个列，命中的列有半概率生成，附近已有五只就不再刷。
 *
 * <p>自然生成只出滑行型；低空飞行型与游动型只能由滑行型分裂出来（见 {@code StringEntity}）
 */
public final class StringSpawner {
    /** 每 100 tick 扫一轮 */
    private static final int INTERVAL = 100;
    /** 每轮每个玩家的候选列数 */
    private static final int ATTEMPTS = 10;
    /** 距离玩家 12 到 44 格，太近会在脸上刷出来，太远没有意义 */
    private static final double MIN_DISTANCE = 12.0D;
    private static final double MAX_DISTANCE = 44.0D;
    /** 玩家周围 48 格内的上限 */
    private static final int MAX_NEARBY = 5;

    private StringSpawner() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.dimension().equals(ModDimensions.CHRONO)) {
                continue;
            }
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    trySpawnAround(level, player);
                }
            }
        }
    }

    private static void trySpawnAround(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(48.0D);
        if (level.getEntitiesOfClass(StringEntity.class, area).size() >= MAX_NEARBY) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (surfaceY <= level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockPos fluidPos = new BlockPos(x, surfaceY - 1, z);
            FluidState fluid = level.getFluidState(fluidPos);
            if (!StringEntity.isPortalFluid(fluid)) {
                continue;
            }
            BlockPos spawnPos = new BlockPos(x, surfaceY, z);
            BlockState at = level.getBlockState(spawnPos);
            if (!at.isAir() || !level.getBlockState(spawnPos.above()).isAir()) {
                continue;
            }
            if (random.nextFloat() > 0.5F) {
                continue;
            }
            StringEntity entity = ModEntities.STRING.get().create(level);
            if (entity == null) {
                return;
            }
            double y = surfaceY - 1 + fluid.getHeight(level, fluidPos) + StringEntity.SURFACE_OFFSET;
            entity.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                MobSpawnType.NATURAL, null);
            if (!level.noCollision(entity)) {
                continue;
            }
            level.addFreshEntity(entity);
            return;
        }
    }
}
