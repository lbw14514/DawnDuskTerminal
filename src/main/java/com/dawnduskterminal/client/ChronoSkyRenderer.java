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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ChronoSkyRenderer {
    private static final int SEGMENTS = 64;
    private static final float RADIUS = 100.0F;
    private static final int STAR_COUNT = 320;
    private static final float SUN_HIDE_DEGREES = 2.0F;

    public static final ResourceLocation SUN_TEXTURE =
        com.dawnduskterminal.DawnDuskTerminal.id("textures/environment/sun_disc.png");

    private static boolean logged;

    private ChronoSkyRenderer() {}

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
        drawHorizonGlow(modelViewMatrix, param);
        drawStars(modelViewMatrix, param);

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        Matrix4f celestialMatrix = poseStack.last().pose();
        float sunSize = DdtConfig.sunTextureSize() / 64.0F * 18.0F;
        if (sunAngle >= -SUN_HIDE_DEGREES) {
            drawCelestial(celestialMatrix, camera, sunAngle, SUN_TEXTURE, sunSize, 1.0F, 1.0F, 1.0F, 1.0F);
        }

        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.setShaderFogEnd(savedFogEnd);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static void drawCelestial(Matrix4f matrix, Camera camera, float angleDegrees, ResourceLocation texture,
                                      float size, float r, float g, float b, float alpha) {
        double angle = Math.toRadians(angleDegrees);
        Vector3f normal = ChronoLineStateHolder.normal();
        double cx = normal.x * Math.cos(angle) * RADIUS;
        double cy = Math.sin(angle) * RADIUS;
        double cz = normal.z * Math.cos(angle) * RADIUS;
        float half = size * 0.5F;
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double rx = -left.x * half;
        double ry = -left.y * half;
        double rz = -left.z * half;
        double ux = up.x * half;
        double uy = up.y * half;
        double uz = up.z * half;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(r, g, b, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, (float) (cx - rx - ux), (float) (cy - ry - uy), (float) (cz - rz - uz)).setUv(0.0F, 1.0F);
        builder.addVertex(matrix, (float) (cx + rx - ux), (float) (cy + ry - uy), (float) (cz + rz - uz)).setUv(1.0F, 1.0F);
        builder.addVertex(matrix, (float) (cx + rx + ux), (float) (cy + ry + uy), (float) (cz + rz + uz)).setUv(1.0F, 0.0F);
        builder.addVertex(matrix, (float) (cx - rx + ux), (float) (cy - ry + uy), (float) (cz - rz + uz)).setUv(0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void drawStars(Matrix4f modelViewMatrix, float param) {
        float night = Mth.clamp(-param, 0.0F, 1.0F);
        if (night <= 0.05F) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RandomSource random = RandomSource.create(20260930L);
        for (int i = 0; i < STAR_COUNT; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double cosPhi = random.nextDouble() * 0.94D;
            double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
            float x = (float) (Math.cos(theta) * sinPhi * RADIUS * 0.96D);
            float y = (float) (cosPhi * RADIUS * 0.96D);
            float z = (float) (Math.sin(theta) * sinPhi * RADIUS * 0.96D);
            float size = 0.30F + random.nextFloat() * 0.55F;
            float alpha = night * (0.35F + random.nextFloat() * 0.65F);
            float rx = -left.x * size;
            float ry = -left.y * size;
            float rz = -left.z * size;
            float ux = up.x * size;
            float uy = up.y * size;
            float uz = up.z * size;
            builder.addVertex(matrix, x - rx - ux, y - ry - uy, z - rz - uz).setColor(1.0F, 1.0F, 0.96F, alpha);
            builder.addVertex(matrix, x + rx - ux, y + ry - uy, z + rz - uz).setColor(1.0F, 1.0F, 0.96F, alpha);
            builder.addVertex(matrix, x + rx + ux, y + ry + uy, z + rz + uz).setColor(1.0F, 1.0F, 0.96F, alpha);
            builder.addVertex(matrix, x - rx + ux, y - ry + uy, z - rz + uz).setColor(1.0F, 1.0F, 0.96F, alpha);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void drawHorizonGlow(Matrix4f modelViewMatrix, float param) {
        float glow = 1.0F - Math.min(1.0F, Math.abs(param) / 0.35F);
        if (glow <= 0.02F) {
            return;
        }
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float innerY = RADIUS * 0.32F;
        float innerR = RADIUS * 0.94F;
        float outerY = -RADIUS * 0.08F;
        float outerR = RADIUS * 1.12F;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            double theta = (double) i / (double) SEGMENTS * Math.PI * 2.0D;
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            builder.addVertex(matrix, (float) (cos * innerR), innerY, (float) (sin * innerR))
                .setColor(1.0F, 0.68F, 0.38F, glow * 0.15F);
            builder.addVertex(matrix, (float) (cos * outerR), outerY, (float) (sin * outerR))
                .setColor(1.0F, 0.55F, 0.26F, glow * 0.75F);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void drawDome(Matrix4f modelViewMatrix, float param) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        Matrix4f matrix = poseStack.last().pose();

        int top = sampleColor(DdtConfig.skyTopColors(), param);
        int horizon = sampleColor(DdtConfig.skyHorizonColors(), param);
        int c1 = lerpColor(top, horizon, 0.25F);
        int c2 = lerpColor(top, horizon, 0.50F);
        int c3 = lerpColor(top, horizon, 0.75F);
        if (!logged) {
            logged = true;
            com.dawnduskterminal.DawnDuskTerminal.LOGGER.info(
                "DDT_SKY param={} top={} c2={} horizon={}", param, top, c2, horizon);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float r1 = RADIUS * 0.72F;
        float r2 = RADIUS * 0.92F;
        float r3 = RADIUS * 1.02F;
        float r4 = RADIUS * 1.12F;
        float y1 = RADIUS * 0.62F;
        float y2 = RADIUS * 0.30F;
        float y3 = RADIUS * 0.05F;
        float y4 = -RADIUS * 0.08F;

        BufferBuilder cap = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        cap.addVertex(matrix, 0.0F, RADIUS, 0.0F).setColor(red(top), green(top), blue(top), 1.0F);
        for (int i = 0; i <= SEGMENTS; i++) {
            double theta = (double) i / (double) SEGMENTS * Math.PI * 2.0D;
            cap.addVertex(matrix, (float) (Math.cos(theta) * r1), y1, (float) (Math.sin(theta) * r1))
                .setColor(red(c1), green(c1), blue(c1), 1.0F);
        }
        BufferUploader.drawWithShader(cap.buildOrThrow());

        band(matrix, r1, y1, c1, r2, y2, c2);
        band(matrix, r2, y2, c2, r3, y3, c3);
        band(matrix, r3, y3, c3, r4, y4, horizon);
    }

    private static void band(Matrix4f matrix, float innerR, float innerY, int innerColor,
                             float outerR, float outerY, int outerColor) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            double theta = (double) i / (double) SEGMENTS * Math.PI * 2.0D;
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            builder.addVertex(matrix, (float) (cos * innerR), innerY, (float) (sin * innerR))
                .setColor(red(innerColor), green(innerColor), blue(innerColor), 1.0F);
            builder.addVertex(matrix, (float) (cos * outerR), outerY, (float) (sin * outerR))
                .setColor(red(outerColor), green(outerColor), blue(outerColor), 1.0F);
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
