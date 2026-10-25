package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.StringEntity;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 「弦」的数值 液面判定与移动
 *
 * <p>姿态与渲染是客户端的事，这里只钉住服务端看得见的部分：变种属性、四类免疫、
 * 液面识别、近战不产生击退，以及贴着液面滑行时「每 tick 都推」与「推进量跟速度属性走」
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class StringEntityTests {
    @GameTest(template = "ddt_empty")
    public static void variantStatsScaleWithGeometry(GameTestHelper helper) {
        StringEntity small = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        small.setVariant(false, 1.0F);
        helper.assertTrue(close(small.getMaxHealth(), 60.0F),
            "small string must have 60 health, got " + small.getMaxHealth());
        helper.assertTrue(close((float) small.getAttributeValue(Attributes.ATTACK_DAMAGE), 10.0F),
            "small string must have 10 attack");

        StringEntity large = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        large.setVariant(true, 1.0F);
        helper.assertTrue(close(large.getMaxHealth(), 150.0F),
            "large string must scale health by 2.5, got " + large.getMaxHealth());
        helper.assertTrue(close((float) large.getAttributeValue(Attributes.ATTACK_DAMAGE), 25.0F),
            "large string must scale attack by 2.5");
        helper.assertTrue(large.getBbWidth() > small.getBbWidth(),
            "large string must have a larger hitbox");

        StringEntity offset = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        offset.setVariant(false, 1.1F);
        helper.assertTrue(close(offset.getMaxHealth(), 66.0F),
            "random size offset must scale health, got " + offset.getMaxHealth());
        helper.succeed();
    }

    /**
     * 体型越大移速越快：大型基准是小型的 1.35 倍，同一档里随机体型也一起乘
     */
    @GameTest(template = "ddt_empty")
    public static void speedGrowsWithSize(GameTestHelper helper) {
        StringEntity small = variant(helper, false, 1.0F);
        StringEntity large = variant(helper, true, 1.0F);
        StringEntity bigger = variant(helper, false, 1.15F);

        double smallSpeed = small.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double largeSpeed = large.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double biggerSpeed = bigger.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.assertTrue(smallSpeed > 0.34D,
            "base speed must be above the old 0.34, got " + smallSpeed);
        helper.assertTrue(largeSpeed > smallSpeed,
            "a large string must be faster than a small one, got " + largeSpeed
                + " vs " + smallSpeed);
        helper.assertTrue(biggerSpeed > smallSpeed,
            "a bigger individual must be faster, got " + biggerSpeed + " vs " + smallSpeed);
        helper.assertTrue(Math.abs(small.getAttributeValue(Attributes.FLYING_SPEED) - smallSpeed)
                < 1.0E-9D
                && Math.abs(large.getAttributeValue(Attributes.FLYING_SPEED) - largeSpeed) < 1.0E-9D,
            "FLYING_SPEED must carry the same value as MOVEMENT_SPEED");
        helper.succeed();
    }

    /**
     * 滑行型只索敌脚边一格就有液体的玩家
     *
     * <p>两格以外的岸边玩家打不到，索敌了也只会被遛，所以直接放弃
     */
    @GameTest(template = "ddt_empty")
    public static void preyMustTouchTheFluid(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.PORTAL_FLUID.get());
        StringEntity glider = variant(helper, false, 1.0F);
        glider.setKind(StringEntity.Kind.SURFACE);

        helper.assertTrue(glider.isValidPrey(playerAt(helper, new BlockPos(3, 1, 2))),
            "a player with portal fluid one block from its feet must be valid prey");
        helper.assertTrue(glider.isValidPrey(playerAt(helper, new BlockPos(2, 1, 2))),
            "a player standing in the fluid must be valid prey");
        helper.assertFalse(glider.isValidPrey(playerAt(helper, new BlockPos(4, 1, 2))),
            "a player two blocks inland must not be targeted");
        helper.succeed();
    }

    /**
     * 两种新变种的索敌范围：飞行型不看流体，游动型只打水里；另外谁都不打非玩家
     */
    @GameTest(template = "ddt_empty")
    public static void variantPreyRanges(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.PORTAL_FLUID.get());
        StringEntity flyer = variant(helper, false, 1.0F);
        flyer.setKind(StringEntity.Kind.FLYER);
        StringEntity swimmer = variant(helper, false, 1.0F);
        swimmer.setKind(StringEntity.Kind.SWIMMER);

        Player inFluid = playerAt(helper, new BlockPos(2, 1, 2));
        Player onShore = playerAt(helper, new BlockPos(8, 1, 8));
        Player inAir = playerAt(helper, new BlockPos(5, 8, 5));

        helper.assertTrue(flyer.isValidPrey(onShore),
            "the flyer must target shore players, the fluid rule does not apply to it");
        helper.assertTrue(flyer.isValidPrey(inFluid), "the flyer must target players in the fluid");
        helper.assertTrue(flyer.isValidPrey(inAir), "the flyer must target airborne players");
        helper.assertTrue(swimmer.isValidPrey(inFluid),
            "the swimmer must target players in the fluid");
        helper.assertFalse(swimmer.isValidPrey(onShore),
            "the swimmer must ignore players standing on dry land");

        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "zombie entity type must create");
        helper.assertFalse(flyer.isValidPrey(zombie) || swimmer.isValidPrey(zombie),
            "no string variant may target anything but a player");
        helper.succeed();
    }

    /**
     * 滑行型挨重击会裂成一对：一低空飞行型 + 一游动型，自己消失
     *
     * <p>概率是 10%，所以把血量顶到一万再连打三百下：一次都不裂的概率约 2e-14，
     * 用它当确定性判据足够。每下之前把 {@code invulnerableTime} 清零——原版受击后有 20 tick 无敌帧，
     * 不清零的话同一 tick 里第二下起全被吃掉，等于只打了一下
     */
    @GameTest(template = "ddt_empty")
    public static void heavyDamageSplitsIntoPair(GameTestHelper helper) {
        StringEntity original = variant(helper, false, 1.0F);
        original.setKind(StringEntity.Kind.SURFACE);
        original.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000.0D);
        original.setHealth(10000.0F);
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        original.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(original);

        DamageSource heavy = helper.getLevel().damageSources().generic();
        for (int i = 0; i < 300 && !original.isRemoved(); i++) {
            original.invulnerableTime = 0;
            original.hurt(heavy, 3.0F);
        }
        helper.assertTrue(original.isRemoved(),
            "a heavy hit must eventually split the surface string in two");

        List<StringEntity> found = new ArrayList<>(helper.getLevel().getEntitiesOfClass(
            StringEntity.class, new AABB(at).inflate(6.0D)));
        boolean hasFlyer = found.stream()
            .anyMatch(entity -> entity.getKind() == StringEntity.Kind.FLYER);
        boolean hasSwimmer = found.stream()
            .anyMatch(entity -> entity.getKind() == StringEntity.Kind.SWIMMER);
        helper.assertTrue(hasFlyer && hasSwimmer,
            "the split must leave one flyer and one swimmer, got " + found.size() + " string(s)");
        helper.succeed();
    }

    /** 2 点及以下的伤害不触发分裂：连着打十下，形态不变 */
    @GameTest(template = "ddt_empty")
    public static void lightDamageNeverSplits(GameTestHelper helper) {
        StringEntity string = variant(helper, false, 1.0F);
        string.setKind(StringEntity.Kind.SURFACE);
        string.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000.0D);
        string.setHealth(10000.0F);
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        string.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(string);

        DamageSource light = helper.getLevel().damageSources().generic();
        for (int i = 0; i < 10; i++) {
            string.invulnerableTime = 0;
            string.hurt(light, 2.0F);
        }
        helper.assertTrue(!string.isRemoved() && string.getKind() == StringEntity.Kind.SURFACE,
            "damage of 2 or less must never split the string");
        helper.succeed();
    }

    /**
     * 变种装的确实是原版那两套运动系统
     *
     * <p>飞行型必须拿到 {@code FlyingMoveControl} 与 {@code FlyingPathNavigation}（蜜蜂用的同一对），
     * 游动型不能是飞行那套；三个变种的控制器互不相同，说明 {@code applyKind} 真的按变种换过
     */
    @GameTest(template = "ddt_empty")
    public static void variantsUseVanillaMovement(GameTestHelper helper) {
        StringEntity glider = variant(helper, false, 1.0F);
        glider.setKind(StringEntity.Kind.SURFACE);
        StringEntity flyer = variant(helper, false, 1.0F);
        flyer.setKind(StringEntity.Kind.FLYER);
        StringEntity swimmer = variant(helper, false, 1.0F);
        swimmer.setKind(StringEntity.Kind.SWIMMER);

        helper.assertTrue(flyer.getMoveControl() instanceof FlyingMoveControl,
            "the flyer must fly on vanilla FlyingMoveControl, got "
                + flyer.getMoveControl().getClass().getSimpleName());
        helper.assertTrue(flyer.getNavigation() instanceof FlyingPathNavigation,
            "the flyer must path with FlyingPathNavigation");
        helper.assertTrue(!(swimmer.getMoveControl() instanceof FlyingMoveControl),
            "the swimmer must not use the flying control");
        helper.assertTrue(glider.getMoveControl() != flyer.getMoveControl()
                && glider.getMoveControl() != swimmer.getMoveControl()
                && swimmer.getMoveControl() != flyer.getMoveControl(),
            "each variant must get its own control");
        helper.succeed();
    }

    /**
     * 刷怪蛋只认传送门流体
     *
     * <p>蛋的三条发放路径（点方块、点流体、发射器）最后都走 {@link StringEntity#canSpawnAt}，
     * 这里钉判定本身：落点是流体、或落点下方是流体才算数，干地上直接拒
     */
    @GameTest(template = "ddt_empty")
    public static void spawnEggOnlyOnPortalFluid(GameTestHelper helper) {
        BlockPos fluid = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.PORTAL_FLUID.get());
        BlockPos shore = helper.absolutePos(new BlockPos(9, 1, 9));

        helper.assertTrue(StringEntity.canSpawnAt(helper.getLevel(), fluid),
            "a spawn egg must be accepted inside the portal fluid");
        helper.assertTrue(StringEntity.canSpawnAt(helper.getLevel(), fluid.above()),
            "a spawn egg must be accepted in the air right above the portal fluid");
        helper.assertFalse(StringEntity.canSpawnAt(helper.getLevel(), shore),
            "a spawn egg must be refused on dry land");
        helper.succeed();
    }

    private static StringEntity variant(GameTestHelper helper, boolean large, float scale) {
        StringEntity entity = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        entity.setVariant(large, scale);
        return entity;
    }

    /** 站（或悬）在相对坐标 {@code relative} 上的模拟玩家，生存模式 */
    private static Player playerAt(GameTestHelper helper, BlockPos relative) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return player;
    }

    /**
     * 飞行型不吃重力也不吃摔伤（照 {@code FlyingMob}）
     *
     * <p>先把 {@code noGravity} 故意关掉——模拟重力标记被读档/NBT/别的代码改掉的情况：
     * 照 {@code FlyingMob#travel} 的写法，没有输入时它一格都不该动；然后每 tick 硬压着它往下砸，
     * 落到石头上时 {@code fallDistance} 必须是 0、血量必须不变（{@code checkFallDamage} 空实现）
     */
    @GameTest(template = "ddt_empty")
    public static void flyerIgnoresGravityAndFallDamage(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        StringEntity flyer = variant(helper, false, 1.0F);
        flyer.setKind(StringEntity.Kind.FLYER);
        flyer.setNoGravity(false);
        BlockPos at = helper.absolutePos(new BlockPos(4, 3, 4));
        flyer.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(flyer);

        double startY = flyer.getY();
        for (int i = 0; i < 20; i++) {
            flyer.travel(Vec3.ZERO);
        }
        helper.assertTrue(Math.abs(flyer.getY() - startY) < 1.0E-6D,
            "the flyer must not sink with no input, moved " + (flyer.getY() - startY));

        float health = flyer.getHealth();
        for (int i = 0; i < 40 && !flyer.onGround(); i++) {
            flyer.setDeltaMovement(0.0D, -0.8D, 0.0D);
            flyer.travel(Vec3.ZERO);
        }
        helper.assertTrue(flyer.onGround(), "the flyer must have landed on the stone floor");
        helper.assertTrue(flyer.fallDistance <= 0.0F,
            "the flyer must not accumulate fall distance, got " + flyer.fallDistance);
        helper.assertTrue(flyer.getHealth() == health,
            "the flyer must take no fall damage, health went from " + health
                + " to " + flyer.getHealth());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void immunities(GameTestHelper helper) {
        StringEntity string = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        string.setVariant(false, 1.0F);
        helper.assertTrue(string.fireImmune(), "string must be fire immune");
        helper.assertTrue(!string.canBeAffected(new MobEffectInstance(MobEffects.POISON)),
            "string must ignore potion effects");
        helper.assertTrue(string.isInvulnerableTo(
                helper.getLevel().damageSources().source(ModDamageTypes.STABILITY)),
            "portal fluid damage must not hurt string");
        helper.assertTrue(!string.isPushable() && !string.isPushedByFluid(),
            "string must ignore entity pushing and fluid currents");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void surfaceIsTheOpenFaceOfPortalFluid(GameTestHelper helper) {
        BlockPos fluid = new BlockPos(2, 1, 2);
        helper.setBlock(fluid, ModBlocks.PORTAL_FLUID.get());
        // 结构坐标是相对的 静态工具方法吃的是世界坐标
        BlockPos absolute = helper.absolutePos(fluid);
        // 1.21 的源流体高度是 8/9 格，不是整块
        double expected = absolute.getY()
            + helper.getLevel().getFluidState(absolute).getHeight(helper.getLevel(), absolute)
            + StringEntity.SURFACE_OFFSET;
        int nearY = absolute.getY() + 1;
        double surface = StringEntity.findFluidSurface(helper.getLevel(),
            absolute.getX(), absolute.getZ(), nearY);
        helper.assertTrue(!Double.isNaN(surface) && Math.abs(surface - expected) < 1.0E-4D,
            "open portal fluid must expose a surface just above the fluid, got " + surface);

        helper.setBlock(fluid.above(), Blocks.STONE);
        double covered = StringEntity.findFluidSurface(helper.getLevel(),
            absolute.getX(), absolute.getZ(), nearY);
        helper.assertTrue(Double.isNaN(covered),
            "a sealed aquifer must not count as a surface, got " + covered);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void meleeDoesNotKnockBack(GameTestHelper helper) {
        StringEntity attacker = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        attacker.setVariant(false, 1.0F);
        StringEntity victim = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        victim.setVariant(false, 1.0F);
        victim.setDeltaMovement(0.5D, 0.0D, 0.5D);

        float before = victim.getHealth();
        helper.assertTrue(attacker.doHurtTarget(victim), "attack must land");
        helper.assertTrue(victim.getHealth() < before, "attack must deal damage");
        helper.assertTrue(Math.abs(victim.getDeltaMovement().x) < 1.0E-6D
                && Math.abs(victim.getDeltaMovement().z) < 1.0E-6D,
            "attack must not add horizontal knockback");
        helper.succeed();
    }

    /**
     * 移动：目标点在够远的地方时，控制器每 tick 都推一次
     *
     * <p>钉的是「一直推」。原版 {@code MoveControl} 一进 {@code MOVE_TO} 就把自己改回 {@code WAIT}
     * —— 它假设行为层每 tick 都重给一次目标点（{@code PathNavigation} 正是这么做的），而这里的行为
     * 每 8 到 17 tick 才给一次。照抄原版就变成「推一 tick、滑一程」：30 tick 只走 0.2 格上下，
     * 肉眼是原地打转
     *
     * <p>这里不走实体自己的 {@code tick()}：行为层会随机换目标点，行程就不可复现。手动跑
     * 「控制器写速度 → 走一遍原版 air 分支」两个动作，等价于 {@code Mob#serverAiStep} 里那两步
     */
    @GameTest(template = "ddt_empty")
    public static void thrustsEveryTickUntilArrival(GameTestHelper helper) {
        StringEntity string = hoveringString(helper, new BlockPos(3, 1, 3));
        double startX = string.getX();
        double startZ = string.getZ();
        BlockPos target = helper.absolutePos(new BlockPos(13, 1, 13));
        string.getMoveControl().setWantedPosition(target.getX(), string.getY(), target.getZ(), 1.15D);

        for (int i = 0; i < 30; i++) {
            glideOneTick(string);
        }

        double dx = string.getX() - startX;
        double dz = string.getZ() - startZ;
        double travelled = Math.sqrt(dx * dx + dz * dz);
        helper.assertTrue(travelled >= 2.5D,
            "the move control must thrust every tick until arrival, 30 ticks gave only "
                + travelled + " blocks");
        helper.succeed();
    }

    /** 到达以后就停住：不会在目标点附近来回抖 */
    @GameTest(template = "ddt_empty")
    public static void stopsAfterArrival(GameTestHelper helper) {
        StringEntity string = hoveringString(helper, new BlockPos(8, 1, 8));
        double startX = string.getX();
        double startZ = string.getZ();
        string.getMoveControl().setWantedPosition(startX + 0.2D, string.getY(), startZ, 1.15D);

        for (int i = 0; i < 10; i++) {
            glideOneTick(string);
        }

        double dx = string.getX() - startX;
        double dz = string.getZ() - startZ;
        double travelled = Math.sqrt(dx * dx + dz * dz);
        helper.assertTrue(!string.getMoveControl().hasWanted(),
            "the control must go back to WAIT once the string arrives");
        helper.assertTrue(travelled < 0.05D,
            "a hover target closer than the arrival distance must not move the string, got " + travelled);
        helper.succeed();
    }

    /**
     * 空中推进量取自速度属性，不是原版那个常量
     *
     * <p>{@code LivingEntity#getFlyingSpeed} 对非玩家操控的生物返回恒定的 0.02，
     * 速度属性完全不参与，于是大小变种的差异与「追击比巡航快」那一档全部失效。
     * 这条用移动速度翻倍做对照：推进量应当跟着翻倍；实现写死常量时两个值是 1:1
     */
    @GameTest(template = "ddt_empty")
    public static void airThrustFollowsMovementSpeed(GameTestHelper helper) {
        double normal = firstTickGlide(helper, new BlockPos(3, 1, 3), 1.0D);
        double doubled = firstTickGlide(helper, new BlockPos(12, 1, 12), 2.0D);
        helper.assertTrue(normal > 0.0D, "the string must move at all, got " + normal);
        double ratio = doubled / normal;
        helper.assertTrue(Math.abs(ratio - 2.0D) < 0.05D,
            "air thrust must scale with MOVEMENT_SPEED, moving twice as fast gave ratio " + ratio);
        helper.succeed();
    }

    /** 造一只站在 {@code relative} 处、不上行为层、没有初速的「弦」 */
    private static StringEntity hoveringString(GameTestHelper helper, BlockPos relative) {
        StringEntity string = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        string.setVariant(false, 1.0F);
        BlockPos absolute = helper.absolutePos(relative);
        string.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        string.setNoGravity(true);
        string.setDeltaMovement(Vec3.ZERO);
        return string;
    }

    /**
     * 一个 tick 的推进，顺序与原版一致
     *
     * <p>先是 {@code Mob#serverAiStep} 末尾的 {@code moveControl.tick()}（写 speed 与 zza），
     * 再是 {@code LivingEntity#aiStep} 末尾的 {@code travel}；那之前原版把 zza 乘了 0.98
     */
    private static void glideOneTick(StringEntity string) {
        string.getMoveControl().tick();
        string.travel(new Vec3(0.0D, 0.0D, string.zza * 0.98F));
    }

    /** 只跑第一个 tick 的位移，用来比较不同速度属性下的推进量 */
    private static double firstTickGlide(GameTestHelper helper, BlockPos relative, double speedScale) {
        StringEntity string = hoveringString(helper, relative);
        string.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34D * speedScale);
        double startX = string.getX();
        double startZ = string.getZ();
        string.getMoveControl().setWantedPosition(startX + 6.0D, string.getY(), startZ, 1.15D);
        glideOneTick(string);
        double dx = string.getX() - startX;
        double dz = string.getZ() - startZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean close(float value, float expected) {
        return Math.abs(value - expected) < 1.0E-3F;
    }
}
