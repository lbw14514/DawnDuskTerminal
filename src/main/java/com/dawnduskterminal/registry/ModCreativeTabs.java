package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dawnduskterminal.main"))
            .icon(() -> new ItemStack(ModItems.HYDRA_TROPHY.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.HYDRA_TROPHY.get());
                output.accept(ModItems.UR_GHAST_TROPHY.get());
                output.accept(ModItems.SNOW_QUEEN_TROPHY.get());
                output.accept(ModItems.CHRONO_CORE.get());
                output.accept(ModItems.PORTAL_BUCKET.get());
                output.accept(ModBlocks.PORTAL_FLUID.get());
                output.accept(ModBlocks.SKY_SOIL.get());
                output.accept(ModBlocks.CHRONO_CRUST.get());
            })
            .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
