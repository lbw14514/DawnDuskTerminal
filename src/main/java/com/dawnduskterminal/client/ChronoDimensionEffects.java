package com.dawnduskterminal.client;

import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class ChronoDimensionEffects extends DimensionSpecialEffects {
    private static final float CLOUD_LEVEL = 300.0F;

    private static boolean cloudLogged;

    public ChronoDimensionEffects() {
        super(CLOUD_LEVEL, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        Minecraft minecraft = Minecraft.getInstance();
        double x = minecraft.player != null ? minecraft.player.getX() : 0.0D;
        double z = minecraft.player != null ? minecraft.player.getZ() : 0.0D;
        float param = (float) ChronoLineState.clientLine().param(x, z);
        double r;
        double g;
        double b;
        if (param <= 0.0D) {
            double u = param + 1.0D;
            r = 0.68D + 0.60D * u;
            g = 0.76D + 0.16D * u;
            b = 0.98D - 0.40D * u;
        } else {
            double u = param;
            r = 1.28D - 0.23D * u;
            g = 0.92D + 0.10D * u;
            b = 0.58D + 0.40D * u;
        }
        return new Vec3(color.x * r, color.y * g, color.z * b);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (!DdtConfig.customSky()) {
            return false;
        }
        ChronoSkyRenderer.render(level, partialTick, modelViewMatrix, camera, projectionMatrix, setupFog);
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        if (!cloudLogged) {
            cloudLogged = true;
            com.dawnduskterminal.DawnDuskTerminal.LOGGER.info(
                "DDT_CLOUD cloudsType={} cloudHeight={}",
                Minecraft.getInstance().options.getCloudsType(), getCloudHeight());
        }
        return false;
    }
}
