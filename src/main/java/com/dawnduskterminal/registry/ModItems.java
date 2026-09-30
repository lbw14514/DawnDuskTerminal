package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredItem<Item> HYDRA_TROPHY = ITEMS.registerSimpleItem(
        "hydra_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(16));

    public static final DeferredItem<Item> UR_GHAST_TROPHY = ITEMS.registerSimpleItem(
        "ur_ghast_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(16));

    public static final DeferredItem<Item> SNOW_QUEEN_TROPHY = ITEMS.registerSimpleItem(
        "snow_queen_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(16));

    public static final DeferredItem<BucketItem> PORTAL_BUCKET = ITEMS.register(
        "portal_bucket",
        () -> new BucketItem(ModFluids.PORTAL_FLUID.get(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<Item> CHRONO_CORE = ITEMS.registerSimpleItem(
        "chrono_core",
        new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
