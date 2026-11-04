package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.LivingArmorEntity;
import com.dawnduskterminal.entity.LivingArmorLoadout;
import com.dawnduskterminal.registry.ModEntities;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.init.TFItems;

/**
 * 「活盔甲」的两件事：生成时一定穿满、穿的是哪一套由群系决定
 *
 * <p>这两条在游戏里都不好验：装备抽错只表现为「这只看起来像铁木的，其实是钢叶的」，
 * 少一件护甲与「附魔没抽到」更是肉眼看不出来。所以这里把判定拆成两半测——
 * 群系到候选池的映射是纯函数（{@link LivingArmorLoadout#pick}），另一半直接跑一遍真实生成
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class LivingArmorTests {
    /** 声明的群系：炽铁或骑士 */
    private static final Set<LivingArmorLoadout.Kit> DARK_FOREST_KITS =
            EnumSet.of(LivingArmorLoadout.Kit.FIERY, LivingArmorLoadout.Kit.KNIGHT);

    /** 未声明的群系：铁木或钢叶 */
    private static final Set<LivingArmorLoadout.Kit> UNDECLARED_KITS =
            EnumSet.of(LivingArmorLoadout.Kit.IRONWOOD, LivingArmorLoadout.Kit.STEELEAF);

    /**
     * 群系决定候选池，池内的两套都要抽得到
     *
     * <p>各抽 200 次：既能验「只落在这两套里」，也能验「两套都出得来」——
     * 只取一次的话，写死成 {@code List.get(0)} 的实现照样能过
     */
    @GameTest(template = "ddt_empty")
    public static void kitsFollowTheBiome(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20250816L);
        Set<LivingArmorLoadout.Kit> darkForest = EnumSet.noneOf(LivingArmorLoadout.Kit.class);
        Set<LivingArmorLoadout.Kit> undeclared = EnumSet.noneOf(LivingArmorLoadout.Kit.class);
        Set<LivingArmorLoadout.Kit> missingBiome = EnumSet.noneOf(LivingArmorLoadout.Kit.class);
        for (int i = 0; i < 200; i++) {
            darkForest.add(LivingArmorLoadout.pick(DawnDuskTerminal.id("dark_forest"), random));
            undeclared.add(LivingArmorLoadout.pick(DawnDuskTerminal.id("plains"), random));
            missingBiome.add(LivingArmorLoadout.pick(null, random));
        }
        helper.assertTrue(
                darkForest.equals(DARK_FOREST_KITS),
                "dawnduskterminal:dark_forest must pick from " + DARK_FOREST_KITS + ", got " + darkForest);
        helper.assertTrue(
                undeclared.equals(UNDECLARED_KITS),
                "an undeclared biome must pick from " + UNDECLARED_KITS + ", got " + undeclared);
        helper.assertTrue(
                missingBiome.equals(UNDECLARED_KITS),
                "a missing biome must fall back to " + UNDECLARED_KITS + ", got " + missingBiome);
        helper.succeed();
    }

    /**
     * 生成一次：四件护甲同属一套、主手一件也来自那一套，每件都带附魔，生命 30
     *
     * <p>走的是真实的 {@code finalizeSpawn}，也就是刷怪蛋与 {@code /summon} 走的那一条，
     * 所以这条用例挂了，说明用刷怪蛋放出来的会是一只空盔甲
     */
    @GameTest(template = "ddt_empty")
    public static void spawnsFullyEquipped(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        LivingArmorEntity armor = ModEntities.LIVING_ARMOR.get().create(helper.getLevel());
        if (armor == null) {
            helper.fail("living_armor could not be created");
            return;
        }
        BlockPos absolute = helper.absolutePos(pos);
        armor.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        armor.finalizeSpawn(
                helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(absolute), MobSpawnType.SPAWN_EGG, null);

        helper.assertTrue(
                armor.getAttributeValue(Attributes.MAX_HEALTH) == 30.0D,
                "living_armor must have 30 health, got " + armor.getAttributeValue(Attributes.MAX_HEALTH));
        // 出生就是一只没有名字的敌怪：原版只在实体有自定义名字时才画头顶那一行
        helper.assertFalse(
                armor.hasCustomName(), "living_armor must spawn without a custom name, got " + armor.getCustomName());
        helper.assertFalse(armor.isCustomNameVisible(), "living_armor must not show a nameplate");
        for (EquipmentSlot slot :
                new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = armor.getItemBySlot(slot);
            helper.assertTrue(!stack.isEmpty(), slot + " must be filled in");
            helper.assertTrue(stack.isEnchanted(), slot + " must carry a random enchantment, got " + stack);
        }
        ItemStack weapon = armor.getMainHandItem();
        helper.assertTrue(!weapon.isEmpty(), "main hand must hold a weapon or a tool");
        helper.assertTrue(weapon.isEnchanted(), "the main hand item must carry an enchantment, got " + weapon);

        // 四件护甲必须是同一套材料：把头盔 id 的 "_helmet" 去掉就是这一套的名字，
        // 其余三件与主手都得是同一个前缀（骑士那一档的链锤例外，它的 id 是 block_and_chain）
        String material = path(armor.getItemBySlot(EquipmentSlot.HEAD));
        helper.assertTrue(material.endsWith("_helmet"), "the helmet id must end with _helmet, got " + material);
        material = material.substring(0, material.length() - "_helmet".length());
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String id = path(armor.getItemBySlot(slot));
            helper.assertTrue(id.startsWith(material), slot + " must belong to the " + material + " set, got " + id);
        }
        String weaponId = path(weapon);
        helper.assertTrue(
                weaponId.startsWith(material) || weaponId.equals("block_and_chain"),
                "the main hand item must come from the " + material + " set, got " + weaponId);
        armor.discard();
        helper.succeed();
    }

    /**
     * 盾牌挡下的那几条反馈：磨损量、斧头破盾、剑不破盾
     *
     * <p>只验机器能验的那部分 —— 音效（`minecraft:item.shield.block`）与击退方向是听感与手感，
     * 代码里对着原版抄的，测不了。能测的是「挡下要掉耐久、掉多少」与「斧头会把盾封 100 tick」，
     * 这两条以前都是错的：耐久按伤害原样扣、破盾根本不存在
     */
    @GameTest(template = "ddt_empty")
    public static void shieldBlockWearsAndAxesDisable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = spawnImmobile(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        LivingArmorEntity attacker = spawnImmobile(level, helper.absolutePos(new BlockPos(3, 4, 5)));
        blocker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(TFItems.KNIGHTMETAL_SHIELD.get()));
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(blocker.hasShield(), "the blocker must hold twilightforest:knightmetal_shield");

        // 原版 LivingEntity#hurtCurrentlyUsedShield：挡下的伤害 >= 3 时扣 1 + 伤害取整
        int before = blocker.getOffhandItem().getDamageValue();
        blocker.blockWithShield(attacker.damageSources().mobAttack(attacker), 6.0F);
        int wear = blocker.getOffhandItem().getDamageValue() - before;
        helper.assertTrue(wear == 7, "a blocked 6-damage hit must wear the shield by 1 + 6 = 7, got " + wear);
        helper.assertFalse(blocker.isShieldDisabled(), "a sword must not disable the shield");

        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        blocker.blockWithShield(attacker.damageSources().mobAttack(attacker), 6.0F);
        helper.assertTrue(
                blocker.isShieldDisabled(),
                "an axe must disable the shield for " + LivingArmorEntity.SHIELD_DISABLED_TICKS + " ticks");
        blocker.discard();
        attacker.discard();
        helper.succeed();
    }

    /**
     * 链锤真的能甩出去、真的打到人
     *
     * <p>锤子本体是暮色森林的 {@code ChainBlock}，它的主人一直是玩家；换成一只自定义生物之后还能不能飞、
     * 能不能结算伤害，编译期一条都验不出来。这里摆两只活盔甲：一只持链锤当投手，另一只在 8 格外当靶子，
     * 扔出去等一秒（20 tick）再看靶子掉没掉血
     *
     * <p>两只都关掉重力：模板是空场地，不关会一起掉出去
     */
    @GameTest(template = "ddt_empty")
    public static void flailReachesTheTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 4, 3));
        LivingArmorEntity thrower = spawnImmobile(level, origin);
        LivingArmorEntity target = spawnImmobile(level, origin.offset(0, 0, 8));
        thrower.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(TFItems.BLOCK_AND_CHAIN.get()));
        helper.assertTrue(thrower.hasFlail(), "the thrower must be holding twilightforest:block_and_chain");
        helper.assertFalse(target.hasShield(), "the target must not be carrying a shield");

        float before = target.getHealth();
        thrower.throwFlail(target);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                    target.getHealth() < before,
                    "the chain flail must damage a target 8 blocks away, health went " + before + " -> "
                            + target.getHealth());
            thrower.discard();
            target.discard();
            helper.succeed();
        });
    }

    /**
     * 远距离挨打也要钉住攻击者
     *
     * <p>10 格外的攻击者：远在近战距离（3 格）之外，也没有视线。以前只挂了原版
     * {@code HurtByTargetGoal}，这次补成在 {@code hurt} 里直接 {@code setTarget}
     *
     * <p>距离压在模板的 16 格之内：模板之间紧挨着摆，摆到隔壁场地里会搅别人的用例
     */
    @GameTest(template = "ddt_empty")
    public static void distantHitAggrosTheAttacker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 4, 3));
        LivingArmorEntity armor = spawnImmobile(level, origin);
        LivingArmorEntity attacker = spawnImmobile(level, origin.offset(0, 0, 10));
        helper.assertTrue(armor.getTarget() == null, "nothing to hunt before the hit, got " + armor.getTarget());
        helper.assertTrue(armor.hurt(attacker.damageSources().mobAttack(attacker), 2.0F), "the 2-damage hit must land");
        helper.assertTrue(
                armor.getTarget() == attacker,
                "a hit from 10 blocks away must make the attacker the target, got " + armor.getTarget());
        armor.discard();
        attacker.discard();
        helper.succeed();
    }

    /**
     * 半血以下：找半血以上的地面敌怪当靠山，靠山掉血就丢掉
     *
     * <p>这几条是「逃 + 跟着打」的判据，都是纯查询，所以能直接验：血量比例、敌怪身份、
     * 会不会把掉到半血以下的靠山继续跟着
     */
    @GameTest(template = "ddt_empty")
    public static void woundedFollowsAHealthyAlly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 4, 3));
        LivingArmorEntity armor = spawnImmobile(level, origin);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            helper.fail("a zombie could not be created");
            return;
        }
        BlockPos zombiePos = origin.offset(1, 0, 0);
        zombie.moveTo(zombiePos.getX() + 0.5D, zombiePos.getY(), zombiePos.getZ() + 0.5D, 0.0F, 0.0F);
        zombie.setNoGravity(true);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);

        helper.assertFalse(armor.isWounded(), "full health must not count as wounded");
        armor.setHealth(14.0F);
        helper.assertTrue(armor.isWounded(), "14 of 30 health must count as wounded");
        helper.assertTrue(armor.isUsableAlly(zombie), "a full-health zombie must qualify as an ally");
        helper.assertTrue(
                armor.scanForAlly() == zombie,
                "the wounded armor must pick the zombie next to it, got " + armor.scanForAlly());
        armor.rescanAlly();
        helper.assertTrue(armor.followedAlly() == zombie, "the armor must follow the zombie it just scanned");

        // 靠山自己掉到半血以下：立刻不再算靠山，跟随也就断了
        zombie.setHealth(1.0F);
        helper.assertFalse(armor.isUsableAlly(zombie), "a zombie at 1 health must not qualify as an ally");
        helper.assertTrue(armor.followedAlly() == null, "a wounded ally must be dropped right away");
        armor.discard();
        zombie.discard();
        helper.succeed();
    }

    /**
     * 斧头在下落那一下打出暴击：伤害 ×1.5，平地打不出
     *
     * <p>「起跳 → 下落 → 劈」这条链在用例里只验结算那一半：把一只摆在空中、给它一个非零的
     * {@code fallDistance}，再对比它和平地那只打同一只靶子的伤害
     */
    @GameTest(template = "ddt_empty")
    public static void axeCritMultipliesDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 4, 3));
        LivingArmorEntity grounded = spawnImmobile(level, origin);
        LivingArmorEntity falling = spawnImmobile(level, origin.offset(0, 3, 0));
        LivingArmorEntity plainVictim = spawnImmobile(level, origin.offset(0, 0, 2));
        LivingArmorEntity critVictim = spawnImmobile(level, origin.offset(0, 3, 2));
        grounded.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        falling.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        helper.runAfterDelay(2, () -> {
            grounded.fallDistance = 0.0F;
            falling.fallDistance = 1.0F;
            helper.assertFalse(grounded.isCritAttack(), "a grounded hit must not crit");
            helper.assertTrue(falling.isCritAttack(), "a hit while falling must crit");
            float plainBefore = plainVictim.getHealth();
            float critBefore = critVictim.getHealth();
            grounded.attackTarget(plainVictim);
            falling.attackTarget(critVictim);
            float plain = plainBefore - plainVictim.getHealth();
            float crit = critBefore - critVictim.getHealth();
            helper.assertTrue(plain > 0.0F, "a plain axe hit must do damage, got " + plain);
            helper.assertTrue(
                    Math.abs(crit - plain * 1.5F) < 0.01F,
                    "a crit must do 1.5x the plain damage: plain " + plain + ", crit " + crit);
            grounded.discard();
            falling.discard();
            plainVictim.discard();
            critVictim.discard();
            helper.succeed();
        });
    }

    /**
     * 挥击间隔与主手武器挂钩，而且不会把服务端崩掉
     *
     * <p>这条是崩溃的回归用例：{@code ATTACK_SPEED} 不在 {@code LivingEntity} 的基础属性表里
     * （原版生物不需要攻击冷却），漏掉它时 {@code getAttributeValue} 直接抛
     * {@code Can't find attribute minecraft:generic.attack_speed}，第一次挥击就把存档崩了
     *
     * <p>装备的属性修饰符要等实体的下一次 tick 才结算，所以先让它在世界里跑两拍再看
     */
    @GameTest(template = "ddt_empty")
    public static void weaponDrivesTheSwingInterval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 4, 3));
        LivingArmorEntity attacker = spawnImmobile(level, origin);
        LivingArmorEntity victim = spawnImmobile(level, origin.offset(0, 0, 2));
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        helper.runAfterDelay(2, () -> {
            double speed = attacker.getAttributeValue(Attributes.ATTACK_SPEED);
            helper.assertTrue(
                    Math.abs(speed - 1.6D) < 1.0E-4D,
                    "a diamond sword must take the attack speed from 4.0 down to 1.6, got " + speed);
            helper.assertTrue(
                    attacker.attackInterval() == 13,
                    "a 1.6 attack speed weapon swings every 13 ticks, got " + attacker.attackInterval());
            float before = victim.getHealth();
            attacker.doHurtTarget(victim);
            helper.assertTrue(
                    victim.getHealth() < before,
                    "a melee swing must damage the target, health went " + before + " -> " + victim.getHealth());
            attacker.discard();
            victim.discard();
            helper.succeed();
        });
    }

    /**
     * 先把自己这片场地里的生物清干净
     *
     * <p>gametest 的世界是<b>跨轮次持久化</b>的：上一轮留下的实体这一轮还在原地，可能多挡下一发链锤、
     * 也可能被当成「身边的靠山」扫进结果里。只清模板范围内（自己这一格），碰不到隔壁用例
     */
    private static void clearLeftovers(GameTestHelper helper) {
        for (Mob leftover : helper.getLevel().getEntitiesOfClass(Mob.class, helper.getBounds())) {
            leftover.discard();
        }
    }

    /** 一只不进世界的活盔甲：只摆位置，不给重力，也不让它自己走 */
    private static LivingArmorEntity spawnImmobile(ServerLevel level, BlockPos pos) {
        LivingArmorEntity armor = ModEntities.LIVING_ARMOR.get().create(level);
        if (armor == null) {
            throw new IllegalStateException("living_armor could not be created");
        }
        armor.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        armor.setNoGravity(true);
        armor.setPersistenceRequired();
        level.addFreshEntity(armor);
        return armor;
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private LivingArmorTests() {}
}
