package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.Experiment066Item;
import com.dawnduskterminal.item.GhastTrapProcess;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import twilightforest.init.TFEntities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 066（恶魂陷阱书）的规格
 *
 * <p>钉住：66 号不再是占位、64 点耐久且翻一次扣 1 点、冷却正好 120 + 60 = 180 tick（第 179 tick 还锁着、
 * 第 180 tick 解开）、场每 tick 都试一次伤害而<b>节奏由无敌帧自己定成每 0.5 秒一下</b>（120 tick 里正好 12 下、
 * 铁傀儡 100 点挨成 16 点）、玩家一样中招（这本书以后要装到敌怪身上）、施放者自己不吃自己的场、
 * 走廊的边界（背后 / 侧面 / 16 格以外）由 {@link GhastTrapProcess#insideField} 的几何判定钉住、
 * 模型与三条语言键、伤害类型在 no_knockback 里而<b>不在</b> bypasses_cooldown 里
 *
 * <p>节奏那条把实体自己那一拍也手动走一遍（{@code entity.tick()} 再 {@code process.tick()}，与服务器里
 * 「实体先 tick、{@code ServerTickEvent.Post} 后跑场」同序）：{@code invulnerableTime} 是在实体 tick 里往下减的，
 * 只连打 {@code process.tick()} 的话无敌帧根本不动，量出来的节奏是假的
 *
 * <p>场一律朝上打（pitch -90）：游戏测试的模板是紧挨着排的，16 格长的走廊横着扫会扫进隔壁用例的场地里，
 * 把别人的实体拽走、打死；朝上的筒子只覆盖自己这一列
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment066Tests {
    private static final float GOLEM_HEALTH = 100.0F;
    private static final float PULSE = GhastTrapProcess.DAMAGE;
    /** 120 tick、每 0.5 秒一下，就是 12 下 */
    private static final int HITS_PER_CAST = GhastTrapProcess.TOTAL_TICKS / 10;
    private static final float PLAYER_HEALTH = 20.0F;

    @GameTest(template = "ddt_empty")
    public static void itemReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(Integer.valueOf(66)),
            "number 66 must not be a placeholder anymore, it has a real item now");
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_066.get());
        helper.assertTrue(stack.getItem() instanceof Experiment066Item,
            "dawnduskterminal:experiment_066 must be an " + Experiment066Item.class.getSimpleName()
                + ", got " + stack.getItem());
        helper.assertTrue(stack.getMaxStackSize() == 1,
            "dawnduskterminal:experiment_066 is damageable, it must not stack, got " + stack.getMaxStackSize());
        helper.assertTrue(stack.getMaxDamage() == 64,
            "dawnduskterminal:experiment_066 must have 64 uses, got " + stack.getMaxDamage());
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .containsKey(DawnDuskTerminal.id("experiment_066")),
            "dawnduskterminal:experiment_066 must be a registered damage type");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void singleClickCostsDurabilityAndCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = aimPlayer(helper);
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_066.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(),
            "right clicking dawnduskterminal:experiment_066 must consume the click, got " + result.getResult());
        helper.assertTrue(stack.getDamageValue() == 1,
            "one cast must cost one durability, got " + stack.getDamageValue());
        helper.assertTrue(player.getCooldowns().isOnCooldown(stack.getItem()), "one cast must start the cooldown");
        helper.assertTrue(GhastTrapProcess.activeCount(player) == 1,
            "one cast must leave exactly one field running, got " + GhastTrapProcess.activeCount(player));
        helper.assertTrue(stack.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
            "a second click inside the cooldown must not open a second field");
        helper.assertTrue(GhastTrapProcess.activeCount(player) == 1,
            "the blocked click must not open a second field, got " + GhastTrapProcess.activeCount(player));
        GhastTrapProcess.start(level, player);
        helper.assertTrue(GhastTrapProcess.activeCount(player) == 1,
            "a second field on the same caster must reuse the running one, got "
                + GhastTrapProcess.activeCount(player));
        helper.assertTrue(Experiment066Item.COOLDOWN_TICKS == GhastTrapProcess.TOTAL_TICKS + 60,
            "the cooldown must be the whole 120 tick field plus 3 seconds, got " + Experiment066Item.COOLDOWN_TICKS);
        for (int i = 0; i < Experiment066Item.COOLDOWN_TICKS - 1; i++) {
            player.getCooldowns().tick();
        }
        helper.assertTrue(player.getCooldowns().isOnCooldown(stack.getItem()),
            "the cooldown must still be running on tick " + (Experiment066Item.COOLDOWN_TICKS - 1));
        player.getCooldowns().tick();
        helper.assertFalse(player.getCooldowns().isOnCooldown(stack.getItem()),
            "the cooldown must clear after " + Experiment066Item.COOLDOWN_TICKS + " ticks");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fieldHurtsEveryTickAndIfframesPaceItToHalfASecond(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        Mob golem = stillMob((Mob)helper.spawn(EntityType.IRON_GOLEM, 10, 1, 2));
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), player);
        tickTogether(process, GhastTrapProcess.TOTAL_TICKS, golem);
        helper.assertTrue(process.age() == GhastTrapProcess.TOTAL_TICKS,
            "the field must last exactly " + GhastTrapProcess.TOTAL_TICKS + " ticks, got " + process.age());
        helper.assertTrue(process.finished(),
            "the field must be over after " + GhastTrapProcess.TOTAL_TICKS + " ticks");
        helper.assertTrue(process.damageTries() == GhastTrapProcess.TOTAL_TICKS,
            "the field must call hurt every tick and let the invulnerability window do the pacing, got "
                + process.damageTries() + " tries in " + GhastTrapProcess.TOTAL_TICKS + " ticks");
        helper.assertTrue(process.hurtLanded() == HITS_PER_CAST,
            "the invulnerability window must pace the field to one hit per 10 ticks, expected "
                + HITS_PER_CAST + " hits, got " + process.hurtLanded() + " (caught=" + process.targetsCaught() + ")");
        float expected = GOLEM_HEALTH - PULSE * HITS_PER_CAST;
        helper.assertTrue(Math.abs(golem.getHealth() - expected) < 1.0E-4F,
            "the golem must take " + PULSE + " per landed hit, expected " + expected + " got " + golem.getHealth());
        tickTogether(process, 20, golem);
        helper.assertTrue(Math.abs(golem.getHealth() - expected) < 1.0E-4F,
            "a finished field must not hurt anything anymore, got " + golem.getHealth());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fieldHurtsPlayersToo(GameTestHelper helper) {
        Player caster = aimPlayer(helper);
        Player bystander = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        bystander.setPos((double)origin.getX() + 10.5D, (double)origin.getY(), (double)origin.getZ() + 2.5D);
        helper.getLevel().addFreshEntity(bystander);
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), caster);
        tickTogether(process, 1, bystander);
        helper.assertTrue(Math.abs(bystander.getHealth() - (PLAYER_HEALTH - PULSE)) < 1.0E-4F,
            "a player standing in the corridor must be hurt on the first tick, expected "
                + (PLAYER_HEALTH - PULSE) + " got " + bystander.getHealth());
        tickTogether(process, 9, bystander);
        helper.assertTrue(process.hurtLanded() == 1,
            "the player's own invulnerability window must swallow the nine ticks after the hit, landed "
                + process.hurtLanded() + " hits");
        tickTogether(process, 1, bystander);
        helper.assertTrue(process.hurtLanded() == 2,
            "the next half second must land another hit, landed " + process.hurtLanded() + " hits");
        helper.assertTrue(bystander.getHealth() < PLAYER_HEALTH - PULSE,
            "the second hit must hurt the player again, got " + bystander.getHealth());
        helper.assertTrue(caster.getHealth() == PLAYER_HEALTH,
            "the caster must never catch its own field, got " + caster.getHealth());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fieldMissesWhatIsOutsideTheCorridor(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        Mob aside = stillMob((Mob)helper.spawn(EntityType.IRON_GOLEM, 14, 1, 2));
        Mob corner = stillMob((Mob)helper.spawn(EntityType.IRON_GOLEM, 2, 1, 8));
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), player);
        tickTogether(process, GhastTrapProcess.TOTAL_TICKS, aside, corner);
        helper.assertTrue(process.hurtLanded() == 0,
            "nothing outside the corridor may be hurt, got " + process.hurtLanded() + " landed hits");
        helper.assertTrue(aside.getHealth() == GOLEM_HEALTH,
            "the golem 6 blocks to the side must keep its health, got " + aside.getHealth());
        helper.assertTrue(corner.getHealth() == GOLEM_HEALTH,
            "the golem in the corner away from the caster must keep its health, got " + corner.getHealth());
        helper.succeed();
    }

    /** 走廊边界不起场也能量：判定就是一条纯几何 */
    @GameTest(template = "ddt_empty")
    public static void corridorGeometryIsPinned(GameTestHelper helper) {
        helper.assertTrue(GhastTrapProcess.LENGTH == 16.0D,
            "the corridor must be 16 blocks long, got " + GhastTrapProcess.LENGTH);
        helper.assertTrue(GhastTrapProcess.RADIUS == 4.0D,
            "the corridor must be 4 blocks thick, got " + GhastTrapProcess.RADIUS);
        Mob golem = stillMob((Mob)helper.spawn(EntityType.IRON_GOLEM, 8, 1, 8));
        Vec3 center = golem.getBoundingBox().getCenter();
        Vec3 look = new Vec3(0.0, 0.0, 1.0);
        helper.assertTrue(GhastTrapProcess.insideField(center.add(0.0, 0.0, -6.0), look, golem),
            "6 blocks down the sight line is inside the corridor");
        helper.assertTrue(GhastTrapProcess.insideField(center.add(-3.0, 0.0, -6.0), look, golem),
            "3 blocks to the side is still inside the corridor");
        helper.assertFalse(GhastTrapProcess.insideField(center.add(0.0, 0.0, -GhastTrapProcess.LENGTH - 2.0), look, golem),
            "past " + GhastTrapProcess.LENGTH + " blocks the corridor must end");
        helper.assertFalse(GhastTrapProcess.insideField(center.add(-6.0, 0.0, -6.0), look, golem),
            "6 blocks to the side is outside the corridor");
        helper.assertFalse(GhastTrapProcess.insideField(center.add(0.0, 0.0, 6.0), look, golem),
            "whatever sits behind the eyes is outside the corridor");
        helper.succeed();
    }

    /** 拽的方向必须朝向施放者身上那个吸口，不是朝向走廊上的某个点 */
    @GameTest(template = "ddt_empty")
    public static void fieldDragsItsTargetsTowardsTheCaster(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        // 关掉 AI 的怪根本不会动（LivingEntity#isEffectiveAi 对 noAI 是 false，travel 那一整段不跑），
        // 所以这一条留 AI；僵尸不会像猪羊那样挨一下就往反方向逃
        Mob zombie = (Mob)helper.spawn(EntityType.ZOMBIE, 10, 1, 2);
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), player);
        Vec3 trap = process.trapCenter();
        double before = zombie.position().distanceTo(trap);
        tickTogether(process, 1, zombie);
        Vec3 velocity = zombie.getDeltaMovement();
        Vec3 toTrap = trap.subtract(zombie.position()).normalize();
        helper.assertTrue(velocity.length() > 0.0D, "the field must drag its targets, got velocity " + velocity);
        helper.assertTrue(velocity.dot(toTrap) > 0.0D,
            "the pull must point at the caster's trap point, velocity " + velocity + " toTrap " + toTrap);
        tickTogether(process, 9, zombie);
        double after = zombie.position().distanceTo(process.trapCenter());
        helper.assertTrue(after < before - 0.5D,
            "the target must be dragged towards the caster, before " + before + " after " + after);
        helper.succeed();
    }

    /** 掉落物也归吸口管：走廊里的被拽向施放者，走廊外的不动 */
    @GameTest(template = "ddt_empty")
    public static void fieldDragsDroppedItemsTowardsTheCaster(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ItemEntity near = new ItemEntity(helper.getLevel(), (double)origin.getX() + 10.5D, (double)origin.getY() + 3.0D,
            (double)origin.getZ() + 2.5D, new ItemStack(Items.STONE, 3));
        ItemEntity aside = new ItemEntity(helper.getLevel(), (double)origin.getX() + 14.5D, (double)origin.getY() + 3.0D,
            (double)origin.getZ() + 8.5D, new ItemStack(Items.STONE, 3));
        helper.getLevel().addFreshEntity(near);
        helper.getLevel().addFreshEntity(aside);
        // 掉出来的东西自带一股随机抛撒（x/z ±0.1、y +0.2），量「有没有被拽」之前先摁平
        near.setDeltaMovement(Vec3.ZERO);
        aside.setDeltaMovement(Vec3.ZERO);
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), player);
        Vec3 trap = process.trapCenter();
        double beforeNear = near.position().distanceTo(trap);
        double beforeAside = horizontalRange(aside, player);
        tickTogether(process, 1, near, aside);
        Vec3 velocity = near.getDeltaMovement();
        Vec3 toTrap = trap.subtract(near.position()).normalize();
        helper.assertTrue(velocity.dot(toTrap) > 0.0D,
            "a dropped item in the corridor must be dragged towards the caster, velocity " + velocity);
        helper.assertTrue(aside.getDeltaMovement().horizontalDistance() < 1.0E-6D,
            "an item outside the corridor must not be dragged at all, got velocity " + aside.getDeltaMovement());
        tickTogether(process, 9, near, aside);
        helper.assertTrue(process.itemsDragged() > 0,
            "the field must count the items it drags, got " + process.itemsDragged());
        helper.assertTrue(near.position().distanceTo(process.trapCenter()) < beforeNear - 0.5D,
            "the dropped item must be pulled towards the caster, before " + beforeNear + " after "
                + near.position().distanceTo(process.trapCenter()));
        helper.assertTrue(Math.abs(horizontalRange(aside, player) - beforeAside) < 0.5D,
            "an item outside the corridor must stay roughly where it fell (it only falls), before " + beforeAside
                + " after " + horizontalRange(aside, player));
        helper.succeed();
    }

    /** 到施放者的水平距离，掉落物那两条断言用它，省得被「掉下去一格」带偏 */
    private static double horizontalRange(Entity entity, Player player) {
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 吸口朝视线：偏移里的「吸口向」那一份必须落在视线上，不能是世界 Y（否则就是从脑门上方往下掉） */
    @GameTest(template = "ddt_empty")
    public static void particlesStreamInAlongTheSightLine(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        aim(player, 0.0F, 0.0F);
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), player);
        Vec3 mouth = process.particleOffset(0.0D, 20.0D, 0.0D);
        helper.assertTrue(Math.abs(mouth.y) < 1.0E-6D,
            "the trap mouth must point along the sight line, not up into the sky, got offset " + mouth);
        helper.assertTrue(mouth.normalize().dot(new Vec3(0.0, 0.0, 1.0)) > 0.99D,
            "looking down +Z must put the inflow twenty blocks down +Z, got " + mouth);
        Vec3 arm = process.particleOffset(2.5D, 20.0D, 0.0D);
        helper.assertTrue(Math.abs(arm.dot(new Vec3(0.0, 0.0, 1.0)) - 20.0D) < 1.0E-6D,
            "a full speed arm must keep the mouth component, got " + arm);
        helper.assertTrue(Math.abs(arm.dot(new Vec3(0.0, 1.0, 0.0))) < 1.0E-6D,
            "a full speed arm must not climb the world Y axis, got " + arm);
        helper.succeed();
    }

    /** 粒子和音效用的是恶魂陷阱方块自己那几支，不是随手挑的原版粒子 */
    @GameTest(template = "ddt_empty")
    public static void theFieldBorrowsTheGhastTrapEffects(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.getKey(GhastTrapProcess.TRAP_PARTICLE) != null,
            "twilightforest:ghast_trap must be a registered particle, got " + GhastTrapProcess.TRAP_PARTICLE);
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.getKey(GhastTrapProcess.TRAP_SMOKE) != null,
            "twilightforest:huge_smoke must be a registered particle, got " + GhastTrapProcess.TRAP_SMOKE);
        for (SoundEvent sound : List.of(GhastTrapProcess.TRAP_WARMUP_SOUND, GhastTrapProcess.TRAP_ON_SOUND,
            GhastTrapProcess.TRAP_SPINDOWN_SOUND)) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.getKey(sound) != null,
                sound + " must be a registered sound event, twilightforest is a required dependency here");
        }
        helper.assertTrue(GhastTrapProcess.WARMUP_TICKS == 30 && GhastTrapProcess.SPINDOWN_FROM == 80,
            "the sound phases must match the block: warmup until 30, on until 80, spindown from 80, got "
                + GhastTrapProcess.WARMUP_TICKS + " / " + GhastTrapProcess.SPINDOWN_FROM);
        helper.assertTrue(GhastTrapProcess.SOUND_VOLUME == 1.0F && GhastTrapProcess.SOUND_PITCH == 4.0F,
            "the block plays its trap sounds at volume 1 and pitch 4, got "
                + GhastTrapProcess.SOUND_VOLUME + " / " + GhastTrapProcess.SOUND_PITCH);
        helper.succeed();
    }

    /**
     * 暮色恶魂的免伤在 {@code UrGhast#hurt} 里按相位打折（tantrum 里 ÷10），书必须把它从 tantrum 里撬出来，
     * 否则前两下满额、之后每下只剩 0.7
     */
    @GameTest(template = "ddt_empty")
    public static void fieldKeepsTheUrGhastOutOfItsTantrum(GameTestHelper helper) {
        EntityType<?> type = TFEntities.UR_GHAST.get();
        Player player = aimPlayer(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Entity spawned = type.create(helper.getLevel());
        helper.assertTrue(spawned instanceof LivingEntity, "ur_ghast must be a LivingEntity, got " + spawned);
        LivingEntity boss = (LivingEntity)spawned;
        boss.moveTo((double)origin.getX() + 8.5D, (double)origin.getY() + 6.0D, (double)origin.getZ() + 2.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(boss);
        GhastTrapProcess process = GhastTrapProcess.detached(helper.getLevel(), player);
        tickTogether(process, 40, boss);
        float lost = boss.getMaxHealth() - boss.getHealth();
        helper.assertTrue(process.hurtLanded() >= 4,
            "sanity: at least four hits must land in 40 ticks, got " + process.hurtLanded());
        helper.assertTrue(Math.abs(lost - (float)process.hurtLanded() * GhastTrapProcess.DAMAGE) < 0.5F,
            "every landed hit on the ur_ghast must cost the full " + GhastTrapProcess.DAMAGE
                + " (the field keeps it out of its tantrum), lost " + lost + " over " + process.hurtLanded() + " hits");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void assetsAndDamageTypeArePinned(GameTestHelper helper) {
        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_066.json");
        helper.assertTrue(model.has("parent") && model.get("parent").getAsString().equals("minecraft:item/generated"),
            "item/experiment_066 must stay on minecraft:item/generated, got " + model.get("parent"));
        helper.assertTrue(
            model.getAsJsonObject("textures").get("layer0").getAsString().equals("dawnduskterminal:item/experiment_066"),
            "item/experiment_066 must read the experiment_066 texture, got " + model.getAsJsonObject("textures"));
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            JsonObject words = readJson(helper, "/assets/dawnduskterminal/lang/" + lang + ".json");
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_066"),
                lang + " must name the item, otherwise the tab shows a raw key");
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_066.desc"),
                lang + " must carry the tooltip line of experiment_066");
            helper.assertTrue(words.has("death.attack.experiment_066"),
                lang + " must carry the death message of the experiment_066 damage type");
        }
        JsonObject damageType = readJson(helper, "/data/dawnduskterminal/damage_type/experiment_066.json");
        helper.assertTrue(damageType.get("message_id").getAsString().equals("experiment_066"),
            "the damage type must resolve to the experiment_066 message, got " + damageType.get("message_id"));
        JsonObject noKnockback = readJson(helper, "/data/minecraft/tags/damage_type/no_knockback.json");
        helper.assertTrue(noKnockback.getAsJsonArray("values").toString().contains("dawnduskterminal:experiment_066"),
            "experiment_066 must stay in no_knockback, otherwise its knockback fights the pull, got "
                + noKnockback.get("values"));
        JsonObject bypass = readJson(helper, "/data/minecraft/tags/damage_type/bypasses_cooldown.json");
        helper.assertFalse(bypass.getAsJsonArray("values").toString().contains("dawnduskterminal:experiment_066"),
            "experiment_066 must NOT bypass the hurt cooldown: the invulnerability window is what paces the field "
                + "to one hit per 0.5 s, got " + bypass.get("values"));
        helper.succeed();
    }

    /** 实体自己那一拍 + 场那一拍，与服务器里「实体先 tick、场在 ServerTickEvent.Post 里跑」同序 */
    private static void tickTogether(GhastTrapProcess process, int ticks, Entity... entities) {
        for (int i = 0; i < ticks; i++) {
            for (Entity entity : entities) {
                if (entity.isAlive()) {
                    entity.tick();
                }
            }
            process.tick();
        }
    }

    /** AI 关掉的木桩：没被拽住的目标得老实待在原地，不然自己溜进走廊 */
    private static Mob stillMob(Mob mob) {
        mob.setNoAi(true);
        return mob;
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment066Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException problem) {
            helper.assertTrue(false, "failed to read " + resource + ": " + problem);
            throw new IllegalStateException(problem);
        }
    }

    private static Player aimPlayer(GameTestHelper helper) {
        return aimPlayer(helper, 8.5D, 2.5D);
    }

    private static Player aimPlayer(GameTestHelper helper, double x, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos((double)origin.getX() + x, (double)origin.getY(), (double)origin.getZ() + z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModExperimentItems.EXPERIMENT_066.get()));
        aim(player, 0.0F, -90.0F);
        return player;
    }

    private static void aim(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setOldPosAndRot();
    }

    private Experiment066Tests() {
    }
}
