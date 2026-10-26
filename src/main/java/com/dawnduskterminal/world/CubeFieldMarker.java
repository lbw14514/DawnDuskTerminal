package com.dawnduskterminal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 反建器那一档的范围标示粒子：把 17×17×17 的场用红石粉画出边框
 *
 * <p>整段照暮色森林 {@code twilightforest:antibuilder} 的 {@code AntibuilderBlockEntity#tick} /
 * {@code makeRandomOutline} / {@code makeOutline} / {@code drawParticleLine} 写的（对照 4.8.3345 的源码），
 * 只改了三处，都在下面注明：
 * <ul>
 *   <li>半径：那边 {@code RADIUS = 4}（9×9×9），这里用 {@link CubeField#RADIUS}（17×17×17），
 *       两台 065/069 是「更高级」的那一档
 *   <li>每条线上的粒子数：那边一段固定 16 颗；这里的框有 17 格宽，固定 16 颗会稀得看不出来，
 *       改成按长度给（每格 {@value #PARTICLES_PER_BLOCK} 颗），密与那边一致
 *   <li>两段线里「中心到角」与「角到中心」仍然随机二选一，与那边一样；点集不一样，所以不能合并
 * </ul>
 *
 * <p>那边每 tick 做两件事，这里都一样：先在方块自己那一格里飘一颗红石粉；再每 {@value #OUTLINE_CHANCE}
 * 次里挑一次，连画三组框线。每组框线是两段：一段从中心斜到某个角（或反过来），一段沿那个角所在的那条棱。
 * 十二条棱分三档：顶面四条、底面四条、竖直四条
 *
 * <p>只在客户端画，而且只在 16 格内有活着的玩家时画（那边同样是 {@code hasNearbyAlivePlayer(..., 16)}）：
 * 场是给玩家看的，没人在附近就一颗都不发。服务端那颗 ticker 根本不会挂，见两个方块的 {@code getTicker}
 */
public final class CubeFieldMarker {
    /** 一组框线里那两段线的采样密度：每格几颗红石粉 */
    private static final int PARTICLES_PER_BLOCK = 3;

    /** 那边的 drawParticleLine 是一段固定 16 颗、棱长 5 格，约合每格 3.2 颗；这里按格数换算出同样的密度 */

    /** 每 tick 有多少分之一的概率画一组框线（那边是 10） */
    private static final int OUTLINE_CHANCE = 10;

    /** 一组框线连画几条（那边一次画三条） */
    private static final int OUTLINES_PER_FLASH = 3;

    /** 框线一共十二条（顶面四条、底面四条、竖直四条） */
    private static final int OUTLINES = 12;

    /** 那边用 16 格判定「附近有没有玩家」，没有就整个 tick 停摆 */
    private static final double PLAYER_RANGE = 16.0D;

    private CubeFieldMarker() {}

    /** 方块中心往外的半边长：8 格加半格，框住的正好是那 17 格方块 */
    public static double halfExtent() {
        return CubeField.RADIUS + 0.5D;
    }

    /** 框线一共十二条，测试用 */
    public static int outlineCount() {
        return OUTLINES;
    }

    /**
     * 方块实体的客户端刻：签名与 {@code BlockEntityTicker} 对齐，方块那边直接方法引用过来
     *
     * <p>顺序也照那边：先问附近有没有玩家，再是方块自己那一格的一颗粉尘，最后是概率性的框线
     */
    public static void tick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!level.isClientSide) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        if (!level.hasNearbyAlivePlayer(cx, cy, cz, PLAYER_RANGE)) {
            return;
        }
        RandomSource random = level.getRandom();
        // 方块自己那一格里飘一颗：那边每 tick 都发，位置随机落在这一格内
        level.addParticle(DustParticleOptions.REDSTONE,
            pos.getX() + random.nextFloat(), pos.getY() + random.nextFloat(), pos.getZ() + random.nextFloat(),
            0.0D, 0.0D, 0.0D);
        if (random.nextInt(OUTLINE_CHANCE) == 0) {
            for (int draw = 0; draw < OUTLINES_PER_FLASH; draw++) {
                outline(level, pos, random, random.nextInt(OUTLINES));
            }
        }
    }

    /**
     * 画一组框线：一段斜线（中心与某个角之间）+ 那个角所在的那条棱
     *
     * <p>「斜线」那一侧与那边一样随机：{@code true} 画「中心→远端角」，{@code false} 画「起始角→中心」。
     * 两个方向在顶面/底面那八条上的点集并不相同，所以这条随机不能省
     */
    private static void outline(Level level, BlockPos pos, RandomSource random, int outline) {
        double[] edge = edge(pos, outline);
        if (edge.length == 0) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        if (random.nextBoolean()) {
            line(level, random, cx, cy, cz, edge[3], edge[4], edge[5]);
        } else {
            line(level, random, edge[0], edge[1], edge[2], cx, cy, cz);
        }
        line(level, random, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5]);
    }

    /**
     * 第 {@code outline} 号框线的那条棱，绝对坐标 {@code {x0, y0, z0, x1, y1, z1}}
     *
     * <p>编号与那边逐条对齐：0~3 是顶面四条、4~7 是竖直四条、8~11 是底面四条；
     * 每条棱的两个端点都落在场最外层方块的外侧面上（{@code pos - RADIUS} 与 {@code pos + RADIUS + 1}）
     */
    public static double[] edge(BlockPos pos, int outline) {
        int r = CubeField.RADIUS;
        double sx = pos.getX();
        double sy = pos.getY();
        double sz = pos.getZ();
        double dx = pos.getX();
        double dy = pos.getY();
        double dz = pos.getZ();
        switch (outline) {
            case 0, 8 -> {
                sx -= r;
                dx += r + 1;
                sz -= r;
                dz -= r;
            }
            case 1, 9 -> {
                sx -= r;
                dx -= r;
                sz -= r;
                dz += r + 1;
            }
            case 2, 10 -> {
                sx -= r;
                dx += r + 1;
                sz += r + 1;
                dz += r + 1;
            }
            case 3, 11 -> {
                sx += r + 1;
                dx += r + 1;
                sz -= r;
                dz += r + 1;
            }
            case 4 -> {
                sx -= r;
                dx -= r;
                sz -= r;
                dz -= r;
            }
            case 5 -> {
                sx += r + 1;
                dx += r + 1;
                sz -= r;
                dz -= r;
            }
            case 6 -> {
                sx += r + 1;
                dx += r + 1;
                sz += r + 1;
                dz += r + 1;
            }
            case 7 -> {
                sx -= r;
                dx -= r;
                sz += r + 1;
                dz += r + 1;
            }
            default -> {
                return new double[0];
            }
        }
        switch (outline) {
            case 0, 1, 2, 3 -> {
                sy += r + 1;
                dy += r + 1;
            }
            case 4, 5, 6, 7 -> {
                sy -= r;
                dy += r + 1;
            }
            case 8, 9, 10, 11 -> {
                sy -= r;
                dy -= r;
            }
            default -> {
            }
        }
        return new double[] {sx, sy, sz, dx, dy, dz};
    }

    /** 一段红石粉线：沿长度均匀采样，每格 {@value #PARTICLES_PER_BLOCK} 颗，抖动照那边（±0.005） */
    private static void line(Level level, RandomSource random, double sx, double sy, double sz,
                             double dx, double dy, double dz) {
        double length = Math.sqrt((dx - sx) * (dx - sx) + (dy - sy) * (dy - sy) + (dz - sz) * (dz - sz));
        int particles = Math.max(2, (int) Math.round(PARTICLES_PER_BLOCK * length));
        for (int i = 0; i < particles; i++) {
            double t = i / (particles - 1.0D);
            level.addParticle(DustParticleOptions.REDSTONE,
                sx + (dx - sx) * t + random.nextFloat() * 0.005D,
                sy + (dy - sy) * t + random.nextFloat() * 0.005D,
                sz + (dz - sz) * t + random.nextFloat() * 0.005D,
                0.0D, 0.0D, 0.0D);
        }
    }
}
