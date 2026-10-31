package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import twilightforest.init.TFBlocks;

/**
 * 通过 {@link FluidInteractionRegistry} 注册流体接触反应，原版 {@code 水 + 岩浆 -> 圆石 / 黑曜石} 走的
 * 是同一个钩子
 *
 * <p>每条反应都是有方向的，与原版一致：模组流体（portal_fluid / enthu_fluid）是<em>发动方</em>，一旦
 * 旁边出现匹配的邻居就被替换成产物方块，正如岩浆碰到水会变成黑曜石或圆石。邻居流体保持不动，所以一次
 * 接触只产出一个方块
 *
 * <p>反应由 {@code LiquidBlock#onPlace} 与 {@code LiquidBlock#neighborChanged} 触发（接触立即生效，
 * 不等随机 tick），键是 {@code FluidType}，源与流动共用同一个，所以从不区分源与流动
 */
public final class FluidReactions {
    private FluidReactions() {}

    /** 在流体类型注册表填充完毕后，把所有反应注册一次 */
    public static void register() {
        // 不稳定液体（portal_fluid）
        FluidInteractionRegistry.addInteraction(
            ModFluids.PORTAL_FLUID_TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.WATER_TYPE.value(), ModTerrainBlocks.FRAGILE_CANOPY.get().defaultBlockState()));
        FluidInteractionRegistry.addInteraction(
            ModFluids.PORTAL_FLUID_TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.LAVA_TYPE.value(), ModTerrainBlocks.CHAOS_STONE.get().defaultBlockState()));
        FluidInteractionRegistry.addInteraction(
            ModFluids.PORTAL_FLUID_TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                ModScorchingTwilight.TYPE.value(), ModTerrainBlocks.CANOPY_STONE.get().defaultBlockState()));

        // 热情暮光（enthu_fluid）
        FluidInteractionRegistry.addInteraction(
            ModScorchingTwilight.TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.WATER_TYPE.value(), auroraBlock().defaultBlockState()));
        FluidInteractionRegistry.addInteraction(
            ModScorchingTwilight.TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.LAVA_TYPE.value(), ModTerrainBlocks.BLAZING_BLOCK.get().defaultBlockState()));
    }

    /** 热情的暮光加水之后得到的方块：暮色森林的极光方块。也是测试的期望值来源 */
    public static Block auroraBlock() {
        return TFBlocks.AURORA_BLOCK.get();
    }
}
