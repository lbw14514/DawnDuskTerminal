package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.LivingArmorEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 「活盔甲」的客户端接线：本体一个像素都不画，只画护甲与手持物
 *
 * <h2>做法</h2>
 * 骨架用的是玩家那套人形几何（{@code ModelLayers.PLAYER}，含两条 4 像素宽的手臂），
 * 但 {@link HollowModel#renderToBuffer} 是空实现 —— 姿势照常每帧算，几何一个面也不提交。
 * 于是：
 *
 * <ul>
 *   <li>{@link HumanoidArmorLayer} 每帧把父模型的姿势 {@code copyPropertiesTo} 到护甲模型上
 *       （原版那行就在它自己里），护甲跟着看不见的四肢一起摆
 *   <li>{@link ItemInHandLayer} 用 {@code translateToHand} 取手腕的位置，武器与盾牌挂在那只手上
 * </ul>
 *
 * <p>两条层都是原版现成的：暮色森林那四套护甲是普通的 {@code ArmorItem} + {@code ArmorMaterial.Layer}
 * （贴图 {@code twilightforest:textures/models/armor/<材料>_layer_1.png}），原版护甲层认的就是这一套，
 * 所以炽铁、骑士、铁木、钢叶四种外观直接出来，不需要为它们各写一份模型或渲染器
 *
 * <h2>贴图</h2>
 * {@link #TEXTURE} 只是 {@code LivingEntityRenderer} 要求的那个返回值：本体不发任何几何，
 * 这张贴图<b>不会被绑定，也不需要存在</b>。真给它放一张 png 反而会让人以为那上面画的是这只生物
 */
public final class LivingArmorRenderer {
    private LivingArmorRenderer() {}

    private static final ResourceLocation TEXTURE = DawnDuskTerminal.id("textures/entity/living_armor.png");

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LIVING_ARMOR.get(), Renderer::new);
    }

    private static final class Renderer
            extends LivingEntityRenderer<LivingArmorEntity, HumanoidModel<LivingArmorEntity>> {
        private Renderer(EntityRendererProvider.Context context) {
            super(context, new HollowModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
            this.addLayer(new HumanoidArmorLayer<>(
                    this,
                    new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(LivingArmorEntity entity) {
            return TEXTURE;
        }

        /**
         * 不画名称牌
         *
         * <p>原版只在实体有自定义名字（{@code Entity#isCustomNameVisible()} 或自定义名 + 准星选中）时
         * 才画头顶那行字，普通敌怪头顶本来就是空的。这里把默认那条路整个关掉，保证这只生物无论名字从哪来，
         * 世界里都不会顶着「活盔甲」三个字走路 —— 野外一只僵尸、骷髅都不显示，它凭什么显示
         *
         * <p>名字本身没有丢：聊天栏的死亡讯息与 {@code /data get} 走的都是 {@code getDisplayName}，
         * 与这行字无关，拦掉的只是渲染
         */
        @Override
        protected boolean shouldShowName(LivingArmorEntity entity) {
            return false;
        }
    }

    /** 只算姿势、不画几何的人形骨架 */
    private static final class HollowModel extends HumanoidModel<LivingArmorEntity> {
        private HollowModel(ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(
                PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
            // 空实现就是这只生物「没有本体」的全部内容：父类的 setupAnim 照旧把四肢摆好，
            // 护甲层与手持层再从这份姿势里取位置
        }
    }
}
