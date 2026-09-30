package com.dawnduskterminal.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class DdtConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue SUN_ANGLE_MAX;
    private static final ModConfigSpec.DoubleValue MAX_DISTANCE;
    private static final ModConfigSpec.DoubleValue BAND_HALF_WIDTH;
    private static final ModConfigSpec.ConfigValue<String> NORMAL_OVERRIDE;
    private static final ModConfigSpec.IntValue PORTAL_COOLDOWN_TICKS;
    private static final ModConfigSpec.BooleanValue WATER_IS_PORTAL;
    private static final ModConfigSpec.BooleanValue CONVERT_WATER;
    private static final ModConfigSpec.BooleanValue CUSTOM_SKY;
    private static final ModConfigSpec.BooleanValue SHADER_WARNING;
    private static final ModConfigSpec.IntValue SUN_TEXTURE_SIZE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SKY_TOP_COLORS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SKY_HORIZON_COLORS;
    private static final ModConfigSpec.BooleanValue ENTITY_DEBUFF;
    private static final ModConfigSpec.BooleanValue TOLERATE_FEATURE_CYCLES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("lighting");
        SUN_ANGLE_MAX = b.comment("max sun height angle in degrees")
            .defineInRange("sun_angle_max", 30.0D, 0.0D, 90.0D);
        b.pop();

        b.push("chrono_line");
        MAX_DISTANCE = b.comment("distance in blocks at which the sun reaches its max angle")
            .defineInRange("max_distance", 5000.0D, 16.0D, 1000000.0D);
        BAND_HALF_WIDTH = b.comment("half width of the band that keeps the delegate biome rules")
            .defineInRange("band_half_width", 1000.0D, 0.0D, 1000000.0D);
        NORMAL_OVERRIDE = b.comment("pin the terminator normal, format x,z, empty means pick a random direction per world")
            .define("normal_override", "");
        b.pop();

        b.push("portal");
        PORTAL_COOLDOWN_TICKS = b.comment("ticks before the player can be teleported again")
            .defineInRange("cooldown_ticks", 100, 0, 72000);
        WATER_IS_PORTAL = b.comment("inside the chrono dimension vanilla water acts as a portal")
            .define("water_is_portal", true);
        CONVERT_WATER = b.comment("replace vanilla water with portal fluid when a chunk is loaded")
            .define("convert_water_on_chunk_load", false);
        ENTITY_DEBUFF = b.comment("non player entities get the portal sickness effect")
            .define("entity_debuff", true);
        b.pop();

        b.push("compat");
        TOLERATE_FEATURE_CYCLES = b.comment("replace the vanilla feature sorter with a cycle tolerant one, required when both vanilla and modded biomes are used in one dimension")
            .define("tolerate_feature_cycles", true);
        b.pop();

        b.push("client");
        CUSTOM_SKY = b.comment("render the chrono sky and the fake sun instead of the vanilla one")
            .define("custom_sky", true);
        SHADER_WARNING = b.comment("warn the player when a shader mod is detected")
            .define("shader_warning", true);
        SUN_TEXTURE_SIZE = b.comment("side length of the square sun texture")
            .defineInRange("sun_texture_size", 64, 8, 512);
        SKY_TOP_COLORS = b.comment("sky zenith gradient stops from polar night to polar day, 6 digit hex without the hash")
            .defineListAllowEmpty("sky_top_colors",
                () -> List.of("05050C", "101A38", "2E3A6E", "4A90D9", "3E7BD8"),
                () -> "FFFFFF",
                entry -> entry instanceof String text && text.matches("[0-9A-Fa-f]{6}"));
        SKY_HORIZON_COLORS = b.comment("sky horizon gradient stops from polar night to polar day, 6 digit hex without the hash")
            .defineListAllowEmpty("sky_horizon_colors",
                () -> List.of("131026", "2A2350", "8A6BB8", "A8CFFF", "FFD9A0"),
                () -> "FFFFFF",
                entry -> entry instanceof String text && text.matches("[0-9A-Fa-f]{6}"));
        b.pop();

        SPEC = b.build();
    }

    private DdtConfig() {}

    public static double sunAngleMax() {
        try {
            return SUN_ANGLE_MAX.get();
        } catch (IllegalStateException e) {
            return 30.0D;
        }
    }

    public static double maxDistance() {
        try {
            return MAX_DISTANCE.get();
        } catch (IllegalStateException e) {
            return 5000.0D;
        }
    }

    public static double bandHalfWidth() {
        try {
            return BAND_HALF_WIDTH.get();
        } catch (IllegalStateException e) {
            return 1000.0D;
        }
    }

    public static String lineNormalOverride() {
        try {
            return NORMAL_OVERRIDE.get();
        } catch (IllegalStateException e) {
            return "";
        }
    }

    public static int portalCooldownTicks() {
        try {
            return PORTAL_COOLDOWN_TICKS.get();
        } catch (IllegalStateException e) {
            return 100;
        }
    }

    public static boolean waterIsPortal() {
        try {
            return WATER_IS_PORTAL.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean convertWater() {
        try {
            return CONVERT_WATER.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean entityDebuff() {
        try {
            return ENTITY_DEBUFF.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean tolerateFeatureCycles() {
        try {
            return TOLERATE_FEATURE_CYCLES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean customSky() {
        try {
            return CUSTOM_SKY.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean shaderWarning() {
        try {
            return SHADER_WARNING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int sunTextureSize() {
        try {
            return SUN_TEXTURE_SIZE.get();
        } catch (IllegalStateException e) {
            return 64;
        }
    }

    private static final int[] DEFAULT_TOP = {0x05050C, 0x101A38, 0x2E3A6E, 0x4A90D9, 0x3E7BD8};
    private static final int[] DEFAULT_HORIZON = {0x131026, 0x2A2350, 0x8A6BB8, 0xA8CFFF, 0xFFD9A0};

    private static List<? extends String> cachedTopRaw;
    private static int[] cachedTop = DEFAULT_TOP;
    private static List<? extends String> cachedHorizonRaw;
    private static int[] cachedHorizon = DEFAULT_HORIZON;

    public static int[] skyTopColors() {
        return resolve(SKY_TOP_COLORS, true);
    }

    public static int[] skyHorizonColors() {
        return resolve(SKY_HORIZON_COLORS, false);
    }

    private static int[] resolve(ModConfigSpec.ConfigValue<List<? extends String>> value, boolean top) {
        List<? extends String> cachedRaw = top ? cachedTopRaw : cachedHorizonRaw;
        try {
            List<? extends String> raw = value.get();
            if (raw != cachedRaw) {
                int[] parsed = parseColors(raw, top ? DEFAULT_TOP : DEFAULT_HORIZON);
                if (top) {
                    cachedTopRaw = raw;
                    cachedTop = parsed;
                } else {
                    cachedHorizonRaw = raw;
                    cachedHorizon = parsed;
                }
            }
        } catch (IllegalStateException e) {
            return top ? DEFAULT_TOP : DEFAULT_HORIZON;
        }
        return top ? cachedTop : cachedHorizon;
    }

    private static int[] parseColors(List<? extends String> raw, int[] fallback) {
        if (raw == null || raw.size() < 2) {
            return fallback;
        }
        int[] result = new int[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            try {
                result[i] = Integer.parseInt(raw.get(i).trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return result;
    }
}
