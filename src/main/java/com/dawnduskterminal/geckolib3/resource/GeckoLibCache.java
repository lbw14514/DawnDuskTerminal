package com.dawnduskterminal.geckolib3.resource;

import com.dawnduskterminal.client.animation.FnBinding;
import com.dawnduskterminal.client.animation.YsmBinding;
import com.dawnduskterminal.geckolib3.core.molang.MolangParser;
import com.dawnduskterminal.geckolib3.file.AnimationFile;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;
import com.dawnduskterminal.molang.runtime.binding.ObjectBinding;
import com.google.common.collect.Maps;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型与动画的缓存，以及唯一的那台 Molang 解析器
 *
 * <p>解析器必须全局唯一：表达式解析会往绑定表里登记槽位，逐帧重建解析器的代价是每帧重新
 * 解析五千多条表达式。
 *
 * <p>两个前缀绑定的是本模组自己的表：{@code ysm} 是 YSM 的函数与变量（{@link YsmBinding}），
 * {@code fn} 是模型包的用户函数（{@link FnBinding}，五个四元数函数的重建实现）。
 */
public final class GeckoLibCache {
    private static final Map<String, ObjectBinding> EXTRA_BINDING = new HashMap<>();
    private static GeckoLibCache INSTANCE;

    public final MolangParser parser = createMolangParser();

    private final Map<ResourceLocation, AnimationFile> animations = Maps.newHashMap();
    private final Map<ResourceLocation, GeoModel> geoModels = Maps.newHashMap();

    private GeckoLibCache() {
    }

    public static GeckoLibCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GeckoLibCache();
        }
        return INSTANCE;
    }

    public Map<ResourceLocation, AnimationFile> getAnimations() {
        return animations;
    }

    public Map<ResourceLocation, GeoModel> getGeoModels() {
        return geoModels;
    }

    /** 资源重载：模型与动画全部作废，解析器里的变量槽位一并清掉 */
    public void invalidate() {
        this.animations.clear();
        this.geoModels.clear();
        this.parser.reset();
    }

    private static MolangParser createMolangParser() {
        EXTRA_BINDING.put("ysm", YsmBinding.INSTANCE);
        EXTRA_BINDING.put("fn", FnBinding.INSTANCE);
        return new MolangParser(EXTRA_BINDING);
    }
}
