package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

/**
 * 试验物品 045 的盾面渲染器
 *
 * <p>原版盾的盾面与旗面图案写死在 {@code BlockEntityWithoutLevelRenderer} 里，判据是
 * {@code stack.is(Items.SHIELD)}，模组的盾走不到那条分支，所以模型挂 {@code minecraft:builtin/entity}
 * 之后由 {@code IClientItemExtensions#getCustomRenderer} 从这里取渲染器。
 *
 * <p>没有图案时画 {@code item/experiment_045_nopattern}；挂上底色或图案以后画
 * {@code item/experiment_045_base} 再把图案一层层叠上去，图案 sprite 直接从 {@code minecraft:banner_patterns}
 * 图集按 {@code BannerPatternLayers} 的顺序取。发光层单独走一条注册过的固定渲染缓冲
 * （{@link #GLOW_LAYER}），保证它排在木盾本体与图案之后
 */

public final class Experiment045ShieldRenderer extends BlockEntityWithoutLevelRenderer {
    private static final Material NO_PATTERN = new Material(InventoryMenu.BLOCK_ATLAS, DawnDuskTerminal.id("item/experiment_045_nopattern"));
    private static final Material BASE = new Material(InventoryMenu.BLOCK_ATLAS, DawnDuskTerminal.id("item/experiment_045_base"));
    private static final Material GLOW = new Material(InventoryMenu.BLOCK_ATLAS, DawnDuskTerminal.id("item/experiment_045_glow"));
    private static final RenderType BODY = RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS);
    public static final RenderType GLOW_LAYER = RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
    @Nullable
    private ShieldModel model;

    public Experiment045ShieldRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ShieldModel shield = this.model();
        BannerPatternLayers layers = (BannerPatternLayers)stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        DyeColor baseColor = (DyeColor)stack.get(DataComponents.BASE_COLOR);
        boolean patterned = !layers.layers().isEmpty() || baseColor != null;
        pose.pushPose();
        pose.scale(1.0F, -1.0F, -1.0F);
        Material material = patterned ? BASE : NO_PATTERN;
        VertexConsumer body = material.sprite().wrap(ItemRenderer.getFoilBufferDirect(buffers, BODY, true, stack.hasFoil()));
        shield.handle().render(pose, body, light, overlay);
        if (patterned) {
            BannerRenderer.renderPatterns(
                pose, buffers, light, overlay, shield.plate(), material, false, Objects.requireNonNullElse(baseColor, DyeColor.WHITE), layers, stack.hasFoil()
            );
        } else {
            shield.plate().render(pose, body, light, overlay);
        }

        VertexConsumer glow = GLOW.sprite().wrap(buffers.getBuffer(GLOW_LAYER));
        shield.plate().render(pose, glow, 15728880, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private ShieldModel model() {
        if (this.model == null) {
            this.model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
        }

        return this.model;
    }
}
