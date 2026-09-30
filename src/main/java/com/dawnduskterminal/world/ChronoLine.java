package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public record ChronoLine(double originX, double originZ, double normalX, double normalZ) {
    public static final Codec<ChronoLine> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.DOUBLE.fieldOf("origin_x").forGetter(ChronoLine::originX),
        Codec.DOUBLE.fieldOf("origin_z").forGetter(ChronoLine::originZ),
        Codec.DOUBLE.fieldOf("normal_x").forGetter(ChronoLine::normalX),
        Codec.DOUBLE.fieldOf("normal_z").forGetter(ChronoLine::normalZ)
    ).apply(inst, ChronoLine::new));

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
                    // fall through to the random direction
                }
            }
        }
        double jitter = (random.nextDouble() - 0.5D) * Math.toRadians(60.0D);
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
