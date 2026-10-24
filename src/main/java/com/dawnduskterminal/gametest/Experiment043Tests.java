package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.Experiment043Item;
import com.dawnduskterminal.item.FireJetProcess;
import com.dawnduskterminal.registry.ModExperimentItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
/**
 * 试验物品 043 的规格
 *
 * <p>钉住：占位号已经让出、64 点耐久且喷一次扣 1 点、大火焰粒子注册过、单击之后整段
 * 「预热 80 tick（三次点火）→ 火焰 61 tick（13 拍伤害）」跑完并自己结束、视线方向上的活物被点着、
 * 喷到 14 格、每一拍都打得进无敌帧、玩家挪位置转身后火跟着走、施放期间换走手里的东西就从脚下往上喷
 * 并烧到自己、背后 / 侧面 / 超距 / 火焰免疫的都不挨
 *
 * <p>时间线直接数 {@link com.dawnduskterminal.item.FireJetProcess} 的计数器，不靠 GameTest 等 141 tick
 * 自然刻；目标用猪（无护甲，掉血正好等于面板伤害）
 */

public final class Experiment043Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_043";
    private static final float PIG_HEALTH = 10.0F;
    private static final double PLAYER_X = 8.5;
    private static final double PLAYER_Z = 2.5;

    @GameTest(
        template = "ddt_empty"
    )
    public static void itemReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(
            ModExperimentItems.placeholders().containsKey(Integer.valueOf(43)), "number 43 must not be a placeholder anymore, it has a real item now"
        );
        ItemStack stack = new ItemStack((ItemLike)ModExperimentItems.EXPERIMENT_043.get());
        helper.assertTrue(
            stack.getItem() instanceof Experiment043Item,
            "dawnduskterminal:experiment_043 must be an " + Experiment043Item.class.getSimpleName() + ", got " + stack.getItem()
        );
        helper.assertTrue(stack.getMaxStackSize() == 1, "dawnduskterminal:experiment_043 is damageable, it must not stack, got " + stack.getMaxStackSize());
        helper.assertTrue(stack.getMaxDamage() == 64, "dawnduskterminal:experiment_043 must have 64 uses, got " + stack.getMaxDamage());
        helper.assertTrue(
            BuiltInRegistries.PARTICLE_TYPE.containsKey(DawnDuskTerminal.id("particle_jet_flame")), "dawnduskterminal:particle_jet_flame must be registered"
        );
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void singleClickRunsTheWholeProcess(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        FireJetProcess process = FireJetProcess.detached(helper.getLevel(), player);

        for (int i = 0; i < 80; i++) {
            process.tick();
        }

        helper.assertTrue(process.pops() == 3, "the preheat phase must pop three times (tick 20, 40, 60), got " + process.pops());
        helper.assertTrue(process.flameTicks() == 0, "the flame phase must not start before tick 80, got " + process.flameTicks());
        helper.assertTrue(process.damagePulses() == 0, "nothing may be hurt before the flame starts, got " + process.damagePulses());

        for (int i = 0; i < 61; i++) {
            process.tick();
        }

        helper.assertTrue(process.flameTicks() == 61, "the flame phase must last 61 ticks, got " + process.flameTicks());
        helper.assertTrue(process.damagePulses() == 13, "the flame must hit 13 times (tick 5, 10, ..., 60 and the closing tick), got " + process.damagePulses());
        helper.assertTrue(process.finished(), "the process must finish after 141 ticks");
        helper.assertTrue(process.age() == 141, "the process must stop at 141, got " + process.age());
        int age = process.age();

        for (int i = 0; i < 10; i++) {
            process.tick();
        }

        helper.assertTrue(process.age() == age, "a finished process must not tick further");
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void singleClickCostsDurabilityAndCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = aimPlayer(helper);
        ItemStack stack = new ItemStack((ItemLike)ModExperimentItems.EXPERIMENT_043.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(
            result.getResult().consumesAction(), "right clicking dawnduskterminal:experiment_043 must consume the click, got " + result.getResult()
        );
        helper.assertTrue(stack.getDamageValue() == 1, "one shot must cost one durability, got " + stack.getDamageValue());
        helper.assertTrue(player.getCooldowns().isOnCooldown(stack.getItem()), "one shot must start the cooldown");
        helper.assertTrue(
            stack.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
            "a second click inside the cooldown must not fire another jet"
        );
        FireJetProcess.clear();
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetBurnsWhatIsInFront(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        Pig pig = (Pig)helper.spawn(EntityType.PIG, 8, 1, 5);
        FireJetProcess process = FireJetProcess.detached(helper.getLevel(), player);

        for (int i = 0; i < 85; i++) {
            process.tick();
        }

        helper.assertTrue(
            process.targetsHit() >= 1,
            "the pig must be inside the damage volume, targets=" + process.targetsHit() + " pig=" + pig.position() + " box=" + pig.getBoundingBox()
        );
        helper.assertTrue(
            Math.abs(pig.getHealth() - 6.0F) < 1.0E-4F,
            "the first damage pulse must hit for 4.0, pig has "
                + pig.getHealth()
                + " (pulses="
                + process.damagePulses()
                + " targets="
                + process.targetsHit()
                + ")"
        );
        helper.assertTrue(pig.getRemainingFireTicks() == 300, "the jet must set the target on fire for 300 ticks, got " + pig.getRemainingFireTicks());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetMissesWhatIsBehind(GameTestHelper helper) {
        Player player = aimPlayer(helper, 8.5, 8.5);
        Pig pig = (Pig)helper.spawn(EntityType.PIG, 8, 1, 4);
        int fireBefore = pig.getRemainingFireTicks();
        runToTheEnd(FireJetProcess.detached(helper.getLevel(), player));
        helper.assertTrue(pig.getHealth() == 10.0F, "the pig behind the player must keep its health, got " + pig.getHealth());
        helper.assertTrue(
            pig.getRemainingFireTicks() == fireBefore,
            "the pig behind the player must not be set on fire, before " + fireBefore + " after " + pig.getRemainingFireTicks()
        );
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetStopsAtFourteenBlocks(GameTestHelper helper) {
        Player player = aimPlayer(helper, 1.5, 1.5);
        aim(player, -45.0F, 0.0F);
        Pig near = (Pig)helper.spawn(EntityType.PIG, 6, 1, 6);
        Pig far = (Pig)helper.spawn(EntityType.PIG, 14, 1, 14);
        runToTheEnd(FireJetProcess.detached(helper.getLevel(), player));
        helper.assertTrue(near.getHealth() < 10.0F, "the pig 6 blocks down the jet must be hit, got " + near.getHealth());
        helper.assertTrue(far.getHealth() == 10.0F, "the pig 17 blocks down the jet must stay untouched, got " + far.getHealth());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetBreaksInvulnerability(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        Pig pig = (Pig)helper.spawn(EntityType.PIG, 8, 1, 5);
        FireJetProcess process = FireJetProcess.detached(helper.getLevel(), player);

        for (int i = 0; i < 85; i++) {
            process.tick();
        }

        helper.assertTrue(Math.abs(pig.getHealth() - 6.0F) < 1.0E-4F, "the first pulse must land, pig has " + pig.getHealth());

        for (int i = 0; i < 5; i++) {
            process.tick();
        }

        helper.assertTrue(Math.abs(pig.getHealth() - 2.0F) < 1.0E-4F, "the second pulse must land through the hurt cooldown, pig has " + pig.getHealth());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetFollowsThePlayer(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        FireJetProcess process = FireJetProcess.detached(helper.getLevel(), player);

        for (int i = 0; i < 10; i++) {
            process.tick();
        }

        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos((double)origin.getX() + 4.5, (double)origin.getY(), (double)origin.getZ() + 8.5);
        aim(player, 90.0F, 0.0F);
        Pig pig = (Pig)helper.spawn(EntityType.PIG, 0, 1, 8);

        for (int i = 0; i < 75; i++) {
            process.tick();
        }

        helper.assertTrue(
            Math.abs(pig.getHealth() - 6.0F) < 1.0E-4F,
            "the jet must follow the player to the new spot and aim, pig has " + pig.getHealth() + " targets=" + process.targetsHit()
        );
        helper.assertTrue(pig.getRemainingFireTicks() == 300, "the followed jet must set the target on fire, got " + pig.getRemainingFireTicks());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetMissesWhatIsToTheSide(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        Pig pig = (Pig)helper.spawn(EntityType.PIG, 12, 1, 6);
        runToTheEnd(FireJetProcess.detached(helper.getLevel(), player));
        helper.assertTrue(pig.getHealth() == 10.0F, "the pig to the side must keep its health, got " + pig.getHealth());
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void jetSkipsFireImmuneTargets(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        Blaze blaze = (Blaze)helper.spawn(EntityType.BLAZE, 8, 1, 6);
        helper.assertTrue(blaze.fireImmune(), "sanity: a blaze must be fire immune");
        float healthBefore = blaze.getHealth();
        int fireBefore = blaze.getRemainingFireTicks();
        runToTheEnd(FireJetProcess.detached(helper.getLevel(), player));
        helper.assertTrue(blaze.getHealth() == healthBefore, "the blaze must keep its health, before " + healthBefore + " after " + blaze.getHealth());
        helper.assertTrue(
            blaze.getRemainingFireTicks() == fireBefore, "the blaze must not be set on fire, before " + fireBefore + " after " + blaze.getRemainingFireTicks()
        );
        helper.succeed();
    }

    @GameTest(
        template = "ddt_empty"
    )
    public static void switchingItemsBurnsFromTheFeet(GameTestHelper helper) {
        Player player = aimPlayer(helper);
        helper.getLevel().addFreshEntity(player);
        Pig pig = (Pig)helper.spawn(EntityType.PIG, 8, 1, 5);
        FireJetProcess process = FireJetProcess.detached(helper.getLevel(), player);

        for (int i = 0; i < 84; i++) {
            process.tick();
        }

        helper.assertTrue(process.aimed(), "sanity: the jet must still be aimed while the item is held");
        helper.assertTrue(player.getHealth() == 20.0F, "sanity: the player must be untouched so far");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        process.tick();
        helper.assertFalse(process.aimed(), "the jet must switch to the feet mode once the item is gone");
        helper.assertTrue(
            Math.abs(player.getHealth() - 16.0F) < 1.0E-4F,
            "the player must be burned by their own jet, health=" + player.getHealth() + " targets=" + process.targetsHit()
        );
        helper.assertTrue(pig.getHealth() == 10.0F, "the pig in front must no longer be hit once the jet points up, got " + pig.getHealth());
        helper.succeed();
    }

    private static void runToTheEnd(FireJetProcess process) {
        for (int i = 0; i < 141; i++) {
            process.tick();
        }
    }

    private static Player aimPlayer(GameTestHelper helper) {
        return aimPlayer(helper, 8.5, 2.5);
    }

    private static Player aimPlayer(GameTestHelper helper, double x, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos((double)origin.getX() + x, (double)origin.getY(), (double)origin.getZ() + z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)ModExperimentItems.EXPERIMENT_043.get()));
        aim(player, 0.0F, 0.0F);
        return player;
    }

    private static void aim(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setOldPosAndRot();
    }

    private Experiment043Tests() {
    }
}
