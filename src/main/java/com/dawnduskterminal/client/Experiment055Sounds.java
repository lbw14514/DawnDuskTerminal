package com.dawnduskterminal.client;

import com.dawnduskterminal.network.Experiment055SoundPayload;
import com.dawnduskterminal.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * 客户端这边「谁在响哪一支」的那张表：一个施放者一支持续音
 *
 * <p>四档来自 {@link Experiment055SoundPayload}：{@code CHARGE} 起（或换成）充能音、{@code GEIGER}
 * 起（或换成）盖革音、{@code FULL} 先把持续音收掉再放一次充满音、{@code STOP} 收声。服务端每秒重发
 * 当前档位，所以「起」必须幂等：同一个施放者已经在响同一支就什么都不做
 *
 * <p>**接缝**：持续音的各遍由 {@link Experiment055SoundInstance} 在剩 1 秒时回调
 * {@link #handOver}，下一遍提前起来、与上一遍重叠约 1 秒（素材首尾都有渐变，接缝落在渐变里）。
 * 所以这里同一时刻可能挂着两支实例：正在放的那支留在 {@code track.instances} 里照常收尾，
 * 收声（{@code STOP} / 换曲 / 放 {@code full}）时把这一串全停掉，不留一秒的拖尾
 *
 * <p>两支持续音的实测长度写成了常量（{@link #CHARGE_TICKS} / {@link #GEIGER_TICKS}，按 ogg 的
 * granule 位置量出来的秒数乘 20）；换素材时这两个数要跟着改，否则重叠点会偏
 */
public final class Experiment055Sounds {
    /**
     * 充能音 {@code experiment_055_charge.ogg} 的实测长度：4.91 秒
     *
     * <p>素材本身就是 5 秒，与充能期一样长，所以它**只放一遍**、不接下一遍；播完到充满之间那两三 tick 是静音
     */
    private static final int CHARGE_TICKS = 98;
    /** 盖革音 {@code experiment_055_geiger.ogg} 的实测长度：15.06 秒 */
    private static final int GEIGER_TICKS = 301;

    private static final Map<Integer, Track> LOOPS = new HashMap<>();

    private Experiment055Sounds() {}

    /** 收到一档；{@code mode} 见 {@link Experiment055SoundPayload} */
    public static void handle(int casterId, int mode) {
        switch (mode) {
            case Experiment055SoundPayload.MODE_CHARGE ->
                ensure(casterId, ModSounds.EXPERIMENT_055_CHARGE.get(), CHARGE_TICKS, false);
            case Experiment055SoundPayload.MODE_GEIGER ->
                ensure(casterId, ModSounds.EXPERIMENT_055_GEIGER.get(), GEIGER_TICKS, true);
            case Experiment055SoundPayload.MODE_FULL -> {
                stop(casterId);
                playFull(casterId);
            }
            default -> stop(casterId);
        }
    }

    /**
     * 起一支，或者换成另一支；同一支正在响就什么都不做
     *
     * <p>{@code repeat} 为真才会在播完前接下一遍（盖革音要一直响）；充能音本来就是 5 秒一遍，{@code repeat}
     * 为假，放过一遍之后即使服务端每秒重发也直接忽略，免得它重新起头
     */
    private static void ensure(int casterId, SoundEvent event, int lengthTicks, boolean repeat) {
        Track track = LOOPS.get(casterId);
        if (track != null) {
            track.pruneStopped();
            if (track.event == event && (!track.instances.isEmpty() || track.onceDone)) {
                return;
            }
            stop(casterId);
        }
        start(casterId, event, lengthTicks, repeat);
    }

    /**
     * 上一遍只剩 1 秒时把下一遍接上
     *
     * <p>只在「还是同一支、还在响」时才接：换曲、收声、施放者没了都不接。
     * 上一遍留在 {@code track.instances} 里按自己的寿命收尾，于是两遍重叠大约 1 秒
     */
    private static void handOver(int casterId, SoundEvent event, int lengthTicks) {
        Track track = LOOPS.get(casterId);
        if (track == null || track.event != event) {
            return;
        }
        track.pruneStopped();
        if (track.instances.isEmpty()) {
            return;
        }
        start(casterId, event, lengthTicks, true);
    }

    /** 起一支新的，并把它挂到这一位施放者的名下 */
    private static void start(int casterId, SoundEvent event, int lengthTicks, boolean repeat) {
        Entity caster = caster(casterId);
        if (caster == null) {
            // 这个施放者还没载入到客户端；服务端每秒还会重发，等它载入再起
            return;
        }
        Track track = LOOPS.get(casterId);
        if (track == null || track.event != event) {
            track = new Track(event);
            LOOPS.put(casterId, track);
        }
        if (!repeat) {
            track.onceDone = true;
        }
        Experiment055SoundInstance instance = new Experiment055SoundInstance(
            event, caster, lengthTicks,
            repeat ? () -> handOver(casterId, event, lengthTicks) : null);
        track.instances.add(instance);
        // 必须走 queueTickingSound 而不是 play：接下一遍的调用来自 Experiment055SoundInstance#tick，
        // 那一刻引擎正在 for-each 遍历 tickingSounds，而 play 会把这一支往同一个 List 里加，
        // 直接 ConcurrentModificationException 崩客户端（22:09 那次崩溃就是这么来的）。
        // queueTickingSound 只往 queuedTickableSounds 里压，引擎下一帧开头再 play，安全
        Minecraft.getInstance().getSoundManager().queueTickingSound(instance);
    }

    /** 充满那一下只放一遍：一次性、不跟着实体走，放完就由引擎自己回收 */
    private static void playFull(int casterId) {
        Entity caster = caster(casterId);
        if (caster == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(new SimpleSoundInstance(
            ModSounds.EXPERIMENT_055_FULL.get(), net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 1.0F, SoundInstance.createUnseededRandom(),
            caster.getX(), caster.getY(), caster.getZ()));
    }

    /** 这一位施放者名下的所有实例一起收声，不留重叠拖尾 */
    private static void stop(int casterId) {
        Track track = LOOPS.remove(casterId);
        if (track != null) {
            track.stopAll();
        }
    }

    /** 断开连接时整批清掉：换存档之后不会再有哪一方来发停止包 */
    public static void clear() {
        for (Track track : LOOPS.values()) {
            track.stopAll();
        }
        LOOPS.clear();
    }

    private static Entity caster(int casterId) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(casterId);
    }

    /** 一位施放者名下的持续音：同一支的若干遍（正常情况下只有一支在放、一支在收尾） */
    private static final class Track {
        private final SoundEvent event;
        private final List<Experiment055SoundInstance> instances = new ArrayList<>(2);
        /** 只放一遍的那一支（充能音）已经放过；服务端每秒重发时靠它把重复的起播挡掉 */
        private boolean onceDone;

        private Track(SoundEvent event) {
            this.event = event;
        }

        /** 把已经自己收声的实例摘掉，别越攒越多 */
        private void pruneStopped() {
            Iterator<Experiment055SoundInstance> iterator = this.instances.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().isStopped()) {
                    iterator.remove();
                }
            }
        }

        private void stopAll() {
            for (Experiment055SoundInstance instance : this.instances) {
                instance.end();
            }
            this.instances.clear();
        }
    }
}
