package com.dawnduskterminal.gametest;

import com.dawnduskterminal.item.Experiment020Item;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 试验物品 007、008、017、037~042 与 104 的规格：007/017 是近战伤害 4 攻速 5 的裸武器，
 * 008 是 5 饥饿 5 饱和度的食物，五件工具是钻石级，042 是普通物品，104 是两口吃完的食物
 *
 * <p>「钻石级」断言成 {@code getTier() == Tiers.DIAMOND} 而不是逐个比数值：耐久 1561、速度 8、
 * 攻击加成 3、附魔值 10、挖不动的方块 {@code INCORRECT_FOR_DIAMOND_TOOL} 全挂在那个 tier 实例上，
 * 换成自建 tier 才是会悄悄跑偏的地方
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class ExperimentItemTests {
    private static final List<Item> TOOLS = List.of(
        ModExperimentItems.EXPERIMENT_037.get(),
        ModExperimentItems.EXPERIMENT_038.get(),
        ModExperimentItems.EXPERIMENT_039.get(),
        ModExperimentItems.EXPERIMENT_040.get(),
        ModExperimentItems.EXPERIMENT_041.get());

    @GameTest(template = "ddt_empty")
    public static void toolsUseDiamondTier(GameTestHelper helper) {
        List<Class<?>> expected = List.of(SwordItem.class, PickaxeItem.class, HoeItem.class,
            ShovelItem.class, AxeItem.class);
        for (int i = 0; i < TOOLS.size(); i++) {
            Item item = TOOLS.get(i);
            String name = item.getDescriptionId();
            helper.assertTrue(expected.get(i).isInstance(item),
                name + " must be a " + expected.get(i).getSimpleName() + ", got " + item.getClass().getSimpleName());
            helper.assertTrue(item instanceof TieredItem, name + " must be a TieredItem");
            Tier tier = ((TieredItem) item).getTier();
            helper.assertTrue(tier == Tiers.DIAMOND,
                name + " must use Tiers.DIAMOND, got " + tier);
        }
        // 钉住「钻石级」这组数字，免得哪天 Tiers.DIAMOND 被换掉还装作没事
        helper.assertTrue(Tiers.DIAMOND.getUses() == 1561, "sanity: diamond tier uses moved");
        helper.assertTrue(Tiers.DIAMOND.getSpeed() == 8.0F, "sanity: diamond tier speed moved");
        helper.assertTrue(Tiers.DIAMOND.getAttackDamageBonus() == 3.0F, "sanity: diamond attack bonus moved");
        helper.assertTrue(Tiers.DIAMOND.getEnchantmentValue() == 10, "sanity: diamond enchantment value moved");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void toolsSitInVanillaToolTags(GameTestHelper helper) {
        // 原版附魔标签是从这五个物品标签派生的，缺一个这工具在附魔台上就没有附魔可给
        assertTagged(helper, ModExperimentItems.EXPERIMENT_037.get(), ItemTags.SWORDS, "swords");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_038.get(), ItemTags.PICKAXES, "pickaxes");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_039.get(), ItemTags.HOES, "hoes");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_040.get(), ItemTags.SHOVELS, "shovels");
        assertTagged(helper, ModExperimentItems.EXPERIMENT_041.get(), ItemTags.AXES, "axes");
        helper.succeed();
    }

    /**
     * 试验物品 007 的规格：近战伤害 4、攻速 5
     *
     * <p>面板上的数字是「玩家底数 + 主手修饰符」算出来的，所以这里也照这个算法算：底数从
     * {@code player} 身上取，不写死，版本改了玩家底数会直接红在这条断言上。攻速 5 也就是每 0.2 秒一刀
     */
    @GameTest(template = "ddt_empty")
    public static void experiment007IsAFourFiveWeapon(GameTestHelper helper) {
        assertMeleeSpec(helper, ModExperimentItems.EXPERIMENT_007.get(), 4.0D, 5.0D);
        helper.succeed();
    }

    /** 试验物品 017 与 007 同规格：近战伤害 4、攻速 5 */
    @GameTest(template = "ddt_empty")
    public static void experiment017IsAFourFiveWeapon(GameTestHelper helper) {
        assertMeleeSpec(helper, ModExperimentItems.EXPERIMENT_017.get(), 4.0D, 5.0D);
        helper.succeed();
    }

    /** 裸近战武器的面板规格：攻击伤害 {@code damage}、攻速 {@code speed} */
    private static void assertMeleeSpec(GameTestHelper helper, Item item, double damage, double speed) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack weapon = new ItemStack(item);
        double actualDamage = heldValue(player, weapon, Attributes.ATTACK_DAMAGE);
        double actualSpeed = heldValue(player, weapon, Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(actualDamage - damage) < 1.0E-6D,
            item.getDescriptionId() + " must hit for " + damage + ", got " + actualDamage);
        helper.assertTrue(Math.abs(actualSpeed - speed) < 1.0E-6D,
            item.getDescriptionId() + " must swing " + speed + " times a second, got " + actualSpeed);
    }

    /**
     * 试验物品 008 的规格：5 点饥饿、5 点饱和度
     *
     * <p>{@code FoodProperties#saturation()} 返回的是算好的点数（系数 × 饥饿 × 2），不是系数，
     * 所以这里断言 5.0；写系数却按点数断言的话就会在 0.5 上红
     */
    @GameTest(template = "ddt_empty")
    public static void experiment008IsAFiveFiveFood(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_008.get());
        FoodProperties food = stack.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "experiment_008 must be edible, but it carries no food component");
        helper.assertTrue(food.nutrition() == 5,
            "experiment_008 must be 5 hunger, got " + food.nutrition());
        helper.assertTrue(Math.abs(food.saturation() - 5.0F) < 1.0E-6F,
            "experiment_008 must be 5 saturation, got " + food.saturation());
        // 普通食物：不额外加效果，也不是「随时可吃」，吃完整份就消失
        helper.assertTrue(food.effects().isEmpty(),
            "experiment_008 must carry no food effect, got " + food.effects());
        helper.assertFalse(food.canAlwaysEat(), "experiment_008 must not be edible on a full stomach");
        helper.assertTrue(stack.getMaxStackSize() == 64,
            "experiment_008 must stack to 64, got " + stack.getMaxStackSize());
        helper.succeed();
    }

    /**
     * 试验物品 016 合成试验物品 013：无形状，原料是生的红钼矿、暮色森林的生铁木、试验物品 016、暮色森林的砷铅铁，
     * 一次出 3 个
     *
     * <p>断言走 {@code RecipeManager} 的 {@code getRecipeFor}，也就是真的拿四样东西「合成」一遍：
     * 配方 JSON 里某个原料写错字段名，整条配方会在加载时被丢掉——文件还在，合成台里却什么也出不来，
     * 只查文件是查不出这种事的
     */
    @GameTest(template = "ddt_empty")
    public static void experiment016CraftsExperiment013(GameTestHelper helper) {
        ItemStack from016 = new ItemStack(ModExperimentItems.placeholders().get(16).get());
        ItemStack expected = new ItemStack(ModExperimentItems.placeholders().get(13).get());
        CraftingInput input = CraftingInput.of(2, 2, List.of(
            new ItemStack(ModItems.RAW_SCARLET_MOLYBDENUM.get()),
            foreign("twilightforest:raw_ironwood"),
            from016,
            foreign("twilightforest:carminite")));
        var recipe = helper.getLevel().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(),
            "experiment_016 must craft experiment_013, but no crafting recipe matches those four items"
                + " (a malformed ingredient drops the whole recipe at load time)");
        ItemStack result = recipe.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(expected.getItem()),
            "the recipe must yield experiment_013, got " + result);
        helper.assertTrue(result.getCount() == 3,
            "the recipe must yield 3 experiment_013, got " + result.getCount());
        helper.succeed();
    }

    /**
     * 效果池的两条规矩：瞬发效果不要；非原版效果必须有一瓶药水带着它
     *
     * <p>模组爱注册一批只给内部逻辑用的技术性效果，摇出来玩家看到的是莫名其妙的一串图标。
     * 判据取 {@code BuiltInRegistries.POTION}：药水瓶显示的效果就是这张表，表里没有就等于没有这瓶药。
     * 原版不套这条（{@code bad_omen} 这类原版也没有药水，但玩家认）
     */
    @GameTest(template = "ddt_empty")
    public static void experiment020EffectPoolSkipsTechnicalModdedEffects(GameTestHelper helper) {
        Set<Holder<MobEffect>> potionEffects = new HashSet<>();
        BuiltInRegistries.POTION.holders().forEach(potion -> potion.value().getEffects()
            .forEach(effect -> potionEffects.add(effect.getEffect())));

        List<Holder<MobEffect>> pool = Experiment020Item.effectPool();
        helper.assertFalse(pool.isEmpty(), "the effect pool must not be empty");
        for (Holder<MobEffect> effect : pool) {
            helper.assertFalse(effect.value().isInstantenous(),
                "instant effects take no duration and must stay out of the pool, got " + effect);
            String namespace = effect.unwrapKey()
                .map(key -> key.location().getNamespace())
                .orElse("minecraft");
            if (!namespace.equals("minecraft")) {
                helper.assertTrue(potionEffects.contains(effect),
                    "modded effect " + effect + " has no potion bottle carrying it, so it must stay out"
                        + " of the pool (technical effects would just be noise on the player)");
            }
        }
        // 原版的非药水效果照旧留着
        helper.assertTrue(pool.contains(MobEffects.BAD_OMEN),
            "vanilla bad_omen has no potion but players know what it means, it must stay in the pool");
        // 瞬发那个在 1.21.1 里叫 HEAL，注册 ID 是 instant_health
        helper.assertFalse(pool.contains(MobEffects.HEAL),
            "instant_health must stay out of the pool");
        helper.succeed();
    }

    /**
     * 试验物品 020 的食物数值：20 点饥饿、20 点饱和度
     *
     * <p>20 点饥饿就是整条饥饿条，饱和度 20.0 也是上限，所以这一口直接把两样吃满
     */
    @GameTest(template = "ddt_empty")
    public static void experiment020IsATwentyTwentyFood(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_020.get());
        FoodProperties food = stack.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "experiment_020 must be edible, but it carries no food component");
        helper.assertTrue(food.nutrition() == 20,
            "experiment_020 must be 20 hunger, got " + food.nutrition());
        helper.assertTrue(Math.abs(food.saturation() - 20.0F) < 1.0E-6F,
            "experiment_020 must be 20 saturation, got " + food.saturation());
        helper.succeed();
    }

    /**
     * 摇效果：数量 0~3、每个 10~300 秒、一口之内不重复、都不是瞬发效果、等级固定 I 级
     *
     * <p>用固定种子连摇 2000 次：既验每条上下界，也验两头真的摇得到（0 个与 3 个都出现过）。
     * 随机源是参数，所以这里可复现，不会时红时绿
     */
    @GameTest(template = "ddt_empty")
    public static void experiment020RollsZeroToThreeTimedEffects(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20260220L);
        Set<Integer> counts = new HashSet<>();
        for (int round = 0; round < 2000; round++) {
            List<MobEffectInstance> rolled = Experiment020Item.rollEffects(random);
            counts.add(rolled.size());
            helper.assertTrue(rolled.size() <= Experiment020Item.MAX_EFFECTS,
                "one bite must roll at most " + Experiment020Item.MAX_EFFECTS + " effects, got " + rolled.size());
            Set<Holder<MobEffect>> seen = new HashSet<>();
            for (MobEffectInstance effect : rolled) {
                helper.assertTrue(seen.add(effect.getEffect()),
                    "one bite must not roll the same effect twice, got " + effect.getEffect());
                helper.assertTrue(effect.getAmplifier() == 0,
                    "every rolled effect must be level I, got amplifier " + effect.getAmplifier());
                helper.assertFalse(effect.getEffect().value().isInstantenous(),
                    "instant effects take no duration and must stay out of the pool, got " + effect.getEffect());
                int seconds = effect.getDuration() / 20;
                helper.assertTrue(seconds >= Experiment020Item.MIN_SECONDS
                        && seconds <= Experiment020Item.MAX_SECONDS,
                    "duration must stay within " + Experiment020Item.MIN_SECONDS + ".."
                        + Experiment020Item.MAX_SECONDS + " seconds, got " + effect.getDuration() + " ticks");
                helper.assertTrue(effect.getDuration() % 20 == 0,
                    "durations are whole seconds, got " + effect.getDuration() + " ticks");
            }
        }
        for (int expected = Experiment020Item.MIN_EFFECTS; expected <= Experiment020Item.MAX_EFFECTS; expected++) {
            helper.assertTrue(counts.contains(expected),
                "2000 bites must hit " + expected + " effects at least once, saw counts " + counts);
        }
        helper.succeed();
    }

    /** 吃一口：饥饿条与饱和度吃满，身上的效果就是摇出来的那 0~3 个，物品本身被吃掉 */
    @GameTest(template = "ddt_empty")
    public static void experiment020FillsBothBarsAndAppliesRolledEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0.0F);
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_020.get());
        ItemStack leftover = stack.getItem().finishUsingItem(stack, helper.getLevel(), player);

        helper.assertTrue(player.getFoodData().getFoodLevel() == 20,
            "a full bite must fill the hunger bar, got " + player.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - 20.0F) < 1.0E-4F,
            "a full bite must fill saturation to 20, got " + player.getFoodData().getSaturationLevel());
        helper.assertTrue(leftover.isEmpty(),
            "eating a single experiment_020 must consume it, got " + leftover);
        helper.assertTrue(player.getActiveEffects().size() <= Experiment020Item.MAX_EFFECTS,
            "one bite must apply at most " + Experiment020Item.MAX_EFFECTS + " effects, got "
                + player.getActiveEffects());
        for (MobEffectInstance effect : player.getActiveEffects()) {
            int seconds = effect.getDuration() / 20;
            helper.assertTrue(seconds >= Experiment020Item.MIN_SECONDS
                    && seconds <= Experiment020Item.MAX_SECONDS,
                "applied effects must last " + Experiment020Item.MIN_SECONDS + ".."
                    + Experiment020Item.MAX_SECONDS + " seconds, got " + effect.getDuration() + " ticks");
        }
        helper.succeed();
    }

    /** 别的模组的物品，只用字符串 ID 取；取不到就是空栈，让断言自己把这件事报出来 */
    private static ItemStack foreign(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    /** 手里拿着 {@code stack} 时某项属性的面板值，算法同原版 tooltip：玩家底数 + 物品修饰符 */
    private static double heldValue(Player player, ItemStack stack, Holder<Attribute> attribute) {
        double total = player.getAttributeBaseValue(attribute);
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().value() == attribute.value()) {
                // 武器修饰符全是 ADD_VALUE，原版五件套与权杖都没有别的运算
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    @GameTest(template = "ddt_empty")
    public static void experiment042IsAPlainItem(GameTestHelper helper) {
        Item item = ModExperimentItems.EXPERIMENT_042.get();
        helper.assertFalse(item instanceof TieredItem,
            "experiment_042 must stay a plain item, not a tool");
        helper.assertTrue(new ItemStack(item).getMaxStackSize() == 64,
            "experiment_042 must stack to 64, got " + new ItemStack(item).getMaxStackSize());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void experiment104IsEatenInTwoBites(GameTestHelper helper) {
        ItemStack whole = new ItemStack(ModItems.EXPERIMENT_104.get());
        ItemStack half = new ItemStack(ModItems.EXPERIMENT_104_HALF.get());
        FoodProperties firstBite = whole.get(DataComponents.FOOD);
        FoodProperties secondBite = half.get(DataComponents.FOOD);
        helper.assertTrue(firstBite != null && secondBite != null,
            "both experiment_104 and its half must be edible");
        helper.assertTrue(whole.getMaxStackSize() == 1,
            "experiment_104 must not stack, got " + whole.getMaxStackSize());
        helper.assertTrue(half.getMaxStackSize() == 1,
            "the half must not stack either, got " + half.getMaxStackSize());
        // 「一个能吃两口」全靠这一条：第一口把整块换成半块，第二口才把东西吃掉
        Item remainder = firstBite.usingConvertsTo().orElse(ItemStack.EMPTY).getItem();
        helper.assertTrue(remainder == ModItems.EXPERIMENT_104_HALF.get(),
            "the first bite must leave the half behind, got " + remainder.getDescriptionId());
        helper.assertTrue(secondBite.usingConvertsTo().isEmpty(),
            "the last bite must leave nothing behind, got " + secondBite.usingConvertsTo());
        // 一口 10 点饥饿、0.6 饱和度系数（饱食度 10 × 0.6 × 2 = 12.0，等于两块烤马铃薯），
        // 两口合计 20 点饥饿、饱食度 24.0
        helper.assertTrue(firstBite.nutrition() == 10,
            "the first bite must be 10 hunger, got " + firstBite.nutrition());
        helper.assertTrue(secondBite.nutrition() == 10,
            "the second bite must be 10 hunger, got " + secondBite.nutrition());
        helper.assertTrue(Math.abs(firstBite.saturation() - 12.0F) < 0.01F,
            "the first bite must be 12 saturation, got " + firstBite.saturation());
        int hunger = firstBite.nutrition() + secondBite.nutrition();
        helper.assertTrue(hunger == 20, "both bites must add up to 20 hunger, got " + hunger);
        float saturation = firstBite.saturation() + secondBite.saturation();
        helper.assertTrue(Math.abs(saturation - 24.0F) < 0.01F,
            "both bites must add up to 24 saturation, got " + saturation);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void placeholdersFillEveryGapUpTo115(GameTestHelper helper) {
        SortedMap<Integer, DeferredItem<Item>> placeholders = ModExperimentItems.placeholders();
        // 1..115 共 115 个号，被占的是 001 方块、004 熔炉机器、007/017 两把 4/5 裸武器、008 与 020 两件食物、027 那支画点的笔、
        // 019 那丛植物、033/034 两支长矛、037~042 六件、043 那台喷火器、045 那面盾、046 权杖、047~050 那套护甲、
        // 051 拆解台、052 那颗方雷、104 那个食物、114 那块蛋糕、115 暮色森林那件
        helper.assertTrue(placeholders.size() == 87,
            "placeholders must cover the 87 free numbers of 1..115, got " + placeholders.size());
        for (int number : new int[] {2, 6, 9, 16, 18, 32, 35, 44, 113}) {
            helper.assertTrue(placeholders.containsKey(number),
                "number " + number + " must have a placeholder item");
        }
        for (int number : new int[] {1, 4, 7, 8, 17, 19, 20, 27, 33, 34, 37, 38, 39, 40, 41, 42, 43, 45, 46,
            47, 48, 49, 50, 51, 52, 104, 114, 115}) {
            helper.assertFalse(placeholders.containsKey(number),
                "number " + number + " is taken and must not get a placeholder item");
        }
        // 注册名必须就是补零三位的序号，不然补出来的是个对不上号的空壳
        placeholders.forEach((number, holder) -> helper.assertTrue(
            holder.getId().getPath().equals("experiment_%03d".formatted(number)),
            "placeholder for " + number + " registered as " + holder.getId()));
        helper.succeed();
    }

    /**
     * 试验物品 003 是熔炉里的铜矿：烧出原版铜锭，走的是 {@code recipe/experiment_003_smelting.json}
     *
     * <p>它本身仍是占位空壳（没有名字之外的用途），所以这条配方就是它存在的全部意义，
     * 断言走 {@code RecipeManager} 而不是查文件，配方写错路径或写错物品名都会红
     */
    @GameTest(template = "ddt_empty")
    public static void experiment003SmeltsIntoCopperIngot(GameTestHelper helper) {
        ItemStack input = new ItemStack(ModExperimentItems.placeholders().get(3).get());
        helper.assertTrue(input.is(ModExperimentItems.placeholders().get(3).get()),
            "number 3 must resolve to a placeholder item");
        var smelting = helper.getLevel().getRecipeManager()
            .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), helper.getLevel());
        helper.assertTrue(smelting.isPresent(), "dawnduskterminal:experiment_003 must be smeltable in a furnace");
        ItemStack result = smelting.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(Items.COPPER_INGOT),
            "dawnduskterminal:experiment_003 must smelt into minecraft:copper_ingot, got " + result);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void spearItemsUseBowAnimation(GameTestHelper helper) {
        for (Item spear : List.of(ModExperimentItems.EXPERIMENT_033.get(),
            ModExperimentItems.EXPERIMENT_034.get())) {
            ItemStack stack = new ItemStack(spear);
            // 拉弓动画走原版 UseAnim.BOW：第一人称的拉满过程与第三人称的双臂姿势都由此而来，
            // 前提是模型带上 item/bow 那套 thirdperson 变换，见两个 model json
            helper.assertTrue(stack.getUseAnimation() == UseAnim.BOW,
                spear.getDescriptionId() + " must animate like a drawn bow, got " + stack.getUseAnimation());
            helper.assertTrue(stack.getUseDuration(null) == 72000,
                spear.getDescriptionId() + " must stay usable as long as a bow, got " + stack.getUseDuration(null));
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void spearPrickDamagesWithoutKnockback(GameTestHelper helper) {
        // 原版甜浆果丛那个伤害类型：普通伤害，且在 #minecraft:no_knockback 里
        DamageSource prick = helper.getLevel().damageSources().sweetBerryBush();
        helper.assertTrue(prick.is(DamageTypeTags.NO_KNOCKBACK),
            "minecraft:sweet_berry_bush must sit in #minecraft:no_knockback, otherwise every prick shoves the target away");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setDeltaMovement(Vec3.ZERO);
        float healthBefore = zombie.getHealth();
        helper.assertTrue(zombie.hurt(prick, 1.0F), "the spear prick must go through");
        helper.assertTrue(zombie.getHealth() < healthBefore,
            "the zombie must lose health, before " + healthBefore + " after " + zombie.getHealth());
        helper.assertTrue(zombie.getDeltaMovement().lengthSqr() < 1.0E-6,
            "the prick must not knock the zombie back, got " + zombie.getDeltaMovement());
        helper.succeed();
    }

    private static void assertTagged(GameTestHelper helper, Item item, TagKey<Item> tag, String tagName) {
        helper.assertTrue(new ItemStack(item).is(tag),
            item.getDescriptionId() + " must be in #minecraft:" + tagName
                + ", otherwise the enchanting table offers it nothing");
    }

    private ExperimentItemTests() {}
}
