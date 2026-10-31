package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.animation.YsmAnimatable;
import com.dawnduskterminal.client.animation.YsmPack;
import com.dawnduskterminal.client.animation.YsmEntityRenderer;
import com.dawnduskterminal.client.animation.YsmPacks;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 高塔恶魂的客户端接线
 *
 * <p>模型包按 YSM 的原样布局放在 {@code assets/dawnduskterminal/ysm/tower_ghast/} 下，运行时解析，
 * 因此这里没有「几何 / 动画 / 贴图」三条路径要写，也没有转换器要跑：动画文件里的 Molang 表达式
 * 由 {@code client/animation} 那套运行时逐帧求值，包括有状态的物理滤波与 {@code timeline} 程序。
 *
 * <p>模型的显示比例取自包内 {@code ysm.json} 的 {@code properties.height_scale}（0.7）。
 */
public final class TowerGhastRenderer {
    private TowerGhastRenderer() {
    }

    static final ResourceLocation PACK = DawnDuskTerminal.id("tower_ghast");

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TOWER_GHAST.get(), Renderer::new);
    }

    private static final class Renderer extends YsmEntityRenderer<TowerGhastEntity, Animatable> {
        private Renderer(EntityRendererProvider.Context context) {
            super(context, Animatable::new);
        }

        @Override
        protected float scaleOf(TowerGhastEntity entity) {
            return YsmPacks.get(PACK).heightScale();
        }

        /** 模型包自带 {@code death} 片段（1 秒，停在最后一帧），关掉原版那套倒地旋转 */
        @Override
        protected boolean suppressDeathRotation(TowerGhastEntity entity) {
            return true;
        }
    }

    private static final class Animatable extends YsmAnimatable<TowerGhastEntity> {
        private Animatable(TowerGhastEntity entity) {
            super(entity, pack().model(), pack().animation(), pack().texture());
        }

        /**
         * 主动画的选择
         *
         * <p>「在不在走」由实体自己判断，<strong>不用 {@code event.isMoving()}</strong>：
         * 这个 fork 里那个标志是 {@code limbSwingAmount <= 阈值}，也就是「几乎没在动」时为真，
         * 直接拿来用会把 walk 与 idle 播反（实测症状是走路时身体不前倾、只有平移）。
         */
        @Override
        protected String mainClip(AnimationEvent<YsmAnimatable<TowerGhastEntity>> event) {
            return TowerGhastEntity.mainAnimation(this.entity, isMoving(event.getPartialTick()));
        }

        private boolean isMoving(float partialTick) {
            return this.entity.walkAnimation.speed(partialTick) > 0.05F
                || this.entity.getDeltaMovement().horizontalDistance() > 0.02D;
        }

        private static YsmPack pack() {
            return YsmPacks.get(PACK);
        }
    }
}
