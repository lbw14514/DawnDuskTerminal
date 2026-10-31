package com.dawnduskterminal.client.animation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 连续的朝向角：把每 tick 的偏航增量累加起来，绕开 ±180 的跳变
 *
 * <p>源表达式里的 {@code query.eye_target_y_rotation} 是实体朝向。直接读 {@code getViewYRot}
 * 会在朝向跨过 ±180 时跳 360 度，而下游的 {@code first_order} / {@code second_order} 是有状态的弹簧：
 * 一次 360 度的阶跃会被放大成布条的一次猛烈抽动。YSM 上游就是直接读的，玩家身上不明显（朝向变化慢），
 * 移动的实体上会周期性出现。
 *
 * <p>累加用 {@code wrapDegrees(yRot - yRotO)}，因此每 tick 只累加真实转过的那一点，跨边界时是连续的。
 * 角度无界，与源表达式「一直累加」的用法一致。
 *
 * <p>按实体分别保存，用 {@link WeakHashMap} 免得把实体钉住；只在渲染线程使用。
 */
public final class ContinuousYaw {
    private static final Map<Entity, Entry> ENTRIES = new WeakHashMap<>();

    private ContinuousYaw() {
    }

    public static float of(Entity entity) {
        Entry entry = ENTRIES.computeIfAbsent(entity, key -> new Entry(key.getYRot(), key.tickCount));
        if (entry.tick != entity.tickCount) {
            entry.value += Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
            entry.tick = entity.tickCount;
        }
        return entry.value;
    }

    private static final class Entry {
        private float value;
        private int tick;

        private Entry(float value, int tick) {
            this.value = value;
            this.tick = tick;
        }
    }
}
