package com.dawnduskterminal.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class PortalDistortion {
    private static final int CHARGE_DURATION = 100;
    private static final int FADE_DURATION = 60;
    private static final int RED = 0xE0301E;
    private static boolean charging = false;
    private static int chargeTicks = 0;
    private static int fadeTicks = -1;
    private static float fadeFrom = 0.0F;

    private PortalDistortion() {}

    public static void setSearching(boolean value) {
        if (value) {
            charging = true;
            chargeTicks = 0;
            fadeTicks = -1;
        } else {
            charging = false;
            fadeFrom = redAmount();
            fadeTicks = 0;
        }
    }

    private static float redAmount() {
        if (charging) {
            float t = Math.min(1.0F, chargeTicks / (float) CHARGE_DURATION);
            return t * t * (3.0F - 2.0F * t) * 0.78F;
        }
        if (fadeTicks >= 0) {
            float t = Math.min(1.0F, fadeTicks / (float) FADE_DURATION);
            return fadeFrom * (1.0F - t);
        }
        return 0.0F;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            charging = false;
            chargeTicks = 0;
            fadeTicks = -1;
            return;
        }
        if (charging) {
            chargeTicks++;
        }
        if (fadeTicks >= 0) {
            fadeTicks++;
            if (fadeTicks > FADE_DURATION) {
                fadeTicks = -1;
            }
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!charging) {
            return;
        }
        float env = Math.min(1.0F, chargeTicks / 24.0F);
        double phase = chargeTicks * Math.PI * 2.0D / CHARGE_DURATION;
        event.setRoll(event.getRoll() + (float) (Math.sin(phase) * 4.0D * env));
        event.setPitch(event.getPitch() + (float) (Math.sin(phase * 0.5D + Math.PI / 2.0D) * 2.0D * env));
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!charging) {
            return;
        }
        double env = Math.min(1.0F, chargeTicks / 24.0F);
        double phase = chargeTicks * Math.PI * 2.0D / CHARGE_DURATION;
        event.setFOV(event.getFOV() * (1.0D - (0.5D - 0.5D * Math.cos(phase)) * 0.12D * env));
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        float amount = redAmount();
        if (amount <= 0.001F) {
            return;
        }
        int alpha = (int) (amount * 255.0F);
        event.getGuiGraphics().fill(0, 0, event.getGuiGraphics().guiWidth(),
            event.getGuiGraphics().guiHeight(), (alpha << 24) | RED);
    }
}
