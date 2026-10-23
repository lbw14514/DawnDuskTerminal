package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.CarminiteBallEntity;
import com.dawnduskterminal.entity.CarminiteFieldEntity;
import com.dawnduskterminal.item.CarminiteStaffItem;
import com.dawnduskterminal.registry.ModArmorMaterials;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModEquipment;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModTiers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 迁移过来的装备只能在这里验证到「数值与注册」，发射 命中 掉血 靠实机，只有反应场的形状有断言
 *
 * <p>护甲等级是任务里写死的要求：T1 对标钻石，也就是四件合计 20 点护甲、2 点韧性。这个数写成断言，
 * 以后调数值时会立刻发现
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class EquipmentTests {
    /** 钻石护甲四件合计的护甲点数 */
    private static final int DIAMOND_DEFENSE = 20;
    /** 钻石护甲的韧性 */
    private static final float DIAMOND_TOUGHNESS = 2.0F;
    /** 钻石护甲的耐久倍率 */
    private static final int DIAMOND_DURABILITY_FACTOR = 33;
    /** T2 四件合计的护甲点数 */
    private static final int T2_DEFENSE = 25;
    /** T2 每件的韧性，四件合计 14 */
    private static final float T2_TOUGHNESS = 3.5F;
    /** T2 的耐久倍率 */
    private static final int T2_DURABILITY_FACTOR = 37;
    /** T3 四件合计的护甲点数 */
    private static final int T3_DEFENSE = 30;
    /** T3 每件的韧性，四件合计 20 */
    private static final float T3_TOUGHNESS = 5.0F;

    private static final ArmorItem.Type[] SLOTS = {
        ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS,
    };

    @GameTest(template = "ddt_empty")
    public static void scarletMolybdArmorMatchesT2(GameTestHelper helper) {
        ArmorMaterial material = ModArmorMaterials.SCARLET_MOLYBDENUM.value();
        int defense = 0;
        for (ArmorItem.Type type : SLOTS) {
            defense += material.getDefense(type);
        }
        helper.assertTrue(defense == T2_DEFENSE,
            "T2 armor must total " + T2_DEFENSE + " defense, got " + defense);
        helper.assertTrue(material.toughness() == T2_TOUGHNESS,
            "T2 armor toughness must be " + T2_TOUGHNESS + ", got " + material.toughness());
        helper.assertTrue(material.enchantmentValue() == 15,
            "T2 armor enchantment value must be 15, got " + material.enchantmentValue());

        ArmorItem helmet = ModItems.SCARLET_MOLYBD_HELMET.get();
        int expected = ArmorItem.Type.HELMET.getDurability(T2_DURABILITY_FACTOR);
        int actual = new ItemStack(helmet).getMaxDamage();
        helper.assertTrue(actual == expected,
            "helmet durability must be " + expected + ", got " + actual);

        // 四个部件必须是同一个材质，否则穿齐时属性会缺一块
        helper.assertTrue(ModItems.SCARLET_MOLYBD_CHESTPLATE.get().getMaterial().value() == material,
            "chestplate must use the scarlet molybdenum material");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void carminiteEquipmentIsRegistered(GameTestHelper helper) {
        helper.assertTrue(ModEntities.CARMINITE_BALL.get().getCategory() == MobCategory.MISC,
            "the carminite ball must be a MISC entity");
        helper.assertTrue(edible(ModItems.COOKED_CARMIN_GHAST_MEAT.get()),
            "cooked carminite ghast meat must be edible");
        helper.assertTrue(edible(ModItems.CARMINITE_GHAST_MEAT.get()),
            "raw carminite ghast meat must be edible");
        helper.assertTrue(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get() instanceof CarminiteStaffItem,
            "the reactor staff must be registered as a staff");

        // 三项都是源 mod 的硬数值：128 耐久、恒定光效、攻速 -3.5
        ItemStack staff = new ItemStack(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get());
        helper.assertTrue(staff.getMaxDamage() == 128,
            "staff durability must be 128, got " + staff.getMaxDamage());
        helper.assertTrue(staff.hasFoil(), "staff must always have the enchantment glint");
        helper.assertTrue(CarminiteStaffItem.createAttributes().modifiers().stream()
                .anyMatch(entry -> entry.attribute().value() == Attributes.ATTACK_SPEED.value()
                    && Math.abs(entry.modifier().amount() + 3.5D) < 1.0E-6D),
            "staff attack speed must be -3.5");
        helper.succeed();
    }

    /**
     * 反应场是圆柱不是长方体：水平 3,3 格落在旧的长方体里、在新圆柱外，不该吃伤害
     *
     * <p>反应场一下 8 点，羊只有 8 血，所以命中的那只直接死，断言只判「掉过血」，不判剩多少
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 20)
    public static void carminiteFieldIsCylindrical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        Sheep inside = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(4, 1, 2));
        inside.setNoGravity(true);
        Sheep corner = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(5, 1, 5));
        corner.setNoGravity(true);
        float before = inside.getHealth();

        CarminiteFieldEntity.spawn(level, center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, null, 0, 0, 0);

        helper.succeedWhen(() -> {
            helper.assertTrue(inside.getHealth() < before,
                "a sheep 2 blocks away must take field damage");
            helper.assertTrue(corner.getHealth() == corner.getMaxHealth(),
                "a sheep in the corner of the old 4x4 box must stay outside the cylinder");
        });
    }

    /**
     * 反应场的落地频率由对方的受伤无敌时间决定，不是自己定间隔
     *
     * <p>原版 {@code LivingEntity.hurt} 命中后把 {@code invulnerableTime} 置 20，只有它衰减到 10 以下
     * 才吃得下下一次等量伤害，所以每 tick 都结算的反应场实际是每 10 tick 掉一次血：
     * 5 tick 时 1 次、15 tick 时 2 次、25 tick 时 3 次
     *
     * <p>下面这条同时钉住三种错法：改成每 20 tick 结算（15 tick 时只有 1 次）、
     * 伤害类型被塞进 {@code bypasses_cooldown}（25 tick 时会掉满 20 多次血）、以及第 3 次还没到就收场
     *
     * <p>羊默认 8 血撑不住第二下，所以先把血量顶到 200，让每次掉血都能读成 8 点的整数
     *
     * <p>羊故意站在离柱心一格的位置：这个伤害类型在 {@code #minecraft:no_knockback} 里，
     * 受击既不该给水平推挤也不该给向上的弹跳，所以每次掉血后 {@code getDeltaMovement()} 必须还是零。
     * 把这行标签删掉，这条断言立刻翻车（原版 hurt 会塞 0.4 的速度）
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 60)
    public static void carminiteFieldDamageFollowsInvulnerability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(3, 1, 2));
        sheep.setNoGravity(true);
        sheep.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        sheep.setHealth(200.0F);

        CarminiteFieldEntity.spawn(level, center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, null, 0, 0, 0);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(sheep.getHealth() == 192.0F,
                "one hit within 5 ticks, got health " + sheep.getHealth());
            helper.assertTrue(sheep.getDeltaMovement().lengthSqr() < 1.0E-6,
                "the field must not knock the sheep back, got " + sheep.getDeltaMovement());
        });
        helper.runAfterDelay(15, () -> helper.assertTrue(sheep.getHealth() == 184.0F,
            "two hits within 15 ticks, got health " + sheep.getHealth()));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(sheep.getHealth() == 176.0F,
                "three hits within 25 ticks, got health " + sheep.getHealth());
            helper.succeed();
        });
    }

    /**
     * 弹体必须能写进 NBT：父类用 ItemStack.CODEC 写「拾取物」栈，传空栈会在存档时抛
     * Cannot encode empty ItemStack，症状是整个区块关服存档报错
     */
    @GameTest(template = "ddt_empty")
    public static void ballSurvivesSaveToNbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Sheep shooter = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(1, 1, 1));
        ItemStack staff = new ItemStack(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get());
        CarminiteBallEntity ball = new CarminiteBallEntity(level, shooter, staff);

        CompoundTag tag = new CompoundTag();
        ball.saveWithoutId(tag);
        helper.assertTrue(tag.get("item") != null, "the arrow must write its pickup stack to NBT");
        helper.succeed();
    }

    /**
     * 带附魔的弹必须能走完：多重射击分裂、穿透连续命中、连续生成反应场都不能卡住服务端刻
     *
     * <p>这条测试跑完就说明没有死循环，失败形式是超时而不是断言
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 100)
    public static void enchantedBallDoesNotLockUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack staff = new ItemStack(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get());
        // 255 级是命令能给的上限，正好拿来验证等级截断
        staff.enchant(enchantments.getHolderOrThrow(Enchantments.PIERCING), 255);
        staff.enchant(enchantments.getHolderOrThrow(Enchantments.MULTISHOT), 255);
        staff.enchant(enchantments.getHolderOrThrow(Enchantments.FLAME), 255);
        staff.enchant(enchantments.getHolderOrThrow(Enchantments.POWER), 255);

        Sheep shooter = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(1, 1, 1));
        shooter.setNoGravity(true);
        for (int x = 3; x <= 6; x++) {
            Sheep target = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(x, 1, 1));
            target.setNoGravity(true);
        }

        CarminiteBallEntity ball = new CarminiteBallEntity(level, shooter, staff);
        ball.setPos(1.5D, 1.5D, 1.5D);
        ball.shoot(1.0D, 0.0D, 0.0D, 1.0F, 0.0F);
        level.addFreshEntity(ball);

        helper.runAfterDelay(80, () -> {
            int alive = level.getEntitiesOfClass(CarminiteBallEntity.class,
                new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(32.0D)).size();
            helper.assertTrue(alive <= 40, "enchantment levels must be clamped, " + alive + " balls alive");
            helper.succeed();
        });
    }

    /** 权杖的附魔加成靠两个原版标签才能附上，等级读取走武器栈 */
    @GameTest(template = "ddt_empty")
    public static void staffAcceptsProjectileEnchantments(GameTestHelper helper) {
        ItemStack staff = new ItemStack(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get());
        helper.assertTrue(staff.is(ItemTags.BOW_ENCHANTABLE), "staff must accept bow enchantments");
        helper.assertTrue(staff.is(ItemTags.CROSSBOW_ENCHANTABLE), "staff must accept crossbow enchantments");
        helper.assertTrue(staff.getEnchantmentValue() > 0, "staff must be enchantable");

        var enchantments = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        helper.assertTrue(staff.supportsEnchantment(enchantments.getHolderOrThrow(Enchantments.POWER)),
            "staff must accept power");
        helper.assertFalse(staff.supportsEnchantment(enchantments.getHolderOrThrow(Enchantments.INFINITY)),
            "staff must refuse infinity");
        helper.assertFalse(EnchantmentHelper.getAvailableEnchantmentResults(30, staff,
                enchantments.holders().map(holder -> (Holder<Enchantment>) holder)).stream()
                .anyMatch(instance -> instance.enchantment.is(Enchantments.INFINITY)),
            "the enchanting table must not offer infinity for the staff");

        staff.enchant(enchantments.getHolderOrThrow(Enchantments.POWER), 3);
        helper.assertTrue(CarminiteBallEntity.enchantmentLevel(staff, Enchantments.POWER) == 3,
            "power level must be readable from the weapon stack");
        helper.succeed();
    }

    /**
     * 铁砧在创造模式下会跳过 supportsEnchantment，无限还是能合上去；
     * 事件在结果算出来之前拦，创造模式也一样拦得住
     *
     * <p>先用力量书确认这条流程本身是通的，否则「无限被拒」可能只是根本没配上
     */
    @GameTest(template = "ddt_empty")
    public static void creativeAnvilRefusesInfinityOnStaff(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;

        AnvilMenu allowed = anvilWith(helper, player, 0, Enchantments.POWER);
        allowed.createResult();
        helper.assertFalse(allowed.getSlot(2).getItem().isEmpty(),
            "a power book must still be applicable to the staff");

        AnvilMenu refused = anvilWith(helper, player, 1, Enchantments.INFINITY);
        refused.createResult();
        helper.assertTrue(refused.getSlot(2).getItem().isEmpty(),
            "a creative anvil must refuse infinity on the staff");
        helper.succeed();
    }

    private static AnvilMenu anvilWith(GameTestHelper helper, Player player, int containerId,
                                       ResourceKey<Enchantment> enchantment) {
        AnvilMenu menu = new AnvilMenu(containerId, player.getInventory(), ContainerLevelAccess.NULL);
        menu.setItem(0, 0, new ItemStack(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get()));
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(enchantment), 1);
        menu.setItem(1, 0, book);
        return menu;
    }

    private static boolean edible(Item item) {
        return new ItemStack(item).get(DataComponents.FOOD) != null;
    }

    /** 六套材料对标设定：天门松是铁，苍穹与砷铅铁是钻石，绯色钼与狄拉克是 T2，䤧是 T3 */
    @GameTest(template = "ddt_empty")
    public static void equipmentSetsMatchTheirVanillaTier(GameTestHelper helper) {
        assertArmor(helper, ModArmorMaterials.SKYGATE_PINE.value(), 15, 0.0F, 9, "skygate_pine");
        assertArmor(helper, ModArmorMaterials.CANOPY.value(), DIAMOND_DEFENSE, DIAMOND_TOUGHNESS, 10, "canopy");
        assertArmor(helper, ModArmorMaterials.CARMINITE.value(), DIAMOND_DEFENSE, DIAMOND_TOUGHNESS, 10, "carminite");
        assertArmor(helper, ModArmorMaterials.SCARLET_MOLYBDENUM.value(), T2_DEFENSE, T2_TOUGHNESS, 15,
            "scarlet molybdenum");
        assertArmor(helper, ModArmorMaterials.DIRAC.value(), T2_DEFENSE, T2_TOUGHNESS, 15, "dirac");
        assertArmor(helper, ModArmorMaterials.HUI.value(), T3_DEFENSE, T3_TOUGHNESS, 18, "hui");

        helper.assertTrue(ModTiers.SKYGATE_PINE.getUses() == 250 && ModTiers.SKYGATE_PINE.getSpeed() == 6.0F,
            "skygate pine tools must be iron tier");
        helper.assertTrue(ModTiers.CANOPY.getUses() == 1561 && ModTiers.CANOPY.getSpeed() == 8.0F,
            "canopy tools must be diamond tier");
        helper.assertTrue(ModTiers.CARMINITE.getUses() == 1561 && ModTiers.CARMINITE.getSpeed() == 8.0F,
            "carminite tools must be diamond tier");
        helper.assertTrue(ModTiers.SCARLET_MOLYBDENUM.getUses() == 2031
                && ModTiers.SCARLET_MOLYBDENUM.getSpeed() == 9.0F
                && ModTiers.DIRAC.getUses() == 2031,
            "T2 tools must beat diamond in both durability and speed");
        helper.assertTrue(ModTiers.HUI.getUses() == 2600 && ModTiers.HUI.getSpeed() == 10.0F,
            "T3 tools must beat T2");

        // 六套各五件工具加四件护甲
        int registered = ModEquipment.sets().values().stream().mapToInt(set -> set.items().size()).sum();
        helper.assertTrue(registered == 54, "expected 54 equipment items, got " + registered);
        helper.succeed();
    }

    private static void assertArmor(GameTestHelper helper, ArmorMaterial material, int defense, float toughness,
                                    int enchantmentValue, String materialName) {
        int total = 0;
        for (ArmorItem.Type type : SLOTS) {
            total += material.getDefense(type);
        }
        helper.assertTrue(total == defense,
            materialName + " armor must total " + defense + " defense, got " + total);
        helper.assertTrue(material.toughness() == toughness,
            materialName + " armor toughness must be " + toughness + ", got " + material.toughness());
        helper.assertTrue(material.enchantmentValue() == enchantmentValue,
            materialName + " armor enchantment value must be " + enchantmentValue);
    }
}
