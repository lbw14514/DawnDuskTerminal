package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
        "enthu_fluid",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.dawnduskterminal.enthu_fluid")
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
        FLUIDS.register("enthu_fluid", com.dawnduskterminal.fluid.ScorchingTwilightFluid.Source::new);

    public static final DeferredHolder<net.minecraft.world.level.material.Fluid,
        com.dawnduskterminal.fluid.ScorchingTwilightFluid.Flowing> FLOWING =
        FLUIDS.register("flowing_enthu_fluid", com.dawnduskterminal.fluid.ScorchingTwilightFluid.Flowing::new);

    public static final DeferredBlock<LiquidBlock> BLOCK = BLOCKS.registerBlock(
        "enthu_fluid",
        props -> new com.dawnduskterminal.block.ScorchingTwilightBlock(SOURCE.get(), props),
        // 原样复用 minecraft:lava 的方块属性（mapColor 火焰色、可替换、无碰撞、randomTicks、强度 100、
        // 光照 15、pushReaction DESTROY、无战利品表、liquid、EMPTY 音效）
        // 岩浆式的*行为*（点燃生物 引燃方块 岩浆粒子）改在 ScorchingTwilightFluid 与
        // ScorchingTwilightBlock 里去掉了
        BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LAVA));

    public static final DeferredItem<BucketItem> BUCKET = ITEMS.register(
        "enthu_fluid_bucket",
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
