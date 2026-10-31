package com.dawnduskterminal.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 恶魂变体共用的那一声
 *
 * <p>原版恶魂的蓄力与喷吐走 {@code levelEvent(1015 / 1016)}，客户端 {@code LevelRenderer}
 * 直接播原版音效，模组没有钩子可拦；所以恶魂变体的这几声都由实体自己播，位置取实体当前位置
 */
public final class GhastVoice {
    /**
     * 音量取原版恶魂 {@code Ghast#getSoundVolume()} 的那个 5
     *
     * <p>那个方法是 {@code protected}，跨类读不到；两个恶魂变体都继承 {@code Ghast}，
     * 值恒为同一个常数
     */
    public static final float VOLUME = 5.0F;

    public static void playVoice(LivingEntity ghast, SoundEvent sound) {
        if (ghast.isSilent()) {
            return;
        }
        ghast.level().playSound(null, ghast.getX(), ghast.getY(), ghast.getZ(),
            sound, SoundSource.HOSTILE, VOLUME, 1.0F);
    }

    private GhastVoice() {}
}
