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
    public ChronoDimensionEffects() {
        super(192.0F, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        Minecraft minecraft = Minecraft.getInstance();
        double x = minecraft.player != null ? minecraft.player.getX() : 0.0D;
        double z = minecraft.player != null ? minecraft.player.getZ() : 0.0D;
        float param = (float) ChronoLineState.clientLine().param(x, z);
        double factor = 0.60D + 0.40D * (param + 1.0D) * 0.5D;
        return color.multiply(factor, factor * 0.98D, factor * 1.06D);
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
        setupFog.run();
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return DdtConfig.customSky();
    }
}
