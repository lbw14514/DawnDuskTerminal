package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModEffects;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModPotions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 稳定药水：物品默认栈自带药水内容，内容是 8 分钟稳定，颜色是传送门那个紫
 *
 * <p>默认药水内容不是写在 {@code Item.Properties} 里的，而是 {@code ModifyDefaultComponentsEvent} 事后补的
 * （物品注册早于药水）。那条补丁没生效时，{@code new ItemStack(item)} 拿到的是一瓶没有内容的药水：
 * 喝下去什么都不发生，名字显示成未翻译的键，而创造栏里看起来只是一瓶白瓶子 —— 所以这里从物品栈往回查
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class StabilityPotionTests {
    /** 传送门流体的雾色，也是稳定效果的颜色 {@code StabilityEffect} */
    private static final int PORTAL_PURPLE = 0x7F159E;

    @GameTest(template = "ddt_empty")
    public static void potionIsEightMinutesOfStability(GameTestHelper helper) {
        PotionContents contents = new ItemStack(ModItems.STABILITY_POTION.get())
            .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        helper.assertTrue(contents.is(ModPotions.STABILITY),
            "stability_potion must default to dawnduskterminal:stability, got " + contents.potion());

        List<MobEffectInstance> effects = new ArrayList<>();
        contents.forEachEffect(effects::add);
        helper.assertTrue(effects.size() == 1,
            "the stability potion must carry exactly one effect, got " + effects.size());

        MobEffectInstance effect = effects.get(0);
        helper.assertTrue(effect.getEffect().value() == ModEffects.STABILITY.get(),
            "the effect must be dawnduskterminal:stability, got " + effect.getEffect());
        helper.assertTrue(effect.getDuration() == 8 * 60 * 20,
            "the effect must last 8 minutes (9600 ticks), got " + effect.getDuration());
        helper.assertTrue(effect.getAmplifier() == 0,
            "the effect must be level I, got amplifier " + effect.getAmplifier());

        // 药水颜色是可见效果的加权平均，只有一条效果时就是它的颜色，也就是传送门流体的紫
        helper.assertTrue(contents.getColor() == FastColor.ARGB32.opaque(PORTAL_PURPLE),
            "the potion must render in the portal purple 0x7F159E, got 0x"
                + Integer.toHexString(contents.getColor()));
        helper.succeed();
    }

    private StabilityPotionTests() {}
}
