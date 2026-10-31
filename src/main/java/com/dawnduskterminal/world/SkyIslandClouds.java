package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * 在浮空岛下面挂几团薄云
 *
 * <p>形状不是一个一个的球，而是三维噪声场切出来的：每个格点算「距离衰减 + 噪声」，超过门槛才放云。
 * 所以一朵云有凸起、有缺口、有挂丝，且完全由世界坐标决定，相邻区块各算各的也不会错位
 *
 * <p>不看列的位置：边缘、平直、向下鼓出来的一律同一条判据，云挂在哪里只由噪声决定
 *
 * <p>为什么不并到地形密度里：密度函数只能看坐标，认不出「这个点在某座岛下面」。要贴岛就只能在地形
 * 生成完、岛的形状已经存在之后算，也就是现在的 {@code FEATURES} 末尾
 *
 * <p>附着点用 {@link SkyIslandDensity#isIsland}，这是唯一的空岛判据。山体在 140~196 与空岛带重叠的那
 * 一段靠 {@code base_3d_noise} 赢过 {@code sky_island_density} 被排除
 *
 * <p>位置全部由世界坐标哈希与 {@link ImprovedNoise} 决定，相邻区块各长各的也不会错位
 *
 * <p>执行线程是区块生成的工作线程（{@code applyBiomeDecoration} 的调用点），不在主线程
 */
public final class SkyIslandClouds {
    /** 薄云，见暮色森林 {@code TFBlocks.WISPY_CLOUD} */
    public static final ResourceLocation WISPY_CLOUD =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "wispy_cloud");

    /** 空的密度梯度从 y 140 起才可能为正，再低就是地面 */
    public static final int ISLAND_MIN_TOP_Y = 140;
    /** 往下最多看到这里，超过这个深度的列一定不是薄片 */
    public static final int ISLAND_SCAN_FLOOR_Y = 128;
    /** 云体横向最多比岛宽几格 */
    public static final int VEIL_SPREAD = 4;
    /** 云体最多垂到岛底下面几格 */
    public static final int VEIL_DEPTH = 9;
    /** 距离衰减半径，超过这个距离不再有云 */
    public static final double VEIL_RADIUS = 6.5D;
    /** 噪声块的密度门槛，越大云越稀 */
    public static final double VEIL_THRESHOLD = 0.55D;
    /** 厚度达到这个值的岛列才会被云啃掉最底一层；薄片一格都不动 */
    public static final int ERODE_MIN_THICKNESS = 3;
    /** 最底一层被换成云的概率上限 */
    public static final double ERODE_CHANCE = 0.35D;

    private static final int SIZE = 16;
    private static final int COLUMNS = SIZE * SIZE;
    private static final int MAX_THICKNESS = 32;

    private static volatile BlockState cloud;
    private static volatile boolean cloudMissing;
    private static volatile long noiseSeed;
    private static volatile ImprovedNoise noise;

    private SkyIslandClouds() {}

    @FunctionalInterface
    public interface BlockSetter {
        void set(int x, int y, int z, BlockState state);
    }

    @FunctionalInterface
    public interface IslandProbe {
        boolean isIsland(int x, int y, int z);
    }

    /** 在 {@code FEATURES} 末尾调用，区块生成线程 */
    public static void process(WorldGenLevel level, ChunkAccess chunk) {
        BlockState state = cloudState();
        if (state == null) {
            return;
        }

        int maxBuildY = level.getMaxBuildHeight() - 1;
        int scanFloorY = Math.max(ISLAND_SCAN_FLOOR_Y, level.getMinBuildHeight());
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();

        int[] tops = new int[COLUMNS];
        boolean any = false;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, originX + x, originZ + z);
                tops[z * SIZE + x] = top;
                if (top >= ISLAND_MIN_TOP_Y && top <= maxBuildY) {
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }

        decorate(level, (x, y, z, set) -> level.setBlock(new BlockPos(x, y, z), set, Block.UPDATE_CLIENTS),
            originX, originZ, tops, state, SkyIslandDensity::isIsland, level.getSeed(),
            ISLAND_MIN_TOP_Y, maxBuildY, scanFloorY, level.getMinBuildHeight(), maxBuildY);
    }

    /**
     * 纯几何部分：找岛列、按连通块分气团、在边缘与凹陷处起团、最底一层概率侵蚀
     *
     * <p>可见性只给 GameTest 直接喂合成地形
     *
     * @param topY  256 个列顶世界坐标，按 {@code z * 16 + x} 排；没有岛的列给 -1
     * @param probe 空岛判据；测试里换成固定答案，生产里是 {@link SkyIslandDensity}
     * @return 写入的云方块数
     */
    public static int decorate(BlockGetter reader, BlockSetter setter, int originX, int originZ,
                               int[] topY, BlockState cloudState, IslandProbe probe, long seed,
                               int minTopY, int maxTopY, int scanFloorY, int minBuildY, int maxBuildY) {
        if (topY.length != COLUMNS) {
            throw new IllegalArgumentException("topY must hold " + COLUMNS + " entries");
        }

        ImprovedNoise field = noise(seed);
        int[] tops = new int[COLUMNS];
        int[] bottoms = new int[COLUMNS];
        int[] thickness = new int[COLUMNS];
        boolean[] island = new boolean[COLUMNS];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int index = 0; index < COLUMNS; index++) {
            int top = topY[index];
            if (top < minTopY || top > maxTopY) {
                continue;
            }
            int x = originX + (index % SIZE);
            int z = originZ + (index / SIZE);
            if (!probe.isIsland(x, top - 1, z)) {
                continue;
            }

            int y = top;
            int count = 0;
            while (y >= scanFloorY && count < MAX_THICKNESS) {
                cursor.set(x, y, z);
                if (reader.getBlockState(cursor).isAir()) {
                    break;
                }
                count++;
                y--;
            }
            // 扫描撞到下限说明这一列一直实心到底，是地面而不是悬空片
            if (count == 0 || y + 1 <= scanFloorY) {
                continue;
            }
            island[index] = true;
            tops[index] = top;
            bottoms[index] = y + 1;
            thickness[index] = count;
        }

        int[] parent = new int[COLUMNS];
        for (int index = 0; index < COLUMNS; index++) {
            parent[index] = island[index] ? index : -1;
        }
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                if (!island[index]) {
                    continue;
                }
                if (x > 0 && island[index - 1]) {
                    union(parent, index, index - 1);
                }
                if (z > 0 && island[index - SIZE]) {
                    union(parent, index, index - SIZE);
                }
            }
        }

        int placed = 0;
        boolean[] done = new boolean[COLUMNS];
        List<Integer> members = new ArrayList<>();
        for (int root = 0; root < COLUMNS; root++) {
            if (!island[root] || done[root]) {
                continue;
            }
            members.clear();
            for (int index = root; index < COLUMNS; index++) {
                if (island[index] && find(parent, index) == find(parent, root)) {
                    members.add(index);
                    done[index] = true;
                }
            }
            placed += veilComponent(reader, setter, cloudState, field, members,
                bottoms, thickness, originX, originZ, minBuildY, maxBuildY);
        }
        return placed;
    }

    /**
     * 一块岛：按「距离衰减 + 噪声」逐格决定放不放
     *
     * <p>噪声用世界坐标，所以形状有机、跨区块一致
     *
     * @return 写入的方块数
     */
    private static int veilComponent(BlockGetter reader, BlockSetter setter, BlockState cloudState,
                                     ImprovedNoise field, List<Integer> members,
                                     int[] bottoms, int[] thickness, int originX, int originZ,
                                     int minBuildY, int maxBuildY) {
        int placed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index : members) {
            int columnX = originX + index % SIZE;
            int columnZ = originZ + index / SIZE;
            int bottom = bottoms[index];
            for (int dy = 1; dy <= VEIL_DEPTH; dy++) {
                int targetY = bottom - dy;
                if (targetY < minBuildY || targetY > maxBuildY) {
                    continue;
                }
                for (int dx = -VEIL_SPREAD; dx <= VEIL_SPREAD; dx++) {
                    for (int dz = -VEIL_SPREAD; dz <= VEIL_SPREAD; dz++) {
                        int targetX = columnX + dx;
                        int targetZ = columnZ + dz;
                        double distance = Math.sqrt(dx * dx + dz * dz + (dy * 0.9D) * (dy * 0.9D));
                        double falloff = 1.0D - distance / VEIL_RADIUS;
                        if (falloff <= 0.0D) {
                            continue;
                        }
                        cursor.set(targetX, targetY, targetZ);
                        if (!reader.getBlockState(cursor).isAir()) {
                            continue;
                        }
                        double clump = field.noise(targetX / 6.0D, targetY / 5.0D, targetZ / 6.0D);
                        if (falloff * 0.9D + 0.55D * clump > VEIL_THRESHOLD) {
                            setter.set(targetX, targetY, targetZ, cloudState);
                            placed++;
                        }
                    }
                }
            }
            // 最底一层按噪声概率换成云：厚度不够就一格都不动，保证岛体至少留两格
            if (thickness[index] >= ERODE_MIN_THICKNESS) {
                double bite = 0.5D + 0.5D * field.noise(columnX / 8.0D, 3.0D, columnZ / 8.0D);
                if (bite < ERODE_CHANCE) {
                    setter.set(columnX, bottom, columnZ, cloudState);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static void union(int[] parent, int left, int right) {
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

    /** 位置哈希，只用来打散顺序与取值，不追求统计性质 */
    private static long mix(long seed, int x, int y, int z) {
        long hash = seed ^ ((long) x * 0x9E3779B97F4A7C15L);
        hash ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        hash ^= (long) y * 0x165667B19E3779F9L;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        return hash;
    }

    private static ImprovedNoise noise(long seed) {
        ImprovedNoise current = noise;
        if (current != null && noiseSeed == seed) {
            return current;
        }
        current = new ImprovedNoise(RandomSource.create(seed));
        noiseSeed = seed;
        noise = current;
        return current;
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
