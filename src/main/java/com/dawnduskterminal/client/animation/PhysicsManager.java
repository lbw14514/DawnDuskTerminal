package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.client.animation.functions.physics.IPhysics;
import com.dawnduskterminal.config.DdtConfig;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * 逐渲染帧推进的骨骼物理：按名字记忆的 {@code first_order} / {@code second_order} 滤波器
 *
 * <p>步长取渲染帧的时间间隔（{@code 渲染 tick 差 / 20}），与 Yes Steve Model 的
 * {@code PhysicsManager.update(seekTime)} 一致：这些滤波器是按帧积分的弹簧，按 tick 推进会失真。
 *
 * <p>步长再乘一个配置倍率 {@code render.ribbon_speed}：模型包把弹簧频率写死在表达式里
 * （高塔恶魂的布条是 2 Hz），有的模型看起来偏快，这个倍率就是唯一的旋钮。默认 1.0 等于 YSM 的表现。
 */
public class PhysicsManager {
    private final Object2ReferenceOpenHashMap<String, IPhysics> physicsValues = new Object2ReferenceOpenHashMap<>(16);
    private float lastRenderTicks = 0;

    public void update(float renderTicks) {
        if (lastRenderTicks > 0) {
            if (renderTicks > lastRenderTicks) {
                float interval = (float) ((renderTicks - lastRenderTicks) / 20f * DdtConfig.ribbonSpeed());
                lastRenderTicks = renderTicks;
                physicsValues.forEach((key, value) -> value.update(interval));
            }
        } else {
            lastRenderTicks = renderTicks;
        }
    }

    public void put(String key, IPhysics physics) {
        this.physicsValues.put(key, physics);
    }

    @Nullable
    public IPhysics get(String key) {
        return this.physicsValues.get(key);
    }

    public void reset() {
        lastRenderTicks = 0;
        physicsValues.clear();
    }
}
