package com.dawnduskterminal.gametest;

import com.dawnduskterminal.item.Experiment045Item;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
/**
 * 试验物品 045 的规格
 *
 * <p>钉住：占位号已经让出、物品是 {@code ShieldItem} 且耐久与 {@code Items.SHIELD} 相同、
 * 物品模型挂 {@code minecraft:builtin/entity} 且带 {@code front} 光照与 {@code blocking} 谓词
 * （举盾姿势靠它切第一/第三人称变换）、物品渲染器是 {@code Experiment045ShieldRenderer}
 */

public final class Experiment045Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_045";

    @GameTest(
        template = "ddt_empty"
    )
    public static void itemReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(
            ModExperimentItems.placeholders().containsKey(Integer.valueOf(45)), "number 45 must not be a placeholder anymore, it has a real item now"
        );
        Item item = (Item)ModExperimentItems.EXPERIMENT_045.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(
            item instanceof Experiment045Item, "dawnduskterminal:experiment_045 must be an " + Experiment045Item.class.getSimpleName() + ", got " + item
        );
        helper.assertTrue(item instanceof ShieldItem, "dawnduskterminal:experiment_045 must extend ShieldItem, the blocking logic lives there");
        helper.assertTrue(stack.getMaxStackSize() == 1, "dawnduskterminal:experiment_045 is damageable, it must not stack, got " + stack.getMaxStackSize());
        helper.assertTrue(stack.getMaxDamage() == 336, "dawnduskterminal:experiment_045 must have 336 uses like a vanilla shield, got " + stack.getMaxDamage());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void itemBlocksLikeAVanillaShield(GameTestHelper helper) {
        Item item = (Item)ModExperimentItems.EXPERIMENT_045.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(
            stack.getUseAnimation() == UseAnim.BLOCK, "dawnduskterminal:experiment_045 must hold up like a shield, got " + stack.getUseAnimation()
        );
        helper.assertTrue(
            stack.getUseDuration(null) == 72000, "dawnduskterminal:experiment_045 must stay usable as long as a shield, got " + stack.getUseDuration(null)
        );
        helper.assertTrue(
            stack.canPerformAction(ItemAbilities.SHIELD_BLOCK),
            "dawnduskterminal:experiment_045 must allow the shield_block ability, otherwise isBlocking never turns true"
        );
        helper.assertTrue(
            item instanceof Equipable && ((Equipable)item).getEquipmentSlot() == EquipmentSlot.OFFHAND,
            "dawnduskterminal:experiment_045 must only go into the offhand slot"
        );
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void brokenShieldRepairsWithPlanks(GameTestHelper helper) {
        Item item = (Item)ModExperimentItems.EXPERIMENT_045.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(
            item.isValidRepairItem(stack, new ItemStack(Items.OAK_PLANKS)), "dawnduskterminal:experiment_045 must repair with planks like a vanilla shield"
        );
        helper.assertFalse(item.isValidRepairItem(stack, new ItemStack(Items.STONE)), "dawnduskterminal:experiment_045 must not repair with stone");
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void itemModelPointsAtTheCustomRenderer(GameTestHelper helper) {
        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_045.json");
        helper.assertTrue(
            model.has("parent") && model.get("parent").getAsString().equals("minecraft:builtin/entity"),
            "item/experiment_045 must parent minecraft:builtin/entity, got " + model.get("parent")
        );
        helper.assertTrue(
            model.has("gui_light") && model.get("gui_light").getAsString().equals("front"),
            "item/experiment_045 must use front lighting like a vanilla shield, got " + model.get("gui_light")
        );
        helper.assertTrue(
            model.getAsJsonObject("textures").get("particle").getAsString().equals("dawnduskterminal:item/experiment_045_nopattern"),
            "the particle texture must be one of the shield textures, got " + model.getAsJsonObject("textures").get("particle")
        );
        JsonObject override = model.getAsJsonArray("overrides").get(0).getAsJsonObject();
        helper.assertTrue(
            override.getAsJsonObject("predicate").get("blocking").getAsFloat() == 1.0F,
            "the override must trigger on blocking=1, got " + override.getAsJsonObject("predicate")
        );
        helper.assertTrue(
            override.get("model").getAsString().equals("dawnduskterminal:item/experiment_045_blocking"),
            "the blocking override must point at item/experiment_045_blocking, got " + override.get("model")
        );
        JsonObject blocking = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_045_blocking.json");
        helper.assertTrue(
            blocking.has("parent") && blocking.get("parent").getAsString().equals("minecraft:builtin/entity"),
            "item/experiment_045_blocking must parent minecraft:builtin/entity, got " + blocking.get("parent")
        );
        helper.assertTrue(
            blocking.getAsJsonObject("display").has("firstperson_righthand"),
            "item/experiment_045_blocking must carry the first person transforms of a raised shield"
        );
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void shieldTexturesShip(GameTestHelper helper) {
        for (String name : List.of("experiment_045_nopattern", "experiment_045_base", "experiment_045_glow")) {
            String resource = "/assets/dawnduskterminal/textures/item/" + name + ".png";

            try (InputStream stream = Experiment045Tests.class.getResourceAsStream(resource)) {
                helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            } catch (IOException var9) {
                helper.assertTrue(false, "failed to read " + resource + ": " + var9);
            }
        }

        helper.succeed();
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try {
            JsonObject var3;
            try (InputStream stream = Experiment045Tests.class.getResourceAsStream(resource)) {
                helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
                var3 = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }

            return var3;
        } catch (IOException var7) {
            helper.assertTrue(false, "failed to read " + resource + ": " + var7);
            return new JsonObject();
        }
    }

    private Experiment045Tests() {
    }
}
