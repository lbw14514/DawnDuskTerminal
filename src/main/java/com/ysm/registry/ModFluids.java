package com.ysm.registry;

import com.ysm.Ysm;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Rarity;
import net.minecraft.sounds.SoundActions;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Ysm.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, Ysm.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> PORTAL_FLUID_TYPE = FLUID_TYPES.register(
        "portal_fluid",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.ysm.portal_fluid")
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
