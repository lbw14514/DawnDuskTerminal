package com.dawnduskterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把暮色森林的树叶染色搬到晨昏线维度的群系副本上
 *
 * <p>暮色森林用 ASM 改写 {@code BiomeColors.FOLIAGE_COLOR_RESOLVER}，再按它自己的群系 ID 查表
 * 上色。晨昏线的副本在 {@code dawnduskterminal:*} 命名空间下，查表必然落空，树叶只会用普通的
 * 树叶色。本类复刻暮色森林的两套树叶算法，由
 * {@link com.dawnduskterminal.mixin.BiomeColorsFoliageMixin} 接入
 */
public final class ChronoFoliageColors {
    private static final ResourceKey<Biome> JUNGLE = biomeKey("jungle");
    private static final ResourceKey<Biome> DARK_FOREST = biomeKey("dark_forest");

    private static final Map<Biome, ResourceKey<Biome>> KEY_CACHE = new ConcurrentHashMap<>();

    private ChronoFoliageColors() {}

    public static int resolve(int original, Biome biome, double x, double z) {
        ResourceKey<Biome> key = key(biome);
        if (JUNGLE.equals(key)) {
            return enchanted(original, (int) x, (int) z);
        }
        if (DARK_FOREST.equals(key)) {
            // 暮色森林的 darkForestCenterFoliage 恒返回 0xE94E14：它的分支条件
            // `(noise + 1) / 2 < -0.1` 永远不成立，噪声选不到另一种颜色
            return 0xE94E14;
        }
        return original;
    }

    private static ResourceKey<Biome> biomeKey(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dawnduskterminal", name));
    }

    private static ResourceKey<Biome> key(Biome biome) {
        ResourceKey<Biome> cached = KEY_CACHE.get(biome);
        if (cached != null) {
            return cached;
        }
        // 取色跑在区块网格重建的工作线程上，主线程随时可能把 level 置空。先取到局部变量再判空与解引用，
        // 否则判空和解引用之间会撞上 NPE 打断区块编译
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        ResourceKey<Biome> key = level.registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getResourceKey(biome)
            .orElse(null);
        if (key != null) {
            KEY_CACHE.put(biome, key);
        }
        return key;
    }

    /** 断开连接时清空 旧世界的 Biome 实例作为 key 会一直留住整张缓存 */
    public static void clearCache() {
        KEY_CACHE.clear();
    }

    /** 暮色森林的 {@code BiomeColorAlgorithms.enchanted}：按坐标在整个群系上做蓝色渐变 */
    private static int enchanted(int original, int x, int z) {
        BlockPos center = nearestCenterXZ(x >> 4, z >> 4);
        int cx = center.getX();
        int cz = center.getZ();
        int dist = (int) Mth.sqrt((cx - x) * (cx - x) + (cz - z) * (cz - z));
        int color = dist * 16;
        color %= 512;
        if (color > 255) {
            color = 511 - color;
        }
        color = 255 - color;
        return (original & 0xFFFF00) + color;
    }

    /** 暮色森林的 {@code LegacyLandmarkPlacements.getNearestCenterXZ}：确定性的、不依赖种子的栅格中心 */
    private static BlockPos nearestCenterXZ(int chunkX, int chunkZ) {
        int regionX = (chunkX + 8) >> 4;
        int regionZ = (chunkZ + 8) >> 4;
        long seed = regionX * 3129871L ^ regionZ * 116129781L;
        seed = seed * seed * 42317861L + seed * 7L;
        int num0 = (int) (seed >> 12 & 3L);
        int num1 = (int) (seed >> 15 & 3L);
        int num2 = (int) (seed >> 18 & 3L);
        int num3 = (int) (seed >> 21 & 3L);
        int centerX = 8 + num0 - num1;
        int centerZ = 8 + num2 - num3;
        int ccz = regionZ >= 0
            ? (regionZ * 16 + centerZ - 8) * 16 + 8
            : (regionZ * 16 + (16 - centerZ) - 8) * 16 + 9;
        int ccx = regionX >= 0
            ? (regionX * 16 + centerX - 8) * 16 + 8
            : (regionX * 16 + (16 - centerX) - 8) * 16 + 9;
        return new BlockPos(ccx, 0, ccz);
    }
}
