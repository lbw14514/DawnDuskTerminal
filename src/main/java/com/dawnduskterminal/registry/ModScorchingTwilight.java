package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModScorchingTwilight {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, DawnDuskTerminal.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, DawnDuskTerminal.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> TYPE = FLUID_TYPES.register(
        "scorching_twilight",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.dawnduskterminal.scorching_twilight")
            .density(3000)
            .viscosity(6000)
            .temperature(1300)
            .lightLevel(15)
            .canSwim(false)
            .canDrown(false)
            .canPushEntity(true)
            .canConvertToSource(false)
            .canHydrate(false)
            .motionScale(0.0023D)
            .supportsBoating(false)
            .rarity(Rarity.UNCOMMON)
            .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL_LAVA)
            .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA)));

    public static final DeferredHolder<net.minecraft.world.level.material.Fluid,
        com.dawnduskterminal.fluid.ScorchingTwilightFluid.Source> SOURCE =
        FLUIDS.register("scorching_twilight", com.dawnduskterminal.fluid.ScorchingTwilightFluid.Source::new);

    public static final DeferredHolder<net.minecraft.world.level.material.Fluid,
        com.dawnduskterminal.fluid.ScorchingTwilightFluid.Flowing> FLOWING =
        FLUIDS.register("flowing_scorching_twilight", com.dawnduskterminal.fluid.ScorchingTwilightFluid.Flowing::new);

    public static final DeferredBlock<LiquidBlock> BLOCK = BLOCKS.registerBlock(
        "scorching_twilight",
        props -> new com.dawnduskterminal.block.ScorchingTwilightBlock(SOURCE.get(), props),
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .replaceable()
            .noCollission()
            .randomTicks()
            .strength(100.0F)
            .lightLevel(state -> 15)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY));

    public static final DeferredItem<BucketItem> BUCKET = ITEMS.register(
        "scorching_twilight_bucket",
        () -> new BucketItem(SOURCE.get(), new net.minecraft.world.item.Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)));

    private ModScorchingTwilight() {}

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
