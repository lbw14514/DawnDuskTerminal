package com.dawnduskterminal.gametest;

import com.dawnduskterminal.item.Experiment095Item;
import com.dawnduskterminal.item.RequiemProcess;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModSounds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
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
 * 试验物品 095（镇魂曲）的规格
 *
 * <p>钉住：95 号不再是占位、{@value Experiment095Item#DURABILITY} 点耐久且不可堆叠、
 * 伤害是 {@code 0.5 × (32 - 距离)}（贴脸 16 点、16 格 8 点、31 格 0.5 点、32 格起 0 点）、
 * 按住时圈里的活物每 tick 试一次而<b>节奏由无敌帧自己定成每 0.5 秒一下</b>、施放者自己不吃自己的圈、
 * 松手即收场、每秒扣 1 点耐久、每支曲子各自在 ddt 资源里并带 {@code stream} 与 {@code attenuation_distance: 32}、
 * 伤害类型在 no_knockback 里而<b>不在</b> bypasses_cooldown 里、两条语言键与死亡信息齐备
 *
 * <p>与 066 一样，节奏那条把实体自己那一拍也手动走一遍（{@code entity.tick()} 再 {@code process.tick()}，
 * 与服务器里「实体先 tick、{@code ServerTickEvent.Post} 后跑过程」同序）：{@code invulnerableTime} 是在
 * 实体 tick 里往下减的，只连打 {@code process.tick()} 的话无敌帧根本不动，量出来的节奏是假的
 *
 * <p>所有实体都关掉重力与 AI：伤害按距离算，木桩只要挪一格就会把断言推翻
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment095Tests {
    private static final float GOLEM_HEALTH = 100.0F;

    @GameTest(template = "ddt_empty")
    public static void itemReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(Integer.valueOf(95)),
            "95 must not be a placeholder item anymore");
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_095.get());
        helper.assertTrue(stack.getItem() instanceof Experiment095Item,
            "experiment_095 must be built from Experiment095Item, got " + stack.getItem());
        helper.assertTrue(stack.getMaxStackSize() == 1,
            "a damageable item must not stack, got " + stack.getMaxStackSize());
        helper.assertTrue(stack.getMaxDamage() == Experiment095Item.DURABILITY,
            "experiment_095 must hold " + Experiment095Item.DURABILITY + " points of durability, got "
                + stack.getMaxDamage());
        helper.assertTrue(stack.getUseDuration(null) == Experiment095Item.USE_DURATION,
            "the use must stay open as long as a bow, got " + stack.getUseDuration(null));
        helper.succeed();
    }

    /** 伤害只由距离决定，所以这条是纯算术，不用起过程 */
    @GameTest(template = "ddt_empty")
    public static void damageFallsOffWithDistance(GameTestHelper helper) {
        helper.assertTrue(RequiemProcess.RADIUS == 32.0D,
            "the field must reach 32 blocks, got " + RequiemProcess.RADIUS);
        helper.assertTrue(RequiemProcess.OUTWARD_SPEED == 3.2D,
            "the notes must leave at 3.2 blocks per tick, which is the 64 blocks per second, got "
                + RequiemProcess.OUTWARD_SPEED);
        assertDamage(helper, 0.0D, 16.0F);
        assertDamage(helper, 2.0D, 15.0F);
        assertDamage(helper, 16.0D, 8.0F);
        assertDamage(helper, 31.0D, 0.5F);
        assertDamage(helper, 32.0D, 0.0F);
        assertDamage(helper, 48.0D, 0.0F);
        assertDamage(helper, -1.0D, 0.0F);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void holdingHurtsEverythingAroundAndNeverTheCaster(GameTestHelper helper) {
        Player caster = usingPlayer(helper);
        // 32 格在测试场里横跨好几个相邻用例，别的用例留下的活物会一起被圈进来（066 那条走廊就是这么被搅红的），
        // 所以先把自己这一圈清空，再摆木桩
        clearAround(helper, caster);
        // 直接按落点生成，不用 setPos 搬：搬动不会同时改实体在区块段里的登记，查询可能把它数两遍
        Mob golem = stillMob((Mob)helper.spawn(EntityType.IRON_GOLEM, new Vec3(12.5D, 0.0D, 8.5D)));
        RequiemProcess process = RequiemProcess.detached(helper.getLevel(), caster, 0);
        float distance = (float)caster.distanceTo(golem);
        helper.assertTrue(Math.abs(distance - 4.0F) < 1.0E-4F,
            "the golem must stand exactly 4 blocks away, got " + distance);
        float pulse = RequiemProcess.damageAt(distance);
        float expected = GOLEM_HEALTH - pulse;

        tickTogether(process, 1, golem);
        helper.assertTrue(Math.abs(golem.getHealth() - expected) < 1.0E-4F,
            "4 blocks out must take " + pulse + " on the first tick, expected " + expected
                + " got " + golem.getHealth() + " (tries " + process.strikeTries()
                + ", landed " + process.strikesLanded() + ")");
        helper.assertTrue(caster.getHealth() == 20.0F,
            "the caster must never catch its own requiem, got " + caster.getHealth());

        // 第二下不能马上就落：原版无敌帧（打出一击后 invulnerableTime 从 20 往下数，数到 10 之前再打不算）
        // 会把连续的施压排成大约每 0.5 秒一下。数出它到底是第几 tick 落的，比写死一个数更抗版本差异
        int secondPulseAt = -1;
        for (int tick = 1; tick <= 20 && secondPulseAt < 0; tick++) {
            tickTogether(process, 1, golem);
            if (golem.getHealth() < expected - 1.0E-4F) {
                secondPulseAt = tick;
            }
        }
        helper.assertTrue(secondPulseAt >= 8 && secondPulseAt <= 12,
            "the invulnerability window must pace the requiem to one pulse per half a second, the second "
                + "pulse landed " + secondPulseAt + " ticks after the first");
        // 每 tick 都试一次：那 10 来 tick 里至少试了 10 次（圈里还有别的活物时会更多，所以是下界）
        helper.assertTrue(process.strikeTries() >= 10,
            "the process must try every tick and let the invulnerability window do the pacing, tried "
                + process.strikeTries() + " times in " + (secondPulseAt + 1) + " ticks");
        // 木桩用完就收，别留给下一个用例（32 格在测试场里横跨好几个结构）
        golem.discard();
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void lettingGoEndsTheRequiem(GameTestHelper helper) {
        Player caster = usingPlayer(helper);
        RequiemProcess process = RequiemProcess.detached(helper.getLevel(), caster, 0);
        tickOnly(process, 1);
        helper.assertFalse(process.finished(), "the requiem must still be running while the button is held");
        caster.stopUsingItem();
        tickOnly(process, 1);
        helper.assertTrue(process.finished(),
            "letting go of the button must end the requiem, otherwise the loop never gets its stop packet");
        tickOnly(process, 5);
        helper.assertTrue(process.age() == 1, "a finished requiem must not tick any more, got " + process.age());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void durabilityIsChargedOncePerSecond(GameTestHelper helper) {
        Player caster = usingPlayer(helper);
        ItemStack stack = caster.getUseItem();
        RequiemProcess process = RequiemProcess.detached(helper.getLevel(), caster, 0);
        // 前 19 tick 一点不扣：第一次扣费在整整 1 秒那一刻
        tickOnly(process, 19);
        helper.assertTrue(stack.getDamageValue() == 0,
            "the first second must not be charged yet, got " + stack.getDamageValue());
        tickOnly(process, 1);
        helper.assertTrue(stack.getDamageValue() == 1,
            "one second of playing must cost exactly one durability, got " + stack.getDamageValue());
        tickOnly(process, 20);
        helper.assertTrue(stack.getDamageValue() == 2,
            "two seconds of playing must cost two durability, got " + stack.getDamageValue());
        helper.assertTrue(process.charges() == 2, "the process must charge twice, got " + process.charges());
        // 一圈音符每 0.5 秒一次，40 tick 正好 4 圈
        helper.assertTrue(process.rings() == 4, "40 ticks must squeeze out 4 rings of notes, got " + process.rings());
        helper.succeed();
    }

    /**
     * 每支曲子都得有自己那一份资源：注册表里有、{@code sounds/requiem/} 下有同名 ogg、{@code sounds.json} 里
     * 有那条事件
     *
     * <p><b>不写死支数</b>：曲目是按目录增删的，测试只钉「表里有几支、这三处就得几支一一对上」，
     * 免得换一批曲子就得改测试
     */
    @GameTest(template = "ddt_empty")
    public static void everyTrackShipsWithItsOwnOgg(GameTestHelper helper) {
        int count = ModSounds.requiemTrackCount();
        helper.assertTrue(count >= 1, "the requiem must ship at least one track");
        JsonObject sounds = readJson(helper, "/assets/dawnduskterminal/sounds.json");
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < count; index++) {
            SoundEvent event = ModSounds.requiemTrack(index);
            helper.assertTrue(event != null, "track " + index + " must be registered");
            String path = event.getLocation().getPath();
            helper.assertTrue(path.startsWith("requiem/"),
                "track " + index + " must live under sounds/requiem/, got " + path);
            helper.assertTrue(event.getLocation().getNamespace().equals("dawnduskterminal"),
                "track " + index + " must stay in this namespace, got " + event.getLocation());
            helper.assertTrue(seen.add(path), "track " + index + " repeats " + path + " in REQUIEM_TRACKS");
            byte[] ogg = readBytes(helper, "/assets/dawnduskterminal/sounds/" + path + ".ogg");
            helper.assertTrue(ogg.length > 1000,
                path + ".ogg must ship with the mod and carry real audio, got " + ogg.length + " bytes");
            JsonObject entry = sounds.getAsJsonObject(path);
            helper.assertTrue(entry != null, "sounds.json must carry an entry for " + path);
            JsonObject sound = entry.getAsJsonArray("sounds").get(0).getAsJsonObject();
            helper.assertTrue(sound.get("stream").getAsBoolean(),
                path + " must stream: a 15~25 second track is not worth holding in memory whole");
            helper.assertTrue(sound.get("attenuation_distance").getAsInt() == 32,
                path + " must be audible inside 32 blocks, got attenuation_distance "
                    + sound.get("attenuation_distance"));
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void dataAndLanguageArePinned(GameTestHelper helper) {
        JsonObject damageType = readJson(helper, "/data/dawnduskterminal/damage_type/experiment_095.json");
        helper.assertTrue(damageType.get("message_id").getAsString().equals("experiment_095"),
            "the damage type must resolve to the experiment_095 message, got " + damageType.get("message_id"));
        JsonObject noKnockback = readJson(helper, "/data/minecraft/tags/damage_type/no_knockback.json");
        helper.assertTrue(noKnockback.getAsJsonArray("values").toString().contains("dawnduskterminal:experiment_095"),
            "experiment_095 must sit in no_knockback, otherwise the requiem shoves everything away, got "
                + noKnockback.get("values"));
        JsonObject bypass = readJson(helper, "/data/minecraft/tags/damage_type/bypasses_cooldown.json");
        helper.assertFalse(bypass.getAsJsonArray("values").toString().contains("dawnduskterminal:experiment_095"),
            "experiment_095 must NOT bypass the hurt cooldown: the invulnerability window is what paces the "
                + "requiem to one hit per 0.5 s, got " + bypass.get("values"));
        for (String lang : new String[] {"/assets/dawnduskterminal/lang/zh_cn.json",
            "/assets/dawnduskterminal/lang/en_us.json"}) {
            JsonObject words = readJson(helper, lang);
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_095"),
                lang + " must name the item");
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_095.desc"),
                lang + " must carry the tooltip line of experiment_095");
            helper.assertTrue(words.has("death.attack.experiment_095"),
                lang + " must carry the death message of the experiment_095 damage type");
            helper.assertTrue(words.has("death.attack.experiment_095.player"),
                lang + " must carry the player-attributed death message too: the damage source names the "
                    + "caster, so vanilla always picks the .player variant");
        }
        helper.succeed();
    }

    private static void assertDamage(GameTestHelper helper, double distance, float expected) {
        float actual = RequiemProcess.damageAt(distance);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-4F,
            distance + " blocks out must take " + expected + ", got " + actual);
    }

    /**
     * 一个按住右键的假玩家：95 拿在手上、正在使用
     *
     * <p>位置取模板原点正中（8.5, 0, 8.5），离模板边界都还有 7 格，木桩摆在哪都不会掉出结构。
     * <b>不 {@code addFreshEntity}</b>：过程只认「手上拿着 95、正在使用」这几条，用不着玩家进世界；
     * 进了世界反而会以玩家身份留在测试场里，把后面用例的判定搅乱
     */
    private static Player usingPlayer(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos((double)origin.getX() + 8.5D, (double)origin.getY(), (double)origin.getZ() + 8.5D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModExperimentItems.EXPERIMENT_095.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        return player;
    }

    /** 把这一圈（32 格）里别的用例留下的活物清掉，断言只落在自己摆的木桩上 */
    private static void clearAround(GameTestHelper helper, Player caster) {
        for (LivingEntity stray : helper.getLevel().getEntitiesOfClass(LivingEntity.class,
            caster.getBoundingBox().inflate(RequiemProcess.RADIUS), other -> other != caster)) {
            stray.discard();
        }
    }

    /** 只走过程那一拍，不碰实体：扣耐久与数圈数用得上 */
    private static void tickOnly(RequiemProcess process, int ticks) {
        for (int i = 0; i < ticks; i++) {
            process.tick();
        }
    }

    /** 实体自己那一拍 + 过程那一拍，与服务器里「实体先 tick、过程在 ServerTickEvent.Post 里跑」同序 */
    private static void tickTogether(RequiemProcess process, int ticks, Entity... entities) {
        for (int i = 0; i < ticks; i++) {
            for (Entity entity : entities) {
                if (entity.isAlive()) {
                    entity.tick();
                }
            }
            process.tick();
        }
    }

    /** 不会自己挪窝的木桩：关掉 AI 与重力，距离才不会变 */
    private static Mob stillMob(Mob mob) {
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        return mob;
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try {
            return JsonParser.parseString(new String(readBytes(helper, resource), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (RuntimeException problem) {
            throw new AssertionError("parsing " + resource + " failed", problem);
        }
    }

    private static byte[] readBytes(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment095Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return stream.readAllBytes();
        } catch (IOException problem) {
            throw new AssertionError("reading " + resource + " failed", problem);
        }
    }

    private Experiment095Tests() {}
}
