package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 钉住苍穹霜唯一不许有的原版雪行为：本格方块光照高于 11 时自己融化
 * （{@code SnowLayerBlock.randomTick}）
 *
 * <p>测试对苍穹霜层与同光照下的原版雪层跑服务器对随机方块所做的同一个调用
 * （{@link GameTestHelper#randomTick}）。原版雪是对照组：它要是也不融化，说明光照根本没到，断言就证明
 * 不了任何东西，所以那个方向也要断言
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class CanopyFrostMeltTests {
    /** 原版融化的条件是「方块光照严格高于 11」 */
    private static final int MELTING_LIGHT = 11;

    private static final BlockPos FROST = new BlockPos(2, 2, 2);
    private static final BlockPos VANILLA_SNOW = new BlockPos(4, 2, 2);
    /** 炽热方块是光照 15，放在每层下方一格 */
    private static final BlockPos FROST_LIGHT = new BlockPos(2, 1, 2);

    private static final BlockPos VANILLA_LIGHT = new BlockPos(4, 1, 2);

    @GameTest(template = "ddt_empty")
    public static void canopyFrostSurvivesTheLightThatMeltsVanillaSnow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 用 setBlockAndUpdate（即 flag 3），因为光源只有带邻居/光照更新放置时光照引擎才会得知；
        // GameTestHelper.setBlock 用的是 flag 2，光照会一直是 0
        level.setBlockAndUpdate(
                helper.absolutePos(FROST_LIGHT),
                ModTerrainBlocks.BLAZING_BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(
                helper.absolutePos(VANILLA_LIGHT),
                ModTerrainBlocks.BLAZING_BLOCK.get().defaultBlockState());
        helper.setBlock(FROST, ModTerrainBlocks.CANOPY_FROST.get());
        helper.setBlock(VANILLA_SNOW, Blocks.SNOW);

        // 光照引擎在服务器 tick 上跑，所以方块光照要过几个 tick 才到
        helper.runAfterDelay(10, () -> {
            BlockPos frostPos = helper.absolutePos(FROST);
            BlockPos snowPos = helper.absolutePos(VANILLA_SNOW);
            int frostLight = level.getBrightness(LightLayer.BLOCK, frostPos);
            int snowLight = level.getBrightness(LightLayer.BLOCK, snowPos);
            helper.assertTrue(
                    frostLight > MELTING_LIGHT && snowLight > MELTING_LIGHT,
                    "precondition: both layers must sit in block light > " + MELTING_LIGHT + ", got " + frostLight
                            + " and " + snowLight);

            helper.randomTick(VANILLA_SNOW);
            helper.assertFalse(
                    level.getBlockState(snowPos).is(Blocks.SNOW),
                    "control: vanilla snow must melt in this light, otherwise the assertion below proves nothing");

            helper.randomTick(FROST);
            helper.assertTrue(
                    level.getBlockState(frostPos).is(ModTerrainBlocks.CANOPY_FROST.get()),
                    "canopy frost must not melt in bright light");
            helper.succeed();
        });
    }
}
