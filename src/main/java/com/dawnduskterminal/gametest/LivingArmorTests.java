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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
     * 副手有盾就算挡着，没有任何「举多久才生效」的门槛
     *
     * <p>前两版把判定挂在「正在用物品」和「举够 5 tick」上，实战里都出现过盾举着却不挡；
     * 现在门只剩「副手有没有盾、是不是被斧头封了」，所以一个 tick 都不用跑就挡得住
     */
    @GameTest(template = "ddt_empty")
    public static void shieldBlocksRightAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = shieldBearer(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        Zombie brawler = brawlerInFront(level, helper.absolutePos(new BlockPos(3, 4, 5)));
        helper.assertTrue(blocker.isBlocking(), "carrying the shield must be enough to count as blocking");

        float before = blocker.getHealth();
        int wearBefore = blocker.getOffhandItem().getDamageValue();
        blocker.hurt(level.damageSources().mobAttack(brawler), 6.0F);
        helper.assertTrue(
                blocker.getHealth() == before,
                "a hit from the front must be blocked, health went " + before + " -> " + blocker.getHealth());
        int wear = blocker.getOffhandItem().getDamageValue() - wearBefore;
        helper.assertTrue(wear == 7, "a blocked 6-damage hit must wear the shield by 1 + 6 = 7, got " + wear);
        helper.assertFalse(blocker.isShieldDisabled(), "a sword must not disable the shield");
        blocker.discard();
        brawler.discard();
        helper.succeed();
    }

    /**
     * 背后打过来挡不住：原版挡盾要挡在正面（{@code isDamageSourceBlocked} 里那条与视线方向的点积）
     *
     * <p>同一只举着盾的活盔甲，攻击者绕到它背后（北边，−Z）。脸朝南时视线是 +Z，
     * 背后来的伤害方向与前半球相反，于是这一下照常生效
     */
    @GameTest(template = "ddt_empty")
    public static void shieldDoesNotCoverTheBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = shieldBearer(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        Zombie front = brawlerInFront(level, helper.absolutePos(new BlockPos(3, 4, 5)));
        Zombie back = brawlerInFront(level, helper.absolutePos(new BlockPos(3, 4, 1)));
        blocker.setTarget(front);
        raiseShield(blocker);
        helper.assertTrue(blocker.isBlocking(), "the blocker must be holding the shield up");

        float health = blocker.getHealth();
        blocker.hurt(level.damageSources().mobAttack(back), 6.0F);
        helper.assertTrue(
                blocker.getHealth() < health,
                "a hit from behind must go through the shield, health went " + health + " -> " + blocker.getHealth());
        blocker.discard();
        front.discard();
        back.discard();
        helper.succeed();
    }

    /**
     * 盾牌挡不住的东西照旧生效：斧头会破盾，{@code #minecraft:bypasses_shield} 里的伤害穿盾
     *
     * <p>破盾那一条照原版 {@code Player#blockUsingShield}：挡下之后问 {@code attacker.canDisableShield()}，
     * 主手是斧类就把盾封 {@value LivingArmorEntity#SHIELD_DISABLED_TICKS} tick，并且把盾收掉。
     * 穿盾那一条用铁砧砸下来的伤害（{@code minecraft:falling_anvil} 就在那张标签里），
     * 它同样带着来向，所以挡住它的不可能是「方向不对」
     */
    @GameTest(template = "ddt_empty")
    public static void axesDisableAndBypassingDamageGetsThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = shieldBearer(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        Zombie axeman = brawlerInFront(level, helper.absolutePos(new BlockPos(3, 4, 5)));
        axeman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        blocker.setTarget(axeman);
        raiseShield(blocker);
        helper.assertTrue(blocker.isBlocking(), "the blocker must be holding the shield up");

        float health = blocker.getHealth();
        blocker.hurt(level.damageSources().mobAttack(axeman), 6.0F);
        helper.assertTrue(blocker.getHealth() == health, "the axe hit itself must be blocked first");
        helper.assertTrue(
                blocker.isShieldDisabled(),
                "an axe must disable the shield for " + LivingArmorEntity.SHIELD_DISABLED_TICKS + " ticks");
        helper.assertFalse(blocker.isBlocking(), "a disabled shield must be lowered");

        // 穿盾：铁砧砸下来，方向对、盾也举着也不行（上一只的盾已经被斧头封了，换一只干净的）
        blocker.discard();
        LivingArmorEntity second = shieldBearer(level, helper.absolutePos(new BlockPos(5, 4, 3)));
        second.setTarget(axeman);
        raiseShield(second);
        helper.assertTrue(second.isBlocking(), "the second blocker must be holding the shield up");
        float health2 = second.getHealth();
        second.hurt(level.damageSources().anvil(axeman), 6.0F);
        helper.assertTrue(
                second.getHealth() < health2,
                "falling_anvil bypasses shields, health went " + health2 + " -> " + second.getHealth());
        second.discard();
        axeman.discard();
        helper.succeed();
    }

    /** 把盾举到位：跑几 tick 让原版那 5 tick 的起手走完。手动推进，不等墙上时间 */
    private static void raiseShield(LivingArmorEntity blocker) {
        for (int i = 0; i < 8; i++) {
            blocker.tick();
        }
    }

    /**
     * 空手一拳也要挡住：玩家那一拳的伤害源是 {@code playerAttack}、伤害只有 1 点
     *
     * <p>之前只测过 {@code mobAttack} 的 6 点伤害，「玩家」与「小伤害」这两条漏在外面：
     * 伤害源换成 {@code playerAttack}、数值换成空手的 1 点，挡盾这条路不该有任何区别
     */
    @GameTest(template = "ddt_empty")
    public static void shieldStopsABareHandedPunch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = shieldBearer(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        Zombie bystander = brawlerInFront(level, helper.absolutePos(new BlockPos(3, 4, 5)));
        blocker.setTarget(bystander);
        raiseShield(blocker);
        helper.assertTrue(blocker.isBlocking(), "the blocker must be holding the shield up");

        Player puncher = helper.makeMockPlayer(GameType.SURVIVAL);
        puncher.moveTo(blocker.getX(), blocker.getY(), blocker.getZ() + 1.5D, 180.0F, 0.0F);
        float health = blocker.getHealth();
        // 原版空手：Player#attack 走的是 damageSources().playerAttack(this)，伤害 1 点
        blocker.hurt(level.damageSources().playerAttack(puncher), 1.0F);
        helper.assertTrue(
                blocker.getHealth() == health,
                "a blocking suit must stop a bare-handed punch, health went " + health + " -> " + blocker.getHealth());
        blocker.discard();
        bystander.discard();
        puncher.discard();
        helper.succeed();
    }

    /**
     * AI 开着的时候也要挡住：照着游戏里那一遍来
     *
     * <p>前面几条用例都把 AI 关了、手动把盾举到位，验的是「举着的情况下挡不挡得住」。
     * 这一条把 AI 打开，让目标、寻路、朝向、举盾全部自己跑 20 tick，然后<b>按它自己的视线方向</b>
     * 站到正面打一拳 —— 失败信息里把 {@code isUsingItem} 与已举 tick 数一起打出来，
     * 下次再坏能直接看出是「没举盾」还是「正面判错了」
     */
    @GameTest(template = "ddt_empty")
    public static void shieldBlocksWithAiRunning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = spawnImmobile(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        blocker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(TFItems.KNIGHTMETAL_SHIELD.get()));
        // 诱饵摆在 8 格外：目标在举盾范围内，但 20 tick 内它跑不到近战距离（近战 3 格），
        // 也就不会出手 —— 出手会收盾 8 tick，那是另一条用例
        LivingArmorEntity bait = spawnImmobile(level, helper.absolutePos(new BlockPos(3, 4, 11)));
        blocker.setTarget(bait);
        for (int i = 0; i < 20; i++) {
            blocker.tick();
        }

        Vec3 look = blocker.calculateViewVector(0.0F, blocker.getYHeadRot());
        Player puncher = helper.makeMockPlayer(GameType.SURVIVAL);
        puncher.moveTo(blocker.getX() + look.x * 1.5D, blocker.getY(), blocker.getZ() + look.z * 1.5D, 0.0F, 0.0F);
        helper.assertTrue(
                blocker.isBlocking(),
                "an AI-driven suit with a threat nearby must be blocking (using=" + blocker.isUsingItem()
                        + ", usedTicks=" + blocker.getTicksUsingItem() + ")");
        float health = blocker.getHealth();
        blocker.hurt(level.damageSources().playerAttack(puncher), 1.0F);
        helper.assertTrue(
                blocker.getHealth() == health,
                "a punch from the direction the suit faces must be blocked, health went " + health + " -> "
                        + blocker.getHealth());
        blocker.discard();
        bait.discard();
        puncher.discard();
        helper.succeed();
    }

    /**
     * 出手那一下不能同时举着盾
     *
     * <p>挥击之前盾会收起来 {@value LivingArmorEntity#SHIELD_SWING_LOWER_TICKS} tick：
     * 这一段里既不算挡着，姿势也放下来；过完这段才重新举
     */
    @GameTest(template = "ddt_empty")
    public static void shieldIsLoweredWhileAttacking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        LivingArmorEntity blocker = shieldBearer(level, helper.absolutePos(new BlockPos(3, 4, 3)));
        Zombie bait = brawlerInFront(level, helper.absolutePos(new BlockPos(3, 4, 4)));
        blocker.setTarget(bait);
        helper.assertTrue(blocker.isBlocking(), "sanity: the shield must be up before it swings");

        blocker.attackTarget(bait);
        helper.assertFalse(blocker.isBlocking(), "a suit that just swung must not be blocking at the same time");

        // 这一段里打进来就是实打实的伤
        float health = blocker.getHealth();
        blocker.hurt(level.damageSources().mobAttack(bait), 6.0F);
        helper.assertTrue(
                blocker.getHealth() < health,
                "a hit taken during the swing must go through, health went " + health + " -> " + blocker.getHealth());

        // 窗口过完，盾重新举起来
        for (int i = 0; i < LivingArmorEntity.SHIELD_SWING_LOWER_TICKS; i++) {
            blocker.tick();
        }
        helper.assertTrue(blocker.isBlocking(), "the shield must come back up after the swing");
        blocker.discard();
        bait.discard();
        helper.succeed();
    }

    /**
     * 移动与疾跑：疾跑比例、举盾时的输入缩放
     *
     * <p>生物的移动速度属性（0.3）与玩家的（0.1）不是同一把尺子，不能直接比数字；
     * 能比的是<b>比例</b>：疾跑 +30%（`minecraft:sprinting`，与玩家同一条修饰符），
     * 举盾时输入按 {@link LivingArmorEntity#GUARD_SPEED_FACTOR} 缩。绝对值那一段由
     * {@link #guardSpeedIsTwentyPercentOfWalk} 实测位移来钉
     */
    @GameTest(template = "ddt_empty")
    public static void speedMatchesThePlayer(GameTestHelper helper) {
        clearLeftovers(helper);
        LivingArmorEntity armor = spawnImmobile(helper.getLevel(), helper.absolutePos(new BlockPos(3, 4, 3)));
        float walk = (float) armor.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(walk - 0.3F) < 1.0E-6F, "base movement speed must stay 0.3, got " + walk);

        armor.setSprintCharging(true);
        float sprinting = (float) armor.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(
                Math.abs(sprinting - walk * 1.3F) < 1.0E-6F,
                "sprinting must add exactly 30% like minecraft:sprinting, got " + sprinting);

        armor.setSpeed(walk);
        armor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(TFItems.KNIGHTMETAL_SHIELD.get()));
        helper.assertTrue(armor.isBlocking(), "sanity: carrying the shield must count as guarding");
        armor.tick();
        helper.assertFalse(armor.isSprintCharging(), "a guarding suit must not be sprint-charging");
        helper.assertTrue(
                Math.abs(armor.getSpeed() - walk * LivingArmorEntity.GUARD_SPEED_FACTOR) < 1.0E-6F,
                "guarding must scale the movement input by " + LivingArmorEntity.GUARD_SPEED_FACTOR + ", got "
                        + armor.getSpeed() + " from " + walk);
        helper.assertTrue(
                Math.abs(armor.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk) < 1.0E-6F,
                "the guard slowdown must not touch the attribute, got "
                        + armor.getAttributeValue(Attributes.MOVEMENT_SPEED));

        armor.attackTarget(armor);
        helper.assertFalse(armor.isBlocking(), "a suit that just swung must not be blocking");
        helper.assertTrue(
                Math.abs(armor.getSpeed() - walk) < 1.0E-6F,
                "the guard slowdown must be gone while swinging, got " + armor.getSpeed());

        armor.discard();
        helper.succeed();
    }

    /**
     * 实测「寻路驱动」下的走速与举盾速度
     *
     * <p>前面那条 travel 用例是满输入，量出来的绝对值偏大；这里换成真寻路：目标摆在 16 格外，
     * 让 AI 自己走过去，跑 40 tick 量位移 —— 与游戏里看到的移动同一条路
     */
    @GameTest(template = "ddt_empty")
    public static void guardSpeedIsAboutTwentyPercentOfWalk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeftovers(helper);
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 3));
        double walk = measureChaseSpeed(level, from, false);
        double guard = measureChaseSpeed(level, from, true);
        double ratio = walk == 0.0D ? Double.NaN : guard / walk;
        helper.assertTrue(walk > 0.5D, "sanity: the chase measurement must see real movement, got " + walk);
        // 玩家举盾时移动输入乘 0.2（LocalPlayer 695-699），实战里看到的就是两成速度
        helper.assertTrue(
                ratio > 0.15D && ratio < 0.25D,
                "guard speed must be about 20% of the walk speed, measured walk=" + walk + " m/s, guard=" + guard
                        + " m/s, ratio=" + ratio);
        helper.succeed();
    }

    /** 铺一条走廊，目标摆在 16 格外，让 AI 自己追 40 tick，返回水平速度（格/秒） */
    private static double measureChaseSpeed(ServerLevel level, BlockPos from, boolean shield) {
        for (int dx = -2; dx <= 22; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(from.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(from.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(from.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
            }
        }
        LivingArmorEntity mob = ModEntities.LIVING_ARMOR.get().create(level);
        if (mob == null) {
            throw new IllegalStateException("living_armor could not be created");
        }
        mob.moveTo(from.getX() + 0.5D, from.getY(), from.getZ() + 0.5D, -90.0F, 0.0F);
        mob.setPersistenceRequired();
        if (shield) {
            mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(TFItems.KNIGHTMETAL_SHIELD.get()));
        }
        level.addFreshEntity(mob);

        Zombie bait = EntityType.ZOMBIE.create(level);
        if (bait == null) {
            throw new IllegalStateException("zombie could not be created");
        }
        bait.moveTo(from.getX() + 18.5D, from.getY(), from.getZ() + 0.5D, 0.0F, 0.0F);
        bait.setNoAi(true);
        bait.setInvulnerable(true);
        level.addFreshEntity(bait);
        mob.setTarget(bait);

        double start = mob.getX();
        for (int i = 0; i < 40; i++) {
            mob.tick();
        }
        double measured = (mob.getX() - start) / 40.0D * 20.0D;
        mob.discard();
        bait.discard();
        return measured;
    }

    /** 举着盾的活盔甲：副手骑士盾牌，脸朝南（+Z），并且站在场上 */
    private static LivingArmorEntity shieldBearer(ServerLevel level, BlockPos pos) {
        LivingArmorEntity blocker = spawnImmobile(level, pos);
        blocker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(TFItems.KNIGHTMETAL_SHIELD.get()));
        // 关掉 AI：位置与朝向要钉死，盾牌那套判定是按脸的方向算的
        blocker.setNoAi(true);
        blocker.setYRot(0.0F);
        blocker.setYHeadRot(0.0F);
        return blocker;
    }

    /** 站在指定位置的一个假人：只当「攻击者」，用的是它的位置与手里那件武器 */
    private static Zombie brawlerInFront(ServerLevel level, BlockPos pos) {
        Zombie brawler = EntityType.ZOMBIE.create(level);
        if (brawler == null) {
            throw new IllegalStateException("zombie could not be created");
        }
        brawler.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        brawler.setNoGravity(true);
        brawler.setNoAi(true);
        brawler.setPersistenceRequired();
        level.addFreshEntity(brawler);
        return brawler;
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
