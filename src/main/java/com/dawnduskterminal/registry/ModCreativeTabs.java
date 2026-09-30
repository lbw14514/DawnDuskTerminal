package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
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
                accept(output, ModItems.HYDRA_TROPHY.get());
                accept(output, ModItems.UR_GHAST_TROPHY.get());
                accept(output, ModItems.SNOW_QUEEN_TROPHY.get());
                accept(output, ModItems.CHRONO_CORE.get());
                accept(output, ModItems.PORTAL_BUCKET.get());
                accept(output, ModScorchingTwilight.BUCKET.get());
                accept(output, ModItems.SKY_SOIL.get());
                accept(output, ModItems.CHRONO_CRUST.get());
            })
            .build());

    private static void accept(CreativeModeTab.Output output, ItemLike item) {
        if (item == null || item.asItem() == Items.AIR) {
            DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal creative entry skipped, no item is registered for {}", item);
            return;
        }
        output.accept(item);
    }

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
