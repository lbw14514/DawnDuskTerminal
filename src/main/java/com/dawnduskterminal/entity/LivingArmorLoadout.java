package com.dawnduskterminal.entity;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;
import twilightforest.init.TFItems;

/**
 * 「活盔甲」身上那一套东西：抽哪一套、每件带什么附魔
 *
 * <h2>抽取规则</h2>
 * 生成时按<b>脚下群系</b>决定套装的候选池，池内等概率抽一套：
 *
 * <ul>
 *   <li>{@code dawnduskterminal:dark_forest}（作者口中的 {@code ddt:dark_forest}）：炽铁或骑士
 *   <li>其余任何群系：铁木或钢叶
 * </ul>
 *
 * <p>「以后还会声明其它情况」就加在 {@link #DECLARED} 这张表里：一个群系 id 对着一个候选池，
 * 没列进去的群系一律落到 {@link #UNDECLARED}。表是唯一的入口，{@link #pick} 之外没有再判断群系的地方
 *
 * <h2>为什么抽到的工具是「一件」而不是一整套</h2>
 * 一整套工具是玩家背包里的东西，生物身上只有主手那一件能挥出去，多给的四件只会让掉落变多、
 * 手上那把的概率变小。所以每套材料只列<b>能当武器挥</b>的那几件，主手从中抽一件
 *
 * <h2>骑士那一档的两件特例</h2>
 * 骑士的工具池里额外放了链锤（{@code twilightforest:block_and_chain}，中文名就叫「链锤」），
 * 抽到它时主手换成链锤的攻击方式，见 {@link LivingArmorEntity#throwFlail}；
 * 骑士盾牌固定占副手（{@code twilightforest:knightmetal_shield}），它带来的六成抵挡见
 * {@link LivingArmorEntity#hurt}。盾牌不参与主手抽取：它是防御件，抽到它当主手等于这一只空手
 */
public final class LivingArmorLoadout {
    /**
     * 附魔的等级区间，按原版附魔台的 30 级封顶那一档随机
     *
     * <p>取整段区间而不是固定 30：30 级几乎必然出三到四条满级附魔，整批活盔甲会长得一模一样
     */
    private static final int ENCHANT_LEVEL_MIN = 8;

    private static final int ENCHANT_LEVEL_MAX = 30;

    /** 未声明的群系：铁木或钢叶 */
    private static final List<Kit> UNDECLARED = List.of(Kit.IRONWOOD, Kit.STEELEAF);

    /**
     * 已声明群系 → 候选套装
     *
     * <p>键是本模组自己那份群系副本的 id（{@code data/dawnduskterminal/worldgen/biome/} 下的文件名），
     * 不是原版的 {@code minecraft:dark_forest}：维度里用的是 {@code PaletteBiomeSource} 换出来的副本
     */
    private static final Map<ResourceLocation, List<Kit>> DECLARED =
            Map.of(DawnDuskTerminal.id("dark_forest"), List.of(Kit.FIERY, Kit.KNIGHT));

    private LivingArmorLoadout() {}

    /**
     * 一套材料。{@code weapons} 是能进主手的候选，{@code offhand} 是可选的副手件
     *
     * <p>四件护甲按 头 胸 腿 脚 排，与 {@code EquipmentSlot} 的语义一致
     */
    public enum Kit {
        /** 炽铁：主手只有剑与镐两件可抽 */
        FIERY(
                TFItems.FIERY_HELMET,
                TFItems.FIERY_CHESTPLATE,
                TFItems.FIERY_LEGGINGS,
                TFItems.FIERY_BOOTS,
                List.of(TFItems.FIERY_SWORD, TFItems.FIERY_PICKAXE),
                null),
        /** 钢叶：五件工具齐全 */
        STEELEAF(
                TFItems.STEELEAF_HELMET,
                TFItems.STEELEAF_CHESTPLATE,
                TFItems.STEELEAF_LEGGINGS,
                TFItems.STEELEAF_BOOTS,
                List.of(
                        TFItems.STEELEAF_SWORD,
                        TFItems.STEELEAF_SHOVEL,
                        TFItems.STEELEAF_PICKAXE,
                        TFItems.STEELEAF_AXE,
                        TFItems.STEELEAF_HOE),
                null),
        /** 铁木：五件工具齐全 */
        IRONWOOD(
                TFItems.IRONWOOD_HELMET,
                TFItems.IRONWOOD_CHESTPLATE,
                TFItems.IRONWOOD_LEGGINGS,
                TFItems.IRONWOOD_BOOTS,
                List.of(
                        TFItems.IRONWOOD_SWORD,
                        TFItems.IRONWOOD_SHOVEL,
                        TFItems.IRONWOOD_PICKAXE,
                        TFItems.IRONWOOD_AXE,
                        TFItems.IRONWOOD_HOE),
                null),
        /** 骑士：主手多一件链锤，副手固定骑士盾牌 */
        KNIGHT(
                TFItems.KNIGHTMETAL_HELMET,
                TFItems.KNIGHTMETAL_CHESTPLATE,
                TFItems.KNIGHTMETAL_LEGGINGS,
                TFItems.KNIGHTMETAL_BOOTS,
                List.of(
                        TFItems.KNIGHTMETAL_SWORD,
                        TFItems.KNIGHTMETAL_PICKAXE,
                        TFItems.KNIGHTMETAL_AXE,
                        TFItems.BLOCK_AND_CHAIN),
                TFItems.KNIGHTMETAL_SHIELD);

        private final DeferredItem<ArmorItem> helmet;

        private final DeferredItem<ArmorItem> chestplate;

        private final DeferredItem<ArmorItem> leggings;

        private final DeferredItem<ArmorItem> boots;

        private final List<DeferredItem<? extends Item>> weapons;

        @Nullable
        private final DeferredItem<? extends Item> offhand;

        Kit(
                DeferredItem<ArmorItem> helmet,
                DeferredItem<ArmorItem> chestplate,
                DeferredItem<ArmorItem> leggings,
                DeferredItem<ArmorItem> boots,
                List<DeferredItem<? extends Item>> weapons,
                @Nullable DeferredItem<? extends Item> offhand) {
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.weapons = weapons;
            this.offhand = offhand;
        }

        /** 主手候选里等概率抽一件 */
        public Item weapon(RandomSource random) {
            return this.weapons.get(random.nextInt(this.weapons.size())).get();
        }

        /** 副手件；没有就是 {@code null} */
        @Nullable
        public Item offhand() {
            return this.offhand == null ? null : this.offhand.get();
        }
    }

    /**
     * 按群系抽一套
     *
     * <p>抽的是套装，不是单件：护甲四件与主手来自同一套材料，不会出现铁木头盔配钢叶剑
     *
     * @param biome 实体脚下的群系 id，{@code null} 表示拿不到（注册表未加载的边缘情况），按未声明处理
     */
    public static Kit pick(@Nullable ResourceLocation biome, RandomSource random) {
        List<Kit> candidates = biome == null ? null : DECLARED.get(biome);
        if (candidates == null) {
            candidates = UNDECLARED;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** 实体脚下那一格的群系 id；拿不到时为 {@code null} */
    @Nullable
    public static ResourceLocation biomeAt(LivingEntity entity) {
        return entity.level()
                .getBiome(entity.blockPosition())
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
    }

    /**
     * 把抽到的那一套穿上
     *
     * <p>只在服务端调用（{@code finalizeSpawn} 本来就只有服务端跑）。每一件都单独掷一次附魔，
     * 因此同一只活盔甲的四件护甲与主手会带不同的附魔
     *
     * <p>掉落概率不在这里设：{@code Mob} 的默认值是 0.085，与原版自然生成时给生物塞装备那一档相同，
     * 也就是「打死了多半什么也拿不到」
     */
    public static void equip(Mob mob) {
        RandomSource random = mob.getRandom();
        Kit kit = pick(biomeAt(mob), random);
        mob.setItemSlot(EquipmentSlot.HEAD, enchanted(mob.level(), random, kit.helmet.get()));
        mob.setItemSlot(EquipmentSlot.CHEST, enchanted(mob.level(), random, kit.chestplate.get()));
        mob.setItemSlot(EquipmentSlot.LEGS, enchanted(mob.level(), random, kit.leggings.get()));
        mob.setItemSlot(EquipmentSlot.FEET, enchanted(mob.level(), random, kit.boots.get()));
        mob.setItemSlot(EquipmentSlot.MAINHAND, enchanted(mob.level(), random, kit.weapon(random)));
        Item offhand = kit.offhand();
        if (offhand != null) {
            mob.setItemSlot(EquipmentSlot.OFFHAND, enchanted(mob.level(), random, offhand));
        }
    }

    /**
     * 一件新装备，按随机等级上附魔
     *
     * <p>主路径直接用原版附魔台那一套（{@code EnchantmentHelper#enchantItem}）：它自己处理「这件物品支不支持」
     * 「互相冲突的两条附魔不能共存」「等级不能超过上限」，与玩家从附魔台拿到的完全一致
     *
     * <p>兜底那一条是给「附魔等级为 0 的物品」准备的：那种物品走附魔台那条路一条附魔都抽不到
     * （{@code selectEnchantment} 见到 {@code getEnchantmentValue() <= 0} 就直接返回空表），
     * 于是这里自己从「能附在这件物品上的附魔」里挑一条。暮色森林的工具都带附魔等级，正常走不到这里，
     * 但一旦有哪件装备改了数据组件，这条兜底能保证「携带随机附魔」这条规则不会静默落空
     */
    private static ItemStack enchanted(Level world, RandomSource random, Item item) {
        ItemStack stack = new ItemStack(item);
        int level = ENCHANT_LEVEL_MIN + random.nextInt(ENCHANT_LEVEL_MAX - ENCHANT_LEVEL_MIN + 1);
        EnchantmentHelper.enchantItem(random, stack, level, world.registryAccess(), Optional.empty());
        if (stack.isEnchanted()) {
            return stack;
        }
        List<Holder.Reference<Enchantment>> usable = world.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .holders()
                .filter(stack::supportsEnchantment)
                .toList();
        if (!usable.isEmpty()) {
            Holder.Reference<Enchantment> chosen = usable.get(random.nextInt(usable.size()));
            stack.enchant(chosen, 1 + random.nextInt(chosen.value().getMaxLevel()));
        }
        return stack;
    }
}
