package com.ysm.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ysm.config.YsmConfig;
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
        double base = random.nextBoolean() ? Math.PI / 2.0D : -Math.PI / 2.0D;
        double jitter = (random.nextDouble() - 0.5D) * Math.toRadians(60.0D);
        double angle = base + jitter;
        return new ChronoLine(0.0D, 0.0D, Math.cos(angle), Math.sin(angle));
    }

    public static ChronoLine fallback() {
        return new ChronoLine(0.0D, 0.0D, 0.0D, 1.0D);
    }

    public double distance(double x, double z) {
        return (x - this.originX) * this.normalX + (z - this.originZ) * this.normalZ;
    }

    public double param(double x, double z) {
        double max = Math.max(1.0D, YsmConfig.maxDistance());
        return Mth.clamp(distance(x, z) / max, -1.0D, 1.0D);
    }

    public float sunAngleDegrees(double x, double z) {
        return (float) (param(x, z) * YsmConfig.sunAngleMax());
    }

    public boolean isDaySide(double x, double z) {
        return distance(x, z) > 0.0D;
    }
}
