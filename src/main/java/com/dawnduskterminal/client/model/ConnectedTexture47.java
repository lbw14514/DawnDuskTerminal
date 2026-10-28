package com.dawnduskterminal.client.model;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create 的 {@code OMNIDIRECTIONAL} 连接纹理索引，也就是老一套的「47 格」序列，一比一移植
 *
 * <p>图集固定 8x8 格，格子序号 = 列 + 行 * 8。四边与四角接不接一共 256 种情况，其中互不相同的形状只有
 * 47 种，剩下的都能折到这 47 格之一，所以一张 8x8 的图就够画完所有连接形状
 *
 * <p>这里只算形状，不认方块：邻居算不算「接上」由调用方给的 {@link Predicate} 决定。镶边塔木与封装塔木梁
 * 用的是「邻居是同一种方块」，黑木台面用的是「邻居是任意一种台面」——同一张图集能同时服务两种判据，
 * 也就不用把这段 80 行的索引公式抄第二遍
 */
public final class ConnectedTexture47 {
    /** 图集大小：8x8 格。格子序号 = 列 + 行 * 8 */
    public static final int SHEET_SIZE = 8;

    /** 面序号（0 到 5）对应的面内 [右, 上] 方向，与 Create 的 buildContext 一致 */
    private static final Direction[][] HV = {
        { Direction.EAST, Direction.SOUTH },   // DOWN
        { Direction.EAST, Direction.NORTH },   // UP
        { Direction.WEST, Direction.UP },      // NORTH
        { Direction.EAST, Direction.UP },      // SOUTH
        { Direction.SOUTH, Direction.UP },     // WEST
        { Direction.NORTH, Direction.UP },     // EAST
    };

    private ConnectedTexture47() {}

    /**
     * 这个面的哪一格
     *
     * @param connects 邻居算不算接上，传进来的是那个邻居方块的状态
     */
    public static int index(BlockAndTintGetter level, BlockPos pos, Direction face, Predicate<BlockState> connects) {
        int side = sideOf(face);
        Direction h = HV[side][0];
        Direction v = HV[side][1];

        boolean up = connects(level, pos, connects, v);
        boolean down = connects(level, pos, connects, v.getOpposite());
        boolean left = connects(level, pos, connects, h.getOpposite());
        boolean right = connects(level, pos, connects, h);

        boolean topLeft = up && left && connects(level, pos, connects, h.getOpposite(), v);
        boolean topRight = up && right && connects(level, pos, connects, h, v);
        boolean bottomLeft = down && left && connects(level, pos, connects, h.getOpposite(), v.getOpposite());
        boolean bottomRight = down && right && connects(level, pos, connects, h, v.getOpposite());

        int tileX = 0;
        int tileY = 0;
        int borders = (!up ? 1 : 0) + (!down ? 1 : 0) + (!left ? 1 : 0) + (!right ? 1 : 0);

        if (up) {
            tileX++;
        }
        if (down) {
            tileX += 2;
        }
        if (left) {
            tileY++;
        }
        if (right) {
            tileY += 2;
        }

        if (borders == 0) {
            if (topRight) {
                tileX++;
            }
            if (topLeft) {
                tileX += 2;
            }
            if (bottomRight) {
                tileY += 2;
            }
            if (bottomLeft) {
                tileY++;
            }
        }

        if (borders == 1) {
            if (!right && (topLeft || bottomLeft)) {
                tileY = 4;
                tileX = -1 + (bottomLeft ? 1 : 0) + (topLeft ? 2 : 0);
            }
            if (!left && (topRight || bottomRight)) {
                tileY = 5;
                tileX = -1 + (bottomRight ? 1 : 0) + (topRight ? 2 : 0);
            }
            if (!down && (topLeft || topRight)) {
                tileY = 6;
                tileX = -1 + (topLeft ? 1 : 0) + (topRight ? 2 : 0);
            }
            if (!up && (bottomLeft || bottomRight)) {
                tileY = 7;
                tileX = -1 + (bottomLeft ? 1 : 0) + (bottomRight ? 2 : 0);
            }
        }

        if (borders == 2) {
            if ((up && left && topLeft) || (down && left && bottomLeft) || (up && right && topRight)
                || (down && right && bottomRight)) {
                tileX += 3;
            }
        }

        return tileX + 8 * tileY;
    }

    private static int sideOf(Direction face) {
        return switch (face) {
            case DOWN -> 0;
            case UP -> 1;
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case EAST -> 5;
        };
    }

    private static boolean connects(BlockAndTintGetter level, BlockPos pos, Predicate<BlockState> connects, Direction d) {
        return connects.test(level.getBlockState(pos.relative(d)));
    }

    private static boolean connects(BlockAndTintGetter level, BlockPos pos, Predicate<BlockState> connects, Direction d1,
            Direction d2) {
        int dx = d1.getStepX() + d2.getStepX();
        int dy = d1.getStepY() + d2.getStepY();
        int dz = d1.getStepZ() + d2.getStepZ();
        return connects.test(level.getBlockState(pos.offset(dx, dy, dz)));
    }
}
