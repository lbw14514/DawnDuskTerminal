package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.CanocanItem;
import com.dawnduskterminal.item.CarminiteGhastMeatItem;
import com.dawnduskterminal.item.CarminiteStaffItem;
import com.dawnduskterminal.item.Experiment114Item;
import com.dawnduskterminal.item.GhastPaintingItem;
import com.dawnduskterminal.item.ScarletMolybdArmorItem;
import com.dawnduskterminal.item.StringSpawnEggItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredItem<Item> HYDRA_TROPHY = ITEMS.registerSimpleItem(
        "hydra_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(1));

    public static final DeferredItem<Item> UR_GHAST_TROPHY = ITEMS.registerSimpleItem(
        "ur_ghast_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(1));

    public static final DeferredItem<Item> SNOW_QUEEN_TROPHY = ITEMS.registerSimpleItem(
        "snow_queen_trophy",
        new Item.Properties().rarity(Rarity.RARE).fireResistant().stacksTo(1));

    /** 恶魂画 #1 的物品形态。没有配方，也不会跟原版画物品互通，见 {@link GhastPaintingItem} */
    public static final DeferredItem<GhastPaintingItem> PAINTING_GHAST_1 = ITEMS.register(
        "painting_ghast_1",
        () -> new GhastPaintingItem(new Item.Properties()));

    public static final DeferredItem<CanocanItem> CANOCAN = ITEMS.register(
        "canocan",
        () -> new CanocanItem(new Item.Properties()));
    
    public static final DeferredItem<BucketItem> PORTAL_BUCKET = ITEMS.register(
        "portal_bucket",
        () -> new BucketItem(ModFluids.PORTAL_FLUID.get(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<Item> ENCASED_DUST = ITEMS.registerSimpleItem(
        "encased_dust",
        new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(1));

    public static final DeferredItem<Item> RAW_SCARLET_MOLYBDENUM = ITEMS.registerSimpleItem(
        "rawscarletmolybdenum", new Item.Properties());

    public static final DeferredItem<Item> GARNET = ITEMS.registerSimpleItem(
        "garnet", new Item.Properties());

    public static final DeferredItem<Item> SKY_SLICES = ITEMS.registerSimpleItem(
        "sky_slices", new Item.Properties());

    public static final DeferredItem<Item> OCHRE = ITEMS.registerSimpleItem(
        "ochre", new Item.Properties());

    public static final DeferredItem<Item> CANOPY_FROST_BALL = ITEMS.registerSimpleItem(
        "canopy_frost_ball", new Item.Properties());

    public static final DeferredItem<Item> ILLUSTRIOUS_POWDER = ITEMS.registerSimpleItem(
        "illustrious_powder", new Item.Properties());

    public static final DeferredItem<Item> CARID = ITEMS.registerSimpleItem(
        "carid", new Item.Properties());

    public static final DeferredItem<Item> AOI = ITEMS.registerSimpleItem(
        "aoi", new Item.Properties());

    public static final DeferredItem<Item> CANOPY_INGOT = ITEMS.registerSimpleItem(
        "canopy_ingot", new Item.Properties());

    public static final DeferredItem<Item> DIRAC = ITEMS.registerSimpleItem(
        "dirac", new Item.Properties());

    public static final DeferredItem<Item> MIXED_CARMINITE = ITEMS.registerSimpleItem(
        "mixed_carminite", new Item.Properties());

    public static final DeferredItem<Item> CARMINITE_INGOT = ITEMS.registerSimpleItem(
        "carminite_ingot", new Item.Properties());

    public static final DeferredItem<Item> SCARLET_MOLYBDENUM_INGOT = ITEMS.registerSimpleItem(
        "scarlet_molybdenum_ingot", new Item.Properties());

    public static final DeferredItem<Item> HUI_INGOT = ITEMS.registerSimpleItem(
        "hui_ingot", new Item.Properties());

    public static final DeferredItem<Item> DIRAC_INGOT = ITEMS.registerSimpleItem(
        "dirac_ingot", new Item.Properties());

    // 生肉照腐肉那一套：吃下大概率给饥饿，已经饥饿时还会加重并附带中毒。熟肉是普通食物
    private static final FoodProperties RAW_GHAST_MEAT = new FoodProperties.Builder()
        .nutrition(2)
        .saturationModifier(0.3F)
        .build();

    private static final FoodProperties COOKED_GHAST_MEAT = new FoodProperties.Builder()
        .nutrition(8)
        .saturationModifier(0.8F)
        .build();

    public static final DeferredItem<Item> CARMINITE_GHAST_LEATHER = ITEMS.registerSimpleItem(
        "carminite_ghast_leather", new Item.Properties());

    public static final DeferredItem<Item> CARMINITED_LEATHER = ITEMS.registerSimpleItem(
        "carminited_leather", new Item.Properties());

    public static final DeferredItem<CarminiteGhastMeatItem> CARMINITE_GHAST_MEAT = ITEMS.register(
        "carminite_ghast_meat",
        () -> new CarminiteGhastMeatItem(new Item.Properties().food(RAW_GHAST_MEAT)));

    public static final DeferredItem<Item> COOKED_CARMIN_GHAST_MEAT = ITEMS.register(
        "cooked_carmin_ghast_meat",
        () -> new Item(new Item.Properties().food(COOKED_GHAST_MEAT)));

    /** 权杖的弹药，也是合成材料 */
    public static final DeferredItem<Item> CARMINITE_BALL = ITEMS.registerSimpleItem(
        "carminite_ball", new Item.Properties().rarity(Rarity.UNCOMMON));

    /** 「弦」的掉落物，贴图来自模型包里的 string.png */
    public static final DeferredItem<Item> STRING = ITEMS.registerSimpleItem(
        "string", new Item.Properties());

    /**
     * 高塔恶魂的刷怪蛋
     *
     * <p>走 NeoForge 的 {@link DeferredSpawnEggItem}：原版 {@code SpawnEggItem} 的构造器要一个现成的
     * {@code EntityType}，而物品注册跑在实体类型之前；这个子类收的是 {@code Supplier}，
     * 第一次真正取值（放生、刷怪箱、创造栏取色）时注册表已经就绪，同时它自己把发射器行为与
     * 两层染色的注册也接上了，不用另写客户端代码
     *
     * <p>两个颜色就是 {@code template_spawn_egg} 的两层染色：底与斑点。取的是高塔恶魂贴图上的
     * 暮紫与骨白，弦取的是它脚下传送门流体的紫（{@code 0x7F159E}，见 {@code PortalFluidExtensions}）
     * 与流体上那层浅色高光
     */
    public static final DeferredItem<DeferredSpawnEggItem> TOWER_GHAST_SPAWN_EGG = ITEMS.register(
        "tower_ghast_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.TOWER_GHAST, 0x4A3B5C, 0xD8C6A0,
            new Item.Properties()));

    /** 只肯放在传送门流体边上，见 {@link StringSpawnEggItem} */
    public static final DeferredItem<StringSpawnEggItem> STRING_SPAWN_EGG = ITEMS.register(
        "string_spawn_egg",
        () -> new StringSpawnEggItem(ModEntities.STRING, 0x7F159E, 0xE6D5F5,
            new Item.Properties()));

    public static final DeferredItem<CarminiteStaffItem> CARMINITE_MOLYBDENUM_REACTOR_STAFF = ITEMS.register(
        "carminite_molybdenum_reactor_staff",
        () -> new CarminiteStaffItem(new Item.Properties()
            .durability(128)
            .attributes(CarminiteStaffItem.createAttributes())));

    // T2 护甲，数值在 ModArmorMaterials 里，四件穿齐时每件自己续生命恢复
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_HELMET = armor(
        "scarlet_molybd_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_CHESTPLATE = armor(
        "scarlet_molybd_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_LEGGINGS = armor(
        "scarlet_molybd_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ScarletMolybdArmorItem> SCARLET_MOLYBD_BOOTS = armor(
        "scarlet_molybd_boots", ArmorItem.Type.BOOTS);

    public static final DeferredItem<BlockItem> CHRONO_CRUST = ITEMS.registerSimpleBlockItem(ModBlocks.CHRONO_CRUST);

    public static final DeferredItem<BlockItem> EXPERIMENT_001 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_001);

    /** 试验物品 004：那台熔炉的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_004} */
    public static final DeferredItem<BlockItem> EXPERIMENT_004 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_004);

    /** 试验物品 019：那丛植物的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_019} */
    public static final DeferredItem<BlockItem> EXPERIMENT_019 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_019);

    /** 试验物品 051：拆解台那台机器的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_051} */
    public static final DeferredItem<BlockItem> EXPERIMENT_051 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_051);

    /** 试验物品 052：那颗方雷的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_052} */
    public static final DeferredItem<BlockItem> EXPERIMENT_052 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_052);

    /** 试验物品 065：那台让人打不出伤害的机器的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_065} */
    public static final DeferredItem<BlockItem> EXPERIMENT_065 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_065);

    /** 试验物品 069：那台让方块更新不处理的机器的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_069} */
    public static final DeferredItem<BlockItem> EXPERIMENT_069 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_069);

    /** 试验物品 076：那个每个面每一刻都在换贴图的异常方块的物品形态，放下来就是 {@link ModBlocks#EXPERIMENT_076} */
    public static final DeferredItem<BlockItem> EXPERIMENT_076 = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIMENT_076);

    /**
     * 试验物品 104 一口的数值：10 与 0.6，也就是 10 点饥饿、饱食度 12.0，等于两块烤马铃薯
     * （一块烤马铃薯是 5 与 0.6，即 5 点饥饿、饱食度 6.0）
     *
     * <p>一个 104 能吃两口，两口合起来 20 点饥饿、饱食度 24.0，等于四块烤马铃薯
     */
    private static final FoodProperties EXPERIMENT_104_BITE = new FoodProperties.Builder()
        .nutrition(10)
        .saturationModifier(0.6F)
        .build();

    /**
     * 试验物品 104 的半块：第二口，嚼完东西就没了
     *
     * <p>必须写在 {@link #EXPERIMENT_104} 前面：104 的食物属性在注册回调里现取半块的物品栈
     * （{@code usingConvertsTo}），那时半块得已经在注册表里，而 {@code DeferredRegister} 是照插入顺序注册的
     */
    public static final DeferredItem<Item> EXPERIMENT_104_HALF = ITEMS.register(
        "experiment_104_half",
        () -> new Item(new Item.Properties().stacksTo(1).food(EXPERIMENT_104_BITE)));

    /**
     * 试验物品 104：一个能吃两口，不可堆叠
     *
     * <p>第一口把剩下那半换成 {@link #EXPERIMENT_104_HALF}：{@code usingConvertsTo} 与原版蜂蜜瓶换玻璃瓶
     * 走同一条路（{@code Player#eat} 里换栈），第二口连半块一起嚼掉
     */
    public static final DeferredItem<Item> EXPERIMENT_104 = ITEMS.register(
        "experiment_104",
        () -> new Item(new Item.Properties().stacksTo(1).food(new FoodProperties.Builder()
            .nutrition(10)
            .saturationModifier(0.6F)
            .usingConvertsTo(EXPERIMENT_104_HALF)
            .build())));

    /**
     * 试验物品 114：能吃能摆
     *
     * <p>食物数值 32 与 0.3 就是 8 份暮色森林试验物品 115（一份 4 与 0.3），摆下来的那一块每片也是 4 与 0.3，
     * 所以吃一个 114、吃光一块摆着的 114、吃 8 个 115 是同一件事
     */
    public static final DeferredItem<Experiment114Item> EXPERIMENT_114 = ITEMS.register(
        "experiment_114",
        () -> new Experiment114Item(
            ModBlocks.EXPERIMENT_114.get(),
            new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(Experiment114Item.NUTRITION)
                .saturationModifier(Experiment114Item.SATURATION_MODIFIER)
                .build())));

    private static DeferredItem<ScarletMolybdArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ScarletMolybdArmorItem(
            ModArmorMaterials.SCARLET_MOLYBDENUM,
            type,
            new Item.Properties().durability(type.getDurability(37))));
    }

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
