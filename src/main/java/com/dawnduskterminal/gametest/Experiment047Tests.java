package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.ExperimentArmorItem;
import com.dawnduskterminal.registry.ModArmorMaterials;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
/**
 * 试验物品 047~050 的规格
 *
 * <p>钉住：四个占位号已经让出、四件都是 {@code ExperimentArmorItem}、材质是
 * {@link com.dawnduskterminal.registry.ModArmorMaterials#EXPERIMENT_ARMOR}（护甲值与铁同级、每件 2 点韧性、
 * 三样材料都能修、耐久倍率照铁）、穿戴贴图指向 {@code textures/entities/experiment_armor.png}
 */

public final class Experiment047Tests {
    private static final int IRON_DEFENSE = 15;
    private static final float TOUGHNESS_PER_PIECE = 2.0F;
    private static final int IRON_DURABILITY_FACTOR = 15;
    private static final Map<Type, Integer> IRON_DEFENSE_PER_PIECE = Map.of(Type.HELMET, 2, Type.CHESTPLATE, 6, Type.LEGGINGS, 5, Type.BOOTS, 2);

    private static Map<Type, Item> pieces() {
        Map<Type, Item> pieces = new LinkedHashMap<>();
        pieces.put(Type.HELMET, (Item)ModExperimentItems.EXPERIMENT_047.get());
        pieces.put(Type.CHESTPLATE, (Item)ModExperimentItems.EXPERIMENT_048.get());
        pieces.put(Type.LEGGINGS, (Item)ModExperimentItems.EXPERIMENT_049.get());
        pieces.put(Type.BOOTS, (Item)ModExperimentItems.EXPERIMENT_050.get());
        return pieces;
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void armorReplacesThePlaceholders(GameTestHelper helper) {
        for (int number : new int[]{47, 48, 49, 50}) {
            helper.assertFalse(
                ModExperimentItems.placeholders().containsKey(Integer.valueOf(number)),
                "number " + number + " must not be a placeholder anymore, it has a real item now"
            );
        }

        for (Entry<Type, Item> entry : pieces().entrySet()) {
            Type type = entry.getKey();
            Item item = entry.getValue();
            helper.assertTrue(item instanceof ExperimentArmorItem, item + " must be an " + ExperimentArmorItem.class.getSimpleName());
            ArmorItem armor = (ArmorItem)item;
            helper.assertTrue(armor.getType() == type, item + " must be a " + type + ", got " + armor.getType());
            helper.assertTrue(armor.getEquipmentSlot() == type.getSlot(), item + " must go into " + type.getSlot() + ", got " + armor.getEquipmentSlot());
            helper.assertTrue(new ItemStack(item).getMaxStackSize() == 1, item + " is damageable, it must not stack");
        }

        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void armorMatchesIronWithTwoToughness(GameTestHelper helper) {
        ArmorMaterial material = (ArmorMaterial)ModArmorMaterials.EXPERIMENT_ARMOR.value();
        int total = 0;

        for (Entry<Type, Item> entry : pieces().entrySet()) {
            Type type = entry.getKey();
            Item item = entry.getValue();
            Map<Type, Integer> expected = IRON_DEFENSE_PER_PIECE;
            int defense = material.getDefense(type);
            helper.assertTrue(defense == expected.get(type), item + " must give " + expected.get(type) + " defense like an iron piece, got " + defense);
            total += defense;
            int durability = type.getDurability(15);
            helper.assertTrue(
                new ItemStack(item).getMaxDamage() == durability,
                item + " must have " + durability + " points of durability like an iron piece, got " + new ItemStack(item).getMaxDamage()
            );
            helper.assertTrue(((ArmorItem)item).getMaterial().value() == material, item + " must use the experiment armor material");
        }

        helper.assertTrue(total == 15, "the whole set must add up to 15 defense like iron, got " + total);
        helper.assertTrue(material.toughness() == 2.0F, "every piece must give 2.0 toughness, got " + material.toughness());
        helper.assertTrue(material.knockbackResistance() == 0.0F, "the set must not add knockback resistance, got " + material.knockbackResistance());
        helper.assertTrue(material.enchantmentValue() == 9, "the set must enchant like iron (9), got " + material.enchantmentValue());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void brokenArmorRepairsWithAnyOfTheThreeIngots(GameTestHelper helper) {
        List<Item> ingots = List.of((Item)ModItems.CARMINITE_INGOT.get(), (Item)ModItems.SCARLET_MOLYBDENUM_INGOT.get(), (Item)ModItems.HUI_INGOT.get());

        for (Entry<Type, Item> entry : pieces().entrySet()) {
            Item item = entry.getValue();
            ItemStack stack = new ItemStack(item);

            for (Item ingot : ingots) {
                helper.assertTrue(item.isValidRepairItem(stack, new ItemStack(ingot)), item + " must repair with " + ingot);
            }

            helper.assertFalse(
                item.isValidRepairItem(stack, new ItemStack(Items.IRON_INGOT)),
                item + " must not repair with an iron ingot, the repair list is the three modded ingots"
            );
            helper.assertFalse(item.isValidRepairItem(stack, new ItemStack(Items.DIAMOND)), item + " must not repair with a diamond");
        }

        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void armorUsesTheDedicatedTexture(GameTestHelper helper) {
        ResourceLocation expected = DawnDuskTerminal.id("textures/entities/experiment_armor.png");

        for (Entry<Type, Item> entry : pieces().entrySet()) {
            ArmorItem armor = (ArmorItem)entry.getValue();
            Layer layer = (Layer)((ArmorMaterial)armor.getMaterial().value()).layers().get(0);

            for (boolean innerModel : new boolean[]{false, true}) {
                ResourceLocation texture = armor.getArmorTexture(new ItemStack(armor), null, armor.getEquipmentSlot(), layer, innerModel);
                helper.assertTrue(expected.equals(texture), armor.getDescriptionId() + " must use the dedicated armor texture " + expected + ", got " + texture);
            }
        }

        for (String name : List.of("experiment_armor", "experiment_armor_glow")) {
            String resource = "/assets/dawnduskterminal/textures/entities/" + name + ".png";

            try (InputStream stream = Experiment047Tests.class.getResourceAsStream(resource)) {
                helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            } catch (IOException var13) {
                helper.assertTrue(false, "failed to read " + resource + ": " + var13);
            }
        }

        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void itemModelsPointAtTheirOwnIcons(GameTestHelper helper) {
        for (int number : new int[]{47, 48, 49, 50}) {
            String id = "experiment_%03d".formatted(number);
            JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/item/" + id + ".json");
            helper.assertTrue(
                "minecraft:item/generated".equals(model.get("parent").getAsString()), "item/" + id + " must be a flat icon, got parent " + model.get("parent")
            );
            String layer = model.getAsJsonObject("textures").get("layer0").getAsString();
            helper.assertTrue(("dawnduskterminal:item/" + id).equals(layer), "item/" + id + " must reference its own icon, got " + layer);
            String icon = "/assets/dawnduskterminal/textures/item/" + id + ".png";

            try (InputStream stream = Experiment047Tests.class.getResourceAsStream(icon)) {
                helper.assertTrue(stream != null, icon + " must ship inside the mod resources");
            } catch (IOException var14) {
                helper.assertTrue(false, "failed to read " + icon + ": " + var14);
            }
        }

        helper.succeed();
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try {
            JsonObject var3;
            try (InputStream stream = Experiment047Tests.class.getResourceAsStream(resource)) {
                helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
                var3 = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }

            return var3;
        } catch (IOException var7) {
            helper.assertTrue(false, "failed to read " + resource + ": " + var7);
            return new JsonObject();
        }
    }

    private Experiment047Tests() {
    }
}
