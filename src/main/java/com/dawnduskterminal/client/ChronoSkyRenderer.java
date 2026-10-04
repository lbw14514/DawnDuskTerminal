package com.dawnduskterminal.client;

import com.mojang.blaze3d.platform.GlStateManager;
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
    private static final int DOME_BANDS = 14;
    private static final float RADIUS = 100.0F;
    private static final int STAR_COUNT = 320;
    private static final float SUN_HIDE_DEGREES = 2.0F;

    public static final ResourceLocation SUN_TEXTURE =
        com.dawnduskterminal.DawnDuskTerminal.id("textures/environment/sun.png");
    public static final ResourceLocation MOON_TEXTURE =
        com.dawnduskterminal.DawnDuskTerminal.id("textures/environment/moon_phases.png");
    private static final float VANILLA_SUN_HALF = 30.0F;
    private static final float VANILLA_MOON_HALF = 20.0F;

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
        float sizeScale = DdtConfig.sunTextureSize() / 64.0F;
        int moonPhase = level.getMoonPhase();
        int phaseX = moonPhase % 4;
        int phaseY = moonPhase / 4 % 2;
        float u0 = (float) phaseX / 4.0F;
        float u1 = (float) (phaseX + 1) / 4.0F;
        float v0 = (float) phaseY / 2.0F;
        float v1 = (float) (phaseY + 1) / 2.0F;
        float moonAngle = -sunAngle;
        if (sunAngle >= -SUN_HIDE_DEGREES) {
            drawCelestial(celestialMatrix, camera, sunAngle, false, SUN_TEXTURE,
                VANILLA_SUN_HALF * sizeScale, 0.0F, 0.0F, 1.0F, 1.0F);
        }
        if (moonAngle >= -SUN_HIDE_DEGREES) {
            drawCelestial(celestialMatrix, camera, moonAngle, true, MOON_TEXTURE,
                VANILLA_MOON_HALF * sizeScale, u0, v0, u1, v1);
        }

        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.setShaderFogEnd(savedFogEnd);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static void drawCelestial(Matrix4f matrix, Camera camera, float angleDegrees, boolean oppositeSide,
                                      ResourceLocation texture, float halfSize,
                                      float u0, float v0, float u1, float v1) {
        double angle = Math.toRadians(angleDegrees);
        Vector3f normal = ChronoLineStateHolder.normal();
        double side = oppositeSide ? -1.0D : 1.0D;
        double cx = normal.x * side * Math.cos(angle) * RADIUS;
        double cy = Math.sin(angle) * RADIUS;
        double cz = normal.z * side * Math.cos(angle) * RADIUS;
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double rx = -left.x * halfSize;
        double ry = -left.y * halfSize;
        double rz = -left.z * halfSize;
        double ux = up.x * halfSize;
        double uy = up.y * halfSize;
        double uz = up.z * halfSize;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, (float) (cx - rx - ux), (float) (cy - ry - uy), (float) (cz - rz - uz)).setUv(u0, v1);
        builder.addVertex(matrix, (float) (cx + rx - ux), (float) (cy + ry - uy), (float) (cz + rz - uz)).setUv(u1, v1);
        builder.addVertex(matrix, (float) (cx + rx + ux), (float) (cy + ry + uy), (float) (cz + rz + uz)).setUv(u1, v0);
        builder.addVertex(matrix, (float) (cx - rx + ux), (float) (cy - ry + uy), (float) (cz - rz + uz)).setUv(u0, v0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
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

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float capElevation = 52.0F;
        float floorElevation = -8.0F;
        double capRad = Math.toRadians(capElevation);
        float capY = (float) (Math.sin(capRad) * RADIUS);
        float capR = (float) (Math.cos(capRad) * RADIUS);

        BufferBuilder cap = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        cap.addVertex(matrix, 0.0F, RADIUS, 0.0F).setColor(red(top), green(top), blue(top), 1.0F);
        for (int i = 0; i <= SEGMENTS; i++) {
            double theta = (double) i / (double) SEGMENTS * Math.PI * 2.0D;
            cap.addVertex(matrix, (float) (Math.cos(theta) * capR), capY, (float) (Math.sin(theta) * capR))
                .setColor(red(top), green(top), blue(top), 1.0F);
        }
        BufferUploader.drawWithShader(cap.buildOrThrow());

        for (int bandIndex = 0; bandIndex < DOME_BANDS; bandIndex++) {
            float t0 = (float) bandIndex / (float) DOME_BANDS;
            float t1 = (float) (bandIndex + 1) / (float) DOME_BANDS;
            float ease0 = smooth(t0);
            float ease1 = smooth(t1);
            int color0 = ease0 <= 0.0F ? top : lerpColor(top, horizon, ease0);
            int color1 = lerpColor(top, horizon, ease1);
            float elevation0 = Mth.lerp(t0, capElevation, floorElevation);
            float elevation1 = Mth.lerp(t1, capElevation, floorElevation);
            double rad0 = Math.toRadians(elevation0);
            double rad1 = Math.toRadians(elevation1);
            band(matrix,
                (float) (Math.cos(rad0) * RADIUS), (float) (Math.sin(rad0) * RADIUS), color0,
                (float) (Math.cos(rad1) * RADIUS), (float) (Math.sin(rad1) * RADIUS), color1);
        }
    }

    private static float smooth(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return t * t * (3.0F - 2.0F * t);
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
