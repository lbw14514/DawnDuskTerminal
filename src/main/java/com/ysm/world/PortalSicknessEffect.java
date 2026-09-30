package com.ysm.world;

import com.ysm.registry.ModDimensions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class PortalSicknessEffect extends MobEffect {
    public PortalSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 0x4B2E83);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().dimension() == ModDimensions.CHRONO) {
            entity.hurt(entity.damageSources().magic(), 1.0F + amplifier);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }
}
