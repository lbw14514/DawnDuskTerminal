package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.world.SkygateBranchTrunkPlacer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.LeaveVineDecorator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 天门松的形状约束：二十到三十格高、分枝铺在树干整段高度上、叶团是压扁的原版樱花叶团、允许挂藤
 *
 * <p>树是浮空种的，跟其它测试结构在竖直方向上错开，不会互相干扰
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SkygatePineTests {
    private static final ResourceLocation SKYGATE_PINE =
        ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "tree/skygate_pine");
    private static final int PLACEMENT_LIFT = 30;

    private static ConfiguredFeature<?, ?> feature(GameTestHelper helper) {
        Registry<ConfiguredFeature<?, ?>> registry = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> feature = registry.get(SKYGATE_PINE);
        helper.assertTrue(feature != null, "configured feature " + SKYGATE_PINE + " missing");
        helper.assertTrue(feature.config() instanceof TreeConfiguration, "skygate_pine must be a minecraft:tree feature");
        return feature;
    }

    /** JSON 必须真的解到自定义树干与压扁的樱花叶团，并带上挂藤装饰器 */
    @GameTest(template = "ddt_empty")
    public static void treeConfigUsesTheSkygateShapes(GameTestHelper helper) {
        TreeConfiguration config = (TreeConfiguration) feature(helper).config();
        helper.assertTrue(config.trunkPlacer instanceof SkygateBranchTrunkPlacer,
            "the trunk placer must be dawnduskterminal:skygate_branching, got " + config.trunkPlacer);
        helper.assertTrue(config.foliagePlacer instanceof CherryFoliagePlacer,
            "foliage must be shaped by minecraft:cherry_foliage_placer, got " + config.foliagePlacer);
        boolean hasVines = config.decorators.stream().anyMatch(decorator -> decorator instanceof LeaveVineDecorator);
        helper.assertTrue(hasVines, "skygate_pine must be allowed to grow vines, decorators were " + config.decorators);
        helper.succeed();
    }

    /** 种一棵，量树高与分枝高度分布 */
    @GameTest(template = "ddt_empty")
    public static void treeGrowsTwentyToThirtyWithBranchesAlongTheTrunk(GameTestHelper helper) {
        ConfiguredFeature<?, ?> feature = feature(helper);
        TreeConfiguration config = (TreeConfiguration) feature.config();
        BlockState log = config.trunkProvider.getState(RandomSource.create(0L), BlockPos.ZERO);

        BlockPos trunkBase = helper.absolutePos(BlockPos.ZERO).above(PLACEMENT_LIFT);
        helper.getLevel().setBlock(trunkBase.below(), Blocks.DIRT.defaultBlockState(), 3);
        boolean placed = feature.place(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
            RandomSource.create(0x5C6A7EL), trunkBase);
        helper.assertTrue(placed, "the skygate pine failed to place in open air");

        int height = 0;
        while (height < 80 && helper.getLevel().getBlockState(trunkBase.above(height)).is(log.getBlock())) {
            height++;
        }

        helper.assertTrue(height >= 20 && height <= 30, "skygate pine must be 20~30 blocks tall, got " + height);

        int trunkHeight = height;
        BlockState wood = ModTerrainBlocks.SKYGATE_PINE_WOOD.get().defaultBlockState();
        Set<Integer> branchHeights = new HashSet<>();
        int branchLogs = 0;
        int stackedLogs = 0;
        int strayLogs = 0;
        for (int dy = 0; dy <= trunkHeight; dy++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    BlockState state = helper.getLevel().getBlockState(trunkBase.offset(dx, dy, dz));
                    if (state.is(wood.getBlock())) {
                        branchLogs++;
                        branchHeights.add(dy);

                        if (helper.getLevel().getBlockState(trunkBase.offset(dx, dy + 1, dz)).is(wood.getBlock())) {
                            stackedLogs++;
                        }
                    } else if (state.is(log.getBlock())) {
                        // 枝端会露出原木横截面，所以离干的位置只准出现天门松木
                        strayLogs++;
                    }
                }
            }
        }

        helper.assertTrue(branchHeights.size() >= 4,
            "branches must reach at least four different trunk heights, got " + branchHeights);
        helper.assertTrue(branchHeights.stream().anyMatch(dy -> dy <= trunkHeight / 2),
            "branches must reach the lower half of the trunk, got " + branchHeights);
        helper.assertTrue(branchHeights.stream().anyMatch(dy -> dy >= trunkHeight * 2 / 3),
            "branches must reach the upper third of the trunk, got " + branchHeights);

        helper.assertTrue(branchLogs > 0, "no side branch was grown out of skygate_pine_wood");
        helper.assertTrue(strayLogs == 0,
            "branches must use skygate_pine_wood instead of the log, got " + strayLogs + " logs off the trunk");

        // 每根枝是一条三维直线：每步只落一块，离干的原木不会竖直叠在另一块上。
        // 逐格单轴步进加竖木填充的老写法在 45 度斜枝上会留下一串竖直叠块，会顶到这条断言
        helper.assertTrue(stackedLogs == 0,
            "side branches must stay one block thick, got " + stackedLogs + " vertically stacked logs off the trunk");
        helper.succeed();
    }

    /**
     * 去皮走 NeoForge 的数据图：映射写在
     * {@code data/neoforge/data_maps/block/strippables.json} 里（命名空间必须是 neoforge），
     * 代码侧只是注册一个 RotatedPillarBlock。数据图路径或 key 写错时加载器只会静默跳过这条映射，
     * 右键斧子什么都不发生，所以这里直接问 {@link AxeItem#getAxeStrippingState} 要结果，
     * 顺带确认轴属性跟着复制过去
     */
    @GameTest(template = "ddt_empty")
    public static void axeStripsTheLogKeepingTheAxis(GameTestHelper helper) {
        BlockState log = ModTerrainBlocks.SKYGATE_PINE_LOG.get().defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);

        BlockState stripped = AxeItem.getAxeStrippingState(log);

        helper.assertTrue(stripped != null,
            "the strippables data map must map skygate_pine_log to a stripped block, AxeItem returned null");
        helper.assertTrue(stripped.is(ModTerrainBlocks.STRIPPED_SKYGATE_PINE_LOG.get()),
            "skygate_pine_log must strip into stripped_skygate_pine_log, got " + stripped);
        helper.assertTrue(stripped.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X,
            "stripping must keep the log axis, got " + stripped);
        helper.succeed();
    }
}
