package com.ysm.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    public static final ResourceKey<Level> CHRONO = ResourceKey.create(Registries.DIMENSION, com.ysm.Ysm.id("chrono"));

    private ModDimensions() {}
}
