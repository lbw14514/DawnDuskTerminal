package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.Experiment057BladeEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 试验物品 057 那片飞刃的客户端接线
 *
 * <p>三条资源全部来自源文件，逐字节复制进 jar：{@code e_57_blade.json}（几何，两根骨骼
 * {@code spin1} 与 {@code spin2}，都只写了 up / down 两个 UV 面）、{@code e_57_blade.animation.json}
 * （片段 {@code parallel0}，0.48 秒一圈，两根骨骼反向转）、{@code e_57_blade.png}（64×64 贴图）。
 * 路径照 {@link TowerGhastRenderer} 那套写法直接写出来，不用 GeckoLib 的命名约定
 * （那个约定会在实体 id 之上再拼一层 {@code entity/}）
 *
 * <p><b>自发光</b>：渲染时把光照打包值换成 {@link LightTexture#FULL_BRIGHT}，于是贴图不再被天光与
 * 方块光压暗，暗处也是原色。做法是在 {@link GeoEntityRenderer#render} 入口把参数换掉再交给父类，
 * 而不是改渲染类型——渲染类型仍是 {@code entityCutoutNoCull}，透明像素照旧裁掉、不写深度，
 * 不会出现半透明层挡在别的实体前面的问题
 */
public final class Experiment057BladeRenderer {
    private Experiment057BladeRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EXPERIMENT_057_BLADE.get(), BladeRenderer::new);
    }

    private static final class BladeRenderer extends GeoEntityRenderer<Experiment057BladeEntity> {
        private BladeRenderer(EntityRendererProvider.Context context) {
            super(context, new BladeModel());
        }

        @Override
        public void render(Experiment057BladeEntity animatable, float entityYaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
            super.render(animatable, entityYaw, partialTick, poseStack, bufferSource,
                LightTexture.FULL_BRIGHT);
        }
    }

    /**
     * 只用本模组发布的那三条路径的 Geo 模型
     *
     * <p>单参数的 {@code getModelResource} / {@code getTextureResource} 被 GeckoLib 标为过时，
     * 但双参数版本的默认实现转调的就是这两个，抽象方法也仍是它们，所以只实现单参数版本
     */
    private static final class BladeModel extends GeoModel<Experiment057BladeEntity> {
        private static final ResourceLocation MODEL = DawnDuskTerminal.id("geo/e_57_blade.geo.json");
        private static final ResourceLocation TEXTURE =
            DawnDuskTerminal.id("textures/entity/e_57_blade.png");
        private static final ResourceLocation ANIMATIONS =
            DawnDuskTerminal.id("animations/e_57_blade.animation.json");

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getModelResource(Experiment057BladeEntity animatable) {
            return MODEL;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getTextureResource(Experiment057BladeEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(Experiment057BladeEntity animatable) {
            return ANIMATIONS;
        }
    }
}
