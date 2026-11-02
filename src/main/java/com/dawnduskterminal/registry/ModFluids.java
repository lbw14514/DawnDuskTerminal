package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 传送门流体在流动行为与（客户端）渲染行为上都照搬 {@code minecraft:water}
 *
 * <ul>
 *   <li><b>流动</b>：{@link #properties()} 原样带着水的数值
 *       （{@code tickRate 5}、{@code slopeFindDistance 4}、{@code levelDecreasePerBlock 1}、
 *       {@code explosionResistance 100.0F}，见 {@code WaterFluid} / {@code Fluids.waterProperties}），
 *       而且两个流体都不再覆写 {@code tick}。真正负责扩散的 {@code FlowingFluid.tick} 因此用的是继承
 *       实现，流体铺开 下落 重新找平都和水一模一样。这一点很关键，因为本流体就是晨昏线维度的
 *       {@code default_fluid}（海洋 含水层 泉 湖全部按传送门流体生成）</li>
 *   <li><b>渲染</b>：静止与流动贴图带真实 alpha 通道
 *       （{@code portal_fluid_still.png} / {@code portal_fluid_flow.png} 统一是 230/255），所以流体
 *       必须画在半透明区块层。该注册在 {@link com.dawnduskterminal.client.DdtClient} 里，对照原版给
 *       {@code Fluids.WATER} / {@code Fluids.FLOWING_WATER} 的 {@code ItemBlockRenderTypes} 条目</li>
 * </ul>
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, DawnDuskTerminal.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> PORTAL_FLUID_TYPE = FLUID_TYPES.register(
            "portal_fluid",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.dawnduskterminal.portal_fluid")
                    .density(1600)
                    .viscosity(1200)
                    .lightLevel(15)
                    .canSwim(true)
                    .canDrown(false)
                    .canPushEntity(true)
                    .canConvertToSource(false)
                    .canHydrate(false)
                    .motionScale(0.014D)
                    .supportsBoating(false)
                    .rarity(Rarity.UNCOMMON)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PORTAL_FLUID =
            FLUIDS.register("portal_fluid", () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> PORTAL_FLOWING =
            FLUIDS.register("flowing_portal_fluid", () -> new BaseFlowingFluid.Flowing(properties()));

    private ModFluids() {}

    /**
     * 原版水的流动属性，逐值照拄：
     *
     * <pre>
     * FlowingFluid.Properties waterProperties = new FlowingFluid.Properties(FluidTypes.WATER, FLOWING_WATER, WATER)
     *     .block(Blocks.WATER)
     *     .bucket(Items.WATER_BUCKET)
     *     .tickRate(5)
     *     .slopeFindDistance(4)
     *     .levelDecreasePerBlock(1)
     *     .explosionResistance(100.0F);
     * </pre>
     *
     * <p>有意不照拄水的地方：
     * <ul>
     *   <li>{@code canConvertToSource}：{@code WaterFluid} 跟随水源游戏规则，但传送门流体的
     *       {@link FluidType} 保持 {@code canConvertToSource(false)}，所以两个相邻源不会生出第三个
     *       （不会无限传送门流体）</li>
     *   <li>{@code canBeReplacedWith}：{@code BaseFlowingFluid} 的实现是拿流体和自己比，而不是判断
     *       {@code #minecraft:water}，所以从上方落下的外来水不会被吞掉</li>
     * </ul>
     */
    public static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(PORTAL_FLUID_TYPE, PORTAL_FLUID, PORTAL_FLOWING)
                .block(ModBlocks.PORTAL_FLUID)
                .bucket(ModItems.PORTAL_BUCKET)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
