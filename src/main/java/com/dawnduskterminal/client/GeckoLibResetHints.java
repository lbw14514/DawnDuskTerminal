package com.dawnduskterminal.client;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Collection;

/**
 * 告诉 {@code GeckoLibAnimationProcessorMixin} 哪些骨骼必须每帧参与 GeckoLib 的重置循环
 *
 * <p>普通动画骨骼由 {@code BoneSnapshot} 自己标记是否需要重置
 * 但高塔恶魂的次级运动会直接在 {@code setCustomAnimations} 里写骨骼
 * 这些骨骼没有对应的动画快照 不能因为快照看起来是初始值就从循环里剔除
 *
 * <p>这个类不能放在 {@code com.dawnduskterminal.mixin} 包下
 * 整个包被 {@code dawnduskterminal.mixins.json} 声明为 mixin 专用
 * 普通代码直接引用会抛 {@code IllegalClassLoadError}
 */
public final class GeckoLibResetHints {
    private static final ObjectSet<String> ALWAYS_RESET = new ObjectOpenHashSet<>();

    private GeckoLibResetHints() {}

    public static void markAlwaysReset(Collection<String> names) {
        ALWAYS_RESET.addAll(names);
    }

    public static boolean isAlwaysReset(String name) {
        return ALWAYS_RESET.contains(name);
    }
}
