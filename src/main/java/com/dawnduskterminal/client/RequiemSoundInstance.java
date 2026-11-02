package com.dawnduskterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * 试验物品 095（镇魂曲）按住右键期间那支循环曲：绑在施放者身上的一支循环音
 *
 * <p>原版没有「服务端让客户端循环播放一段声音」的路子：{@code Level#playSound} 发下去的一次性音效，
 * 放完就没了，服务端也停不掉。循环只存在于客户端的 {@code SoundInstance#isLooping()} 上，
 * 所以这条链路是「服务端发包 → 客户端自己起一支循环音」，见 {@link RequiemSounds}
 *
 * <p>基类选 {@link EntityBoundSoundInstance}：它每帧把位置跟到实体身上，实体被移除时自己收声，
 * 所以施放者走路、转身、死亡、下线都不需要服务端再操心
 *
 * <p>音量 1.0、音源 {@link SoundSource#RECORDS}：唱片那一档默认满音量、也不会跟背景音乐抢音量条。
 * 「32 格内听得见」不靠这里的音量，靠 {@code sounds.json} 里每一条曲子的
 * {@code attenuation_distance: 32}——线性衰减从 0 格满音量降到 32 格静音
 */
public final class RequiemSoundInstance extends EntityBoundSoundInstance {
    public static final float VOLUME = 1.0F;

    /**
     * 走这么远就自己收声
     *
     * <p>服务端的停止包按 64 格发（见 {@code RequiemProcess}），可要是有人在按住期间走得更远，
     * 那颗停止包就到不了他那儿；光是音量衰减到 0 并不算收声，声道还占着。所以这里再兜一层
     */
    public static final double CUTOFF = 64.0D;

    private final Entity caster;

    RequiemSoundInstance(SoundEvent event, Entity caster) {
        super(event, SoundSource.RECORDS, VOLUME, 1.0F, caster, caster.getId());
        this.caster = caster;
        // 循环就靠这一个字段：流式音频那条路会把它包成 LoopingAudioStream，接不上就只放一遍
        this.looping = true;
    }

    /** 收声：引擎在下一帧看到 {@code isStopped()} 就把声道停掉，并从可 tick 的表里摘掉 */
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
        if (minecraft.player == null || this.caster.isRemoved()
            || minecraft.player.distanceToSqr(this.caster) > CUTOFF * CUTOFF) {
            this.stop();
        }
    }
}
