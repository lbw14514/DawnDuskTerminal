package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.CampCarminiteFireBlock;
import com.dawnduskterminal.block.entity.CampCarminiteFireBlockEntity;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.world.CampCarminiteFireAura;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 红篝火：一团会烧人的篝火，外加 16 格里那套把消耗度倒过来算的规矩
 *
 * <p>伤害与粒子照 {@code CarminiteFireBlock} 验：直接调 {@code BlockState#entityInside}，
 * 那是原版「实体待在方块里」那一刻的入口。消耗度那一套用 {@code makeMockPlayer} 放出玩家，
 * 直接调 {@code Player#causeFoodExhaustion} —— 那就是原版每个动作最终都会走到的那个口子
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class CampCarminiteFireTests {
    private static final BlockPos CENTRE = new BlockPos(4, 2, 4);

    /** 与 {@code CarminiteFireBlock} 同一条：猪 10 点血，挨一下剩 8 */
    private static final float PIG_HEALTH = 10.0F;

    private static final float CONTACT_DAMAGE = 2.0F;

    /** 原版篝火的状态：点着、含水、朝向 */
    @GameTest(template = "ddt_empty")
    public static void blockLooksAndLightsLikeACampfire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());

        BlockState state = level.getBlockState(helper.absolutePos(CENTRE));
        helper.assertTrue(
                state.is(ModBlocks.CAMP_CARMINITE_FIRE.get()), "the campfire must be placeable, got " + state);
        helper.assertTrue(state.getValue(CampCarminiteFireBlock.LIT), "a fresh campfire must be lit");
        helper.assertFalse(state.getValue(CampCarminiteFireBlock.WATERLOGGED), "a fresh campfire must be dry");
        helper.assertTrue(
                state.hasProperty(CampCarminiteFireBlock.FACING), "the campfire must keep the campfire facing");
        helper.assertTrue(
                state.is(BlockTags.CAMPFIRES),
                "the campfire must be in #minecraft:campfires, otherwise flint and steel cannot light it");

        helper.assertTrue(
                state.getLightEmission(level, helper.absolutePos(CENTRE)) == 15,
                "a lit campfire must emit light 15, got " + state.getLightEmission(level, helper.absolutePos(CENTRE)));
        BlockState unlit = state.setValue(CampCarminiteFireBlock.LIT, false);
        helper.assertTrue(
                unlit.getLightEmission(level, helper.absolutePos(CENTRE)) == 0,
                "an unlit campfire must emit no light, got "
                        + unlit.getLightEmission(level, helper.absolutePos(CENTRE)));
        helper.assertTrue(
                Math.abs(state.getShape(level, helper.absolutePos(CENTRE))
                                        .bounds()
                                        .getYsize()
                                - 0.4375D)
                        < 1.0E-4D,
                "the campfire shape must be the vanilla seven-pixel log pile");

        helper.assertTrue(CampfireBlock.canLight(unlit), "an unlit campfire must be lightable");
        helper.assertFalse(CampfireBlock.canLight(state), "a lit campfire must not be lightable again");
        helper.succeed();
    }

    /** 打火石点着、铲子熄灭：两条都走 NeoForge 的工具动作，不靠 {@code useItemOn} */
    @GameTest(template = "ddt_empty")
    public static void flintAndSteelLightsItAndShovelDousesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(
                CENTRE,
                ModBlocks.CAMP_CARMINITE_FIRE.get().defaultBlockState().setValue(CampCarminiteFireBlock.LIT, false));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState unlit = level.getBlockState(pos);
        BlockState lit = unlit.getToolModifiedState(
                context(level, player, new ItemStack(Items.FLINT_AND_STEEL), pos),
                ItemAbilities.FIRESTARTER_LIGHT,
                false);
        helper.assertTrue(lit != null, "flint and steel must be able to light the campfire");
        helper.assertTrue(lit.getValue(CampCarminiteFireBlock.LIT), "flint and steel must set lit=true, got " + lit);

        BlockState doused = lit.getToolModifiedState(
                context(level, player, new ItemStack(Items.IRON_SHOVEL), pos), ItemAbilities.SHOVEL_DOUSE, false);
        helper.assertTrue(doused != null, "a shovel must be able to douse the campfire");
        helper.assertFalse(doused.getValue(CampCarminiteFireBlock.LIT), "a shovel must set lit=false, got " + doused);
        helper.succeed();
    }

    /** 泡水即灭：灌水那一支要把火熄掉，不能只记一个「含水」 */
    @GameTest(template = "ddt_empty")
    public static void waterExtinguishesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());

        BlockState before = level.getBlockState(pos);
        helper.assertTrue(
                ModBlocks.CAMP_CARMINITE_FIRE.get().placeLiquid(level, pos, before, Fluids.WATER.getSource(false)),
                "water must be placeable in the campfire");
        BlockState after = level.getBlockState(pos);
        helper.assertFalse(after.getValue(CampCarminiteFireBlock.LIT), "water must put the campfire out");
        helper.assertTrue(after.getValue(CampCarminiteFireBlock.WATERLOGGED), "the campfire must be waterlogged");
        helper.succeed();
    }

    /**
     * 接触伤害与砷铅铁火焰同款：2 点、用 {@code carminite_fire} 那条伤害类型、不点火
     *
     * <p>熄灭的那一堆木头踩上去不掉血
     */
    @GameTest(template = "ddt_empty")
    public static void contactHurtsExactlyLikeCarminiteFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());

        Pig pig = helper.spawn(EntityType.PIG, 5, 2, 4);
        int fireTicksBefore = pig.getRemainingFireTicks();
        BlockState lit = level.getBlockState(pos);
        lit.entityInside(level, pos, pig);
        helper.assertTrue(
                Math.abs(pig.getHealth() - (PIG_HEALTH - CONTACT_DAMAGE)) < 1.0E-4F,
                "a lit campfire must deal exactly 2 damage on contact, the pig has " + pig.getHealth());
        helper.assertTrue(
                pig.getLastDamageSource() != null && pig.getLastDamageSource().is(ModDamageTypes.CARMINITE_FIRE),
                "the campfire must hit with the carminite_fire damage type, got " + pig.getLastDamageSource());
        helper.assertTrue(
                pig.getRemainingFireTicks() == fireTicksBefore,
                "the campfire must never set an entity on fire, fire ticks went from " + fireTicksBefore + " to "
                        + pig.getRemainingFireTicks());

        pig.setHealth(pig.getMaxHealth());
        BlockState unlit = lit.setValue(CampCarminiteFireBlock.LIT, false);
        unlit.entityInside(level, pos, pig);
        helper.assertTrue(
                Math.abs(pig.getHealth() - PIG_HEALTH) < 1.0E-4F,
                "an unlit campfire must deal no damage, the pig has " + pig.getHealth());
        helper.succeed();
    }

    /** 挖掉掉自己：表里没有工具条件，空手也掉一颗，且正好一颗 */
    @GameTest(template = "ddt_empty")
    public static void miningItDropsItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());

        List<ItemStack> drops = Block.getDrops(level.getBlockState(pos), level, pos, null);
        helper.assertTrue(drops.size() == 1, "the campfire must drop exactly one stack, got " + drops.size());
        helper.assertTrue(
                drops.get(0).is(ModItems.CAMP_CARMINITE_FIRE.get()),
                "the campfire must drop itself, it dropped " + drops.get(0));
        helper.assertFalse(drops.get(0).isEmpty(), "the campfire must not drop an empty stack: " + drops.get(0));
        helper.succeed();
    }

    /**
     * 规则一：16 格内，增加量不大于 1 的消耗度改成等量减少；走到 16 格外照旧算增加
     *
     * <p>边界按 1.0 卡：正好 1.0 算小增量，1.0001 就不算了
     */
    @GameTest(template = "ddt_empty")
    public static void smallIncrementsAreInvertedWithinSixteenBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());
        Vec3 centre = Vec3.atCenterOf(pos);

        Player near = playerAt(helper, centre.add(2.0D, 0.0D, 0.0D));
        near.getFoodData().setExhaustion(2.0F);
        near.causeFoodExhaustion(0.5F);
        helper.assertTrue(
                Math.abs(near.getFoodData().getExhaustionLevel() - 1.5F) < 1.0E-4F,
                "a 0.5 increment must become a 0.5 decrease, exhaustion is "
                        + near.getFoodData().getExhaustionLevel());

        near.getFoodData().setExhaustion(2.0F);
        near.causeFoodExhaustion(1.0F);
        helper.assertTrue(
                Math.abs(near.getFoodData().getExhaustionLevel() - 1.0F) < 1.0E-4F,
                "an increment of exactly 1 must still be inverted, exhaustion is "
                        + near.getFoodData().getExhaustionLevel());

        near.getFoodData().setExhaustion(2.0F);
        near.causeFoodExhaustion(1.0001F);
        helper.assertTrue(
                Math.abs(near.getFoodData().getExhaustionLevel() - 3.0001F) < 1.0E-3F,
                "anything above 1 must stay an increase, exhaustion is "
                        + near.getFoodData().getExhaustionLevel());

        Player far = playerAt(helper, centre.add(CampCarminiteFireAura.RADIUS + 2.0D, 0.0D, 0.0D));
        far.getFoodData().setExhaustion(2.0F);
        far.causeFoodExhaustion(0.5F);
        helper.assertTrue(
                Math.abs(far.getFoodData().getExhaustionLevel() - 2.5F) < 1.0E-4F,
                "outside the aura an increment must stay an increase, exhaustion is "
                        + far.getFoodData().getExhaustionLevel());
        helper.succeed();
    }

    /**
     * 大于 1 的增量一点也没变：照样加进消耗度、照样按原版「大于 4 掉一点」扣
     *
     * <p>从 0 起算的 6.0 只该扣一点：先加满 6，原版那一 tick 扣 4、掉一点饱和度、剩 2
     */
    @GameTest(template = "ddt_empty")
    public static void largeIncrementsConsumeExactlyLikeVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());
        Player player =
                playerAt(helper, Vec3.atCenterOf(helper.absolutePos(CENTRE)).add(2.0D, 0.0D, 0.0D));
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(5.0F);
        food.setExhaustion(0.0F);

        // 自然回血那一档是 6.0
        player.causeFoodExhaustion(6.0F);
        helper.assertTrue(
                Math.abs(food.getExhaustionLevel() - 6.0F) < 1.0E-4F,
                "a 6 point increment must stay an increase, exhaustion is " + food.getExhaustionLevel());

        // 原版那一 tick：大于 4，扣 4 点、掉一点饱和度
        food.tick(player);
        helper.assertTrue(
                Math.abs(food.getExhaustionLevel() - 2.0F) < 1.0E-4F,
                "vanilla must consume exactly 4 points, exhaustion is " + food.getExhaustionLevel());
        helper.assertTrue(
                Math.abs(food.getSaturationLevel() - 4.0F) < 1.0E-4F,
                "vanilla must take exactly one saturation point, saturation is " + food.getSaturationLevel());
        helper.assertTrue(
                food.getFoodLevel() == 20, "saturation must be spent before hunger, food is " + food.getFoodLevel());
        helper.succeed();
    }

    /** 篝火边上照样回血：消耗度那条规则不碰原版的回血链路 */
    @GameTest(template = "ddt_empty")
    public static void healthStillRegeneratesNextToTheFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());
        Player player =
                playerAt(helper, Vec3.atCenterOf(helper.absolutePos(CENTRE)).add(2.0D, 0.0D, 0.0D));
        FoodData food = player.getFoodData();
        player.setHealth(10.0F);
        food.setFoodLevel(20);
        food.setSaturation(6.0F);
        food.setExhaustion(0.0F);

        // 原版每 tick 的顺序：先食物那一 tick，再轮到每 tick 的气场结算
        for (int i = 0; i < 60; i++) {
            food.tick(player);
            CampCarminiteFireAura.tick(player);
        }
        helper.assertTrue(
                player.getHealth() > 10.0F,
                "a hurt player next to a lit campfire must still regenerate, health is " + player.getHealth());
        helper.succeed();
    }

    /**
     * 规则二：消耗度见底时补 1 点<b>饥饿值</b>（饥饿值满了才补饱和度），然后把消耗度压回 4
     *
     * <p>先饥饿值不是为了好看：原版回血的两道门都只看饥饿值，快的那道要 20、慢的那道要 18，
     * 饥饿值掉到 17 以下之后补饱和度永远补不回这两道门。这一条就是「生命值完全不恢复」的根因
     */
    @GameTest(template = "ddt_empty")
    public static void drainedExhaustionGrantsOneHungerPointThenRefillsToFour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());
        Player player =
                playerAt(helper, Vec3.atCenterOf(helper.absolutePos(CENTRE)).add(2.0D, 0.0D, 0.0D));
        FoodData food = player.getFoodData();
        food.setFoodLevel(17);
        food.setSaturation(0.0F);
        food.setExhaustion(0.2F);

        // 饥饿值写在前面，就先补饥饿值：17 补到 18 正好跨过慢速回血那道门
        player.causeFoodExhaustion(0.5F);
        helper.assertTrue(
                food.getFoodLevel() == 18,
                "an empty exhaustion bar must raise the hunger point first, food is " + food.getFoodLevel());
        helper.assertTrue(
                Math.abs(food.getSaturationLevel()) < 1.0E-4F,
                "saturation must stay untouched while hunger is not full, saturation is " + food.getSaturationLevel());
        helper.assertTrue(
                Math.abs(food.getExhaustionLevel() - CampCarminiteFireAura.REFILL) < 1.0E-4F,
                "the refill must push exhaustion back to 4, exhaustion is " + food.getExhaustionLevel());

        // 那 4 点要走完才会补下一次：三次 1.0 之后还剩 1 点
        player.causeFoodExhaustion(1.0F);
        player.causeFoodExhaustion(1.0F);
        player.causeFoodExhaustion(1.0F);
        helper.assertTrue(
                food.getFoodLevel() == 18,
                "three points of work must not grant a second point yet, food is " + food.getFoodLevel());
        // 第四点走完，见底，再补一点
        player.causeFoodExhaustion(1.0F);
        helper.assertTrue(
                food.getFoodLevel() == 19,
                "walking the 4 points down must grant the next point, food is " + food.getFoodLevel());

        // 饥饿值满了就补饱和度：换一个干净的玩家，前面那位还压着 4 点
        Player full =
                playerAt(helper, Vec3.atCenterOf(helper.absolutePos(CENTRE)).add(2.0D, 0.0D, 0.0D));
        full.getFoodData().setFoodLevel(20);
        full.getFoodData().setSaturation(5.0F);
        full.getFoodData().setExhaustion(0.1F);
        full.causeFoodExhaustion(0.5F);
        helper.assertTrue(
                Math.abs(full.getFoodData().getSaturationLevel() - 6.0F) < 1.0E-4F,
                "with hunger full the refill must raise saturation, saturation is "
                        + full.getFoodData().getSaturationLevel());

        // 走过来的时候消耗度本来就是 0：每 tick 那一下也要补
        Player walker =
                playerAt(helper, Vec3.atCenterOf(helper.absolutePos(CENTRE)).add(2.0D, 0.0D, 0.0D));
        walker.getFoodData().setFoodLevel(10);
        walker.getFoodData().setSaturation(0.0F);
        walker.getFoodData().setExhaustion(0.0F);
        CampCarminiteFireAura.tick(walker);
        helper.assertTrue(
                walker.getFoodData().getFoodLevel() == 11,
                "walking in with an empty exhaustion bar must grant a point too, food is "
                        + walker.getFoodData().getFoodLevel());
        CampCarminiteFireAura.tick(walker);
        helper.assertTrue(
                walker.getFoodData().getFoodLevel() == 11,
                "the 4 point refill must stop a second grant on the next tick, food is "
                        + walker.getFoodData().getFoodLevel());
        helper.succeed();
    }

    /**
     * 它是一台能烤东西的篝火：方块实体带着原版篝火那套四格食材与进度，
     * 配方查询、放食材、烧烤 ticker 三样都通
     */
    @GameTest(template = "ddt_empty")
    public static void itCooksLikeAVanillaCampfire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(CENTRE, ModBlocks.CAMP_CARMINITE_FIRE.get());

        BlockEntity blockEntity = level.getBlockEntity(pos);
        helper.assertTrue(
                blockEntity instanceof CampCarminiteFireBlockEntity,
                "the campfire must bring the campfire cooking block entity, got " + blockEntity);
        CampCarminiteFireBlockEntity campfire = (CampCarminiteFireBlockEntity) blockEntity;

        ItemStack porkchop = new ItemStack(Items.PORKCHOP);
        helper.assertTrue(
                campfire.getCookableRecipe(porkchop).isPresent(),
                "the campfire must accept vanilla campfire recipes, a raw porkchop is not cookable");
        helper.assertTrue(
                campfire.placeFood(playerAt(helper, Vec3.atCenterOf(pos).add(0.0D, 1.0D, 0.0D)), porkchop, 600),
                "the campfire must accept a slice of food");
        helper.assertTrue(
                campfire.getItems().get(0).is(Items.PORKCHOP),
                "the placed food must sit on the campfire, it holds "
                        + campfire.getItems().get(0));

        BlockState lit = level.getBlockState(pos);
        helper.assertTrue(
                ModBlocks.CAMP_CARMINITE_FIRE.get().getTicker(level, lit, ModBlockEntities.CAMP_CARMINITE_FIRE.get())
                        != null,
                "a lit campfire must run the campfire cooking ticker");
        helper.succeed();
    }

    /** 熄灭的那一堆不算数：气场只在点着的时候生效 */
    @GameTest(template = "ddt_empty")
    public static void unlitCampfireHasNoAura(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        helper.setBlock(
                CENTRE,
                ModBlocks.CAMP_CARMINITE_FIRE.get().defaultBlockState().setValue(CampCarminiteFireBlock.LIT, false));

        Player player = playerAt(helper, Vec3.atCenterOf(pos).add(2.0D, 0.0D, 0.0D));
        player.getFoodData().setExhaustion(2.0F);
        player.causeFoodExhaustion(0.5F);
        helper.assertTrue(
                Math.abs(player.getFoodData().getExhaustionLevel() - 2.5F) < 1.0E-4F,
                "an unlit campfire must not invert anything, exhaustion is "
                        + player.getFoodData().getExhaustionLevel());
        helper.succeed();
    }

    /**
     * 客户端那侧的接线：方块状态八格、两张模型、物品模型、三张贴图与两条动画
     *
     * <p>服务端跑不到模型，所以这些 JSON 只能在这儿读一遍：语法坏了、指向不存在的父模型的、
     * 贴图名写错的都在这里先失败，不用等进客户端看紫黑格子
     */
    @GameTest(template = "ddt_empty")
    public static void assetsWiringIsComplete(GameTestHelper helper) {
        JsonObject states = readJson(helper, "/assets/dawnduskterminal/blockstates/campcarminitefire.json")
                .getAsJsonObject("variants");
        helper.assertTrue(states.size() == 8, "the campfire must declare 8 blockstate variants, got " + states.size());
        for (String facing : new String[] {"north", "east", "south", "west"}) {
            for (boolean lit : new boolean[] {false, true}) {
                String key = "facing=" + facing + ",lit=" + lit;
                helper.assertTrue(states.has(key), "blockstates/campcarminitefire.json must declare " + key);
                String model = states.getAsJsonObject(key).get("model").getAsString();
                String expected = lit
                        ? "dawnduskterminal:block/campcarminitefire"
                        : "dawnduskterminal:block/campcarminitefire_off";
                helper.assertTrue(model.equals(expected), key + " must point at " + expected + ", got " + model);
            }
        }

        JsonObject litModel = readJson(helper, "/assets/dawnduskterminal/models/block/campcarminitefire.json");
        helper.assertTrue(
                litModel.get("parent").getAsString().equals("minecraft:block/template_campfire"),
                "the lit model must reuse the vanilla campfire template, got " + litModel.get("parent"));
        JsonObject litTextures = litModel.getAsJsonObject("textures");
        assertTexture(helper, litTextures, "fire", "dawnduskterminal:block/campcarminitefire_fire");
        assertTexture(helper, litTextures, "lit_log", "dawnduskterminal:block/campcarminitefire_log_lit");
        assertTexture(helper, litTextures, "log", "dawnduskterminal:block/campcarminitefire_log");
        assertTexture(helper, litTextures, "particle", "dawnduskterminal:block/campcarminitefire_log");

        JsonObject offModel = readJson(helper, "/assets/dawnduskterminal/models/block/campcarminitefire_off.json");
        helper.assertTrue(
                offModel.get("parent").getAsString().equals("minecraft:block/campfire_off"),
                "the unlit model must reuse the vanilla unlit campfire model, got " + offModel.get("parent"));
        assertTexture(
                helper, offModel.getAsJsonObject("textures"), "log", "dawnduskterminal:block/campcarminitefire_log");

        JsonObject itemModel = readJson(helper, "/assets/dawnduskterminal/models/item/campcarminitefire.json");
        helper.assertTrue(
                itemModel.get("parent").getAsString().equals("minecraft:item/generated"),
                "the item must use its own icon, got parent " + itemModel.get("parent"));
        assertTexture(
                helper, itemModel.getAsJsonObject("textures"), "layer0", "dawnduskterminal:item/campcarminitefire");

        for (String texture : new String[] {
            "campcarminitefire_fire.png",
            "campcarminitefire_fire.png.mcmeta",
            "campcarminitefire_log.png",
            "campcarminitefire_log_lit.png",
            "campcarminitefire_log_lit.png.mcmeta"
        }) {
            helper.assertTrue(
                    resourceAt("/assets/dawnduskterminal/textures/block/" + texture) != null,
                    "textures/block/" + texture + " must ship inside the mod resources");
        }
        helper.assertTrue(
                resourceAt("/assets/dawnduskterminal/textures/item/campcarminitefire.png") != null,
                "textures/item/campcarminitefire.png must ship inside the mod resources");
        helper.succeed();
    }

    private static void assertTexture(GameTestHelper helper, JsonObject textures, String key, String expected) {
        helper.assertTrue(textures.has(key), "the model must declare the " + key + " texture");
        helper.assertTrue(
                textures.get(key).getAsString().equals(expected),
                "the " + key + " texture must be " + expected + ", got " + textures.get(key));
    }

    private static java.io.InputStream resourceAt(String resource) {
        return CampCarminiteFireTests.class.getResourceAsStream(resource);
    }

    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (java.io.InputStream stream = resourceAt(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            helper.assertTrue(false, "failed to read " + resource + ": " + e);
            return new JsonObject();
        }
    }

    private static Player playerAt(GameTestHelper helper, Vec3 at) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    private static UseOnContext context(Level level, Player player, ItemStack stack, BlockPos pos) {
        return new UseOnContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private CampCarminiteFireTests() {}
}
