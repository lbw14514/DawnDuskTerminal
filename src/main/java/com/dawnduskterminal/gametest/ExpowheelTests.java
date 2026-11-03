package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.ExpowheelEntity;
import com.dawnduskterminal.item.ExpowheelFleshbookItem;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModSounds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 博览轮舞（试验物品 057 的加强版）那片飞轮的规格
 *
 * <p>钉住需求里的每一条：物品**不再**有近战属性、不进 {@code #minecraft:swords}、附魔值为 0，
 * 右键 1.5 秒一发并各扣 1 点耐久；飞轮寿命 6 秒；接触伤害固定 10 点，命中**不**弹回、**不**扣寿命；
 * 发射者自己不吃伤害；速度比 057 快一档、转角快得多（转弯半径压到 5 格上下）；撞方块仍弹性反射；
 * 三档索敌（视线锥内优先且不限距离、其次是弹体与发射者附近 24 格、都找不到才回落到 057 的视线制导）；
 * 两条音效、几何与动画与贴图与物品模型与语言键都在 jar 里。
 *
 * <p>弹跳、命中、命中不反弹、寿命与制导都是手动 {@code entity.tick()} 推进的：GameTest 的模板只有
 * 16x16x16 格，让飞轮自己飞 6 秒会撞进隔壁用例的场地。
 *
 * <p>这批用例必须**单独跑**（{@code -Pgametest_namespaces=dawnduskterminalexpo}），不进默认的那一份名单：
 * 第 1 档索敌是按需求做的不限距离的锥，和别的用例同场跑时，隔壁场地里的僵尸只要落在 20 度锥内就会被
 * 优先选走，量出来的就不是自己的目标了。每一条用例进来先清掉附近的敌怪（{@link #clearEnemies}），
 * 这样单独跑时不会被上一批用例留下的僵尸搅局。
 *
 * <p>替身玩家站在场地内部（原点再加 3.5 格）而不是原点角上：测试框架会给每片场地罩一圈屏障方块，
 * 贴在角上出生的话「眼睛前方 0.6 格」可能正好落在屏障里面，飞轮每一拍都会弹回来。
 */
@GameTestHolder(ExpowheelTests.TEMPLATE_NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ExpowheelTests {
    /** 这一批用例自己的模板命名空间：模板结构同名副本放在 {@code data/dawnduskterminalexpo/structure/} 下 */
    public static final String TEMPLATE_NAMESPACE = "dawnduskterminalexpo";

    /** 数飞轮时的搜索半径 */
    private static final double SEARCH_RADIUS = 6.0D;

    /** 清场半径：比第二档索敌的 24 格再放一圈 */
    private static final double CLEAR_RADIUS = 24.0D;

    /** 僵尸的初始生命 */
    private static final float ZOMBIE_HEALTH = 20.0F;

    /** 命中的用例里飞轮起点离僵尸多远：要在判定箱之外，才有一段「线段穿进去」可判 */
    private static final double WHEEL_START_DISTANCE = 2.0D;

    /** 相向而行的用例里飞轮的速度，随便一个大于 0 的值；{@code steer} 会按回它自己的 speed */
    private static final double TEST_SPEED = 2.2D;

    /** 057 的飞刃的初速上限与转角上限，用来对照「这一片更快、拐得更急」 */
    private static final float BLADE_MAX_SPEED = 1.8F;

    private static final double BLADE_TURN_DEGREES = 6.0D;

    /** 057 的飞刃的转弯半径：平均速度 1.5 格/tick ÷ 6 度（0.1047 弧度），约 14.3 格 */
    private static final double BLADE_RADIUS = 1.5D / Math.toRadians(BLADE_TURN_DEGREES);

    /**
     * 物品不再有近战：没有属性修饰符、不进剑标签、附魔值为 0、耐久 64 点且不可堆叠
     *
     * <p>057 是「面板 8 伤害 1.6 攻速 + 挂进 {@code #minecraft:swords}」，这里四项全部反过来
     */
    @GameTest(template = "ddt_empty")
    public static void itemDropsEveryMeleePart(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModExperimentItems.EXPOWHEEL_FLESHBOOK.get());
        helper.assertTrue(
                stack.getItem() instanceof ExpowheelFleshbookItem,
                "dawnduskterminal:expowheel_fleshbook must be an " + ExpowheelFleshbookItem.class.getSimpleName()
                        + ", got " + stack.getItem().getClass().getSimpleName());
        helper.assertTrue(
                stack.getAttributeModifiers().modifiers().isEmpty(),
                "the fleshbook must carry no attribute modifier at all, got "
                        + stack.getAttributeModifiers().modifiers());
        helper.assertFalse(
                stack.is(ItemTags.SWORDS),
                "experiment_057 sat in #minecraft:swords; the fleshbook must not, it has no melee part");
        helper.assertTrue(
                stack.getEnchantmentValue() == 0,
                "the fleshbook must report a zero enchantment value, got " + stack.getEnchantmentValue());
        helper.assertTrue(
                stack.getMaxDamage() == ExpowheelFleshbookItem.DURABILITY,
                "dawnduskterminal:expowheel_fleshbook must have " + ExpowheelFleshbookItem.DURABILITY + " uses, got "
                        + stack.getMaxDamage());
        helper.assertTrue(
                stack.getMaxStackSize() == 1,
                "the fleshbook is damageable, it must not stack, got " + stack.getMaxStackSize());
        helper.succeed();
    }

    /** 一按右键只出一片飞轮，扣 1 点耐久、冷却 1.5 秒（30 tick），冷却中再按什么都不发生 */
    @GameTest(template = "ddt_empty")
    public static void rightClickFiresOneWheelPerOneAndAHalfSeconds(GameTestHelper helper) {
        clearEnemies(helper);
        ServerLevel level = helper.getLevel();
        Player player = aimPlayer(helper);
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);

        InteractionResultHolder<ItemStack> first = stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                first.getResult().consumesAction(),
                "right clicking the fleshbook must consume the click, got " + first.getResult());
        helper.assertTrue(
                stack.getDamageValue() == 1, "one shot must cost one durability, got " + stack.getDamageValue());
        helper.assertTrue(
                player.getCooldowns().isOnCooldown(ModExperimentItems.EXPOWHEEL_FLESHBOOK.get()),
                "one shot must put the item on cooldown");
        helper.assertTrue(
                wheels(helper).size() == 1,
                "one shot must spawn exactly one wheel, got " + wheels(helper).size());
        helper.assertTrue(
                ExpowheelFleshbookItem.COOLDOWN_TICKS == 30,
                "the cooldown must be 1.5 seconds (30 ticks), got " + ExpowheelFleshbookItem.COOLDOWN_TICKS);

        InteractionResultHolder<ItemStack> second = stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertFalse(
                second.getResult().consumesAction(),
                "the second click inside the cooldown must be ignored, got " + second.getResult());
        helper.assertTrue(
                stack.getDamageValue() == 1,
                "an ignored click must not cost durability, got " + stack.getDamageValue());
        helper.assertTrue(
                wheels(helper).size() == 1,
                "an ignored click must not spawn a second wheel, got "
                        + wheels(helper).size());
        wheels(helper).forEach(ExpowheelEntity::discard);
        helper.succeed();
    }

    /**
     * 比 057 的飞刃更快、拐得更急：初速 1.9~2.1（057 是 1.2~1.8）、每 tick 至多转 24 度（057 是 6 度），
     * 转弯半径因此从 057 的约 14.3 格压到 5 格以内，且无重力
     *
     * <p>转弯半径 = 速度 ÷ 每 tick 转角（弧度）。只把两项一起翻倍是没用的——半径几乎不变，
     * 所以这条用例把半径本身也钉住
     *
     * <p>连发 200 片逐片核对，初速的随机范围与「落在视线前方 30 度以内」一起验
     */
    @GameTest(template = "ddt_empty")
    public static void wheelIsFasterAndTurnsHarderThanTheBlade(GameTestHelper helper) {
        clearEnemies(helper);
        helper.assertTrue(
                ExpowheelEntity.MIN_SPEED > BLADE_MAX_SPEED,
                "the wheel must start off faster than the 057 blade's 1.8 limit, got " + ExpowheelEntity.MIN_SPEED);
        helper.assertTrue(
                ExpowheelEntity.TURN_DEGREES_PER_TICK > BLADE_TURN_DEGREES,
                "the wheel must turn faster than the 057 blade's 6 degrees, got "
                        + ExpowheelEntity.TURN_DEGREES_PER_TICK);
        double tightestRadius = ExpowheelEntity.MAX_SPEED / Math.toRadians(ExpowheelEntity.TURN_DEGREES_PER_TICK);
        helper.assertTrue(
                tightestRadius < 6.0D, "the turn radius at full speed must stay under 6 blocks, got " + tightestRadius);
        helper.assertTrue(
                tightestRadius < BLADE_RADIUS,
                "the turn radius must be tighter than the 057 blade's " + BLADE_RADIUS + ", got " + tightestRadius);

        ServerLevel level = helper.getLevel();
        Player player = aimPlayer(helper);
        Vec3 look = player.getViewVector(1.0F).normalize();
        double minDot = Math.cos(Math.toRadians(ExpowheelEntity.MAX_DEVIATION_DEGREES));
        for (int shot = 0; shot < 200; shot++) {
            ExpowheelEntity wheel = ExpowheelEntity.launch(level, player);
            Vec3 motion = wheel.getDeltaMovement();
            double speed = motion.length();
            helper.assertTrue(
                    speed >= ExpowheelEntity.MIN_SPEED - 1.0E-6D && speed <= ExpowheelEntity.MAX_SPEED + 1.0E-6D,
                    "the random launch speed must stay within " + ExpowheelEntity.MIN_SPEED + ".."
                            + ExpowheelEntity.MAX_SPEED + ", got " + speed);
            double dot = motion.normalize().dot(look);
            helper.assertTrue(
                    dot >= minDot - 1.0E-6D,
                    "the launch must point in front of the launcher, at most "
                            + ExpowheelEntity.MAX_DEVIATION_DEGREES + " degrees off the look, got "
                            + Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0D, 1.0D))) + " degrees");
            helper.assertTrue(wheel.isNoGravity(), "the wheel must not fall");
            wheel.discard();
        }
        helper.succeed();
    }

    /** 碰撞箱是「宽 2 格、厚半格」的扁盒，与模型那道直径两格的薄轮子对得上 */
    @GameTest(template = "ddt_empty")
    public static void wheelHitboxIsWideAndFlat(GameTestHelper helper) {
        float width = ModEntities.EXPOWHEEL.get().getWidth();
        float height = ModEntities.EXPOWHEEL.get().getHeight();
        helper.assertTrue(Math.abs(width - 2.0F) < 1.0E-4F, "the wheel hitbox must be 2 blocks wide, got " + width);
        helper.assertTrue(
                Math.abs(height - 0.5F) < 1.0E-4F, "the wheel hitbox must be half a block thick, got " + height);
        helper.succeed();
    }

    /** 寿命：第 120 tick 还活着，第 121 tick 自己消失（6 秒） */
    @GameTest(template = "ddt_empty")
    public static void wheelExpiresAfterSixSeconds(GameTestHelper helper) {
        clearEnemies(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ExpowheelEntity wheel = new ExpowheelEntity(ModEntities.EXPOWHEEL.get(), helper.getLevel());
        wheel.setPos(origin.getX() + 4.5D, origin.getY() + 4.5D, origin.getZ() + 4.5D);
        wheel.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(wheel);

        for (int tick = 0; tick < ExpowheelEntity.LIFETIME_TICKS; tick++) {
            wheel.tick();
        }
        helper.assertFalse(
                wheel.isRemoved(),
                "the wheel must live for " + ExpowheelEntity.LIFETIME_TICKS + " ticks, it died early");
        wheel.tick();
        helper.assertTrue(
                wheel.isRemoved(),
                "the wheel must be gone after " + (ExpowheelEntity.LIFETIME_TICKS + 1) + " ticks, it is still here");
        helper.succeed();
    }

    /** 撞方块仍按 057 的做法弹性反射：速度沿撞击面法线的分量取反，实体不消失 */
    @GameTest(template = "ddt_empty")
    public static void wheelBouncesOffBlocksWithoutDying(GameTestHelper helper) {
        clearEnemies(helper);
        helper.setBlock(new BlockPos(6, 3, 4), Blocks.STONE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ExpowheelEntity wheel = new ExpowheelEntity(ModEntities.EXPOWHEEL.get(), helper.getLevel());
        wheel.setPos(origin.getX() + 4.5D, origin.getY() + 3.5D, origin.getZ() + 4.5D);
        wheel.setDeltaMovement(TEST_SPEED, 0.0D, 0.0D);
        helper.getLevel().addFreshEntity(wheel);

        for (int tick = 0; tick < 8; tick++) {
            wheel.tick();
            if (wheel.getDeltaMovement().x < 0.0D) {
                break;
            }
        }
        helper.assertFalse(wheel.isRemoved(), "a block hit must not remove the wheel, it is supposed to bounce");
        helper.assertTrue(
                wheel.getDeltaMovement().x < 0.0D,
                "an elastic hit on the WEST face must flip the x velocity, got " + wheel.getDeltaMovement());
        wheel.discard();
        helper.setBlock(new BlockPos(6, 3, 4), Blocks.AIR);
        helper.succeed();
    }

    /**
     * 命中活物：固定 10 点伤害，**不**弹回、**不**扣寿命
     *
     * <p>057 是「发射者攻击力 × 1.5 暴击 + 附魔」再原路折返、扣 4 秒寿命；这里三件事全部反过来：
     * 伤害与发射者的属性无关，命中之后同一拍的速度方向仍是往前，{@code age} 只涨了 tick 本身那 1
     */
    @GameTest(template = "ddt_empty")
    public static void wheelDealsTenPointsWithoutReflectingOrSpendingLifetime(GameTestHelper helper) {
        clearEnemies(helper);
        Player player = plainPlayer(helper);
        // 把发射者的攻击力摆成 20：接触伤害若还读属性，这一击就会是 20 而不是 10
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20.0D);
        Zombie target = spawnedZombie(helper, player.getX(), player.getY(), player.getZ() + 4.0D);

        ExpowheelEntity wheel = ExpowheelEntity.launch(helper.getLevel(), player);
        aimAt(wheel, target);
        Vec3 incoming = wheel.getDeltaMovement().normalize();
        wheel.tick();

        float dealt = ZOMBIE_HEALTH - target.getHealth();
        helper.assertTrue(
                Math.abs(dealt - ExpowheelEntity.CONTACT_DAMAGE) < 1.0E-4F,
                "a wheel hit must deal exactly " + ExpowheelEntity.CONTACT_DAMAGE + " points, got " + dealt);
        helper.assertTrue(
                wheel.getAge() == 1,
                "a hit must not spend any lifetime on top of the tick itself, age=" + wheel.getAge());
        helper.assertFalse(wheel.isRemoved(), "a hit must not finish the wheel, it does not bounce or expire");
        double dot = wheel.getDeltaMovement().normalize().dot(incoming);
        helper.assertTrue(
                dot > 0.5D,
                "a hit must not send the wheel back along its own path, dot with the incoming heading is " + dot);
        target.discard();
        wheel.discard();
        helper.succeed();
    }

    /**
     * 发射者自己不吃伤害：把飞轮摆回发射者的判定箱里打一拍，玩家一点血都不掉
     *
     * <p>弹体从眼睛前方 0.6 格出生、判定箱 0.8 见方，发射者就贴在旁边，没有这一道判断一出手就打自己
     */
    @GameTest(template = "ddt_empty")
    public static void wheelNeverHurtsItsLauncher(GameTestHelper helper) {
        clearEnemies(helper);
        Player player = plainPlayer(helper);
        ExpowheelEntity wheel = ExpowheelEntity.launch(helper.getLevel(), player);
        for (int tick = 0; tick < 5; tick++) {
            wheel.setPos(player.getX(), player.getY() + 1.0D, player.getZ());
            wheel.setDeltaMovement(0.0D, 0.0D, 0.2D);
            wheel.tick();
        }
        helper.assertTrue(
                player.getHealth() == 20.0F,
                "the wheel must never hurt its launcher, health went down to " + player.getHealth());
        wheel.discard();
        helper.succeed();
    }

    /**
     * 第一档索敌：只在发射者视线两侧 20 度以内找，且取其中最近的一只
     *
     * <p>视线正前方 6 格有 A（锥内）、侧方 5 格有 B（锥外、更近）。若没有第一档，第二档会挑更近的 B，
     * 所以「掉血的是 A」这一条同时证明了「用锥而不是用距离」
     */
    @GameTest(template = "ddt_empty")
    public static void wheelAimsAtTheEnemyInsideTheSightCone(GameTestHelper helper) {
        clearEnemies(helper);
        Player player = aimPlayer(helper);
        Zombie inside = spawnedZombie(helper, player.getX(), player.getY(), player.getZ() + 6.0D);
        Zombie outside = spawnedZombie(helper, player.getX() + 6.0D, player.getY(), player.getZ());

        ExpowheelEntity wheel = ExpowheelEntity.launch(helper.getLevel(), player);
        helper.assertTrue(
                wheel.getOwner() == player, "the wheel must be owned by the test player, got " + wheel.getOwner());
        wheel.setDeltaMovement(0.0D, 0.0D, TEST_SPEED);
        StringBuilder trace = new StringBuilder("inside=")
                .append(inside.getBoundingBox().getCenter())
                .append(" alive=")
                .append(inside.isAlive())
                .append(" outside=")
                .append(outside.getBoundingBox().getCenter())
                .append(" shooter=")
                .append(player.position())
                .append(" shooterAlive=")
                .append(player.isAlive());
        for (int tick = 0; tick < 12 && inside.getHealth() == ZOMBIE_HEALTH; tick++) {
            wheel.tick();
        }
        trace.append(" | end pos=")
                .append(wheel.position())
                .append(" vel=")
                .append(wheel.getDeltaMovement())
                .append(" removed=")
                .append(wheel.isRemoved());
        helper.assertTrue(
                inside.getHealth() < ZOMBIE_HEALTH,
                "the enemy inside the 20 degree cone must be the one the wheel goes for, it took no damage: " + trace);
        helper.assertTrue(
                outside.getHealth() == ZOMBIE_HEALTH,
                "the closer enemy outside the cone must be ignored, it lost " + (ZOMBIE_HEALTH - outside.getHealth()));
        inside.discard();
        outside.discard();
        wheel.discard();
        helper.succeed();
    }

    /**
     * 索敌必须看得见：锥内那只躲在墙后面时，第 1 档不锁它，退到第 2 档去抓旁边那只看得见的
     *
     * <p>发射者朝 +Z 看：锥内 6 格处一只、正中间砌一道三格高的石墙；锥外 6 格处再放一只一眼就能看到的。
     * 飞轮头 4 tick 的航向必须朝侧面那只拐过去（{@code x} 分量过半）——第 1 档要是穿墙锁人，
     * 航向会一直是 +Z，朝墙里钻
     */
    @GameTest(template = "ddt_empty")
    public static void wheelNeedsLineOfSightForTheCone(GameTestHelper helper) {
        clearEnemies(helper);
        Player player = aimPlayer(helper);
        Zombie behindWall = spawnedZombie(helper, player.getX(), player.getY(), player.getZ() + 6.0D);
        Zombie visible = spawnedZombie(helper, player.getX() + 6.0D, player.getY(), player.getZ());
        // 替身玩家站在原点 +3.5，墙砌在中间那一列（x = 3、z = 6），三格高足够挡住眼睛到目标中心那条线
        for (int layer = 0; layer < 3; layer++) {
            helper.setBlock(new BlockPos(3, 1 + layer, 6), Blocks.STONE);
        }
        StringBuilder trace = new StringBuilder("behindWall=")
                .append(behindWall.getBoundingBox().getCenter())
                .append(" visible=")
                .append(visible.getBoundingBox().getCenter())
                .append(" shooter=")
                .append(player.position());

        Vec3 direction = directionAfterTicks(helper, player, 4, trace);
        for (int layer = 0; layer < 3; layer++) {
            helper.setBlock(new BlockPos(3, 1 + layer, 6), Blocks.AIR);
        }
        helper.assertTrue(
                direction.x > 0.5D,
                "the wheel must go for the enemy it can see and never through a wall, its direction is " + direction
                        + trace);
        behindWall.discard();
        visible.discard();
        helper.succeed();
    }

    /**
     * 第二档索敌：锥内没有目标时，取「弹体附近 24 格」与「发射者附近 24 格」并集里最近的一只
     *
     * <p>发射者朝 +Z 看、僵尸在 45 度方向（锥外、7 格）。飞轮被摆成先朝 +Z 飞，4 tick 后看它拐了多少：
     * 有僵尸时航向会朝 +X 偏出 30 度以上（{@code x} 分量过半），同一套摆位不放僵尸时那条只会沿视线直飞，
     * {@code x} 分量仍是 0
     *
     * <p>量的是「航向拐了多少」而不是「与僵尸的夹角」：僵尸离得近，4 tick 就能飞过它的头顶，
     * 之后夹角反而会变大，量出来的是「已经飞过头」而不是「没在瞄准」
     */
    @GameTest(template = "ddt_empty")
    public static void wheelFallsBackToTheNearestEnemyAround(GameTestHelper helper) {
        clearEnemies(helper);
        Player player = aimPlayer(helper);
        Zombie target = spawnedZombie(helper, player.getX() + 5.0D, player.getY(), player.getZ() + 5.0D);
        StringBuilder trace = new StringBuilder("target=")
                .append(target.getBoundingBox().getCenter())
                .append(" alive=")
                .append(target.isAlive())
                .append(" shooter=")
                .append(player.position())
                .append(" look=")
                .append(player.getViewVector(1.0F));

        Vec3 guided = directionAfterTicks(helper, player, 4, trace);
        target.discard();
        helper.assertTrue(
                guided.x > 0.5D,
                "the wheel must turn towards the enemy 45 degrees off the look, its direction is " + guided + trace);

        Vec3 straight = directionAfterTicks(helper, player, 4, trace);
        helper.assertTrue(
                Math.abs(straight.x) < 0.1D,
                "without an enemy the wheel must just fly along the sight line (+Z), its direction is " + straight
                        + trace);
        helper.succeed();
    }

    /**
     * 第三档：锥内与 24 格内都没有敌怪时，回落到 057 的视线制导
     *
     * <p>让发射者原地朝正上方看（视线是一条竖直的线），飞轮从这条线旁边 3 格处、沿线的方向放出去——
     * 「方向已经与视线平行、位置却偏在线外」。手动走 14 拍，横向偏距必须明显缩小
     */
    @GameTest(template = "ddt_empty")
    public static void wheelGuidesOntoTheSightLineWhenThereIsNoEnemy(GameTestHelper helper) {
        clearEnemies(helper);
        Player player = plainPlayer(helper);
        player.setXRot(-90.0F);
        player.setOldPosAndRot();
        Vec3 line = player.getViewVector(1.0F).normalize();

        ExpowheelEntity wheel = new ExpowheelEntity(ModEntities.EXPOWHEEL.get(), helper.getLevel());
        wheel.setOwner(player);
        Vec3 start = player.getEyePosition().add(3.0D, 6.0D, 0.0D);
        wheel.setPos(start.x, start.y, start.z);
        wheel.setDeltaMovement(line.scale(TEST_SPEED));
        helper.getLevel().addFreshEntity(wheel);

        double before = lateralDistance(wheel, player, line);
        for (int tick = 0; tick < 14 && !wheel.isRemoved(); tick++) {
            wheel.tick();
        }
        double after = lateralDistance(wheel, player, line);
        helper.assertFalse(wheel.isRemoved(), "nothing in the way, the wheel must still be flying");
        helper.assertTrue(
                after < before * 0.5D,
                "the wheel must be pulled onto the sight line, lateral offset went from " + before + " to " + after);
        wheel.discard();
        helper.succeed();
    }

    /**
     * 几何、动画、贴图、物品模型、语言键与两条音效：缺一件客户端那边就是一个紫黑格或原始键
     *
     * <p>贴图不另画一份，用的是「测试恶魂591」（{@code tower_ghast} 模型包）的整张 256×256 贴图，
     * 与几何里写的 {@code texture_width/height} 对得上
     */
    @GameTest(template = "ddt_empty")
    public static void wheelResourcesAndSoundsArePinned(GameTestHelper helper) {
        for (String resource : List.of(
                "/assets/dawnduskterminal/textures/item/expowheel_fleshbook.png",
                "/assets/dawnduskterminal/models/item/expowheel_fleshbook.json",
                "/assets/dawnduskterminal/geo/expowheel.geo.json",
                "/assets/dawnduskterminal/animations/expowheel.animation.json",
                "/assets/dawnduskterminal/ysm/tower_ghast/textures/ghast.png",
                "/assets/dawnduskterminal/sounds/expowheel_shoot.ogg",
                "/assets/dawnduskterminal/sounds/expowheel_ambient.ogg")) {
            helper.assertTrue(
                    ExpowheelTests.class.getResource(resource) != null,
                    resource + " must ship inside the mod resources");
        }

        JsonObject model = readJson("/assets/dawnduskterminal/models/item/expowheel_fleshbook.json");
        helper.assertTrue(
                model.get("parent").getAsString().equals("minecraft:item/generated"),
                "item/expowheel_fleshbook must stay on minecraft:item/generated, got " + model.get("parent"));
        helper.assertTrue(
                model.getAsJsonObject("textures")
                        .get("layer0")
                        .getAsString()
                        .equals("dawnduskterminal:item/expowheel_fleshbook"),
                "item/expowheel_fleshbook must read its own texture, got " + model.getAsJsonObject("textures"));

        JsonObject geometry = readJson("/assets/dawnduskterminal/geo/expowheel.geo.json");
        JsonObject description = geometry.getAsJsonArray("minecraft:geometry")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("description");
        helper.assertTrue(
                description.get("texture_width").getAsInt() == 256
                        && description.get("texture_height").getAsInt() == 256,
                "the source geometry is laid out for a 256x256 sheet like the tower ghast one, got "
                        + description.get("texture_width") + "x" + description.get("texture_height"));

        JsonObject animation = readJson("/assets/dawnduskterminal/animations/expowheel.animation.json");
        JsonObject bones = animation
                .getAsJsonObject("animations")
                .getAsJsonObject("parallel0")
                .getAsJsonObject("bones");
        helper.assertTrue(
                bones.has("RightMagic") && bones.has("ysmGlowIn") && bones.has("ysmGlowQ"),
                "the source clip parallel0 must drive the magic bone and the glow rims, got " + bones.keySet());

        JsonObject soundEvents = readJson("/assets/dawnduskterminal/sounds.json");
        helper.assertTrue(
                soundEvents.has("expowheel_shoot") && soundEvents.has("expowheel_ambient"),
                "sounds.json must declare both events, got its keys " + soundEvents.keySet());
        helper.assertTrue(
                ModSounds.EXPOWHEEL_SHOOT.get().getLocation().equals(DawnDuskTerminal.id("expowheel_shoot")),
                "the shoot event must be registered as dawnduskterminal:expowheel_shoot");
        helper.assertTrue(
                ModSounds.EXPOWHEEL_AMBIENT.get().getLocation().equals(DawnDuskTerminal.id("expowheel_ambient")),
                "the ambient event must be registered as dawnduskterminal:expowheel_ambient");

        for (String lang : new String[] {"en_us", "zh_cn"}) {
            JsonObject words = readJson("/assets/dawnduskterminal/lang/" + lang + ".json");
            helper.assertTrue(words.has("item.dawnduskterminal.expowheel_fleshbook"), lang + " must name the item");
            helper.assertTrue(
                    words.has("subtitles.dawnduskterminal.expowheel_shoot"), lang + " must carry the shoot subtitle");
            helper.assertTrue(
                    words.has("subtitles.dawnduskterminal.expowheel_ambient"),
                    lang + " must carry the ambient subtitle");
        }
        helper.succeed();
    }

    /**
     * 发射一片飞轮、朝 +Z 飞 {@code ticks} tick，返回它最后的航向
     *
     * <p>不带敌怪时这条只会沿视线（也是 +Z）直飞，航向的 {@code x} 分量是 0；有敌怪时会被拐过去
     */
    private static Vec3 directionAfterTicks(GameTestHelper helper, Player player, int ticks, StringBuilder trace) {
        ExpowheelEntity wheel = ExpowheelEntity.launch(helper.getLevel(), player);
        wheel.setDeltaMovement(0.0D, 0.0D, TEST_SPEED);
        trace.append(" | spawn=")
                .append(wheel.position())
                .append(" owner=")
                .append(wheel.getOwner())
                .append(" shooterAlive=")
                .append(player.isAlive());
        for (int tick = 0; tick < ticks && !wheel.isRemoved(); tick++) {
            wheel.tick();
        }
        Vec3 direction = wheel.getDeltaMovement().normalize();
        trace.append(" | end pos=").append(wheel.position()).append(" dir=").append(direction);
        wheel.discard();
        return direction;
    }

    /** 弹体到「眼睛 + t × 视线」这条射线的垂直偏距 */
    private static double lateralDistance(ExpowheelEntity wheel, Player player, Vec3 line) {
        Vec3 offset = wheel.position().subtract(player.getEyePosition());
        return offset.subtract(line.scale(offset.dot(line))).length();
    }

    /** 一只站住不动的僵尸：无重力、无 AI、护甲清零 */
    private static Zombie spawnedZombie(GameTestHelper helper, double x, double y, double z) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            throw new AssertionError("minecraft:zombie could not be created");
        }
        zombie.setPersistenceRequired();
        zombie.moveTo(x, y, z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(zombie);
        zombie.setNoGravity(true);
        zombie.setNoAi(true);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0D);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ZOMBIE_HEALTH);
        zombie.setHealth(ZOMBIE_HEALTH);
        return zombie;
    }

    /** 把飞轮摆到僵尸正前方 {@value #WHEEL_START_DISTANCE} 格、朝它飞 */
    private static void aimAt(ExpowheelEntity wheel, Zombie target) {
        wheel.setPos(target.getX(), target.getY() + 1.0D, target.getZ() - WHEEL_START_DISTANCE);
        wheel.setDeltaMovement(0.0D, 0.0D, TEST_SPEED);
    }

    /** 场地里现存的飞轮 */
    private static List<ExpowheelEntity> wheels(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ExpowheelEntity.class, new AABB(origin).inflate(SEARCH_RADIUS));
    }

    /** 清掉场地附近的敌怪：单独跑这一批时，上一批用例留下的僵尸不该被第 1 档的锥顺手抓走 */
    private static void clearEnemies(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (LivingEntity enemy : helper.getLevel()
                .getEntitiesOfClass(
                        LivingEntity.class, new AABB(origin).inflate(CLEAR_RADIUS), e -> e instanceof Enemy)) {
            enemy.discard();
        }
    }

    /** 一个站在场地内部、朝 +Z（yaw 0）、手里拿着博览轮舞的替身玩家 */
    private static Player aimPlayer(GameTestHelper helper) {
        Player player = plainPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModExperimentItems.EXPOWHEEL_FLESHBOOK.get()));
        return player;
    }

    /** 空着手的替身玩家，站在场地内部（原点再进 3.5 格，避开角落的屏障方块） */
    private static Player plainPlayer(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(origin.getX() + 3.5D, origin.getY() + 1.0D, origin.getZ() + 3.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setYBodyRot(0.0F);
        player.setOldPosAndRot();
        return player;
    }

    /** 从 mod 资源里读一份 JSON：路径以 {@code /} 开头，相对于 jar 根 */
    private static JsonObject readJson(String resource) {
        try (InputStream stream = ExpowheelTests.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError(resource + " must ship inside the mod resources");
            }
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException problem) {
            throw new AssertionError("reading " + resource + " failed", problem);
        }
    }

    private ExpowheelTests() {}
}
