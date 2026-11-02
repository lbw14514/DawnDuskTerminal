package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.geckolib3.file.AnimationFile;
import com.dawnduskterminal.geckolib3.resource.GeckoLibCache;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * 一个 YSM 模型包解析后的索引
 *
 * <p>只记录文件位置，不持有解析结果：几何与动画的体积都很大，按需从 {@link YsmPacks} 取。
 *
 * @param models      {@code models/} 下的文件名（不含扩展名）到文件
 * @param animations  {@code animations/} 下的文件名（去掉 {@code .animation.json}）到文件
 * @param textures    {@code textures/} 下的文件名到文件
 * @param heightScale     {@code ysm.json} 的 {@code properties.height_scale}，模型显示比例
 * @param widthScale      {@code properties.width_scale}
 * @param extraAnimations {@code properties.extra_animation}：按钮键到片段名，宿主手动触发用
 * @param roamingOptions  {@code properties.extra_animation_buttons}：外观开关（衣物、表情、体型、形态）
 */
public record YsmPack(
        ResourceLocation id,
        Map<String, ResourceLocation> models,
        Map<String, ResourceLocation> animations,
        Map<String, ResourceLocation> textures,
        String defaultTexture,
        float heightScale,
        float widthScale,
        Map<String, String> extraAnimations,
        List<RoamingOption> roamingOptions) {

    /**
     * 模型包声明的一个外观开关
     *
     * @param variable 去掉 {@code v.} 前缀的变量名，例如 {@code roaming.hat}、{@code cloth}
     * @param title    模型包里写的显示名，用作命令提示
     * @param min      取值范围下限
     * @param max      取值范围上限
     * @param step     步长
     */
    public record RoamingOption(String variable, String type, String title, double min, double max, double step) {
        /** 命令里用的短名：{@code roaming.hat} → {@code hat} */
        public String key() {
            return this.variable.startsWith("roaming.") ? this.variable.substring("roaming.".length()) : this.variable;
        }
    }

    /** 按短名查一个开关，找不到返回 null */
    public RoamingOption roamingOption(String key) {
        for (RoamingOption option : this.roamingOptions) {
            if (option.key().equals(key) || option.variable().equals(key)) {
                return option;
            }
        }
        return null;
    }

    /** 主几何文件：{@code models/main.json} 优先，否则取排序后的第一个 */
    public ResourceLocation model() {
        return first(models, "main");
    }

    /** 主动画文件：{@code animations/main.animation.json} 优先，否则取排序后的第一个 */
    public ResourceLocation animation() {
        return animations.isEmpty() ? null : first(animations, "main");
    }

    /** 贴图：默认贴图不存在时取排序后的第一个 */
    public ResourceLocation texture() {
        if (textures.isEmpty()) {
            return null;
        }
        ResourceLocation declared = textures.get(this.defaultTexture);
        return declared != null ? declared : textures.values().iterator().next();
    }

    /**
     * 手动动作的片段名
     *
     * <p>键与片段名多数相同（{@code id1} 播 {@code id1}）；写法不同的以声明值为准，
     * 两者都找不到时返回 null。
     *
     * @return 片段名；{@code key} 不是本包声明的手动动作时返回 null
     */
    public String extraClip(String key) {
        if (!this.extraAnimations.containsKey(key) || this.animation() == null) {
            return null;
        }
        AnimationFile file = GeckoLibCache.getInstance().getAnimations().get(this.animation());
        if (file == null) {
            return null;
        }
        if (file.getAnimation(key) != null) {
            return key;
        }
        String declared = this.extraAnimations.get(key);
        return declared != null && file.getAnimation(declared) != null ? declared : null;
    }

    private static ResourceLocation first(Map<String, ResourceLocation> files, String preferred) {
        ResourceLocation selected = files.get(preferred);
        if (selected != null) {
            return selected;
        }
        return files.entrySet().stream()
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
