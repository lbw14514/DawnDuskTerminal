package com.dawnduskterminal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix4f;

public record SkyContext(
    ClientLevel level,
    LocalPlayer player,
    PoseStack poseStack,
    Matrix4f projectionMatrix,
    Matrix4f modelViewMatrix,
    Camera camera,
    float partialTick,
    float lineParam,
    float sunAngleDegrees
) {
}
