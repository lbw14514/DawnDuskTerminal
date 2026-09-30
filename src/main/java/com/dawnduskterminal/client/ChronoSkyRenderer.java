package com.dawnduskterminal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class ChronoSkyRenderer {
    private static final int SEGMENTS = 64;
    private static final float RADIUS = 100.0F;

    private static SunRenderer sunRenderer = new PixelSunRenderer();

    private static boolean logged;

    private ChronoSkyRenderer() {}

    public static void setSunRenderer(SunRenderer renderer) {
        sunRenderer = renderer;
    }

    public static void render(ClientLevel level, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                              Matrix4f projectionMatrix, Runnable setupFog) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        ChronoLine line = ChronoLineState.clientLine();
        float param = (float) line.param(player.getX(), player.getZ());
        float sunAngle = (float) (param * DdtConfig.sunAngleMax());

        if (setupFog != null) {
            setupFog.run();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        float savedFogStart = RenderSystem.getShaderFogStart();
        float savedFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E9F);
        RenderSystem.setShaderFogEnd(1.0E9F);
        drawDome(modelViewMatrix, param);

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        SkyContext context = new SkyContext(level, player, poseStack, projectionMatrix, modelViewMatrix, camera, partialTick, param, sunAngle);
        sunRenderer.render(context);

        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.setShaderFogEnd(savedFogEnd);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static void drawDome(Matrix4f modelViewMatrix, float param) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        Matrix4f matrix = poseStack.last().pose();

        int top = sampleColor(DdtConfig.skyTopColors(), param);
        int horizon = sampleColor(DdtConfig.skyHorizonColors(), param);
        if (!logged) {
            logged = true;
            com.dawnduskterminal.DawnDuskTerminal.LOGGER.info(
                "DDT_SKY param={} top={} horizon={} radius={}", param, top, horizon, RADIUS);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(matrix, 0.0F, RADIUS, 0.0F)
            .setColor(red(top), green(top), blue(top), 1.0F);
        float ringY = -RADIUS;
        float ringRadius = RADIUS * 1.05F;
        for (int i = 0; i <= SEGMENTS; i++) {
            double theta = (double) i / (double) SEGMENTS * Math.PI * 2.0D;
            float x = (float) (Math.cos(theta) * ringRadius);
            float z = (float) (Math.sin(theta) * ringRadius);
            builder.addVertex(matrix, x, ringY, z)
                .setColor(red(horizon), green(horizon), blue(horizon), 1.0F);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static int sampleColor(int[] stops, float param) {
        float t = (param + 1.0F) * 0.5F * (stops.length - 1);
        int index = (int) Math.floor(t);
        if (index < 0) {
            return stops[0];
        }
        if (index >= stops.length - 1) {
            return stops[stops.length - 1];
        }
        float local = t - index;
        return lerpColor(stops[index], stops[index + 1], local);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) ((red(from) + (red(to) - red(from)) * t) * 255.0F);
        int g = (int) ((green(from) + (green(to) - green(from)) * t) * 255.0F);
        int b = (int) ((blue(from) + (blue(to) - blue(from)) * t) * 255.0F);
        return (r << 16) | (g << 8) | b;
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }
}
