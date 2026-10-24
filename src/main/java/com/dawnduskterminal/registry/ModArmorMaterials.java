package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * 护甲材质
 *
 * <p>每个材质只声明数值与贴图层，具体物品由 {@link ModItems} 注册。贴图层按
 * {@code textures/models/armor/<路径>_layer_1.png}（外层，头 胸 脚）与
 * {@code _layer_2.png}（内层，腿）解析，两条路径都由 {@link ArmorMaterial.Layer} 拼出来
 *
 * <p>韧性与护甲一样都是「每件给多少」，设定里的 14 与 20 是四件合计，所以 T2 写 3.5、T3 写 5.0
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, DawnDuskTerminal.MOD_ID);

    /**
     * T2 绯色钼。四件合计 25 点护甲、14 韧性（每件 3.5），附魔值 15，耐久倍率 37
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SCARLET_MOLYBDENUM =
        ARMOR_MATERIALS.register("scarlet_molybdenum", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 5,
                ArmorItem.Type.CHESTPLATE, 9,
                ArmorItem.Type.LEGGINGS, 7,
                ArmorItem.Type.BOOTS, 4),
            15,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(ModItems.SCARLET_MOLYBDENUM_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("scarlet_molybdenum"))),
            3.5F,
            0.0F));

    /**
     * 天门松原木，对标铁：护甲 15 点、韧性 0、附魔值 9、耐久倍率 15
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SKYGATE_PINE =
        ARMOR_MATERIALS.register("skygate_pine", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 2,
                ArmorItem.Type.CHESTPLATE, 6,
                ArmorItem.Type.LEGGINGS, 5,
                ArmorItem.Type.BOOTS, 2),
            9,
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(ModTerrainBlocks.SKYGATE_PINE_LOG.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("skygate_pine"))),
            0.0F,
            0.0F));

    /** 苍穹锭，对标钻石 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CANOPY =
        ARMOR_MATERIALS.register("canopy", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 3,
                ArmorItem.Type.CHESTPLATE, 8,
                ArmorItem.Type.LEGGINGS, 6,
                ArmorItem.Type.BOOTS, 3),
            10,
            SoundEvents.ARMOR_EQUIP_DIAMOND,
            () -> Ingredient.of(ModItems.CANOPY_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("canopy"))),
            2.0F,
            0.0F));

    /** 砷铅铁锭，对标钻石 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CARMINITE =
        ARMOR_MATERIALS.register("carminite", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 3,
                ArmorItem.Type.CHESTPLATE, 8,
                ArmorItem.Type.LEGGINGS, 6,
                ArmorItem.Type.BOOTS, 3),
            10,
            SoundEvents.ARMOR_EQUIP_DIAMOND,
            () -> Ingredient.of(ModItems.CARMINITE_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("carminite"))),
            2.0F,
            0.0F));

    /** T2 狄拉克，数值与绯色钼同档，逃课材料的回报只体现在获取门槛上 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DIRAC =
        ARMOR_MATERIALS.register("dirac", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 5,
                ArmorItem.Type.CHESTPLATE, 9,
                ArmorItem.Type.LEGGINGS, 7,
                ArmorItem.Type.BOOTS, 4),
            15,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(ModItems.DIRAC_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("dirac"))),
            3.5F,
            0.0F));

    /** T3 䤧，毕业级：四件合计 30 点护甲、20 韧性（每件 5.0），附魔值 18，耐久倍率 40 */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HUI =
        ARMOR_MATERIALS.register("hui", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 6,
                ArmorItem.Type.CHESTPLATE, 11,
                ArmorItem.Type.LEGGINGS, 8,
                ArmorItem.Type.BOOTS, 5),
            18,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(ModItems.HUI_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("hui"))),
            5.0F,
            0.0F));

    /**
     * 试验物品 047~050 那套：护甲值与铁同级（2 / 6 / 5 / 2，合计 15）、每件 2 点韧性、附魔值 9、耐久倍率 15。
     * 维修材料是砷铅铁锭、绯红钼锭、䤧锭三样里的任意一样，{@code Ingredient.of} 收候选清单，命中一个就成立
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EXPERIMENT_ARMOR =
        ARMOR_MATERIALS.register("experiment_armor", () -> new ArmorMaterial(
            Map.of(
                ArmorItem.Type.HELMET, 2,
                ArmorItem.Type.CHESTPLATE, 6,
                ArmorItem.Type.LEGGINGS, 5,
                ArmorItem.Type.BOOTS, 2),
            9,
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(
                ModItems.CARMINITE_INGOT.get(),
                ModItems.SCARLET_MOLYBDENUM_INGOT.get(),
                ModItems.HUI_INGOT.get()),
            List.of(new ArmorMaterial.Layer(DawnDuskTerminal.id("experiment_armor"))),
            2.0F,
            0.0F));

    private ModArmorMaterials() {}

    public static void register(IEventBus bus) {
        ARMOR_MATERIALS.register(bus);
    }
}
