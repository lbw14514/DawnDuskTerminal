package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN =
        register("portal_open");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_ENTER =
        register("portal_enter");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_EXIT =
        register("portal_exit");

    public static final DeferredHolder<SoundEvent, SoundEvent> CHRONO_AMBIENT =
        register("chrono_ambient");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_HURT =
        register("portal_hurt");

    /** 反应权杖发射 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARMINITE_STAFF_SHOOT =
        register("carminite_staff_shoot");

    /** 弹体命中后炸开的反应场，也用作弹体命中地面的声音 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARMINITE_FIELD =
        register("carminite_field");

    /**
     * 高塔恶魂的待机语音，7 条随机取一条
     *
     * <p>变体放在 {@code sounds.json} 的同一个事件里由原版随机挑，所以这里只要注册一个事件
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_GHAST_AMBIENT =
        register("tower_ghast_ambient");

    /** 高塔恶魂的受击语音，5 条随机取一条 */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_GHAST_HURT =
        register("tower_ghast_hurt");

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

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(DawnDuskTerminal.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
