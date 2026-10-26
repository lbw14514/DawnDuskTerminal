package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.Experiment020Item;
import com.dawnduskterminal.item.Experiment027Item;
import com.dawnduskterminal.item.Experiment043Item;
import com.dawnduskterminal.item.Experiment045Item;
import com.dawnduskterminal.item.ExperimentArmorItem;
import com.dawnduskterminal.item.ThrustSpearItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 试验物品栏里那批实验品：一台照熔炉工作的机器（试验物品 004）、两把近战武器（试验物品 007、017）、
 * 一件食物（试验物品 008）、一支画红石点的笔（试验物品 027）、两支长矛（试验物品 033、034）、
 * 高塔五件套（试验物品 037~041）与一件简单物品（试验物品 042）
 *
 * <p>整批试验物品的注册 ID 与显示名都用三位数序列，含已经就是三位的 {@code experiment_001}，
 * 所以这里从 {@code experiment_033} 起步，不是 {@code experiment_33}
 *
 * <p>五件工具直接挂 {@link Tiers#DIAMOND}，于是耐久 1561、挖掘速度 8、攻击加成 3、附魔值 10、
 * 挖不动的方块是 {@code INCORRECT_FOR_DIAMOND_TOOL}，与原版钻石工具逐项相同；攻击力与攻速的底数
 * 照抄 1.21.1 的 {@code Items}（剑 3/-2.4、镐 1/-2.8、斧 5/-3.0、锹 1.5/-3.0、锄 -3/0）
 *
 * <p>没有配方文件，所以不可合成，也不需要另外禁止什么
 *
 * <p>「高塔剑」那行是物品描述，不是名字：名字是「试验物品NNN」，
 * 与 {@code carminite_molybdenum_reactor_staff}（试验物品046）一致
 */
public final class ModExperimentItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    /**
     * 试验物品 007：近战伤害 4、攻速 5
     *
     * <p>玩家自身的底数是攻击力 1、攻速 4，所以两个修饰符分别给 +3 与 +1，面板上就是「4 攻击伤害、
     * 5 攻击速度」，每 0.2 秒一刀。两条都写全：属性表里没有 {@code ATTACK_SPEED} 这一项，面板就不显示攻速
     *
     * <p>不挂 tool tier，所以它没有耐久、挖不动方块、也不在五个原版工具标签里；要的就是一件只拿来比
     * 伤害与攻速尺子的裸武器，规格由 {@code ExperimentItemTests#experiment007IsAFourFiveWeapon} 钉住
     */
    public static final DeferredItem<Item> EXPERIMENT_007 = ITEMS.register("experiment_007",
        () -> new Item(new Item.Properties().attributes(meleeAttributes("experiment_007", 3.0D, 1.0D))));

    /**
     * 试验物品 017：近战伤害 4、攻速 5，与 {@link #EXPERIMENT_007} 同一副规格
     *
     * <p>两件物品共用 {@link #meleeAttributes}，只有修饰符的 ID 前缀不同——同一个属性上挂两个同 ID 的
     * 修饰符会互相顶掉，所以 ID 必须按物品分开
     */
    public static final DeferredItem<Item> EXPERIMENT_017 = ITEMS.register("experiment_017",
        () -> new Item(new Item.Properties().attributes(meleeAttributes("experiment_017", 3.0D, 1.0D))));

    /**
     * 试验物品 008 的食物属性：5 点饥饿、5 点饱和度
     *
     * <p>{@link FoodProperties} 里存的不是饱和度本身，是饱和度系数，实际给的点数是
     * 系数 × 饥饿 × 2，所以 5 与 0.5 才等于 5 点饥饿、5 点饱和度（对照烤马铃薯：5 与 0.6 → 6.0）。
     * 5 点饥饿是两块生猪排以下、一块熟猪排以上的量
     */
    private static final FoodProperties EXPERIMENT_008_FOOD = new FoodProperties.Builder()
        .nutrition(5)
        .saturationModifier(0.5F)
        .build();

    /**
     * 试验物品 008：食物，5 点饥饿、5 点饱和度，吃完东西就没了
     *
     * <p>可堆叠到 64（食物物品的默认值），没有附加效果、也不带「随时可吃」——饥饿满时吃不下，
     * 与原版普通食物一致
     */
    public static final DeferredItem<Item> EXPERIMENT_008 = ITEMS.register("experiment_008",
        () -> new Item(new Item.Properties().food(EXPERIMENT_008_FOOD)));

    /**
     * 试验物品 020 的食物属性：20 点饥饿、20 点饱和度
     *
     * <p>系数还是 0.5：系数 × 饥饿 × 2 = 0.5 × 20 × 2 = 20.0。20 点饥饿就是整条饥饿条，
     * 饱和度的上限也正好是 20，所以这一口直接吃满
     */
    private static final FoodProperties EXPERIMENT_020_FOOD = new FoodProperties.Builder()
        .nutrition(20)
        .saturationModifier(0.5F)
        .build();

    /**
     * 试验物品 020：20 点饥饿、20 点饱和度的食物，吃完随机给 0~3 个效果，每个 10~300 秒
     *
     * <p>摇效果的逻辑在 {@link Experiment020Item}：随机表进不了 {@code FoodProperties}（那张表是固定的、
     * 还会写进 tooltip），只能在吃完那一刻现摇
     */
    public static final DeferredItem<Experiment020Item> EXPERIMENT_020 = ITEMS.register(
        "experiment_020",
        () -> new Experiment020Item(new Item.Properties().food(EXPERIMENT_020_FOOD)));

    /** 试验物品 027：按住右键在视线命中的方块面上每 tick 画一个红石点，行为在 {@link Experiment027Item} */
    public static final DeferredItem<Experiment027Item> EXPERIMENT_027 = ITEMS.register(
        "experiment_027", () -> new Experiment027Item(new Item.Properties()));

    public static final DeferredItem<SwordItem> EXPERIMENT_037 = ITEMS.register("experiment_037",
        () -> new SwordItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3.0F, -2.4F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_037.desc");
            }
        });

    public static final DeferredItem<PickaxeItem> EXPERIMENT_038 = ITEMS.register("experiment_038",
        () -> new PickaxeItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(PickaxeItem.createAttributes(Tiers.DIAMOND, 1.0F, -2.8F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_038.desc");
            }
        });

    public static final DeferredItem<HoeItem> EXPERIMENT_039 = ITEMS.register("experiment_039",
        () -> new HoeItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(HoeItem.createAttributes(Tiers.DIAMOND, -3.0F, 0.0F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_039.desc");
            }
        });

    public static final DeferredItem<ShovelItem> EXPERIMENT_040 = ITEMS.register("experiment_040",
        () -> new ShovelItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(ShovelItem.createAttributes(Tiers.DIAMOND, 1.5F, -3.0F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_040.desc");
            }
        });

    public static final DeferredItem<AxeItem> EXPERIMENT_041 = ITEMS.register("experiment_041",
        () -> new AxeItem(Tiers.DIAMOND, new Item.Properties()
            .attributes(AxeItem.createAttributes(Tiers.DIAMOND, 5.0F, -3.0F))) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                        List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                describe(tooltip, "item.dawnduskterminal.experiment_041.desc");
            }
        });

    /** 试验物品 042：一件普通物品，没有行为也没有描述 */
    public static final DeferredItem<Item> EXPERIMENT_042 = ITEMS.registerSimpleItem(
        "experiment_042", new Item.Properties());

    /**
     * 试验物品 043：单击右键沿视线喷一次火，整套过程在 {@link Experiment043Item} 与
     * {@link com.dawnduskterminal.item.FireJetProcess} 里
     *
     * <p>64 点耐久，喷一次扣 1 点；可损坏的物品默认不可堆叠（{@code MAX_STACK_SIZE} 缺省是 1）
     */
    public static final DeferredItem<Experiment043Item> EXPERIMENT_043 = ITEMS.register(
        "experiment_043",
        () -> new Experiment043Item(new Item.Properties().durability(Experiment043Item.DURABILITY)));

    /**
     * 试验物品 045：一面盾，格挡那套整份照抄原版盾（{@link Experiment045Item} 继承 {@code ShieldItem}）
     *
     * <p>耐久与 {@code Items.SHIELD} 同为 336，且同样带上「旗面图案」这个组件的空表：默认没有图案时盾面走
     * {@code item/experiment_045_nopattern}，一旦被挂上图案或底色就换 {@code item/experiment_045_base}
     * 并把图案一层层叠上去（见客户端 {@code client/Experiment045ShieldRenderer}）
     */
    public static final DeferredItem<Experiment045Item> EXPERIMENT_045 = ITEMS.register(
        "experiment_045",
        () -> new Experiment045Item(new Item.Properties()
            .durability(Experiment045Item.DURABILITY)
            .component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)));

    /**
     * 试验物品 047~050：一套护甲（047 头、048 胸、049 腿、050 脚）
     *
     * <p>数值在 {@link ModArmorMaterials#EXPERIMENT_ARMOR}：护甲值与铁同级（四件合计 15），每件 2 点韧性，
     * 砷铅铁锭、绯红钼锭、䤧锭 三样都能修，耐久倍率照铁
     *
     * <p>穿戴外观是源文件那一份基岩版几何加一张 64×64 贴图，四件共用：模型在
     * {@code client/model/ExperimentArmorModel}，发光层由 {@code mixin/ExperimentArmorGlowMixin} 叠上去
     */
    public static final DeferredItem<ExperimentArmorItem> EXPERIMENT_047 =
        armor("experiment_047", ArmorItem.Type.HELMET);
    public static final DeferredItem<ExperimentArmorItem> EXPERIMENT_048 =
        armor("experiment_048", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ExperimentArmorItem> EXPERIMENT_049 =
        armor("experiment_049", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ExperimentArmorItem> EXPERIMENT_050 =
        armor("experiment_050", ArmorItem.Type.BOOTS);

    /** 试验物品 033、034：按住右键架起来突刺的长矛，行为在 {@link ThrustSpearItem} */
    public static final DeferredItem<ThrustSpearItem> EXPERIMENT_033 = ITEMS.register(
        "experiment_033", () -> new ThrustSpearItem(new Item.Properties()));

    public static final DeferredItem<ThrustSpearItem> EXPERIMENT_034 = ITEMS.register(
        "experiment_034", () -> new ThrustSpearItem(new Item.Properties()));

    /** 序号上限：115 及以内 */
    private static final int LAST_NUMBER = 115;

    /** 铁级护甲的耐久倍率，试验物品 047~050 那套照铁 */
    private static final int IRON_DURABILITY_FACTOR = 15;

    /**
     * 已经被占掉的序号
     *
     * <p>001 是能当合成台用的方块，004 是照熔炉工作的那台机器（方块在
     * {@link ModBlocks#EXPERIMENT_004}、物品在 {@link ModItems#EXPERIMENT_004}），
     * 007 与 017 是上面那两把近战伤害 4、攻速 5 的裸武器，008 是上面那件
     * 5 点饥饿 5 点饱和度的食物，020 是上面那件 20 点饥饿 20 点饱和度、随机给效果的食物，
     * 027 是上面那支画红石点的笔，019 是那丛植物（方块在 {@link ModBlocks#EXPERIMENT_019}、
     * 物品在 {@link ModItems#EXPERIMENT_019}），033/034 是上面那两支长矛，
     * 051 是拆解台那台机器（方块在
     * {@link ModBlocks#EXPERIMENT_051}、物品在 {@link ModItems#EXPERIMENT_051}），
     * 052 是那颗方雷（方块在 {@link ModBlocks#EXPERIMENT_052}、物品在 {@link ModItems#EXPERIMENT_052}），
     * 065 是那台压出「打不出伤害」的场的机器（方块在 {@link ModBlocks#EXPERIMENT_065}、
     * 物品在 {@link ModItems#EXPERIMENT_065}），
     * 069 是那台压出「更新不处理」的场的机器（方块在 {@link ModBlocks#EXPERIMENT_069}、
     * 物品在 {@link ModItems#EXPERIMENT_069}），
     * 037~042 是上面那批工具与简单物品，043 是上面那台喷火器，045 是上面那面盾，
     * 047~050 是上面那套护甲，
     * 046 是砷铅铁钼反应权杖（注册 ID 仍是 {@code carminite_molybdenum_reactor_staff}），
     * 104 是那个能吃的东西（在 {@link ModItems#EXPERIMENT_104}），114 是能吃能摆的那一块（在
     * {@link ModItems#EXPERIMENT_114}），115 是暮色森林自带的试验物品
     *
     * <p>剩下的空号全部补成占位简单物品，贴图统一借 {@code experiment_temp.png}
     */
    private static final Set<Integer> TAKEN =
        Set.of(1, 4, 7, 8, 17, 19, 20, 27, 33, 34, 37, 38, 39, 40, 41, 42, 43, 45, 46, 47, 48, 49, 50,
            51, 52, 65, 69, 104, 114, 115);

    private static final SortedMap<Integer, DeferredItem<Item>> PLACEHOLDERS = registerPlaceholders();

    /**
     * 115 以内的空号，按序号升序
     *
     * <p>注册名由序号补零到三位拼出来，所以这里不存在手写 ID 与序号对不上的可能
     */
    public static SortedMap<Integer, DeferredItem<Item>> placeholders() {
        return PLACEHOLDERS;
    }

    private static SortedMap<Integer, DeferredItem<Item>> registerPlaceholders() {
        SortedMap<Integer, DeferredItem<Item>> map = new TreeMap<>();
        for (int number = 1; number <= LAST_NUMBER; number++) {
            if (TAKEN.contains(number)) {
                continue;
            }
            String id = "experiment_%03d".formatted(number);
            map.put(number, ITEMS.registerSimpleItem(id, new Item.Properties()));
        }
        return Collections.unmodifiableSortedMap(map);
    }

    /**
     * 裸近战武器的属性表：主手攻击力加 {@code attackDamage}、攻速加 {@code attackSpeed}
     *
     * <p>玩家底数是攻击力 1、攻速 4，所以「面板 4 伤害、5 攻速」对应的是 +3 与 +1。修饰符 ID 带上物品 ID
     * 前缀，同一属性上挂两个同 ID 的修饰符会互相顶掉
     */
    private static ItemAttributeModifiers meleeAttributes(String itemPath, double attackDamage,
                                                          double attackSpeed) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(DawnDuskTerminal.id(itemPath + "_attack_damage"), attackDamage,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(DawnDuskTerminal.id(itemPath + "_attack_speed"), attackSpeed,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    private static void describe(List<Component> tooltip, String key) {
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    /** 试验物品 047~050 四件护甲，材质固定在 {@link ModArmorMaterials#EXPERIMENT_ARMOR} */
    private static DeferredItem<ExperimentArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ExperimentArmorItem(
            ModArmorMaterials.EXPERIMENT_ARMOR, type,
            new Item.Properties().durability(type.getDurability(IRON_DURABILITY_FACTOR))));
    }

    private ModExperimentItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
