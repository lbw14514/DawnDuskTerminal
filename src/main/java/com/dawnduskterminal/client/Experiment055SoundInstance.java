package com.dawnduskterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 试验物品 055 按住右键期间那支持续音：绑在施放者身上，一遍放完之前就把下一遍接上
 *
 * <p>与 095 的 {@link RequiemSoundInstance} 同一个基类（{@link EntityBoundSoundInstance}）：位置每帧
 * 跟到实体身上，实体被移除时自己收声。区别是这里**不让引擎自己循环**（{@code looping} 为假）：
 * 素材首尾都做了渐变，要的是「下一遍提前一秒起、两遍叠着换句」，所以每一遍各是一个实例，
 * 播到剩 {@value #OVERLAP_TICKS} tick 时回调 {@code onNearEnd} 让调度器起下一遍，本实例继续播到寿命终点。
 * 两遍因此有大约 1 秒的重叠，接缝落在两边的渐变里
 */
public final class Experiment055SoundInstance extends EntityBoundSoundInstance {
    public static final float VOLUME = 1.0F;

    /** 走这么远就自己收声：停止包按 64 格发，比这更远的人收不到，光是衰减到 0 不算收声 */
    public static final double CUTOFF = 64.0D;

    /** 两遍之间重叠这么多 tick（1 秒）：接缝因此落在素材自己的首尾渐变里 */
    public static final int OVERLAP_TICKS = 20;

    private final SoundEvent event;
    private final Entity caster;
    /** 这一遍的实际长度（tick），由调度器按 ogg 的实测时长给出；{@code <= 0} 表示不放完就不管 */
    private final int lengthTicks;
    /**
     * 剩 {@link #OVERLAP_TICKS} tick 时调一次，用来把下一遍接上
     *
     * <p>{@code null} 表示这一支只放一遍（充能音恰好就是 5 秒，与充能期同长，不需要接下一遍）
     */
    @Nullable
    private final Runnable onNearEnd;

    private int age;
    private boolean handedOver;

    Experiment055SoundInstance(SoundEvent event, Entity caster, int lengthTicks, @Nullable Runnable onNearEnd) {
        super(event, SoundSource.PLAYERS, VOLUME, 1.0F, caster, caster.getId());
        this.event = event;
        this.caster = caster;
        this.lengthTicks = lengthTicks;
        this.onNearEnd = onNearEnd;
        // 有意不设 looping：引擎自己循环就没有「重叠一遍」可言了
        this.looping = false;
    }

    /** 现在放的是哪一支，用来判断要不要换曲 */
    SoundEvent event() {
        return this.event;
    }

    /** 收声：引擎在下一帧看到 {@code isStopped()} 就把声道停掉 */
    void end() {
        this.stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isStopped()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || this.caster.isRemoved()
                || minecraft.player.distanceToSqr(this.caster) > CUTOFF * CUTOFF) {
            this.stop();
            return;
        }
        if (this.lengthTicks <= 0) {
            return;
        }
        this.age++;
        if (this.onNearEnd != null && !this.handedOver && this.age >= this.lengthTicks - OVERLAP_TICKS) {
            this.handedOver = true;
            this.onNearEnd.run();
        }
        if (this.age >= this.lengthTicks) {
            this.stop();
        }
    }
}
