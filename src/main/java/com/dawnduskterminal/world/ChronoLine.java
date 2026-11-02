package com.dawnduskterminal.world;

import com.dawnduskterminal.config.DdtConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public record ChronoLine(double originX, double originZ, double normalX, double normalZ) {
    public static final Codec<ChronoLine> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                    Codec.DOUBLE.fieldOf("origin_x").forGetter(ChronoLine::originX),
                    Codec.DOUBLE.fieldOf("origin_z").forGetter(ChronoLine::originZ),
                    Codec.DOUBLE.fieldOf("normal_x").forGetter(ChronoLine::normalX),
                    Codec.DOUBLE.fieldOf("normal_z").forGetter(ChronoLine::normalZ))
            .apply(inst, ChronoLine::new));

    public static ChronoLine create(RandomSource random) {
        String override = DdtConfig.lineNormalOverride();
        if (override != null && !override.isBlank()) {
            String[] parts = override.split(",");
            if (parts.length == 2) {
                try {
                    double nx = Double.parseDouble(parts[0].trim());
                    double nz = Double.parseDouble(parts[1].trim());
                    double length = Math.hypot(nx, nz);
                    if (length > 1.0E-6D) {
                        return new ChronoLine(0.0D, 0.0D, nx / length, nz / length);
                    }
                } catch (NumberFormatException ignored) {
                    // 落到下面的随机方向
                }
            }
        }
        double jitter = (random.nextDouble() - 0.5D) * Math.toRadians(60.0D);
        // 太阳朝着法向。一半世界把它留在东侧（0 度正负 30），另一半翻到西侧（180 度正负 30，即 150 到
        // 210 度），于是太阳从西边升起
        if (random.nextBoolean()) {
            jitter += Math.PI;
        }
        return new ChronoLine(0.0D, 0.0D, Math.cos(jitter), Math.sin(jitter));
    }

    public static ChronoLine fallback() {
        return new ChronoLine(0.0D, 0.0D, 1.0D, 0.0D);
    }

    public double distance(double x, double z) {
        return (x - this.originX) * this.normalX + (z - this.originZ) * this.normalZ;
    }

    public double param(double x, double z) {
        double max = Math.max(1.0D, DdtConfig.maxDistance());
        return Mth.clamp(distance(x, z) / max, -1.0D, 1.0D);
    }

    public float sunAngleDegrees(double x, double z) {
        return (float) (param(x, z) * DdtConfig.sunAngleMax());
    }
}
