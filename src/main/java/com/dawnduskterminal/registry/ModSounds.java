package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = register("portal_open");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_ENTER = register("portal_enter");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_EXIT = register("portal_exit");

    public static final DeferredHolder<SoundEvent, SoundEvent> CHRONO_AMBIENT = register("chrono_ambient");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_HURT = register("portal_hurt");

    /** 反应权杖发射 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARMINITE_STAFF_SHOOT =
            register("carminite_staff_shoot");

    /** 弹体命中后炸开的反应场，也用作弹体命中地面的声音 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARMINITE_FIELD = register("carminite_field");

    /**
     * 高塔恶魂的待机语音，7 条随机取一条
     *
     * <p>变体放在 {@code sounds.json} 的同一个事件里由原版随机挑，所以这里只要注册一个事件
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_GHAST_AMBIENT = register("tower_ghast_ambient");

    /** 高塔恶魂的受击语音，5 条随机取一条 */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_GHAST_HURT = register("tower_ghast_hurt");

    /** 「弦」的常驻音，3 条随机取一条 */
    public static final DeferredHolder<SoundEvent, SoundEvent> STRING_AMBIENT = register("string_ambient");

    /** 「弦」的受伤与死亡音，3 条随机取一条 */
    public static final DeferredHolder<SoundEvent, SoundEvent> STRING_HIT = register("string_hit");

    /**
     * 恶魂通用语音：模型包 {@code sounds/} 下那一套 7 条 moan、5 条 scream，加上
     * death / charge / fireball4 / affectionate_scream
     *
     * <p>命名不带具体生物：这套素材与 {@code ghast_universal.animation.json} 一样是共享的，
     * 以后其它恶魂变体直接复用同一组事件，不再各存一份；变体由 {@code sounds.json} 里
     * 同一事件的多个文件交由原版随机挑选
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_UNIVERSAL_AMBIENT =
            register("ghast_universal_ambient");

    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_UNIVERSAL_HURT = register("ghast_universal_hurt");

    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_UNIVERSAL_DEATH =
            register("ghast_universal_death");

    /**
     * 蓄力与喷吐
     *
     * <p>原版恶魂这两声走的是 {@code levelEvent(1015 / 1016)}，客户端 {@code LevelRenderer}
     * 直接播原版音效，模组没有钩子可拦；所以这两声由实体自己的射击目标播放，见
     * {@code GhastFruityEntity.GhastShootFireballGoal}
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_UNIVERSAL_CHARGE =
            register("ghast_universal_charge");

    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_UNIVERSAL_SHOOT =
            register("ghast_universal_shoot");

    /** 把砸在自己身上的火球拨开时的那一声 */
    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_UNIVERSAL_DEFLECT =
            register("ghast_universal_deflect");

    /** 试验物品 052 收到红石信号、点火那一下 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPERIMENT_052_IGNITE =
            register("experiment_052_ignite");

    /**
     * 试验物品 052 的爆炸
     *
     * <p>主爆炸走 {@code ServerLevel#explode} 的音效参数，只有这一个事件；碎片那次小爆炸是静音的
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPERIMENT_052_EXPLODE =
            register("experiment_052_explode");

    /** 试验物品 055 充能期间循环的那支 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPERIMENT_055_CHARGE =
            register("experiment_055_charge");

    /** 试验物品 055 充满那一下，只放一遍 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPERIMENT_055_FULL = register("experiment_055_full");

    /** 试验物品 055 充满之后循环的那支盖革声 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPERIMENT_055_GEIGER =
            register("experiment_055_geiger");

    /**
     * 试验物品 095 循环播放的那组曲子，一个下标一支
     *
     * <p>素材整份放在 {@code assets/dawnduskterminal/sounds/requiem/}，事件名与文件名一对一，
     * 与 {@code ghast_universal/moan1} 那套同一个写法。每次按住右键由服务端摇一个下标，客户端据此挑事件，
     * 所以这里必须是一张有序的表——下标就是协议里那个整数，顺序不能随手改。往目录里加曲子时三处一起改：
     * 这张表、{@code sounds.json} 里的一条、以及目录里那个 {@code .ogg}
     *
     * <p>{@code sounds.json} 里每一条都写了 {@code stream: true} 与 {@code attenuation_distance: 32}：
     * 前者让客户端边读边放（单曲十几到二十几秒，整段进内存不值得），后一个把线性衰减的距离定在 32 格，
     * 与 095 的伤害半径同一个数
     */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> REQUIEM_TRACKS = List.of(
            register("requiem/bgmm3"),
            register("requiem/flower"),
            register("requiem/extendash"),
            register("requiem/requiem"),
            register("requiem/sirius"),
            register("requiem/nanana"));

    /** 下标越界时返回 {@code null}，让客户端干脆不出声，不抛异常 */
    @Nullable
    public static SoundEvent requiemTrack(int index) {
        return index < 0 || index >= REQUIEM_TRACKS.size()
                ? null
                : REQUIEM_TRACKS.get(index).get();
    }

    /** 试验物品 057 那类发射：血肉之书的发射音 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPOWHEEL_SHOOT = register("expowheel_shoot");

    /**
     * 血肉之书那片飞轮的持续音
     *
     * <p>素材本身 9.98 秒，比弹体 6 秒的寿命长，所以客户端只起一支挂在弹体身上的定位音
     * （{@code client/ExpowheelSounds}），不循环、也不由服务端播
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPOWHEEL_AMBIENT = register("expowheel_ambient");

    /** 一支曲子都没有时 {@code nextInt} 会抛异常，所以这边留个口子 */
    public static int requiemTrackCount() {
        return REQUIEM_TRACKS.size();
    }

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(DawnDuskTerminal.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
