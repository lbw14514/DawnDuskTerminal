package com.dawnduskterminal.geckolib3.geo;

import com.dawnduskterminal.geckolib3.geo.animated.ILocationModel;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

public interface IGeoEntity {
    ILocationModel getGeoModel();

    /**
     * 切换实体当前使用的模型包与贴图
     *
     * <p>YSM 一侧的用法是按模型 id 换皮，本模组没有换皮需求，保留该口子给渲染器
     */
    void setYsmModel(String modelId, String texture);

    /**
     * 把 YSM 的 {@code v.roaming.*} 变量灌进来
     *
     * <p>模型包可以在 {@code ysm.json} 里声明对外暴露的变量，宿主负责逐帧更新
     */
    void updateRoamingVars(Object2FloatOpenHashMap<String> roamingVars);
}
