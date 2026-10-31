package com.dawnduskterminal.mixin;

import com.dawnduskterminal.entity.GhastEliteEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import twilightforest.entity.monster.CarminiteGhastling;

/**
 * 大恶魂召出来的小鬼不挑人：手里有索敌、看得见就打
 *
 * <p>暮色森林的 {@code CarminiteGhastling#shouldAttack} 是「守卫」规则：玩家头顶戴着雕刻南瓜时不打；
 * 3.5 格以内看得见就打；更远则要求 {@code dot(玩家视线, 指向它的方向) > 1 - 0.025/距离}，
 * 也就是<b>玩家的准星必须对着它</b>。于是被召唤出来的小鬼只要玩家侧身路过就只会悬停，
 * 与「共享索敌」的意图相悖
 *
 * <p>这里只放开被 {@link GhastEliteEntity#MINION_TAG} 标记过的那批（大恶魂召的），
 * 野生的砷铅铁恶魂守卫仍按原规则挑人；仍然要求有视线，免得隔着树开火
 */
@Mixin(CarminiteGhastling.class)
public abstract class GhastlingShouldAttackMixin {
    @Inject(method = "shouldAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void ddt$attackSharedTarget(LivingEntity target, CallbackInfoReturnable<Boolean> info) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getPersistentData().getBoolean(GhastEliteEntity.MINION_TAG)) {
            return;
        }
        if (target != null && target.isAlive() && !target.isSpectator() && self.hasLineOfSight(target)) {
            info.setReturnValue(true);
        }
    }
}
