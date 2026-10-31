package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.entity.GhastElitePopulation;
import com.dawnduskterminal.entity.GhastFruityEntity;
import com.dawnduskterminal.entity.GhastKin;
import com.dawnduskterminal.entity.GhastFruityPopulation;
import com.dawnduskterminal.item.FireJetProcess;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import twilightforest.init.TFEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.init.TFBlocks;

/**
 * 「恶果」的几件事：配置（数值、尺寸、生成、还手距离、暮色火球的继承关系）、生成表、接住火球之后往哪飞
 *
 * <p>接火球这一条不依赖真实弹道：{@code ProjectileImpactEvent} 就是 {@code LargeFireball} 撞击时
 * 服务端唯一的那处分叉，直接构造一个事件交给处理器，走的正是实机里的那条路径
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class GhastFruityTests {
    /**
     * 一整套配置钉在这里：生命值 30、索敌 24 格、碰撞箱照 {@code Root_spin_M} 的 1.5 格、
     * 一次只生一只（加载范围内最多 4 只）、火球从脸前 1.3 格出膛，外加两条依附于它的约定
     *
     * <p>尺寸是硬指标：模型给的几何不做任何缩放，碰撞箱就是那个 24 单位见方的立方体
     *
     * <p>「24 格是索敌距离，不是还手距离」：原版 {@code HurtByTargetGoal} 只收 {@code PathfinderMob}，
     * 恶魂是 {@code FlyingMob}，所以这条只能由实体自己的 {@code hurt} 补上；这里把攻击者放在 44 格开外
     * 验证它仍然成为目标
     *
     * <p>暮色森林的恶魂火球也要能被拨开：反编译 4.8.3345 可知砷铅铁恶灵与守卫的
     * {@code GhastguardAttackGoal} 直接 {@code new} 原版 {@code LargeFireball}，乌尔恶魂用的是
     * {@code twilightforest.entity.projectile.UrGhastFireball}，而那个类继承自 {@code LargeFireball}。
     * 反弹只认 {@code instanceof LargeFireball}，这里把该继承关系钉住，暮色森林以后改基类时先在这里炸
     */
    @GameTest(template = "ddt_empty")
    public static void statsAreTheConfiguredOnes(GameTestHelper helper) {
        GhastFruityEntity ghast = new GhastFruityEntity(ModEntities.GHASTFRUITY.get(), helper.getLevel());
        ghast.setPos(4.0D, 2.0D, 4.0D);

        helper.assertTrue(ghast.getMaxHealth() == 30.0F,
            "ghastfruity must have 30 max health, was " + ghast.getMaxHealth());
        helper.assertTrue(ghast.getAttributeValue(Attributes.FOLLOW_RANGE) == 24.0D,
            "ghastfruity must use the normal 24 block follow range, was "
                + ghast.getAttributeValue(Attributes.FOLLOW_RANGE));
        helper.assertTrue(ghast.getType().getDimensions().width() == 1.5F
                && ghast.getType().getDimensions().height() == 1.5F,
            "the hitbox must match Root_spin_M (24 units = 1.5 blocks), was "
                + ghast.getType().getDimensions().width() + "x" + ghast.getType().getDimensions().height());
        helper.assertTrue(ghast.getMaxSpawnClusterSize() == 1,
            "a natural batch must be a single ghastfruity (the 4 cap is a population cap), was "
                + ghast.getMaxSpawnClusterSize());
        helper.assertTrue(ghast.getType().fireImmune(), "a fireball spitting ghast must be immune to fire");
        helper.assertTrue(ghast.getType().getCategory() == MobCategory.MONSTER,
            "ghastfruity must spawn through the monster channel");

        Player far = helper.makeMockPlayer(GameType.SURVIVAL);
        far.setPos(4.5D, 2.0D, 48.5D);
        helper.assertTrue(far.distanceTo(ghast) > 24.0D,
            "sanity: the attacker must stand outside the follow range, was " + far.distanceTo(ghast));
        ghast.hurt(ghast.damageSources().playerAttack(far), 1.0F);
        helper.assertTrue(ghast.getTarget() == far,
            "an attack from beyond the 24 block follow range must still make the ghastfruity retaliate, got "
                + ghast.getTarget());

        Class<?> urGhastFireball = classOrNull("twilightforest.entity.projectile.UrGhastFireball");
        helper.assertTrue(urGhastFireball != null,
            "Twilight Forest is required at runtime, so twilightforest.entity.projectile.UrGhastFireball must exist");
        helper.assertTrue(LargeFireball.class.isAssignableFrom(urGhastFireball),
            "the ur-ghast fireball must stay a LargeFireball, otherwise the ghastfruity stops deflecting it");

        // 出膛点：脸的正面在体中心前 0.75 格，球口贴着它；原版恶魂那 4 格是给 4 格见方的身体写的
        ghast.setPos(0.0D, 2.0D, 0.0D);
        ghast.setYRot(90.0F);
        ghast.setXRot(0.0F);
        Vec3 mouth = GhastFruityEntity.mouthPosition(ghast);
        Vec3 view = ghast.getViewVector(1.0F);
        Vec3 horizontal = mouth.subtract(ghast.getX(), mouth.y, ghast.getZ());
        helper.assertTrue(Math.abs(mouth.y - (ghast.getY() + 0.75D)) < 1.0E-6D,
            "the fireball must leave from the face centre (0.75 above the feet), was " + mouth);
        helper.assertTrue(Math.abs(horizontal.length() - 1.3D) < 1.0E-4D,
            "the fireball must leave 1.3 blocks ahead of the body centre (the face is at 0.75), was " + mouth);
        helper.assertTrue(horizontal.normalize().dot(view) > 0.9998D,
            "the mouth must sit in the facing direction (yRot=" + ghast.getYRot() + " view=" + view
                + "), was " + mouth);

        // 持块变体：手里那块方块只认暮色森林的三个 ID；重现方块那一只受击有四成概率整只消失
        helper.assertTrue(GhastFruityEntity.HeldBlock.REAPPEARING_BLOCK.block() == TFBlocks.REAPPEARING_BLOCK.get()
                && GhastFruityEntity.HeldBlock.ENCASED_FIRE_JET.block() == TFBlocks.ENCASED_FIRE_JET.get()
                && GhastFruityEntity.HeldBlock.GHAST_TRAP.block() == TFBlocks.GHAST_TRAP.get(),
            "the three held blocks must be the twilight forest ones");
        ghast.setHeldBlock(GhastFruityEntity.HeldBlock.REAPPEARING_BLOCK);
        helper.assertTrue(ghast.isBlockVariant(), "setting a held block must turn the entity into the block variant");
        boolean dodged = false;
        for (int attempt = 0; attempt < 200 && !dodged; attempt++) {
            ghast.invulnerableTime = 0;
            ghast.setHealth(30.0F);
            dodged = !ghast.hurt(ghast.damageSources().playerAttack(far), 1.0F);
        }
        helper.assertTrue(dodged, "the reappearing block variant must swallow some hits (40% each)");
        MobEffectInstance invisibility = ghast.getEffect(MobEffects.INVISIBILITY);
        helper.assertTrue(invisibility != null && !invisibility.isVisible(),
            "a swallowed hit must grant a particle-less invisibility, was " + invisibility);
        helper.assertTrue(invisibility.getDuration() > 0 && invisibility.getDuration() <= 100,
            "the invisibility must last 5 seconds, was " + invisibility.getDuration() + " ticks");
        ghast.setHeldBlock(GhastFruityEntity.HeldBlock.NONE);

        // 容量：玩家加载范围内最多 4 只。一批 4 只不算数，累计数量才是被卡住的那个
        helper.assertTrue(GhastFruityPopulation.CAP == 4,
            "the natural population cap must be 4, was " + GhastFruityPopulation.CAP);
        for (int i = 0; i < GhastFruityPopulation.CAP; i++) {
            var loaded = helper.spawnWithNoFreeWill(ModEntities.GHASTFRUITY.get(), 6, 2, 4 + i);
            loaded.setNoAi(true);
        }
        helper.assertTrue(GhastFruityPopulation.loaded(helper.getLevel()) == GhastFruityPopulation.CAP,
            "four loaded ghastfruitries must be counted, got "
                + GhastFruityPopulation.loaded(helper.getLevel()));
        helper.assertFalse(GhastFruityPopulation.tryReserve(helper.getLevel()),
            "a full loaded population must refuse further natural spawns");

        // 俯角：目标在下面时必须低头。喷火与陷阱的走廊沿着 getViewVector 走，
        // 只转偏航的话走廊永远是水平的，站在地上的目标只能蹭到边缘
        GhastFruityEntity aimer = (GhastFruityEntity) helper.spawn(ModEntities.GHASTFRUITY.get(), 4, 5, 4);
        Pig below = helper.spawn(EntityType.PIG, 12, 2, 4);
        aimer.setTarget(below);

        // 生成时抬高 0.75 格：几何脚下的裙摆要落在地面上，而不是埋进去。
        // 走真正的刷怪蛋路径（EntityType#spawn → finalizeSpawn），helper.spawn 不调 finalizeSpawn
        BlockPos eggPos = helper.absolutePos(new BlockPos(2, 5, 2));
        Entity eggSpawned = ModEntities.GHASTFRUITY.get().spawn(helper.getLevel(),
            new ItemStack(ModItems.GHASTFRUITY_SPAWN_EGG.get()), null, eggPos, MobSpawnType.SPAWN_EGG, false, false);
        helper.assertTrue(eggSpawned instanceof GhastFruityEntity, "the spawn egg must place a ghastfruity");
        helper.assertTrue(Math.abs(eggSpawned.getY() - (eggPos.getY() + 0.75D)) < 1.0E-4D,
            "the egg spawn must be lifted by the skirt depth (0.75), got y=" + eggSpawned.getY()
                + " for " + eggPos.getY());

        for (int tick = 0; tick < 5; tick++) {
            aimer.tick();
        }
        helper.assertTrue(aimer.getXRot() > 5.0F,
            "a lower target must give a real depression, got xRot=" + aimer.getXRot());

        // 大恶魂：250 血、索敌 100、碰撞箱 8 格、自然生成上限 1、生成抬高裙摆的 3 格
        helper.assertTrue(GhastEliteEntity.MAX_HEALTH == 250.0F,
            "the elite must have 250 health, was " + GhastEliteEntity.MAX_HEALTH);
        helper.assertTrue(GhastEliteEntity.FOLLOW_RANGE == 100.0D,
            "the elite must target within 100 blocks, was " + GhastEliteEntity.FOLLOW_RANGE);
        GhastEliteEntity elite = (GhastEliteEntity) helper.spawn(ModEntities.GHAST_ELITE.get(), 4, 5, 12);
        helper.assertTrue(elite.getMaxHealth() == 250.0F,
            "the elite attribute map must carry 250 max health, was " + elite.getMaxHealth());
        helper.assertTrue(elite.getAttributeValue(Attributes.FOLLOW_RANGE) == 100.0D,
            "the elite follow range must be 100, was " + elite.getAttributeValue(Attributes.FOLLOW_RANGE));
        helper.assertTrue(elite.getBbWidth() == 8.0F && elite.getBbHeight() == 8.0F,
            "the elite hitbox must match thehead (8x8x8), was " + elite.getBbWidth() + "x" + elite.getBbHeight());
        helper.assertTrue(elite.getMaxSpawnClusterSize() == 1, "the elite must spawn one at a time");
        helper.assertTrue(GhastElitePopulation.CAP == 1,
            "the elite natural cap must be 1, was " + GhastElitePopulation.CAP);
        helper.assertTrue(GhastElitePopulation.loaded(helper.getLevel()) == 1,
            "one loaded elite must be counted, got " + GhastElitePopulation.loaded(helper.getLevel()));
        helper.assertFalse(GhastElitePopulation.tryReserve(helper.getLevel()),
            "a loaded elite must refuse a second natural spawn");

        // 会飞：给出目标点之后必须真的挪动（无碰撞移动控制器）
        Vec3 eliteStart = elite.position();
        elite.getMoveControl().setWantedPosition(elite.getX() + 6.0D, elite.getY() + 4.0D, elite.getZ(), 1.0D);
        for (int tick = 0; tick < 30; tick++) {
            elite.tick();
        }
        helper.assertTrue(elite.position().distanceToSqr(eliteStart) > 1.0D,
            "the elite must fly towards its waypoint, moved " + elite.position().distanceTo(eliteStart));

        // 索敌之后召唤迷你恶魂：六只 twilightforest:carminite_ghastling，并把目标共享给它们
        elite.setTarget(below);
        for (int tick = 0; tick < 5; tick++) {
            elite.tick();
        }
        EntityType<?> ghastlingType = TFEntities.CARMINITE_GHASTLING.get();
        List<Entity> minions = helper.getLevel().getEntitiesOfClass(Entity.class,
            new AABB(elite.blockPosition()).inflate(64.0D), entity -> entity.getType() == ghastlingType);
        helper.assertTrue(!minions.isEmpty(),
            "the elite must summon carminite ghastlings after acquiring a target");
        helper.assertTrue(minions.stream().anyMatch(minion -> minion instanceof Mob mob && mob.getTarget() == below),
            "the summoned minions must share the elite's target");

        // 泪滴柱：照暮色恶魂的公式，水平就是本体自己的 8 格宽，竖直 32 格加本体 8 格
        helper.assertTrue(Math.abs(elite.tearField().getXsize() - 8.0D) < 1.0E-6D,
            "the tear column must be as wide as the body, was " + elite.tearField().getXsize());
        helper.assertTrue(Math.abs(elite.tearField().getYsize() - 40.0D) < 1.0E-6D,
            "the tear column must reach 32 blocks down plus the 8 tall body, was " + elite.tearField().getYsize());

        // 小鬼的落脚点：水平 6 格，必须落在泪滴柱子（半宽 4 格）之外，否则会被吸掉
        Vec3 hold = GhastEliteEntity.holdPoint(Vec3.ZERO, new Vec3(40.0D, 0.0D, 0.0D));
        helper.assertTrue(Math.abs(hold.x - 6.0D) < 1.0E-6D && Math.abs(hold.z) < 1.0E-6D,
            "a minion 40 blocks out must be held 6 blocks out on the same side, got " + hold);
        helper.assertTrue(hold.x > 4.0D,
            "the hold point must be outside the 4 block tear half width, was " + hold.x);
        helper.assertTrue(GhastEliteEntity.holdPoint(Vec3.ZERO, Vec3.ZERO).horizontalDistance() > 4.0D,
            "a minion right on top of the elite must still be pushed outside the tear column");

        // 目标点埋在地里时不能永远推着不动：卡住 30 tick 之后把点丢掉、重挑一个
        double buriedY = elite.getY() - 12.0D;
        elite.getMoveControl().setWantedPosition(elite.getX() + 6.0D, buriedY, elite.getZ(), 1.0D);
        Vec3 pinned = elite.position();
        for (int tick = 0; tick < 45; tick++) {
            elite.setDeltaMovement(Vec3.ZERO);
            elite.setPos(pinned.x, pinned.y, pinned.z);
            elite.tick();
        }
        helper.assertTrue(elite.getMoveControl().getWantedY() != buriedY,
            "a waypoint it cannot reach must be dropped instead of pushed forever, still wanted y="
                + elite.getMoveControl().getWantedY());

        // 被召唤的小鬼不再挑人：玩家背对着它也该打；没标记的野生守卫仍按暮色森林的规则拒绝
        Player bystander = helper.makeMockPlayer(GameType.SURVIVAL);
        bystander.setPos(elite.getX(), elite.getY(), elite.getZ() + 6.0D);
        bystander.setYRot(0.0F);
        bystander.setXRot(0.0F);
        Entity marked = ghastlingType.create(helper.getLevel());
        helper.assertTrue(marked instanceof Mob, "a carminite ghastling must be a mob");
        marked.moveTo(elite.getX(), elite.getY(), elite.getZ());
        marked.getPersistentData().putBoolean(GhastEliteEntity.MINION_TAG, true);
        helper.getLevel().addFreshEntity(marked);
        Entity wild = ghastlingType.create(helper.getLevel());
        helper.assertTrue(wild instanceof Mob, "a carminite ghastling must be a mob");
        wild.moveTo(elite.getX(), elite.getY(), elite.getZ());
        helper.getLevel().addFreshEntity(wild);
        try {
            java.lang.reflect.Method shouldAttack = Class.forName(
                    "twilightforest.entity.monster.CarminiteGhastling")
                .getMethod("shouldAttack", LivingEntity.class);
            helper.assertTrue((Boolean) shouldAttack.invoke(marked, bystander),
                "a summoned minion must attack a target it can see, even when the player looks away");
            helper.assertFalse((Boolean) shouldAttack.invoke(wild, bystander),
                "a wild carminite ghastling must keep the vanilla guard rule");
        } catch (ReflectiveOperationException problem) {
            helper.assertTrue(false, "carminite ghastling hook: " + problem);
        }
        marked.discard();
        wild.discard();

        // 自己人：两种恶魂与小鬼互不算敌人，玩家不算
        Entity kinGhastling = ghastlingType.create(helper.getLevel());
        helper.assertTrue(kinGhastling != null, "a carminite ghastling must be creatable");
        helper.assertTrue(GhastKin.isKin(elite, kinGhastling),
            "the elite and a carminite ghastling must count as kin");
        helper.assertFalse(GhastKin.isKin(elite, below),
            "the elite and a player must not count as kin");

        // 同一个施放者不会叠第二道火焰；施放者没了过程当场收尾
        Player caster = helper.makeMockPlayer(GameType.SURVIVAL);
        FireJetProcess.start(helper.getLevel(), caster);
        FireJetProcess.start(helper.getLevel(), caster);
        helper.assertTrue(FireJetProcess.activeCount(caster) == 1,
            "a second jet on the same caster must reuse the running one, got " + FireJetProcess.activeCount(caster));
        caster.discard();
        FireJetProcess.tickAll();
        helper.assertTrue(FireJetProcess.activeCount(caster) == 0,
            "a jet must end as soon as its caster is gone");

        // 吃碰撞才吃火球：noPhysics 的实体不被任何弹体命中，拨弹会整条失效
        helper.assertFalse(elite.noPhysics,
            "the elite must collide, or AbstractHurtingProjectile#canHitEntity rejects it and the deflect never fires");

        // 经验与暮色恶魂的 UrGhast 同值
        helper.assertTrue(GhastEliteEntity.XP_REWARD == 317,
            "the elite must drop the UrGhast's 317 experience, was " + GhastEliteEntity.XP_REWARD);

        // 出膛点：斜 45 度时不能落在自己 8 格见方的箱子里
        elite.setYRot(45.0F);
        elite.setXRot(0.0F);
        Vec3 eliteMouth = GhastEliteEntity.mouthPosition(elite);
        double outward = Math.max(Math.abs(eliteMouth.x - elite.getX()), Math.abs(eliteMouth.z - elite.getZ()));
        helper.assertTrue(outward >= 4.0D,
            "a diagonal muzzle must clear the 8-wide hitbox, was " + outward + " blocks out");
        elite.setYRot(0.0F);

        // 泪滴落到小鬼身上才吸收：泪滴范围内的小鬼消失，本体回 2 点
        Entity dripping = ghastlingType.create(helper.getLevel());
        helper.assertTrue(dripping != null, "a carminite ghastling must be creatable");
        dripping.moveTo(elite.getX(), elite.getY() + 1.0D, elite.getZ());
        helper.getLevel().addFreshEntity(dripping);
        elite.setHealth(200.0F);
        for (int tick = 0; tick < 12; tick++) {
            elite.tick();
        }
        helper.assertTrue(dripping.isRemoved(),
            "a minion inside the tear field must be absorbed");
        helper.assertTrue(elite.getHealth() > 200.0F,
            "absorbing a minion must heal the elite, health was " + elite.getHealth());

        // 刷怪蛋路径：生成位置要抬高裙摆的 3 格
        BlockPos eliteEggPos = helper.absolutePos(new BlockPos(10, 5, 12));
        Entity eliteEgg = ModEntities.GHAST_ELITE.get().spawn(helper.getLevel(),
            new ItemStack(ModItems.GHAST_ELITE_SPAWN_EGG.get()), null, eliteEggPos,
            MobSpawnType.SPAWN_EGG, false, false);
        helper.assertTrue(eliteEgg instanceof GhastEliteEntity, "the elite spawn egg must place a ghast_elite");
        helper.assertTrue(Math.abs(eliteEgg.getY() - (eliteEggPos.getY() + 3.0D)) < 1.0E-4D,
            "the elite egg spawn must be lifted by the skirt depth (3.0), got y=" + eliteEgg.getY()
                + " for " + eliteEggPos.getY());

        // 创造模式手持方块右击 → 变成对应变体；生存模式不许变
        Item reappearing = TFBlocks.REAPPEARING_BLOCK.get().asItem();
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(reappearing));
        aimer.setHeldBlock(GhastFruityEntity.HeldBlock.NONE);
        helper.assertTrue(aimer.mobInteract(creative, InteractionHand.MAIN_HAND).consumesAction(),
            "a creative block click must consume the interaction");
        helper.assertTrue(aimer.getHeldBlock() == GhastFruityEntity.HeldBlock.REAPPEARING_BLOCK,
            "holding the reappearing block must turn the ghastfruity into that variant, got " + aimer.getHeldBlock());

        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(reappearing));
        aimer.setHeldBlock(GhastFruityEntity.HeldBlock.NONE);
        aimer.mobInteract(survival, InteractionHand.MAIN_HAND);
        helper.assertTrue(aimer.getHeldBlock() == GhastFruityEntity.HeldBlock.NONE,
            "a survival block click must not change the variant, got " + aimer.getHeldBlock());
        helper.succeed();
    }

    /** 生成表必须真的登记过，否则这一条在群系 JSON 里写了也不会生效 */
    @GameTest(template = "ddt_empty")
    public static void spawnPlacementIsRegistered(GameTestHelper helper) {
        helper.assertTrue(SpawnPlacements.hasPlacement(ModEntities.GHASTFRUITY.get()),
            "ghastfruity must have a spawn placement, otherwise natural spawning never even asks the rules");
        helper.succeed();
    }

    /**
     * 白桦林副本的 monster 生成表里必须有它
     *
     * <p>群系 JSON 里的 spawners 是数据包内容，写错 id 或写错分类时游戏不会报错，只是「刷不出来」；
     * 这里直接读注册表里的那一份群系，把列表翻出来对一遍
     */
    @GameTest(template = "ddt_empty")
    public static void oldGrowthBirchForestSpawnsIt(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Biome biome = biomes.get(DawnDuskTerminal.id("old_growth_birch_forest"));
        helper.assertTrue(biome != null,
            "the palette copy dawnduskterminal:old_growth_birch_forest must exist");
        boolean listed = biome.getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
            .anyMatch(data -> data.type == ModEntities.GHASTFRUITY.get());
        helper.assertTrue(listed,
            "dawnduskterminal:old_growth_birch_forest must list ghastfruity in its monster spawners, got "
                + biome.getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                    .map(data -> data.type + " x" + data.minCount + "-" + data.maxCount)
                    .toList());
        helper.succeed();
    }

    /** 没有索敌时沿来路弹回：速度方向取反，且挪到碰撞箱之外，免得下一 tick 又撞上自己 */
    @GameTest(template = "ddt_empty")
    public static void fireballBouncesBackWithoutTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GhastFruityEntity ghast = ghastAt(level);
        ghast.setTarget(null);

        LargeFireball fireball = incomingFireball(level, ghast);
        ProjectileImpactEvent event = new ProjectileImpactEvent(
            fireball, new EntityHitResult(ghast, ghast.position()));
        GhastFruityEntity.onProjectileImpact(event);

        helper.assertTrue(event.isCanceled(), "the impact must be cancelled, otherwise the fireball explodes on contact");
        Vec3 movement = fireball.getDeltaMovement();
        helper.assertTrue(Math.abs(movement.x) < 1.0E-6D && Math.abs(movement.y) < 1.0E-6D && movement.z > 0.9D,
            "a fireball that hits a targetless ghastfruity must fly straight back, was " + movement);
        helper.assertTrue(!ghast.getBoundingBox().contains(fireball.position()),
            "the bounced fireball must start outside the hitbox, was " + fireball.position());
        helper.succeed();
    }

    /** 有索敌时打向索敌对象的眼睛，并且这一发算恶果自己的攻击 */
    @GameTest(template = "ddt_empty")
    public static void fireballIsAimedAtTheCurrentTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GhastFruityEntity ghast = ghastAt(level);
        Pig pig = helper.spawn(EntityType.PIG, 12, 2, 8);
        ghast.setTarget(pig);

        LargeFireball fireball = incomingFireball(level, ghast);
        ProjectileImpactEvent event = new ProjectileImpactEvent(
            fireball, new EntityHitResult(ghast, ghast.position()));
        GhastFruityEntity.onProjectileImpact(event);

        Vec3 aim = pig.getEyePosition().subtract(ghast.getEyePosition()).normalize();
        Vec3 movement = fireball.getDeltaMovement().normalize();
        helper.assertTrue(aim.distanceToSqr(movement) < 1.0E-4D,
            "the deflected fireball must head for the current target, wanted " + aim + " got " + movement);
        helper.assertTrue(fireball.getOwner() == ghast,
            "the deflected fireball must count as the ghastfruity's own projectile");
        helper.succeed();
    }

    /** 三张脸的状态机：没目标 → 平静，有目标 → 不悦，蓄力 → 攻击 */
    @GameTest(template = "ddt_empty")
    public static void faceFollowsTargetAndCharge(GameTestHelper helper) {
        GhastFruityEntity ghast = ghastAt(helper.getLevel());
        helper.assertTrue(ghast.currentFace() == GhastFruityEntity.Face.CALM,
            "an idle ghastfruity must wear the calm face, got " + ghast.currentFace());

        ghast.setTarget(helper.makeMockPlayer(GameType.SURVIVAL));
        helper.assertTrue(ghast.currentFace() == GhastFruityEntity.Face.ANNOYED,
            "a ghastfruity that has a target must wear the annoyed face, got " + ghast.currentFace());

        ghast.setCharging(true);
        helper.assertTrue(ghast.currentFace() == GhastFruityEntity.Face.ATTACK,
            "a charging ghastfruity must wear the attack face, got " + ghast.currentFace());

        ghast.setCharging(false);
        ghast.setTarget(null);
        helper.assertTrue(ghast.currentFace() == GhastFruityEntity.Face.CALM,
            "the face must fall back to calm once the target is gone");
        helper.succeed();
    }

    /** 只按类名反射，代码里不 import 暮色森林，与仓库里其它暮色兼容代码同一做法；不触发静态初始化 */
    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, GhastFruityTests.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }

    private static GhastFruityEntity ghastAt(ServerLevel level) {
        GhastFruityEntity ghast = new GhastFruityEntity(ModEntities.GHASTFRUITY.get(), level);
        ghast.setPos(4.0D, 2.0D, 4.0D);
        return ghast;
    }

    /** 一发朝 -Z 飞、速度 1 格每 tick 的火球，正是原版恶魂那一发 */
    private static LargeFireball incomingFireball(ServerLevel level, GhastFruityEntity ghast) {
        LargeFireball fireball = new LargeFireball(level, ghast, new Vec3(0.0D, 0.0D, -1.0D), 1);
        fireball.setDeltaMovement(0.0D, 0.0D, -1.0D);
        return fireball;
    }

    private GhastFruityTests() {}
}
