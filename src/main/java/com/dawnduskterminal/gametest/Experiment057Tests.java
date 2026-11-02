package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.Experiment057BladeEntity;
import com.dawnduskterminal.item.Experiment057Item;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 057 那片制导飞刃的规格
 *
 * <p>钉住：物品面板 8 点伤害、攻速 1.6、64 点耐久且能上近战附魔；右键一次只出一片（冷却 2 秒、
 * 扣 1 点耐久）、初速是「朝视线前方」的一个随机方向（偏角不超过
 * {@value Experiment057BladeEntity#MAX_DEVIATION_DEGREES} 度）且无重力、制导会把弹体收回到发射者的
 * **视线**上（方向与位置都对，不是只把方向摆平行）、撞方块是弹性反射且实体不消失、命中活物之后原路弹回
 * 并扣掉 4 秒寿命（两次命中就把 8 秒用完）、寿命到点消失、命中活物时的伤害是「发射者此刻的攻击力 × 1.5
 * + 附魔加成」（属性伤害先乘暴击、锋利这类附魔最后相加，与原版近战同序，不是写死的 8）且「发射者正在下落」
 * 再叠一层（1.5 × 1.5 = 2.25），以及源文件的几何、动画、两张贴图、物品模型与两条语言键都在 jar 里
 *
 * <p>这些用例挂在单独的模板命名空间 {@value #TEMPLATE_NAMESPACE} 下（模板是同一份 {@code ddt_empty}
 * 的副本），于是可以用 {@code -Pgametest_namespaces=dawnduskterminal057} 只跑这一批，
 * 不必把整模组的用例全跑一遍
 *
 * <p>弹跳、命中与制导都是手动 {@code entity.tick()} 推进的：GameTest 的模板只有 3x16x16 格，
 * 让飞刃自己飞 8 秒会撞进隔壁用例的场地；手动走拍才能把「撞上之后做了什么」单独量出来。
 * 场地里只数 {@value #SEARCH_RADIUS} 格以内的飞刃，隔壁用例的场地再近也不会串进来
 */
@GameTestHolder(Experiment057Tests.TEMPLATE_NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Experiment057Tests {
    /** 这一批用例自己的模板命名空间：模板结构同名副本放在 {@code data/dawnduskterminal057/structure/} 下 */
    public static final String TEMPLATE_NAMESPACE = "dawnduskterminal057";
    /** 发射者的攻击力（物品面板上的近战伤害）：测试里直接写进属性底数 */
    private static final float ATTACK_DAMAGE = 8.0F;
    /** 僵尸的初始生命 */
    private static final float ZOMBIE_HEALTH = 20.0F;
    /** 数飞刃时的搜索半径 */
    private static final double SEARCH_RADIUS = 4.0D;
    /** 命中用例里飞刃的起点离僵尸多远：要在判定箱（0.6 宽）之外，才有一段「线段穿进去」可判 */
    private static final double BLADE_START_DISTANCE = 1.2D;
    /** 命中用例把僵尸抬到这么高，离开地面那层可能残留方块的位置 */
    private static final double HIT_ALTITUDE = 8.0D;

    /**
     * 试验物品 057 的规格：近战伤害 8、攻速 1.6、64 点耐久、不可堆叠
     *
     * <p>玩家底数攻速 4，减去 2.4 就是每 1.6 秒一刀，与原版剑那一条攻速修饰符同值
     */
    @GameTest(template = "ddt_empty")
    public static void itemIsAnEightOnePointSixBlade(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(57),
            "number 57 must not be a placeholder anymore, it has a real item now");
        Item item = ModExperimentItems.EXPERIMENT_057.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(item instanceof Experiment057Item,
            "dawnduskterminal:experiment_057 must be an " + Experiment057Item.class.getSimpleName()
                + ", got " + item.getClass().getSimpleName());
        ExperimentItemTests.assertMeleeSpec(helper, item, 8.0D, 1.6D);
        // 提示里的「8 攻击伤害 / 1.6 攻击速度」靠修饰符 ID 认出来：原版只对这两个 ID 显示最终值，
        // 换成自定义 ID 就会退化成「+7 攻击伤害 / -2.4 攻击速度」
        for (var entry : stack.getAttributeModifiers().modifiers()) {
            ResourceLocation id = entry.modifier().id();
            helper.assertTrue(id.equals(ResourceLocation.withDefaultNamespace("base_attack_damage"))
                    || id.equals(ResourceLocation.withDefaultNamespace("base_attack_speed")),
                "the melee modifiers must use the vanilla base ids so the tooltip prints the panel"
                    + " values, got " + id);
        }
        helper.assertTrue(stack.getMaxDamage() == Experiment057Item.DURABILITY,
            "dawnduskterminal:experiment_057 must have " + Experiment057Item.DURABILITY
                + " uses, got " + stack.getMaxDamage());
        helper.assertTrue(stack.getMaxStackSize() == 1,
            "dawnduskterminal:experiment_057 is damageable, it must not stack, got "
                + stack.getMaxStackSize());
        helper.succeed();
    }

    /**
     * 057 可接受近战附魔：在 {@code #minecraft:swords} 里、附魔值不为 0，且原版那几个近战附魔都认它
     *
     * <p>锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、横扫之刃、耐久、经验修补的
     * {@code supported_items} 全部由 {@code #minecraft:enchantable/sharp_weapon} 或
     * {@code #minecraft:enchantable/sword} 派生，这两条又都指向 {@code #minecraft:swords}——
     * 所以「挂进剑标签」这一条就是它们全部能上去的原因。最后一条再验附魔台 30 级不会给出空表
     */
    @GameTest(template = "ddt_empty")
    public static void itemAcceptsMeleeEnchantments(GameTestHelper helper) {
        ItemStack blade = new ItemStack(ModExperimentItems.EXPERIMENT_057.get());
        helper.assertTrue(blade.is(ItemTags.SWORDS),
            "experiment_057 must sit in #minecraft:swords, otherwise no melee enchantment reaches it");
        helper.assertTrue(blade.getEnchantmentValue() > 0,
            "experiment_057 must report a positive enchantment value, got "
                + blade.getEnchantmentValue());

        var enchantments = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT,
            Enchantments.LOOTING, Enchantments.SWEEPING_EDGE, Enchantments.UNBREAKING,
            Enchantments.MENDING)) {
            helper.assertTrue(blade.supportsEnchantment(enchantments.getHolderOrThrow(key)),
                "experiment_057 must accept " + key.location());
        }
        helper.assertFalse(EnchantmentHelper.getAvailableEnchantmentResults(30, blade,
                enchantments.holders().map(holder -> (Holder<Enchantment>) holder)).isEmpty(),
            "the enchanting table must offer something for experiment_057 at level 30");
        helper.succeed();
    }

    /** 一按右键只出一片，扣 1 点耐久、冷却 2 秒，冷却中再按右键什么都不发生 */
    @GameTest(template = "ddt_empty")
    public static void rightClickFiresOneBladePerTwoSeconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = aimPlayer(helper);
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);

        InteractionResultHolder<ItemStack> first = stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.getResult().consumesAction(),
            "right clicking dawnduskterminal:experiment_057 must consume the click, got " + first.getResult());
        helper.assertTrue(stack.getDamageValue() == 1,
            "one shot must cost one durability, got " + stack.getDamageValue());
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModExperimentItems.EXPERIMENT_057.get()),
            "one shot must put the item on cooldown");
        helper.assertTrue(blades(helper).size() == 1,
            "one shot must spawn exactly one blade, got " + blades(helper).size());
        helper.assertTrue(Experiment057Item.COOLDOWN_TICKS == 40,
            "the cooldown must be 2 seconds (40 ticks), got "
                + Experiment057Item.COOLDOWN_TICKS);

        InteractionResultHolder<ItemStack> second = stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertFalse(second.getResult().consumesAction(),
            "the second click inside the cooldown must be ignored, got " + second.getResult());
        helper.assertTrue(stack.getDamageValue() == 1,
            "an ignored click must not cost durability, got " + stack.getDamageValue());
        helper.assertTrue(blades(helper).size() == 1,
            "an ignored click must not spawn a second blade, got " + blades(helper).size());
        blades(helper).forEach(Experiment057BladeEntity::discard);
        helper.succeed();
    }

    /**
     * 初速是「朝视线前方」的一个随机方向：与视线的夹角不超过
     * {@value Experiment057BladeEntity#MAX_DEVIATION_DEGREES} 度，速度落在 1.2~1.8 格/tick，且无重力
     *
     * <p>方向与速度都由 {@code Level#getRandom()} 摇出来，这里连发 200 片逐片核对
     */
    @GameTest(template = "ddt_empty")
    public static void bladeLaunchesInFrontWithoutGravity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = aimPlayer(helper);
        Vec3 look = player.getViewVector(1.0F).normalize();
        double minDot = Math.cos(Math.toRadians(Experiment057BladeEntity.MAX_DEVIATION_DEGREES));
        for (int shot = 0; shot < 200; shot++) {
            Experiment057BladeEntity blade = Experiment057BladeEntity.launch(level, player);
            Vec3 motion = blade.getDeltaMovement();
            double speed = motion.length();
            helper.assertTrue(speed >= 1.2D - 1.0E-6D && speed <= 1.8D + 1.0E-6D,
                "the random launch speed must stay within 1.2..1.8, got " + speed);
            double dot = motion.normalize().dot(look);
            helper.assertTrue(dot >= minDot - 1.0E-6D,
                "the launch must point in front of the launcher, at most "
                    + Experiment057BladeEntity.MAX_DEVIATION_DEGREES + " degrees off the look, got "
                    + Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0D, 1.0D))) + " degrees");
            helper.assertTrue(blade.isNoGravity(), "the blade must not fall");
            helper.assertTrue(blade.getGravity() == 0.0D,
                "the blade must report zero gravity, got " + blade.getGravity());
            blade.discard();
        }
        helper.succeed();
    }

    /**
     * 制导：飞刃会回到发射者的视线上，而不是只把方向摆成与视线平行
     *
     * <p>让发射者原地朝正上方看（视线是一条竖直的线），飞刃从这条线旁边 3 格处、沿线的方向放出去，
     * 于是「方向已经与视线平行、位置却偏在线外」——只做方向对齐的实现会一直保持 3 格偏距。
     * 手动走 14 拍，横向偏距必须明显缩小、且速度要朝线的方向拐过去
     */
    @GameTest(template = "ddt_empty")
    public static void bladeGuidesOntoTheSightLine(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = plainPlayer(helper);
        player.setXRot(-90.0F);
        player.setOldPosAndRot();
        Vec3 line = player.getViewVector(1.0F).normalize();

        Experiment057BladeEntity blade = new Experiment057BladeEntity(
            ModEntities.EXPERIMENT_057_BLADE.get(), helper.getLevel());
        blade.setOwner(player);
        Vec3 start = player.getEyePosition().add(0.0D, 10.0D, 0.0D)
            .add(3.0D, 0.0D, 0.0D);
        blade.setPos(start.x, start.y, start.z);
        blade.setDeltaMovement(line.scale(1.5D));
        helper.getLevel().addFreshEntity(blade);

        double before = lateralDistance(blade, player, line);
        for (int tick = 0; tick < 14 && !blade.isRemoved(); tick++) {
            blade.tick();
        }
        double after = lateralDistance(blade, player, line);
        helper.assertFalse(blade.isRemoved(), "nothing in the way, the blade must still be flying");
        helper.assertTrue(after < before * 0.5D,
            "the blade must be pulled onto the sight line, lateral offset went from " + before
                + " to " + after);
        helper.assertTrue(blade.getDeltaMovement().normalize().dot(line) > 0.5D,
            "the blade must still fly along the line, got direction "
                + blade.getDeltaMovement().normalize());
        blade.discard();
        helper.succeed();
    }

    /** 弹体到「眼睛 + t × 视线」这条射线的垂直偏距 */
    private static double lateralDistance(Experiment057BladeEntity blade, Player player, Vec3 line) {
        Vec3 offset = blade.position().subtract(player.getEyePosition());
        return offset.subtract(line.scale(offset.dot(line))).length();
    }

    /** 寿命：第 160 tick 还活着，第 161 tick 自己消失（8 秒） */
    @GameTest(template = "ddt_empty")
    public static void bladeExpiresAfterEightSeconds(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Experiment057BladeEntity blade = new Experiment057BladeEntity(
            ModEntities.EXPERIMENT_057_BLADE.get(), helper.getLevel());
        blade.setPos(origin.getX() + 1.5D, origin.getY() + 3.5D, origin.getZ() + 1.5D);
        blade.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(blade);

        for (int tick = 0; tick < Experiment057BladeEntity.LIFETIME_TICKS; tick++) {
            blade.tick();
        }
        helper.assertFalse(blade.isRemoved(),
            "the blade must live for " + Experiment057BladeEntity.LIFETIME_TICKS + " ticks, it died early");
        blade.tick();
        helper.assertTrue(blade.isRemoved(),
            "the blade must be gone after " + (Experiment057BladeEntity.LIFETIME_TICKS + 1)
                + " ticks, it is still here");
        helper.succeed();
    }

    /**
     * 撞方块：速度沿撞击面法线的分量取反，实体不消失
     *
     * <p>把一片石头摆在 +X 方向，飞刃朝它直飞；撞上之后 x 分量必须变成负数，实体还在
     */
    @GameTest(template = "ddt_empty")
    public static void bladeBouncesOffBlocksWithoutDying(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Experiment057BladeEntity blade = new Experiment057BladeEntity(
            ModEntities.EXPERIMENT_057_BLADE.get(), helper.getLevel());
        blade.setPos(origin.getX() + 1.5D, origin.getY() + 1.5D, origin.getZ() + 1.5D);
        blade.setDeltaMovement(1.5D, 0.0D, 0.0D);
        helper.getLevel().addFreshEntity(blade);

        for (int tick = 0; tick < 8; tick++) {
            blade.tick();
            if (blade.getDeltaMovement().x < 0.0D) {
                break;
            }
        }
        helper.assertFalse(blade.isRemoved(),
            "a block hit must not remove the blade, it is supposed to bounce");
        helper.assertTrue(blade.getDeltaMovement().x < 0.0D,
            "an elastic hit on the WEST face must flip the x velocity, got " + blade.getDeltaMovement());
        blade.discard();
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR);
        helper.succeed();
    }

    /**
     * 命中活物：发射者直接结算一次这件物品的暴击伤害
     *
     * <p>发射者的攻击力先按面板上的 8 点摆好（不拿东西、直接写底数，于是没有别的修饰符掺进来），
     * 于是「没在下落」应当是 12 点、「正在下落」是 18 点；两片飞刃各打一只僵尸，不让无敌帧搅在一起
     */
    @GameTest(template = "ddt_empty")
    public static void bladeHitsForCritDamageAndStacksWhenFalling(GameTestHelper helper) {
        Player player = plainPlayer(helper);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        helper.assertFalse(Experiment057BladeEntity.isFalling(player),
            "a mock player with no fall distance must not count as falling");

        Zombie grounded = helper.spawn(EntityType.ZOMBIE, 1, 1, 4);
        float first = hitOnce(helper, player, grounded);
        helper.assertTrue(Math.abs(first - ATTACK_DAMAGE * 1.5F) < 1.0E-4F,
            "a blade hit must deal 1.5 x " + ATTACK_DAMAGE + " = " + (ATTACK_DAMAGE * 1.5F)
                + ", got " + first);

        // 第二只：把发射者按在「正在下落」那一档上，倍率叠成 2.25
        player.fallDistance = 3.0F;
        helper.assertTrue(Experiment057BladeEntity.isFalling(player),
            "fallDistance > 0 with no ground contact must count as falling");
        Zombie whileFalling = helper.spawn(EntityType.ZOMBIE, 1, 1, 8);
        float stacked = hitOnce(helper, player, whileFalling);
        helper.assertTrue(Math.abs(stacked - ATTACK_DAMAGE * 1.5F * 1.5F) < 1.0E-4F,
            "a hit while the launcher is falling must deal 1.5 x 1.5 x " + ATTACK_DAMAGE + " = "
                + (ATTACK_DAMAGE * 1.5F * 1.5F) + ", got " + stacked);

        helper.assertTrue(Experiment057BladeEntity.critMultiplier(false) == 1.5F,
            "the plain crit multiplier must be 1.5, got "
                + Experiment057BladeEntity.critMultiplier(false));
        helper.assertTrue(Experiment057BladeEntity.critMultiplier(true) == 2.25F,
            "the falling crit multiplier must stack to 2.25, got "
                + Experiment057BladeEntity.critMultiplier(true));
        grounded.discard();
        whileFalling.discard();
        helper.succeed();
    }

    /**
     * 命中活物之后：原路弹回 + 扣 4 秒寿命
     *
     * <p>把僵尸抬到半空，飞刃从正前方打进去。命中那一拍之后速度方向必须整个反过来（原路反射，回程仍受
     * 制导影响，所以留 6 度的余量），寿命要多扣
     * {@value Experiment057BladeEntity#HIT_LIFETIME_COST_TICKS} tick；再打一次就超过 160 tick 的寿命，
     * 飞刃就地消失——8 秒的飞刃只扛得住两次命中
     */
    @GameTest(template = "ddt_empty")
    public static void bladeHitCostsFourSecondsAndReflects(GameTestHelper helper) {
        Player player = plainPlayer(helper);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        Zombie target = helper.spawn(EntityType.ZOMBIE, 1, 1, 8);
        prepareTarget(helper, target);

        Experiment057BladeEntity blade = Experiment057BladeEntity.launch(helper.getLevel(), player);
        helper.assertTrue(blade.getAge() == 0, "a fresh blade must start with a full lifetime");
        aimAt(blade, target);
        Vec3 incoming = blade.getDeltaMovement().normalize();
        blade.tick();
        helper.assertTrue(blade.getAge() == 1 + Experiment057BladeEntity.HIT_LIFETIME_COST_TICKS,
            "a hit must cost " + Experiment057BladeEntity.HIT_LIFETIME_COST_TICKS
                + " ticks on top of the tick itself, got " + blade.getAge());
        helper.assertFalse(blade.isRemoved(), "one hit must not finish an 8 second blade");
        double dot = blade.getDeltaMovement().normalize().dot(incoming);
        helper.assertTrue(dot < -0.99D,
            "a hit must send the blade straight back along its own path, dot with the incoming"
                + " heading is " + dot);

        aimAt(blade, target);
        blade.tick();
        helper.assertTrue(blade.isRemoved(),
            "the second hit must burn the remaining lifetime, age=" + blade.getAge());
        target.discard();
        helper.succeed();
    }

    /**
     * 锋利算进伤害里：附魔加成与原版近战同一条路（{@code EnchantmentHelper#modifyDamage}），
     * 且与原版同序——属性伤害先乘暴击，附魔加成最后相加
     *
     * <p>发射者攻击力按 8 点摆，主手拿一把锋利 V 的 057（数据里是 1.0 + 0.5×4 = 3 点），
     * 于是这一击应当是 8 × 1.5 + 3 = 15，而不是 12
     */
    @GameTest(template = "ddt_empty")
    public static void bladeDamageIncludesSharpness(GameTestHelper helper) {
        Player player = plainPlayer(helper);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        ItemStack weapon = new ItemStack(ModExperimentItems.EXPERIMENT_057.get());
        weapon.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.SHARPNESS), 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);

        Zombie target = helper.spawn(EntityType.ZOMBIE, 1, 1, 4);
        float dealt = hitOnce(helper, player, target, 200.0F);
        helper.assertTrue(Math.abs(dealt - 15.0F) < 1.0E-4F,
            "a Sharpness V blade must crit for 8 x 1.5 + 3 = 15, got " + dealt);
        target.discard();
        helper.succeed();
    }

    /**
     * 伤害不是写死的：命中那一刻现读发射者的近战伤害，换个攻击力就换个伤害
     *
     * <p>同一只发射者先把攻击力按 8 点摆（暴击 12 点），再按 20 点摆（暴击 30 点）——倍率 1.5 没变，
     * 变的只是被乘的那个数。锋利这类近战附魔加的正是这个属性，所以附魔多少级就按多少级算
     */
    @GameTest(template = "ddt_empty")
    public static void bladeDamageFollowsTheLauncherAttribute(GameTestHelper helper) {
        Player player = plainPlayer(helper);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D);
        Zombie first = helper.spawn(EntityType.ZOMBIE, 1, 1, 4);
        float plain = hitOnce(helper, player, first, 200.0F);
        helper.assertTrue(Math.abs(plain - 12.0F) < 1.0E-4F,
            "8 points of attack damage must crit for 12, got " + plain);

        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20.0D);
        Zombie second = helper.spawn(EntityType.ZOMBIE, 1, 1, 8);
        float stronger = hitOnce(helper, player, second, 200.0F);
        helper.assertTrue(Math.abs(stronger - 30.0F) < 1.0E-4F,
            "20 points of attack damage must crit for 30, got " + stronger);

        first.discard();
        second.discard();
        helper.succeed();
    }

    /**
     * 打一只僵尸一次，返回它掉了多少血
     *
     * <p>先把护甲清零：僵尸自带 2 点护甲，物理伤害会按原版公式减掉 1.6%（12 点变成 11.808），
     * 那是伤害管线的正常行为，与「暴击倍率是多少」是两件事，混在一起量出来的就不是倍率了
     *
     * <p>再把僵尸抬到半空、飞刃从它正前方 1.2 格处朝它飞：{@code ProjectileUtil} 的实体判定只认
     * 「线段穿过判定箱」，起点已经在箱子里时反而判不中（{@code AABB#clip} 对起点在内部的线段返回空），
     * 所以必须从外面进。抬到 {@value #HIT_ALTITUDE} 格是为了离开地面那一层：GameTest 的场地按用例复用，
     * {@code ddt_empty} 模板里一个方块都没有，上一轮用例留在地面上的方块不会因为「重新摆一遍模板」而消失，
     * 贴地打会撞在那些残留方块上弹回来
     */
    private static float hitOnce(GameTestHelper helper, Player player, Zombie target) {
        return hitOnce(helper, player, target, ZOMBIE_HEALTH);
    }

    /** 同上，但把僵尸的生命上限抬到 {@code health}，便于量比 20 点还高的伤害 */
    private static float hitOnce(GameTestHelper helper, Player player, Zombie target, float health) {
        prepareTarget(helper, target, health);
        Experiment057BladeEntity blade = Experiment057BladeEntity.launch(helper.getLevel(), player);
        aimAt(blade, target);
        for (int tick = 0; tick < 4 && !blade.isRemoved(); tick++) {
            blade.tick();
        }
        float dealt = health - target.getHealth();
        if (dealt <= 0.0F) {
            helper.fail("the blade never hit: blade=" + blade.position() + " motion="
                + blade.getDeltaMovement() + " removed=" + blade.isRemoved()
                + " owner=" + blade.getOwner() + " target=" + target.position()
                + " targetHealth=" + target.getHealth());
        }
        blade.discard();
        return dealt;
    }

    /** 把僵尸抬到半空、护甲清零、生命回满，并清掉它周围可能残留的方块 */
    private static void prepareTarget(GameTestHelper helper, Zombie target) {
        prepareTarget(helper, target, ZOMBIE_HEALTH);
    }

    /** 同上，生命上限抬到 {@code health}（至少留 20 点，免得比原版模板还低） */
    private static void prepareTarget(GameTestHelper helper, Zombie target, float health) {
        target.setPos(target.getX(), target.getY() + HIT_ALTITUDE, target.getZ());
        target.setNoGravity(true);
        BlockPos relative = helper.relativePos(target.blockPosition());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.setBlock(relative.offset(dx, dy, dz), Blocks.AIR);
                }
            }
        }
        target.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0D);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(health, ZOMBIE_HEALTH));
        target.setHealth(health);
    }

    /** 把飞刃摆到僵尸正前方 {@value #BLADE_START_DISTANCE} 格、朝它飞 */
    private static void aimAt(Experiment057BladeEntity blade, Zombie target) {
        blade.setPos(target.getX(), target.getY() + 1.0D, target.getZ() - BLADE_START_DISTANCE);
        blade.setDeltaMovement(0.0D, 0.0D, 1.5D);
    }

    /** 场地里现存的飞刃 */
    private static List<Experiment057BladeEntity> blades(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(Experiment057BladeEntity.class,
            new AABB(origin).inflate(SEARCH_RADIUS));
    }

    /** 一个站在模板原点、朝 +Z（yaw 0）、手里拿着 057 的替身玩家 */
    private static Player aimPlayer(GameTestHelper helper) {
        Player player = plainPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(ModExperimentItems.EXPERIMENT_057.get()));
        return player;
    }

    /** 空着手的替身玩家：攻击力就是底数，没有物品修饰符掺进来 */
    private static Player plainPlayer(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setYBodyRot(0.0F);
        player.setOldPosAndRot();
        return player;
    }

    /** 几何、动画、贴图、模型与两条语言键：缺一件客户端那边就是一个紫黑格或原始键 */
    @GameTest(template = "ddt_empty")
    public static void bladeResourcesArePinned(GameTestHelper helper) {
        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_057.json");
        helper.assertTrue(model.get("parent").getAsString().equals("minecraft:item/generated"),
            "item/experiment_057 must stay on minecraft:item/generated, got " + model.get("parent"));
        helper.assertTrue(model.getAsJsonObject("textures").get("layer0").getAsString()
                .equals("dawnduskterminal:item/experiment_057"),
            "item/experiment_057 must read its own texture, got " + model.getAsJsonObject("textures"));

        for (String resource : List.of(
            "/assets/dawnduskterminal/textures/item/experiment_057.png",
            "/assets/dawnduskterminal/geo/e_57_blade.geo.json",
            "/assets/dawnduskterminal/animations/e_57_blade.animation.json",
            "/assets/dawnduskterminal/textures/entity/e_57_blade.png")) {
            helper.assertTrue(Experiment057Tests.class.getResource(resource) != null,
                resource + " must ship inside the mod resources");
        }

        JsonObject animation = readJson(helper,
            "/assets/dawnduskterminal/animations/e_57_blade.animation.json");
        JsonObject bones = animation.getAsJsonObject("animations").getAsJsonObject("parallel0")
            .getAsJsonObject("bones");
        helper.assertTrue(bones.has("spin1") && bones.has("spin2"),
            "the source clip parallel0 must turn both plate bones, got " + bones.keySet());

        for (String lang : new String[] {"en_us", "zh_cn"}) {
            JsonObject words = readJson(helper, "/assets/dawnduskterminal/lang/" + lang + ".json");
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_057"),
                lang + " must name experiment_057");
            helper.assertTrue(words.has("item.dawnduskterminal.experiment_057.desc"),
                lang + " must carry the tooltip line of experiment_057");
        }
        helper.succeed();
    }

    /** 从 mod 资源里读一份 JSON：路径以 {@code /} 开头，相对于 jar 根 */
    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment057Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException problem) {
            throw new AssertionError("reading " + resource + " failed", problem);
        }
    }

    private Experiment057Tests() {}
}
