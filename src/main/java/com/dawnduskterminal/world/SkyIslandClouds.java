package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 把高空里被密度钳制压成薄片的空岛整体换成暮色森林的薄云，并向外补两格
 *
 * <p>{@code sky_island_density} 用两段 {@code y_clamped_gradient} 把 y 140~240 与 260~310 夹住，
 * 越过这两段后梯度不再变化，噪声只要整体偏高或偏低，岛体就只剩噪声零点附近的一薄片。这里在
 * {@code FEATURES} 步骤末尾按列量一次厚度，把整块薄岛换成 {@code twilightforest:wispy_cloud}
 *
 * <p>只看高空那一层：列顶低于 {@link #ISLAND_MIN_TOP_Y} 的一律不碰，地面山体因为厚度远大于
 * {@link #THICKNESS_LIMIT} 自然被排除。这里不看群系，晨昏线维度里任何群系都走同一条路径
 *
 * <p>厚度用 {@code WORLD_SURFACE_WG} 高度图量。{@code FINAL_HEIGHTMAPS} 那一套在放树时会被
 * {@code ProtoChunk.setBlockState} 更新，会把树叶算进来；{@code _WG} 那套在 {@code CARVERS} 之后再没人更新，
 * 正好停在纯地形上，所以从它往下数的连续实心块就是岛体本身
 *
 * <p>判定薄岛后不是只换岛体，而是从岛体开始沿 6 邻接 flood fill 收集所有非空气方块
 * 树 树叶 草 花 雪都算进来，白名单只有空气和已经换好的薄云；最后再围绕整团替换结果外补两格
 *
 * <p>执行线程是区块生成的工作线程（{@code applyBiomeDecoration} 的调用点），不在主线程
 */
public final class SkyIslandClouds {
    /** 薄云，见暮色森林 {@code TFBlocks.WISPY_CLOUD} */
    public static final ResourceLocation WISPY_CLOUD =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "wispy_cloud");

    /** 空的密度梯度从 y 140 起才可能为正，再低就是地面 */
    public static final int ISLAND_MIN_TOP_Y = 140;
    /** 第二段梯度在 y 310 收尾；这里只是测试兜底，生产直接用维度最大建筑高度 */
    public static final int ISLAND_MAX_TOP_Y = 511;
    /** 往下最多看到这里，超过这个深度的列一定不是薄片 */
    public static final int ISLAND_SCAN_FLOOR_Y = 128;
    /** 厚度小于这个值才算薄片 */
    public static final int THICKNESS_LIMIT = 4;
    /** 换成薄云后，各方向向外补几格 */
    public static final int CLOUD_MARGIN = 2;
    /** flood fill 允许越过区块边界的格数，给树冠留空间 */
    public static final int SEARCH_MARGIN = 6;

    private static final int SIZE = 16;
    private static final int COLUMNS = SIZE * SIZE;

    private static volatile BlockState cloud;
    private static volatile boolean cloudMissing;

    private SkyIslandClouds() {}

    @FunctionalInterface
    public interface BlockSetter {
        void set(int x, int y, int z, BlockState state);
    }

    /** 在 {@code FEATURES} 末尾调用，区块生成线程 */
    public static void process(WorldGenLevel level, ChunkAccess chunk) {
        BlockState state = cloudState();
        if (state == null) {
            return;
        }

        int minBuildY = level.getMinBuildHeight();
        int maxBuildY = level.getMaxBuildHeight() - 1;
        int maxTopY = maxBuildY;
        int scanFloorY = Math.max(ISLAND_SCAN_FLOOR_Y, minBuildY);
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();

        int[] tops = new int[COLUMNS];
        boolean any = false;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, originX + x, originZ + z);
                tops[z * SIZE + x] = top;
                if (top >= ISLAND_MIN_TOP_Y && top <= maxTopY) {
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }

        processColumns(level, (x, y, z, set) -> level.setBlock(new BlockPos(x, y, z), set, Block.UPDATE_CLIENTS),
            originX, originZ, tops, state, ISLAND_MIN_TOP_Y, maxTopY, scanFloorY, minBuildY, maxBuildY);
    }

    /**
     * 纯几何部分：给每列的地形顶，量厚度、找出薄岛、换云并外补
     *
     * <p>可见性只给 GameTest 直接喂合成地形
     *
     * @param topY 256 个列顶世界坐标，按 {@code z * 16 + x} 排；没有岛的列给 -1
     * @return 被换掉的岛体方块数
     */
    public static int processColumns(BlockGetter reader, BlockSetter setter, int originX, int originZ,
                                     int[] topY, BlockState cloudState, int minTopY, int maxTopY,
                                     int scanFloorY, int minBuildY, int maxBuildY) {
        if (topY.length != COLUMNS) {
            throw new IllegalArgumentException("topY must hold " + COLUMNS + " entries");
        }

        int[] tops = new int[COLUMNS];
        int[] bottoms = new int[COLUMNS];
        int[] thickness = new int[COLUMNS];
        boolean[] body = new boolean[COLUMNS];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int index = 0; index < COLUMNS; index++) {
            int top = topY[index];
            if (top < minTopY || top > maxTopY) {
                continue;
            }
            int x = originX + (index % SIZE);
            int z = originZ + (index / SIZE);

            int y = top;
            int count = 0;
            while (y >= scanFloorY) {
                cursor.set(x, y, z);
                if (reader.getBlockState(cursor).isAir()) {
                    break;
                }
                count++;
                y--;
            }
            if (count == 0) {
                continue;
            }
            body[index] = true;
            tops[index] = top;
            bottoms[index] = y + 1;
            thickness[index] = count;
        }

        int[] parent = new int[COLUMNS];
        for (int index = 0; index < COLUMNS; index++) {
            parent[index] = body[index] ? index : -1;
        }
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                if (!body[index]) {
                    continue;
                }
                if (x > 0) {
                    unionOverlapping(parent, body, tops, bottoms, index, index - 1);
                }
                if (z > 0) {
                    unionOverlapping(parent, body, tops, bottoms, index, index - SIZE);
                }
            }
        }

        int[] componentThickness = new int[COLUMNS];
        for (int index = 0; index < COLUMNS; index++) {
            if (body[index]) {
                int root = find(parent, index);
                componentThickness[root] = Math.max(componentThickness[root], thickness[index]);
            }
        }

        int replaced = 0;
        int minDilationY = Math.max(minBuildY, scanFloorY - CLOUD_MARGIN);
        int maxDilationY = maxBuildY;
        boolean[] processed = new boolean[COLUMNS];
        for (int index = 0; index < COLUMNS; index++) {
            if (!body[index]) {
                continue;
            }
            int root = find(parent, index);
            if (componentThickness[root] >= THICKNESS_LIMIT || processed[root]) {
                continue;
            }
            processed[root] = true;
            replaced += replaceComponent(reader, setter, cloudState, parent, body, tops, bottoms, root,
                originX, originZ, scanFloorY, maxBuildY, minDilationY, maxDilationY);
        }
        return replaced;
    }

    /**
     * 从薄岛岛体开始沿 6 邻接收集所有非空气方块，整团替换，再围绕整团外补
     *
     * <p>白名单只有空气和已经换好的薄云，所以树干 树叶 草 花 雪都会被一起换掉
     */
    private static int replaceComponent(BlockGetter reader, BlockSetter setter, BlockState cloudState,
                                        int[] parent, boolean[] body, int[] tops, int[] bottoms, int root,
                                        int originX, int originZ, int scanFloorY, int maxBuildY,
                                        int minDilationY, int maxDilationY) {
        int minX = originX - SEARCH_MARGIN;
        int maxX = originX + SIZE - 1 + SEARCH_MARGIN;
        int minZ = originZ - SEARCH_MARGIN;
        int maxZ = originZ + SIZE - 1 + SEARCH_MARGIN;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();

        for (int index = 0; index < COLUMNS; index++) {
            if (!body[index] || find(parent, index) != root) {
                continue;
            }
            int x = originX + (index % SIZE);
            int z = originZ + (index / SIZE);
            for (int y = bottoms[index]; y <= tops[index]; y++) {
                long key = BlockPos.asLong(x, y, z);
                if (visited.add(key)) {
                    queue.add(key);
                }
            }
        }

        while (!queue.isEmpty()) {
            long key = queue.poll();
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                            continue;
                        }
                        int nx = x + dx;
                        int ny = y + dy;
                        int nz = z + dz;
                        if (nx < minX || nx > maxX || nz < minZ || nz > maxZ
                            || ny < scanFloorY || ny > maxBuildY) {
                            continue;
                        }
                        long next = BlockPos.asLong(nx, ny, nz);
                        if (visited.contains(next)) {
                            continue;
                        }
                        cursor.set(nx, ny, nz);
                        BlockState state = reader.getBlockState(cursor);
                        if (!state.isAir() && !state.is(cloudState.getBlock())) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        int replaced = 0;
        for (long key : visited) {
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            cursor.set(x, y, z);
            if (!reader.getBlockState(cursor).isAir()) {
                setter.set(x, y, z, cloudState);
                replaced++;
            }
        }

        dilate(reader, setter, cloudState, visited, originX, originZ, minDilationY, maxDilationY);
        return replaced;
    }

    private static void dilate(BlockGetter reader, BlockSetter setter, BlockState cloudState,
                               Set<Long> blocks, int originX, int originZ, int minY, int maxY) {
        int boundMinX = originX - SEARCH_MARGIN - CLOUD_MARGIN;
        int boundMaxX = originX + SIZE - 1 + SEARCH_MARGIN + CLOUD_MARGIN;
        int boundMinZ = originZ - SEARCH_MARGIN - CLOUD_MARGIN;
        int boundMaxZ = originZ + SIZE - 1 + SEARCH_MARGIN + CLOUD_MARGIN;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (long key : blocks) {
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            for (int dy = -CLOUD_MARGIN; dy <= CLOUD_MARGIN; dy++) {
                int targetY = y + dy;
                if (targetY < minY || targetY > maxY) {
                    continue;
                }
                for (int dx = -CLOUD_MARGIN; dx <= CLOUD_MARGIN; dx++) {
                    int targetX = x + dx;
                    if (targetX < boundMinX || targetX > boundMaxX) {
                        continue;
                    }
                    for (int dz = -CLOUD_MARGIN; dz <= CLOUD_MARGIN; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int targetZ = z + dz;
                        if (targetZ < boundMinZ || targetZ > boundMaxZ) {
                            continue;
                        }
                        cursor.set(targetX, targetY, targetZ);
                        if (reader.getBlockState(cursor).isAir()) {
                            setter.set(targetX, targetY, targetZ, cloudState);
                        }
                    }
                }
            }
        }
    }

    private static void unionOverlapping(int[] parent, boolean[] body, int[] tops, int[] bottoms,
                                         int left, int right) {
        if (!body[right]) {
            return;
        }
        boolean overlap = bottoms[left] <= tops[right] && bottoms[right] <= tops[left];
        if (!overlap) {
            return;
        }
        int a = find(parent, left);
        int b = find(parent, right);
        if (a != b) {
            parent[b] = a;
        }
    }

    private static int find(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[index] != index) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static BlockState cloudState() {
        BlockState state = cloud;
        if (state != null) {
            return state;
        }
        if (cloudMissing) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(WISPY_CLOUD);
        if (block == Blocks.AIR) {
            cloudMissing = true;
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal could not find {}, thin sky islands will keep their terrain", WISPY_CLOUD);
            return null;
        }
        cloud = state = block.defaultBlockState();
        return state;
    }
}
