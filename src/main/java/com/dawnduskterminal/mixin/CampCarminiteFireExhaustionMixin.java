package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.CampCarminiteFireAura;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 红篝火 16 格内，增加消耗度的<b>小增量</b>改成等量减少
 *
 * <p>钩在 {@code Player#causeFoodExhaustion} 上头而不是 {@code FoodData#addExhaustion}：
 * 后者拿不到玩家，判不了距离；而原版玩家动作（挖掘 0.005、奔跑 0.1、跳跃 0.05 与 0.2、攻击 0.1 与 0.2、
 * 受伤 0.1、饥饿效果、吃东西带的那点）全都从前者进
 *
 * <p>翻不翻由 {@link CampCarminiteFireAura#absorb} 自己判（增量为正且不大于 1、附近有点亮着的红篝火、
 * 不是创造模式的无敌玩家、不是客户端），翻了就整条吞掉原版那次加，所以这里只管把结果转成 cancel
 */
@Mixin(Player.class)
public abstract class CampCarminiteFireExhaustionMixin {
    @Inject(method = "causeFoodExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void ddt$invertExhaustionNearCampfire(float exhaustion, CallbackInfo ci) {
        if (CampCarminiteFireAura.absorb((Player) (Object) this, exhaustion)) {
            ci.cancel();
        }
    }
}
