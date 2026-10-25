package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.StringEntity;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 「弦」的数值与液面判定
 *
 * <p>姿态与渲染是客户端的事，这里只钉住服务端看得见的部分：变种属性、四类免疫、
 * 液面识别，以及近战不产生击退
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class StringEntityTests {
    @GameTest(template = "ddt_empty")
    public static void variantStatsScaleWithGeometry(GameTestHelper helper) {
        StringEntity small = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        small.setVariant(false, 1.0F);
        helper.assertTrue(close(small.getMaxHealth(), 60.0F),
            "small string must have 60 health, got " + small.getMaxHealth());
        helper.assertTrue(close((float) small.getAttributeValue(Attributes.ATTACK_DAMAGE), 10.0F),
            "small string must have 10 attack");

        StringEntity large = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        large.setVariant(true, 1.0F);
        helper.assertTrue(close(large.getMaxHealth(), 150.0F),
            "large string must scale health by 2.5, got " + large.getMaxHealth());
        helper.assertTrue(close((float) large.getAttributeValue(Attributes.ATTACK_DAMAGE), 25.0F),
            "large string must scale attack by 2.5");
        helper.assertTrue(large.getBbWidth() > small.getBbWidth(),
            "large string must have a larger hitbox");

        StringEntity offset = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        offset.setVariant(false, 1.1F);
        helper.assertTrue(close(offset.getMaxHealth(), 66.0F),
            "random size offset must scale health, got " + offset.getMaxHealth());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void immunities(GameTestHelper helper) {
        StringEntity string = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        string.setVariant(false, 1.0F);
        helper.assertTrue(string.fireImmune(), "string must be fire immune");
        helper.assertTrue(!string.canBeAffected(new MobEffectInstance(MobEffects.POISON)),
            "string must ignore potion effects");
        helper.assertTrue(string.isInvulnerableTo(
                helper.getLevel().damageSources().source(ModDamageTypes.STABILITY)),
            "portal fluid damage must not hurt string");
        helper.assertTrue(!string.isPushable() && !string.isPushedByFluid(),
            "string must ignore entity pushing and fluid currents");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void surfaceIsTheOpenFaceOfPortalFluid(GameTestHelper helper) {
        BlockPos fluid = new BlockPos(2, 1, 2);
        helper.setBlock(fluid, ModBlocks.PORTAL_FLUID.get());
        // 结构坐标是相对的 静态工具方法吃的是世界坐标
        BlockPos absolute = helper.absolutePos(fluid);
        // 1.21 的源流体高度是 8/9 格，不是整块
        double expected = absolute.getY()
            + helper.getLevel().getFluidState(absolute).getHeight(helper.getLevel(), absolute)
            + StringEntity.SURFACE_OFFSET;
        int nearY = absolute.getY() + 1;
        double surface = StringEntity.findFluidSurface(helper.getLevel(),
            absolute.getX(), absolute.getZ(), nearY);
        helper.assertTrue(!Double.isNaN(surface) && Math.abs(surface - expected) < 1.0E-4D,
            "open portal fluid must expose a surface just above the fluid, got " + surface);

        helper.setBlock(fluid.above(), Blocks.STONE);
        double covered = StringEntity.findFluidSurface(helper.getLevel(),
            absolute.getX(), absolute.getZ(), nearY);
        helper.assertTrue(Double.isNaN(covered),
            "a sealed aquifer must not count as a surface, got " + covered);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void meleeDoesNotKnockBack(GameTestHelper helper) {
        StringEntity attacker = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        attacker.setVariant(false, 1.0F);
        StringEntity victim = new StringEntity(ModEntities.STRING.get(), helper.getLevel());
        victim.setVariant(false, 1.0F);
        victim.setDeltaMovement(0.5D, 0.0D, 0.5D);

        float before = victim.getHealth();
        helper.assertTrue(attacker.doHurtTarget(victim), "attack must land");
        helper.assertTrue(victim.getHealth() < before, "attack must deal damage");
        helper.assertTrue(Math.abs(victim.getDeltaMovement().x) < 1.0E-6D
                && Math.abs(victim.getDeltaMovement().z) < 1.0E-6D,
            "attack must not add horizontal knockback");
        helper.succeed();
    }

    private static boolean close(float value, float expected) {
        return Math.abs(value - expected) < 1.0E-3F;
    }
}
