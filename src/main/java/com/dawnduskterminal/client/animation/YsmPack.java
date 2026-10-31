package com.dawnduskterminal.client.animation;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 一个 YSM 模型包解析后的索引
 *
 * <p>只记录文件位置，不持有解析结果：几何与动画的体积都很大，按需从 {@link YsmPacks} 取。
 *
 * @param models      {@code models/} 下的文件名（不含扩展名）到文件
 * @param animations  {@code animations/} 下的文件名（去掉 {@code .animation.json}）到文件
 * @param textures    {@code textures/} 下的文件名到文件
 * @param heightScale {@code ysm.json} 的 {@code properties.height_scale}，模型显示比例
 * @param widthScale  {@code properties.width_scale}
 */
public record YsmPack(ResourceLocation id, Map<String, ResourceLocation> models,
                      Map<String, ResourceLocation> animations, Map<String, ResourceLocation> textures,
                      String defaultTexture, float heightScale, float widthScale) {

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
