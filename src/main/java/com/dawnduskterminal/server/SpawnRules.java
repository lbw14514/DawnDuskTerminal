package com.dawnduskterminal.server;

import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public final class SpawnRules {
    private static final List<Rule> RULES = new ArrayList<>();

    private SpawnRules() {}

    public static void add(Rule rule) {
        RULES.add(rule);
    }

    public static boolean allow(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (!level.dimension().equals(ModDimensions.CHRONO)) {
            return true;
        }
        double param = ChronoLineState.serverLine().param(pos.getX(), pos.getZ());
        for (Rule rule : RULES) {
            if (!rule.allow(param, pos, type)) {
                return false;
            }
        }
        return true;
    }

    public interface Rule {
        boolean allow(double param, BlockPos pos, EntityType<?> type);
    }
}
