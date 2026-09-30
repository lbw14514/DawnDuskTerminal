package com.dawnduskterminal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PixelSunRenderer implements SunRenderer {
    public static final ResourceLocation SUN_TEXTURE = DawnDuskTerminal.id("textures/environment/sun_disc.png");

    @Override
    public void render(SkyContext context) {
        float size = DdtConfig.sunTextureSize() / 64.0F * 18.0F;
        float half = size * 0.5F;
        double angle = Math.toRadians(context.sunAngleDegrees());
        Vector3f normal = ChronoLineStateHolder.normal();
        double cx = normal.x * Math.cos(angle) * 100.0D;
        double cy = Math.sin(angle) * 100.0D;
        double cz = normal.z * Math.cos(angle) * 100.0D;

        Vector3f up = context.camera().getUpVector();
        Vector3f left = context.camera().getLeftVector();
        double rx = -left.x * half;
        double ry = -left.y * half;
        double rz = -left.z * half;
        double ux = up.x * half;
        double uy = up.y * half;
        double uz = up.z * half;

        Matrix4f matrix = context.poseStack().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SUN_TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, (float) (cx - rx - ux), (float) (cy - ry - uy), (float) (cz - rz - uz)).setUv(0.0F, 1.0F);
        builder.addVertex(matrix, (float) (cx + rx - ux), (float) (cy + ry - uy), (float) (cz + rz - uz)).setUv(1.0F, 1.0F);
        builder.addVertex(matrix, (float) (cx + rx + ux), (float) (cy + ry + uy), (float) (cz + rz + uz)).setUv(1.0F, 0.0F);
        builder.addVertex(matrix, (float) (cx - rx + ux), (float) (cy - ry + uy), (float) (cz - rz + uz)).setUv(0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
