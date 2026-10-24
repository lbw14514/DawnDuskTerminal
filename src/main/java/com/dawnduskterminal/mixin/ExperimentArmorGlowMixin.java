package com.dawnduskterminal.mixin;

import com.dawnduskterminal.client.model.ExperimentArmorModel;
import com.dawnduskterminal.item.ExperimentArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({HumanoidArmorLayer.class})
/**
 * 试验物品 047~050 那套护甲的发光层
 *
 * <p>原版护甲渲染一次只画一层贴图，模组想在暗处再加一层亮光就得接管渲染流程：这里的 mixin 在
 * {@code HumanoidArmorLayer} 画完本体之后再叠一次同样的几何，贴图换成
 * {@code textures/entities/experiment_armor_glow.png}，并且用满亮的光照值，于是暗处也看得见
 */

public abstract class ExperimentArmorGlowMixin {
    private static final String RENDER_ARMOR_PIECE = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V";

    @Inject(
        method = {"renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V"},
        at = {@At("TAIL")}
    )
    private void ddt$renderGlowLayer(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        LivingEntity entity,
        EquipmentSlot slot,
        int packedLight,
        HumanoidModel<?> model,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo ci
    ) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.getItem() instanceof ExperimentArmorItem) {
            Model glow = IClientItemExtensions.of(stack).getGenericArmorModel(entity, stack, slot, model);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.armorCutoutNoCull(ExperimentArmorModel.GLOW_TEXTURE));
            glow.renderToBuffer(poseStack, consumer, 15728880, OverlayTexture.NO_OVERLAY);
        }
    }
}
