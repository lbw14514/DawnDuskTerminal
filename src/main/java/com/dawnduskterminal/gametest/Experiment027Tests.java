package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.item.Experiment027Item;
import com.dawnduskterminal.registry.ModExperimentItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 027 的规格
 *
 * <p>钉住三件事：它不再是占位物品（有自己的 {@link Experiment027Item} 与 72000 tick 的使用时长）、
 * 粒子类型真的注册进了 {@code minecraft:particle_type}、以及「视线在触及范围内打中方块面」这条射线：
 * 正对一块石头时落点就在那个面上，看空或者超距时什么都不生
 *
 * <p>粒子本体的静止、半尺寸、60 秒寿命写在客户端 {@code client/Experiment027Particle} 里，
 * GameTestServer 跑的是服务端，类不在 classpath 上，所以那三条不在这里验
 *
 * <p>射线的朝向不能只设 {@code setYRot}：{@code LivingEntity#getViewYRot(float)} 用的是 {@code yHeadRot}，
 * 而假玩家没有 tick 过，头部的旋转字段还停在构造时的值；{@link #aim} 把 yRot、yHeadRot、yBodyRot 与旧旋转
 * 一起钉死，射线才是笔直朝 +Z 的
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment027Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_027";

    /** 眼高：{@code Player#getStandingEyeHeight} 在 1.21.1 是 1.62 */
    private static final double EYE_HEIGHT = 1.62D;

    @GameTest(template = "ddt_empty")
    public static void itemReplacesThePlaceholderAndKeepsUsingWhileHeld(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(27),
            "number 27 must not be a placeholder anymore, it has a real item now");
        ItemStack stack = new ItemStack(ModExperimentItems.EXPERIMENT_027.get());
        helper.assertTrue(stack.getItem() instanceof Experiment027Item,
            SUBJECT + " must be an " + Experiment027Item.class.getSimpleName() + ", got " + stack.getItem());
        helper.assertTrue(stack.getUseDuration(null) == 72000,
            SUBJECT + " must keep using as long as a bow (72000), got " + stack.getUseDuration(null));
        // 服务端发粒子用的是同一个类型，注册丢了这里先红
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.containsKey(DawnDuskTerminal.id("particle_experiment_027")),
            "dawnduskterminal:particle_experiment_027 must be registered");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void surfacePointLandsOnTheLookedAtFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(1, 3, 4));
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // 眼睛对准方块中心，朝 +Z 看：方块在正前方 2.5 格，触及范围内
        player.setPos(target.getX() + 0.5D, target.getY() + 0.5D - EYE_HEIGHT, target.getZ() - 2.5D);
        aim(player, 0.0F, 0.0F);

        Vec3 point = Experiment027Item.surfacePoint(level, player);
        helper.assertTrue(point != null, "looking straight at a stone block within reach must hit it");
        helper.assertTrue(Math.abs(point.x - (target.getX() + 0.5D)) < 1.0E-3D,
            "the point must stay on the block's x center, got " + point
                + " [eye=" + player.getEyePosition() + " view=" + player.getViewVector(1.0F) + "]");
        helper.assertTrue(Math.abs(point.y - (target.getY() + 0.5D)) < 1.0E-3D,
            "the point must stay on the block's y center, got " + point);
        // 北面的落点再沿法线让出 SURFACE_OFFSET，免得粒子半边陷进方块里
        double expectedZ = target.getZ() - Experiment027Item.SURFACE_OFFSET;
        helper.assertTrue(Math.abs(point.z - expectedZ) < 1.0E-3D,
            "the point must sit on the north face offset by " + Experiment027Item.SURFACE_OFFSET
                + ", got " + point);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void surfacePointIsEmptyWithoutABlockInSight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(1.5D, 2.0D, 1.5D);
        // 掉头：身后是空结构，射线什么都打不到
        aim(player, 180.0F, 0.0F);
        helper.assertTrue(Experiment027Item.surfacePoint(level, player) == null,
            "looking into the air must not produce a particle point");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void surfacePointIsEmptyBeyondReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(1, 3, 4));
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // 同样正对，但站到 5.5 格外：默认 block_interaction_range 是 4.5
        player.setPos(target.getX() + 0.5D, target.getY() + 0.5D - EYE_HEIGHT, target.getZ() - 5.5D);
        aim(player, 0.0F, 0.0F);
        helper.assertTrue(player.blockInteractionRange() < 5.5D,
            "the default block interaction range must be shorter than 5.5 to make this test meaningful, got "
                + player.blockInteractionRange());
        helper.assertTrue(Experiment027Item.surfacePoint(level, player) == null,
            "a block beyond the block interaction range must not be hit");
        helper.succeed();
    }

    /**
     * 把假玩家的朝向彻底钉住
     *
     * <p>射线用的是相机那一套朝向：{@code getViewYRot} 读 {@code yHeadRot}，{@code getViewVector} 在
     * partialTick=1 时读当前值。假玩家没有 tick 过，只调 {@code setYRot} 头部字段还停在别处，
     * 于是射线会带一个几度的偏航——这里三个旋转与旧值一起写，顺带把眼睛位置用的旧坐标也对齐
     */
    private static void aim(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setOldPosAndRot();
    }

    private Experiment027Tests() {}
}
