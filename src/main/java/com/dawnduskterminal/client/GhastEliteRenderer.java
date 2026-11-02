package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.animation.YsmAnimatable;
import com.dawnduskterminal.client.animation.YsmEntityRenderer;
import com.dawnduskterminal.client.animation.YsmPacks;
import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 「大恶魂」的客户端接线
 *
 * <p>模型包按 YSM 的原样布局放在 {@code assets/dawnduskterminal/ysm/ghast_elite/} 下。这个包只有两条片段：
 * {@code parallel0} 是七根触手与四肢的柏林噪声摆动，{@code parallel1} 是一段每帧执行的 {@code timeline}
 * 程序（布条与触手的链式物理）。两者都由并行动画控制器逐帧求值，没有第三段主动画。
 */
public final class GhastEliteRenderer {
    private GhastEliteRenderer() {}

    static final ResourceLocation PACK = DawnDuskTerminal.id("ghast_elite");

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHAST_ELITE.get(), Renderer::new);
        // 火球的渲染器漏了会让 EntityRenderDispatcher 拿到 null，客户端直接 NPE 崩掉
        event.registerEntityRenderer(
                ModEntities.GHAST_ELITE_FIREBALL.get(), context -> new ThrownItemRenderer<>(context, 3.0F, true));
    }

    private static final class Renderer extends YsmEntityRenderer<GhastEliteEntity, Animatable> {
        private Renderer(EntityRendererProvider.Context context) {
            super(context, Animatable::new);
            // 碰撞箱 8 格见方，影子半径取一半
            this.shadowRadius = 4.0F;
        }

        @Override
        protected float scaleOf(GhastEliteEntity entity) {
            return YsmPacks.get(requestedPack(entity)).heightScale();
        }

        /** 换了模型包就重建动画实例 */
        @Override
        protected ResourceLocation requestedPack(GhastEliteEntity entity) {
            return resolvePack(entity);
        }
    }

    /** 实体指定的模型包；空串或写错时用默认包 */
    private static ResourceLocation resolvePack(GhastEliteEntity entity) {
        String declared = entity.ysmPackId();
        if (declared.isEmpty()) {
            return PACK;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(declared);
        return parsed == null ? PACK : parsed;
    }

    private static final class Animatable extends YsmAnimatable<GhastEliteEntity> {
        private Animatable(GhastEliteEntity entity) {
            super(entity, YsmPacks.get(resolvePack(entity)));
        }

        /** 模型包没有主动画：所有通道都由并行动画层写 */
        @Override
        protected String mainClip(AnimationEvent<YsmAnimatable<GhastEliteEntity>> event) {
            return null;
        }
    }
}
