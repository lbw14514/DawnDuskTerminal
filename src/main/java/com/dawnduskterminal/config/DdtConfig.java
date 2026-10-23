package com.dawnduskterminal.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 配置项与代码里的取值入口
 *
 * <p>每个取值方法都给一份兜底值：mixin 与世界生成会在配置加载之前调用它们（{@code ChronoLine} 建线、
 * {@code ChronoSkyLight} 算天光、天空按 {@code sun_angle_max} 换算），那一刻 {@code ModConfigSpec} 抛
 * {@link IllegalStateException}。兜底值即配置的默认值，两者共用同一个常量，改默认值时不会只改一处
 */
public final class DdtConfig {
    public static final ModConfigSpec SPEC;

    private static final double SUN_ANGLE_MAX_DEFAULT = 20.0D;
    private static final boolean SKY_LIGHT_BY_DISTANCE_DEFAULT = true;
    private static final int SKY_LIGHT_DAY_DEFAULT = 15;
    private static final int SKY_LIGHT_CENTER_DEFAULT = 14;
    private static final int SKY_LIGHT_NIGHT_DEFAULT = 12;
    private static final double MAX_DISTANCE_DEFAULT = 5000.0D;
    private static final String NORMAL_OVERRIDE_DEFAULT = "";
    private static final int PORTAL_COOLDOWN_TICKS_DEFAULT = 100;
    private static final boolean ENTITY_DEBUFF_DEFAULT = true;
    private static final boolean TOLERATE_FEATURE_CYCLES_DEFAULT = true;

    private static final ModConfigSpec.DoubleValue SUN_ANGLE_MAX;
    private static final ModConfigSpec.BooleanValue SKY_LIGHT_BY_DISTANCE;
    private static final ModConfigSpec.IntValue SKY_LIGHT_DAY;
    private static final ModConfigSpec.IntValue SKY_LIGHT_CENTER;
    private static final ModConfigSpec.IntValue SKY_LIGHT_NIGHT;
    private static final ModConfigSpec.DoubleValue MAX_DISTANCE;
    private static final ModConfigSpec.ConfigValue<String> NORMAL_OVERRIDE;
    private static final ModConfigSpec.IntValue PORTAL_COOLDOWN_TICKS;
    private static final ModConfigSpec.BooleanValue ENTITY_DEBUFF;
    private static final ModConfigSpec.BooleanValue TOLERATE_FEATURE_CYCLES;

    /**
     * 热路径用的缓存
     *
     * <p>{@code ChronoLine.param} 是逐方块被调的（温度密度函数、群系选择、天光），每次去读
     * {@code ModConfigSpec} 都是白花。这几个值在配置加载与重载时刷一次，平时只读字段。初值就是默认值，
     * 所以配置还没加载时读到的与 {@link #safe} 的兜底一致
     */
    private static volatile double maxDistanceCache = MAX_DISTANCE_DEFAULT;
    private static volatile SkyLightConfig skyLightCache = new SkyLightConfig(
        SKY_LIGHT_BY_DISTANCE_DEFAULT, SKY_LIGHT_DAY_DEFAULT,
        SKY_LIGHT_CENTER_DEFAULT, SKY_LIGHT_NIGHT_DEFAULT);

    /**
     * 天光参数的一次性快照
     *
     * <p>{@code LightEngine.getLightValue} 每次都会读一遍，一次取四个 volatile 不如取一个快照
     */
    public record SkyLightConfig(boolean byDistance, int day, int center, int night) {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("lighting");
        SUN_ANGLE_MAX = b.comment("max sun height angle in degrees")
            .defineInRange("sun_angle_max", SUN_ANGLE_MAX_DEFAULT, 0.0D, 90.0D);
        SKY_LIGHT_BY_DISTANCE = b.comment("let sky light fall off across the terminator instead of being 15 everywhere")
            .define("sky_light_by_distance", SKY_LIGHT_BY_DISTANCE_DEFAULT);
        SKY_LIGHT_DAY = b.comment("sky light at the full day side")
            .defineInRange("sky_light_day", SKY_LIGHT_DAY_DEFAULT, 0, 15);
        SKY_LIGHT_CENTER = b.comment("sky light on the terminator itself")
            .defineInRange("sky_light_center", SKY_LIGHT_CENTER_DEFAULT, 0, 15);
        SKY_LIGHT_NIGHT = b.comment("sky light at the full night side, 8 and below lets monsters spawn")
            .defineInRange("sky_light_night", SKY_LIGHT_NIGHT_DEFAULT, 0, 15);
        b.pop();

        b.push("chrono_line");
        MAX_DISTANCE = b.comment("distance in blocks at which the sun reaches its max angle")
            .defineInRange("max_distance", MAX_DISTANCE_DEFAULT, 16.0D, 1000000.0D);
        NORMAL_OVERRIDE = b.comment("pin the terminator normal, format x,z, empty means pick a random direction per world")
            .define("normal_override", NORMAL_OVERRIDE_DEFAULT);
        b.pop();

        b.push("portal");
        PORTAL_COOLDOWN_TICKS = b.comment("ticks before the player can be teleported again")
            .defineInRange("cooldown_ticks", PORTAL_COOLDOWN_TICKS_DEFAULT, 0, 72000);
        ENTITY_DEBUFF = b.comment("non player entities get the portal sickness effect")
            .define("entity_debuff", ENTITY_DEBUFF_DEFAULT);
        b.pop();

        b.push("compat");
        TOLERATE_FEATURE_CYCLES = b.comment("replace the vanilla feature sorter with a cycle tolerant one, required when both vanilla and modded biomes are used in one dimension")
            .define("tolerate_feature_cycles", TOLERATE_FEATURE_CYCLES_DEFAULT);
        b.pop();

        SPEC = b.build();
    }

    private DdtConfig() {}

    /** 配置还没加载时返回兜底值，而不是让调用方撞上 {@link IllegalStateException} */
    private static <T> T safe(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static double sunAngleMax() {
        return safe(SUN_ANGLE_MAX, SUN_ANGLE_MAX_DEFAULT);
    }

    public static SkyLightConfig skyLightConfig() {
        return skyLightCache;
    }

    public static boolean skyLightByDistance() {
        return skyLightCache.byDistance();
    }

    public static int skyLightDay() {
        return skyLightCache.day();
    }

    public static int skyLightCenter() {
        return skyLightCache.center();
    }

    public static int skyLightNight() {
        return skyLightCache.night();
    }

    public static double maxDistance() {
        return maxDistanceCache;
    }

    /**
     * 配置加载或重载后刷新热路径缓存
     *
     * <p>挂的是 {@code ModConfigEvent.Loading} 与 {@code ModConfigEvent.Reloading}，两件都要：前者在进程
     * 启动时调一次，后者在玩家改完配置重载时调
     */
    public static void refreshCache() {
        skyLightCache = new SkyLightConfig(
            safe(SKY_LIGHT_BY_DISTANCE, SKY_LIGHT_BY_DISTANCE_DEFAULT),
            safe(SKY_LIGHT_DAY, SKY_LIGHT_DAY_DEFAULT),
            safe(SKY_LIGHT_CENTER, SKY_LIGHT_CENTER_DEFAULT),
            safe(SKY_LIGHT_NIGHT, SKY_LIGHT_NIGHT_DEFAULT));
        maxDistanceCache = safe(MAX_DISTANCE, MAX_DISTANCE_DEFAULT);
    }

    public static String lineNormalOverride() {
        return safe(NORMAL_OVERRIDE, NORMAL_OVERRIDE_DEFAULT);
    }

    public static int portalCooldownTicks() {
        return safe(PORTAL_COOLDOWN_TICKS, PORTAL_COOLDOWN_TICKS_DEFAULT);
    }

    public static boolean entityDebuff() {
        return safe(ENTITY_DEBUFF, ENTITY_DEBUFF_DEFAULT);
    }

    public static boolean tolerateFeatureCycles() {
        return safe(TOLERATE_FEATURE_CYCLES, TOLERATE_FEATURE_CYCLES_DEFAULT);
    }
}
