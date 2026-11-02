package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.ScarletMolybdArmorItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 六套材料各五件工具加四件护甲，一共 54 件
 *
 * <p>没有为每件写一个字段：除了给创造模式物品栏按套枚举，没有别的地方按名字取单件，所以这里按套存放，
 * 与 {@link ModChronoOres} 存那批矿石的做法一致
 *
 * <p>T1 三套的数值照原版对应等级，攻速与攻击力的差别只出现在斧与锄上（原版铁斧 6.0/-3.1、铁锄 -2.0/-1.0，
 * 钻石斧 5.0/-3.0、钻石锄 -3.0/0.0），所以两套数组就不是同一个值
 *
 * <p>T2 与 T3 沿用地钽石的攻速与攻击力底数，档差由 {@link ModTiers} 的攻击加成给
 */
public final class ModEquipment {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    /** 工具顺序：剑 镐 斧 锹 锄 */
    private static final float[] IRON_DAMAGE = {3.0F, 1.0F, 6.0F, 1.5F, -2.0F};

    private static final float[] IRON_SPEED = {-2.4F, -2.8F, -3.1F, -3.0F, -1.0F};
    private static final float[] DIAMOND_DAMAGE = {3.0F, 1.0F, 5.0F, 1.5F, -3.0F};
    private static final float[] DIAMOND_SPEED = {-2.4F, -2.8F, -3.0F, -3.0F, 0.0F};

    /** 铁级护甲的耐久倍率 */
    private static final int IRON_DURABILITY_FACTOR = 15;
    /** 钻石级护甲的耐久倍率 */
    private static final int DIAMOND_DURABILITY_FACTOR = 33;
    /** T2 护甲的耐久倍率 */
    private static final int T2_DURABILITY_FACTOR = 37;
    /** T3 护甲的耐久倍率 */
    private static final int T3_DURABILITY_FACTOR = 40;

    private static final Map<String, Set> SETS = registerSets();

    private ModEquipment() {}

    /** 一套装备。{@code items} 的顺序就是创造模式物品栏里的顺序：五件工具然后四件护甲 */
    public record Set(String material, List<DeferredItem<? extends Item>> items) {}

    public static Map<String, Set> sets() {
        return SETS;
    }

    private static Map<String, Set> registerSets() {
        Map<String, Set> sets = new LinkedHashMap<>();
        sets.put(
                "skygate_pine",
                registerSet(
                        "skygate_pine",
                        ModTiers.SKYGATE_PINE,
                        ModArmorMaterials.SKYGATE_PINE,
                        IRON_DAMAGE,
                        IRON_SPEED,
                        IRON_DURABILITY_FACTOR));
        sets.put(
                "canopy",
                registerSet(
                        "canopy",
                        ModTiers.CANOPY,
                        ModArmorMaterials.CANOPY,
                        DIAMOND_DAMAGE,
                        DIAMOND_SPEED,
                        DIAMOND_DURABILITY_FACTOR));
        sets.put(
                "carminite",
                registerSet(
                        "carminite",
                        ModTiers.CARMINITE,
                        ModArmorMaterials.CARMINITE,
                        DIAMOND_DAMAGE,
                        DIAMOND_SPEED,
                        DIAMOND_DURABILITY_FACTOR));
        // 绯色钼的护甲带套装效果，物品在 ModItems 里注册，这里只借那四个 holder 拼成一整套
        sets.put(
                "scarlet_molybd",
                registerSet(
                        "scarlet_molybd",
                        ModTiers.SCARLET_MOLYBDENUM,
                        ModArmorMaterials.SCARLET_MOLYBDENUM,
                        DIAMOND_DAMAGE,
                        DIAMOND_SPEED,
                        T2_DURABILITY_FACTOR,
                        List.of(
                                ModItems.SCARLET_MOLYBD_HELMET,
                                ModItems.SCARLET_MOLYBD_CHESTPLATE,
                                ModItems.SCARLET_MOLYBD_LEGGINGS,
                                ModItems.SCARLET_MOLYBD_BOOTS)));
        sets.put(
                "dirac",
                registerSet(
                        "dirac",
                        ModTiers.DIRAC,
                        ModArmorMaterials.DIRAC,
                        DIAMOND_DAMAGE,
                        DIAMOND_SPEED,
                        T2_DURABILITY_FACTOR));
        sets.put(
                "hui",
                registerSet(
                        "hui",
                        ModTiers.HUI,
                        ModArmorMaterials.HUI,
                        DIAMOND_DAMAGE,
                        DIAMOND_SPEED,
                        T3_DURABILITY_FACTOR));
        return Collections.unmodifiableMap(sets);
    }

    private static Set registerSet(
            String material,
            Tier tier,
            Holder<ArmorMaterial> armor,
            float[] damage,
            float[] speed,
            int durabilityFactor) {
        return registerSet(material, tier, armor, damage, speed, durabilityFactor, null);
    }

    /**
     * @param armorItems 已经注册好的四件护甲（头 胸 腿 脚），null 时按通用部件现注四件
     */
    private static Set registerSet(
            String material,
            Tier tier,
            Holder<ArmorMaterial> armor,
            float[] damage,
            float[] speed,
            int durabilityFactor,
            @Nullable List<DeferredItem<? extends Item>> armorItems) {
        List<DeferredItem<? extends Item>> items = new ArrayList<>();
        items.add(ITEMS.register(
                material + "_sword",
                () -> new SwordItem(
                        tier,
                        new Item.Properties().attributes(SwordItem.createAttributes(tier, damage[0], speed[0])))));
        items.add(ITEMS.register(
                material + "_pickaxe",
                () -> new PickaxeItem(
                        tier,
                        new Item.Properties().attributes(PickaxeItem.createAttributes(tier, damage[1], speed[1])))));
        items.add(ITEMS.register(
                material + "_axe",
                () -> new AxeItem(
                        tier, new Item.Properties().attributes(AxeItem.createAttributes(tier, damage[2], speed[2])))));
        items.add(ITEMS.register(
                material + "_shovel",
                () -> new ShovelItem(
                        tier,
                        new Item.Properties().attributes(ShovelItem.createAttributes(tier, damage[3], speed[3])))));
        items.add(ITEMS.register(
                material + "_hoe",
                () -> new HoeItem(
                        tier, new Item.Properties().attributes(HoeItem.createAttributes(tier, damage[4], speed[4])))));

        if (armorItems != null) {
            items.addAll(armorItems);
            return new Set(material, List.copyOf(items));
        }

        items.add(armorItem(material, "helmet", ArmorItem.Type.HELMET, armor, durabilityFactor));
        items.add(armorItem(material, "chestplate", ArmorItem.Type.CHESTPLATE, armor, durabilityFactor));
        items.add(armorItem(material, "leggings", ArmorItem.Type.LEGGINGS, armor, durabilityFactor));
        items.add(armorItem(material, "boots", ArmorItem.Type.BOOTS, armor, durabilityFactor));

        return new Set(material, List.copyOf(items));
    }

    /** 这几套护甲走通用部件，不继承 {@link ScarletMolybdArmorItem} 的套装效果 */
    private static DeferredItem<ArmorItem> armorItem(
            String material, String slot, ArmorItem.Type type, Holder<ArmorMaterial> armor, int durabilityFactor) {
        return ITEMS.register(
                material + "_" + slot,
                () -> new ArmorItem(
                        armor, type, new Item.Properties().durability(type.getDurability(durabilityFactor))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
