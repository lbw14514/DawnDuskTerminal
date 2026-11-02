package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.BeetrootProcess;
import com.dawnduskterminal.item.Experiment055Item;
import com.dawnduskterminal.network.Experiment055SoundPayload;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 055 的规格
 *
 * <p>钉住：55 号不再是占位、{@value Experiment055Item#DURABILITY} 点耐久且不可堆叠、
 * 半径与伤害系数在前 100 tick 里线性涨到 24 与 0.3、之后封顶不再涨、伤害是 {@code 系数 × (半径 − 距离)}
 * （满充能时贴脸 7.2、4 格 6.0、23 格 0.3、24 格起 0）、圈里的活物会被打而施放者自己不被圈打、
 * 充满之后连施放者自己也在掉血、每 20 tick 扣 1 点耐久、每秒把半径内的群系刷成
 * {@code dawnduskterminal:old_growth_birch_forest}、松手即收场、三支音效与死亡信息齐备
 *
 * <p>与 066、095 一样，节奏那条把实体自己那一拍也手动走一遍（{@code entity.tick()} 再 {@code process.tick()}）：
 * {@code invulnerableTime} 是在实体 tick 里往下减的，只连打 {@code process.tick()} 的话无敌帧根本不动
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment055Tests {
    private static final float GOLEM_HEALTH = 100.0F;
    /** 满半径的场要在空中跑，免得扫到邻座结构里的实体 */
    private static final int LIFTED = 48;

    @GameTest(template = "ddt_empty")
    public static void itemReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(
                ModExperimentItems.placeholders().containsKey(Integer.valueOf(55)),
                "55 must not be a placeholder item anymore");
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_055.get());
        helper.assertTrue(
                stack.getItem() instanceof Experiment055Item,
                "experiment_055 must be built from Experiment055Item, got " + stack.getItem());
        helper.assertTrue(
                stack.getMaxStackSize() == 1, "a damageable item must not stack, got " + stack.getMaxStackSize());
        helper.assertTrue(
                stack.getMaxDamage() == Experiment055Item.DURABILITY,
                "experiment_055 must hold " + Experiment055Item.DURABILITY + " points of durability, got "
                        + stack.getMaxDamage());
        helper.assertTrue(
                stack.getUseDuration(null) == Experiment055Item.USE_DURATION,
                "the use must stay open as long as a bow, got " + stack.getUseDuration(null));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void growthIsLinearAndStopsAtFiveSeconds(GameTestHelper helper) {
        helper.assertTrue(
                BeetrootProcess.CHARGE_TICKS == 100,
                "the charge must last 5 seconds, got " + BeetrootProcess.CHARGE_TICKS + " ticks");
        Player caster = usingPlayer(helper, LIFTED);
        BeetrootProcess process = BeetrootProcess.detached(helper.getLevel(), caster);
        helper.assertTrue(process.progress() == 0.0D, "a fresh field must start at zero");
        helper.assertTrue(process.radius() == 0.0D, "a fresh field must have no radius");
        helper.assertFalse(process.charged(), "a fresh field is not charged yet");

        tickOnly(process, 50);
        assertClose(helper, "radius at 2.5 s", process.radius(), 12.0D);
        assertClose(helper, "factor at 2.5 s", process.damageFactor(), 0.15D);
        helper.assertFalse(process.charged(), "half way through is not charged yet");
        helper.assertTrue(
                process.soundMode() == Experiment055SoundPayload.MODE_CHARGE,
                "the charge loop must be the one playing while charging, got mode " + process.soundMode());

        tickOnly(process, 50);
        assertClose(helper, "radius at 5 s", process.radius(), BeetrootProcess.MAX_RADIUS);
        assertClose(helper, "factor at 5 s", process.damageFactor(), BeetrootProcess.MAX_DAMAGE_FACTOR);
        helper.assertTrue(process.charged(), "the field must be charged after " + BeetrootProcess.CHARGE_TICKS);
        helper.assertTrue(
                process.soundMode() == Experiment055SoundPayload.MODE_GEIGER,
                "the full cue must hand over to the geiger loop, got mode " + process.soundMode());

        tickOnly(process, 100);
        assertClose(helper, "radius stays capped", process.radius(), BeetrootProcess.MAX_RADIUS);
        assertClose(helper, "factor stays capped", process.damageFactor(), BeetrootProcess.MAX_DAMAGE_FACTOR);

        // 满充能时的伤害表：0.3 × (24 − 距离)
        assertDamage(helper, 0.0D, 7.2F);
        assertDamage(helper, 4.0D, 6.0F);
        assertDamage(helper, 23.0D, 0.3F);
        assertDamage(helper, 24.0D, 0.0F);
        assertDamage(helper, 40.0D, 0.0F);

        // 群系只刷到 0.7 倍影响半径（enchant 粒子那一条在客户端，见 Experiment055Particles）
        helper.assertTrue(
                BeetrootProcess.BIOME_RADIUS_SCALE == 0.7D,
                "the biome spread must stop at 0.7x the radius, got " + BeetrootProcess.BIOME_RADIUS_SCALE);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fieldHurtsInsideAndTurnsTheCasterSour(GameTestHelper helper) {
        Player caster = usingPlayer(helper, LIFTED);
        BeetrootProcess process = BeetrootProcess.detached(helper.getLevel(), caster);
        // 先把场充满，木桩之后再摆：充能过程中砸下去的伤害会干扰下面那一拍的读数
        tickOnly(process, BeetrootProcess.CHARGE_TICKS - 1);
        helper.assertFalse(process.charged(), "the field is not charged one tick before its time");
        helper.assertTrue(
                caster.getHealth() == 20.0F,
                "the caster must not lose health while charging, got " + caster.getHealth());
        // 充满那一拍起，使用者自己开始掉同类型的血
        tickOnly(process, 1);
        helper.assertTrue(process.charged(), "the field must be charged before anything is measured");
        helper.assertTrue(
                caster.getHealth() < 20.0F, "a charged field must hurt its own caster, got " + caster.getHealth());

        clearAround(helper, caster);
        Mob golem = stillMob((Mob) helper.spawn(EntityType.IRON_GOLEM, new Vec3(12.5D, LIFTED, 8.5D)));
        double distance = caster.distanceTo(golem);
        helper.assertTrue(
                Math.abs(distance - 4.0D) < 1.0E-4D, "the golem must stand exactly 4 blocks away, got " + distance);
        float expected = GOLEM_HEALTH
                - BeetrootProcess.damageAt(BeetrootProcess.MAX_DAMAGE_FACTOR, BeetrootProcess.MAX_RADIUS, distance);

        tickTogether(process, 1, golem);
        helper.assertTrue(
                Math.abs(golem.getHealth() - expected) < 1.0E-4F,
                "4 blocks out must take " + (GOLEM_HEALTH - expected) + " once charged, expected " + expected + " got "
                        + golem.getHealth() + " (tries " + process.damageTries() + ")");
        helper.assertTrue(
                process.hitsLanded() >= 1,
                "the first tick of a charged field must land a hit, landed " + process.hitsLanded());
        helper.assertTrue(
                caster.getHealth() < 20.0F, "a charged field must hurt its own caster, got " + caster.getHealth());
        golem.discard();
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void chargingIsFreeForTheCaster(GameTestHelper helper) {
        Player caster = usingPlayer(helper);
        BeetrootProcess process = BeetrootProcess.detached(helper.getLevel(), caster);
        tickOnly(process, 19);
        helper.assertTrue(
                caster.getHealth() == 20.0F,
                "the caster must not take the field's damage before it is charged, got " + caster.getHealth());
        ItemStack stack = caster.getUseItem();
        helper.assertTrue(
                stack.getDamageValue() == 0, "the first second must not be charged yet, got " + stack.getDamageValue());
        tickOnly(process, 1);
        helper.assertTrue(
                stack.getDamageValue() == 1,
                "one second of playing must cost exactly one durability, got " + stack.getDamageValue());
        helper.assertTrue(
                caster.getHealth() == 20.0F,
                "the caster must still be untouched one tick before full charge, got " + caster.getHealth());
        tickOnly(process, 20);
        helper.assertTrue(
                stack.getDamageValue() == 2,
                "two seconds of playing must cost two durability, got " + stack.getDamageValue());
        helper.assertTrue(process.charges() == 2, "the field must charge twice, got " + process.charges());
        helper.succeed();
    }

    /**
     * 群系只刷「贴地的一圈」：水平是圆、竖直只有施放者上下各 4 格
     *
     * <p>球体的形状只有半径一个参数，所以三条断言：脚下（球心处）被刷成目标群系；方形角上（方形内、
     * 球外）不动；被刷单元的竖直范围不超过半径（上下也是半径，不是无底的柱子）。
     * 竖直那一半用过程自报的 {@link BeetrootProcess#biomeMinY()} / {@link BeetrootProcess#biomeMaxY()}
     * 来判——测试场里结构挨着排，别的用例也会改群系（052 的爆炸就刷半径 12 的一大片），
     * 直接读世界里的某一点分不清是谁改的
     */
    @GameTest(template = "ddt_empty")
    public static void biomeSpreadsAsASliceOnly(GameTestHelper helper) {
        Player caster = usingPlayer(helper);
        BlockPos under = caster.blockPosition();
        BlockPos corner = under.offset(8, 0, 8);
        String cornerBefore = biomeName(helper, corner);
        BeetrootProcess process = BeetrootProcess.detached(helper.getLevel(), caster);
        // 40 tick：影响半径 9.6、填充半径 6.72，刚好扫不到邻座结构
        tickOnly(process, 40);
        assertClose(
                helper,
                "the fill radius at 2 s",
                process.radius() * BeetrootProcess.BIOME_RADIUS_SCALE,
                BeetrootProcess.MAX_RADIUS * 0.4D * BeetrootProcess.BIOME_RADIUS_SCALE);
        helper.assertTrue(
                process.biomeFills() >= 1,
                "the field must paint biomes every second, painted " + process.biomeFills() + " chunk columns");
        helper.assertTrue(
                biomeIsTarget(helper, under),
                "the biome under the caster must become dawnduskterminal:old_growth_birch_forest, got "
                        + biomeName(helper, under));
        helper.assertTrue(
                biomeName(helper, corner).equals(cornerBefore),
                "(8, 8) is inside the square but outside the 6.72 block circle, so it must not be touched, was "
                        + cornerBefore + " now " + biomeName(helper, corner));

        int centreY = under.getY();
        double fillRadius = process.radius() * BeetrootProcess.BIOME_RADIUS_SCALE;
        helper.assertTrue(
                process.biomeMinY() != Integer.MAX_VALUE, "the field must have replaced at least one biome unit");
        // 球：竖直方向也只用这一个半径，所以不能比 fillRadius 更深更高（这里给 2 格量化余量）
        helper.assertTrue(
                process.biomeMinY() >= centreY - fillRadius - 2,
                "a sphere of radius " + fillRadius + " around y " + centreY
                        + " must not reach deeper, lowest unit centre was " + process.biomeMinY());
        helper.assertTrue(
                process.biomeMaxY() <= centreY + fillRadius + 2,
                "a sphere of radius " + fillRadius + " around y " + centreY
                        + " must not reach higher, highest unit centre was " + process.biomeMaxY());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void lettingGoEndsTheField(GameTestHelper helper) {
        Player caster = usingPlayer(helper);
        BeetrootProcess process = BeetrootProcess.detached(helper.getLevel(), caster);
        tickOnly(process, 1);
        helper.assertFalse(process.finished(), "the field must still run while the button is held");
        caster.stopUsingItem();
        tickOnly(process, 1);
        helper.assertTrue(
                process.finished(), "letting go must end the field, otherwise the loop never gets its stop packet");
        tickOnly(process, 5);
        helper.assertTrue(process.age() == 1, "a finished field must not tick any more, got " + process.age());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void soundsAndDataArePinned(GameTestHelper helper) {
        JsonObject sounds = readJson(helper, "/assets/dawnduskterminal/sounds.json");
        for (String name : new String[] {"experiment_055_charge", "experiment_055_full", "experiment_055_geiger"}) {
            JsonObject entry = sounds.getAsJsonObject(name);
            helper.assertTrue(entry != null, "sounds.json must carry an entry for " + name);
            JsonObject sound = entry.getAsJsonArray("sounds").get(0).getAsJsonObject();
            helper.assertTrue(
                    sound.get("attenuation_distance").getAsInt() == 32,
                    name + " must be audible inside 32 blocks, got attenuation_distance "
                            + sound.get("attenuation_distance"));
            byte[] ogg = readBytes(helper, "/assets/dawnduskterminal/sounds/" + name + ".ogg");
            helper.assertTrue(
                    ogg.length > 1000,
                    name + ".ogg must ship with the mod and carry real audio, got " + ogg.length + " bytes");
        }

        JsonObject damageType = readJson(helper, "/data/dawnduskterminal/damage_type/experiment_055.json");
        helper.assertTrue(
                damageType.get("message_id").getAsString().equals("experiment_055"),
                "the damage type must resolve to the experiment_055 message, got " + damageType.get("message_id"));
        JsonObject noKnockback = readJson(helper, "/data/minecraft/tags/damage_type/no_knockback.json");
        helper.assertTrue(
                noKnockback.getAsJsonArray("values").toString().contains("dawnduskterminal:experiment_055"),
                "experiment_055 must sit in no_knockback, otherwise the field shoves everything away, got "
                        + noKnockback.get("values"));
        JsonObject bypass = readJson(helper, "/data/minecraft/tags/damage_type/bypasses_cooldown.json");
        helper.assertFalse(
                bypass.getAsJsonArray("values").toString().contains("dawnduskterminal:experiment_055"),
                "experiment_055 must NOT bypass the hurt cooldown: the invulnerability window is what paces the "
                        + "field to one hit per 0.5 s, got " + bypass.get("values"));

        for (String lang :
                new String[] {"/assets/dawnduskterminal/lang/zh_cn.json", "/assets/dawnduskterminal/lang/en_us.json"}) {
            JsonObject words = readJson(helper, lang);
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_055"), lang + " must name the item");
            helper.assertTrue(
                    words.has("death.attack.experiment_055"),
                    lang + " must carry the death message of the experiment_055 damage type");
            helper.assertTrue(
                    words.has("death.attack.experiment_055.player"),
                    lang + " must carry the player-attributed death message too: the damage source names the caster, "
                            + "so vanilla always picks the .player variant");
            for (String subtitle :
                    new String[] {"experiment_055_charge", "experiment_055_full", "experiment_055_geiger"}) {
                helper.assertTrue(
                        words.has("subtitles.dawnduskterminal." + subtitle),
                        lang + " must carry the subtitle of " + subtitle);
            }
        }
        helper.succeed();
    }

    private static boolean biomeIsTarget(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBiome(pos).is(DawnDuskTerminal.id("old_growth_birch_forest"));
    }

    private static String biomeName(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel()
                .getBiome(pos)
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("?");
    }

    private static void assertClose(GameTestHelper helper, String what, double actual, double expected) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-4D, what + " must be " + expected + ", got " + actual);
    }

    private static void assertDamage(GameTestHelper helper, double distance, float expected) {
        float actual =
                BeetrootProcess.damageAt(BeetrootProcess.MAX_DAMAGE_FACTOR, BeetrootProcess.MAX_RADIUS, distance);
        helper.assertTrue(
                Math.abs(actual - expected) < 1.0E-4F,
                distance + " blocks out must take " + expected + ", got " + actual);
    }

    /** 一个按住右键的假玩家：055 拿在手上、正在使用；不进世界，免得留在测试场里搅乱别的用例 */
    private static Player usingPlayer(GameTestHelper helper) {
        return usingPlayer(helper, 0);
    }

    /**
     * 同上，但把施放者抬到结构上方 {@code lift} 格
     *
     * <p>满充能时场的半径是 24 格，而测试场的结构是紧挨着排的：站在地面上按，那一圈会扫到邻座结构里
     * 别的用例的木桩、把它们打死（066 那套走廊测试就因此偶发抓不到目标）。要测满半径就抬到空中，
     * 离地 40 格以上就扫不到别家了
     */
    private static Player usingPlayer(GameTestHelper helper, int lift) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos((double) origin.getX() + 8.5D, (double) origin.getY() + lift, (double) origin.getZ() + 8.5D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModExperimentItems.EXPERIMENT_055.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        return player;
    }

    /** 把这一圈里别的用例留下的活物清掉，断言只落在自己摆的木桩上 */
    private static void clearAround(GameTestHelper helper, Player caster) {
        for (LivingEntity stray : helper.getLevel()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        caster.getBoundingBox().inflate(BeetrootProcess.MAX_RADIUS),
                        other -> other != caster)) {
            stray.discard();
        }
    }

    private static void tickOnly(BeetrootProcess process, int ticks) {
        for (int i = 0; i < ticks; i++) {
            process.tick();
        }
    }

    /** 实体自己那一拍 + 场那一拍，与服务器里「实体先 tick、场在 ServerTickEvent.Post 里跑」同序 */
    private static void tickTogether(BeetrootProcess process, int ticks, Entity... entities) {
        for (int i = 0; i < ticks; i++) {
            for (Entity entity : entities) {
                if (entity.isAlive()) {
                    entity.tick();
                }
            }
            process.tick();
        }
    }

    private static Mob stillMob(Mob mob) {
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        return mob;
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        return JsonParser.parseString(new String(readBytes(helper, resource), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static byte[] readBytes(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment055Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return stream.readAllBytes();
        } catch (IOException problem) {
            throw new AssertionError("reading " + resource + " failed", problem);
        }
    }

    private Experiment055Tests() {}
}
