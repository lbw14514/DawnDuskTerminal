package com.dawnduskterminal.client;

import com.dawnduskterminal.entity.ExpowheelEntity;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 博览轮舞那片飞轮的持续音：位置跟着弹体走，音量由这里按距离算
 *
 * <p>两件事：
 *
 * <ul>
 *   <li><b>距离衰减</b>：{@code EntityBoundSoundInstance} 走的是 OpenAL 那套按位置的距离模型，
 *       实测对这支不生效（听起来全程满音量），所以把 {@code attenuation} 设成
 *       {@link SoundInstance.Attenuation#NONE}，音量完全由 {@link #getVolume()} 说了算：
 *       监听者到弹体的距离按 {@value #RANGE} 格线性收到 0。引擎每 tick 读一次 {@code getVolume()}
 *       （{@code SoundEngine#tickNonPaused}），所以这是逐帧算的
 *   <li><b>收尾</b>：素材 9.98 秒、弹体只活 {@value ExpowheelEntity#LIFETIME_TICKS} tick（6 秒），
 *       本来就用不到一半，也没有任何循环。素材自己首尾都做了渐变，所以这里**不做淡出**：
 *       只到点（{@value ExpowheelEntity#LIFETIME_TICKS} tick）或弹体消失时立刻自停，
 *       万一实体被别的逻辑留在场上，声音也不会跟着挂下去
 *   <li><b>走远再走近</b>：出了 {@value #RANGE} 格只是音量归零，实例不停；再走近声音就回来，
 *       素材没有明显的播放进度，中途接着听一段不违和
 * </ul>
 */
public final class ExpowheelSoundInstance extends EntityBoundSoundInstance {
    /** 满音量 */
    public static final float VOLUME = 1.0F;

    /**
     * 听距：监听者离弹体超过这个距离音量就是 0
     *
     * <p>弹体一 tick 飞两格，16 格只够响半秒多，所以放到 {@value #RANGE} 格（客户端跟踪半径是 4 区块）。
     * 走出去只是音量归零，**不停止**：再走近声音就回来了——素材没有明显的播放进度，
     * 中途听一段也不违和，但要是把实例停掉，回来就再也不响了
     */
    private static final double RANGE = 48.0D;

    private final ExpowheelEntity wheel;

    private int age;

    ExpowheelSoundInstance(ExpowheelEntity wheel) {
        super(ModSounds.EXPOWHEEL_AMBIENT.get(), SoundSource.PLAYERS, VOLUME, 1.0F, wheel, wheel.getId());
        this.wheel = wheel;
        // 距离衰减由 getVolume 自己算，引擎那套位置模型不再插一脚
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    /** 只在弹体消失、或它自己 6 秒的寿命到点时收声；走远走不回来都不算结束 */
    @Override
    public void tick() {
        super.tick();
        if (this.isStopped()) {
            return;
        }
        this.age++;
        Player listener = Minecraft.getInstance().player;
        if (listener == null || this.wheel.isRemoved() || this.age > ExpowheelEntity.LIFETIME_TICKS) {
            this.stop();
        }
    }

    @Override
    public float getVolume() {
        Player listener = Minecraft.getInstance().player;
        if (listener == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(listener.distanceToSqr(this.wheel));
        return VOLUME * (float) Mth.clamp(1.0D - distance / RANGE, 0.0D, 1.0D);
    }
}
