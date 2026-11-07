package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.CarminiteBallEntity;
import com.dawnduskterminal.entity.CarminiteFieldEntity;
import com.dawnduskterminal.entity.CarminiteShrapnelEntity;
import com.dawnduskterminal.entity.Experiment011PlateEntity;
import com.dawnduskterminal.entity.Experiment057BladeEntity;
import com.dawnduskterminal.entity.ExpowheelEntity;
import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.entity.GhastEliteFireball;
import com.dawnduskterminal.entity.GhastFruityEntity;
import com.dawnduskterminal.entity.GhastPaintingEntity;
import com.dawnduskterminal.entity.LivingArmorEntity;
import com.dawnduskterminal.entity.StringEntity;
import com.dawnduskterminal.entity.TowerGhastEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DawnDuskTerminal.MOD_ID);

    // 碰撞箱与玩家同尺寸（0.6 x 1.8，眼高 1.62）：模型本体比这大得多（头发 袖子伸出去，
    // 量出来约 3.6 x 3.8），拿模型外廓做碰撞箱会让它庞大得不好打，所以按感觉对齐玩家
    public static final DeferredHolder<EntityType<?>, EntityType<TowerGhastEntity>> TOWER_GHAST =
            ENTITIES.register("tower_ghast", () -> EntityType.Builder.of(TowerGhastEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("tower_ghast"));

    /**
     * 「恶果」。碰撞箱照几何里的 {@code Root_spin_M}：那个立方体是 24 单位见方，即 1.5x1.5x1.5 格，
     * 眼高取这个立方体的中心（模型 y=12 单位 = 0.75 格）
     *
     * <p>模型按 1:1 绘制，不做任何缩放（见 {@code GhastFruityRenderer}）；{@code fireImmune()}
     * 照抄原版恶魂 —— 这只生物自己就在喷火球，会烧伤反而奇怪
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GhastFruityEntity>> GHASTFRUITY =
            ENTITIES.register("ghastfruity", () -> EntityType.Builder.of(GhastFruityEntity::new, MobCategory.MONSTER)
                    .sized(1.5F, 1.5F)
                    .eyeHeight(0.75F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("ghastfruity"));

    /** 权杖的弹体。updateInterval 1，跟踪范围小：它是一个很快就消失的飞行物 */
    public static final DeferredHolder<EntityType<?>, EntityType<CarminiteBallEntity>> CARMINITE_BALL =
            ENTITIES.register("carminite_ball", () -> EntityType.Builder.<CarminiteBallEntity>of(
                            CarminiteBallEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("carminite_ball"));

    /** 命中后留在原地的反应场。不可见 不参与碰撞，客户端靠它每 tick 生成边缘粒子 */
    public static final DeferredHolder<EntityType<?>, EntityType<CarminiteFieldEntity>> CARMINITE_FIELD =
            ENTITIES.register("carminite_field", () -> EntityType.Builder.<CarminiteFieldEntity>of(
                            CarminiteFieldEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .fireImmune()
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("carminite_field"));

    /**
     * 试验物品 052 炸出来的碎片。碰撞箱比一颗球还小，跟踪范围与更新间隔按原版权杖弹体那一档：
     * 它也是一闪就没的飞行物
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CarminiteShrapnelEntity>> CARMINITE_SHRAPNEL =
            ENTITIES.register("carminite_shrapnel", () -> EntityType.Builder.<CarminiteShrapnelEntity>of(
                            CarminiteShrapnelEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("carminite_shrapnel"));

    /**
     * 试验物品 057 打出去的那片飞刃
     *
     * <p>碰撞箱取 0.8x0.8：几何最宽处 24 单位 = 1.5 格，箱子比模型收一圈，免得贴墙时还没碰到就弹开。
     * updateInterval 1——它每 tick 都被制导拐向发射者的视线，位置包慢一拍就会看出折线
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Experiment057BladeEntity>> EXPERIMENT_057_BLADE =
            ENTITIES.register("experiment_057_blade", () -> EntityType.Builder.<Experiment057BladeEntity>of(
                            Experiment057BladeEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("experiment_057_blade"));

    /**
     * 博览轮舞打出去的那片飞轮（试验物品 057 的加强版）
     *
     * <p>碰撞箱 2.0 x 0.5：几何的 {@code visible_bounds_width} 写的就是 4 格、最外圈 {@code ysmGlowIn}
     * 是 15 单位，摆出来是一片直径两格上下的薄轮子，所以箱子取「宽 2 格、厚半格」的扁盒，与模型的外轮廓
     * 对得上；0.8 见方的方盒比模型小一半，得等中心点擦到才算命中。updateInterval 1——它每 tick 都被
     * 索敌或制导拐一次，位置包慢一拍就会看出折线；跟踪范围沿用 057 的 4
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ExpowheelEntity>> EXPOWHEEL = ENTITIES.register(
            "expowheel", () -> EntityType.Builder.<ExpowheelEntity>of(ExpowheelEntity::new, MobCategory.MISC)
                    .sized(2.0F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("expowheel"));

    /**
     * 「弦」。碰撞箱的基准值是小型，真正的尺寸由 {@link StringEntity#getDefaultDimensions} 按变种
     * 与随机体型偏移算；这里只要给一个能通过构造期校验的占位值。
     *
     * <p>updateInterval 2：它水平移动很快，位置包慢一点就会看到明显的折线
     */
    public static final DeferredHolder<EntityType<?>, EntityType<StringEntity>> STRING =
            ENTITIES.register("string", () -> EntityType.Builder.of(StringEntity::new, MobCategory.CREATURE)
                    .sized(1.8F, 1.8F)
                    .eyeHeight(0.9F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("string"));

    /**
     * 「大恶魂」。碰撞箱与恶魂一样取几何里的主立方体 {@code thehead}：128 单位见方，即 8x8x8 格，
     * 眼高取这个立方体的中心（模型 y=64 单位 = 4 格）；触手与腿在箱子外，与恶果的裙摆同理，不参与判定
     *
     * <p>模型按 1:1 绘制，不做任何缩放；{@code fireImmune()} 照抄原版恶魂
     */
    /** 大恶魂的火球：不与其他弹体相撞，见 {@code GhastEliteFireball} */
    public static final DeferredHolder<EntityType<?>, EntityType<GhastEliteFireball>> GHAST_ELITE_FIREBALL =
            ENTITIES.register("ghast_elite_fireball", () -> EntityType.Builder.<GhastEliteFireball>of(
                            GhastEliteFireball::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("ghast_elite_fireball"));

    public static final DeferredHolder<EntityType<?>, EntityType<GhastEliteEntity>> GHAST_ELITE =
            ENTITIES.register("ghast_elite", () -> EntityType.Builder.of(GhastEliteEntity::new, MobCategory.MONSTER)
                    .sized(8.0F, 8.0F)
                    .eyeHeight(4.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("ghast_elite"));

    /**
     * 恶魂画：一幅画一个实体类型，各自只认自己那一幅变体，见 {@link GhastPaintingEntity}
     *
     * <p>尺寸与 updateInterval 照抄原版画：画不会移动，{@code sized} 只是占位，
     * 真正的包围盒由 {@code Painting#calculateBoundingBox} 按变体在 JSON 里写的宽高算出来
     * （{@code data/dawnduskterminal/painting_variant/} 下的条目，从 1x2 到 6x4）；
     * updateInterval 拉到 {@link Integer#MAX_VALUE} 是原版的做法，非初始同步不发位置包
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_1 =
            painting(ModPaintingVariants.GHAST_1);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_2 =
            painting(ModPaintingVariants.GHAST_2);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_2_SUB =
            painting(ModPaintingVariants.GHAST_2_SUB);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_3 =
            painting(ModPaintingVariants.GHAST_3);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_4 =
            painting(ModPaintingVariants.GHAST_4);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_5 =
            painting(ModPaintingVariants.GHAST_5);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_6 =
            painting(ModPaintingVariants.GHAST_6);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_7 =
            painting(ModPaintingVariants.GHAST_7);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_8 =
            painting(ModPaintingVariants.GHAST_8);

    public static final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> GHAST_PAINTING_9 =
            painting(ModPaintingVariants.GHAST_9);

    /**
     * 「活盔甲」。碰撞箱照玩家取 0.6 x 1.8、眼高 1.62：它走起来就是一具玩家尺寸的空盔甲，
     * 本体不显示外观（见 {@code client/LivingArmorRenderer}），撞得到、打得着靠的都是这个箱子
     *
     * <p>不注册自然生成：作者暂时不做自然生成，现在只有刷怪蛋与 {@code /summon} 两条路能放出来，
     * 所以 {@link #onRegisterSpawnPlacements} 里也没有它
     */
    public static final DeferredHolder<EntityType<?>, EntityType<LivingArmorEntity>> LIVING_ARMOR =
            ENTITIES.register("living_armor", () -> EntityType.Builder.of(LivingArmorEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("living_armor"));

    /**
     * 一幅画一个实体类型。注册名直接取变体名（{@code painting_ghast_1} 等），
     * 种类由 lambda 带进构造，实体与物品、变体三处的配对因此只有一处来源
     */
    private static DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> painting(
            ModPaintingVariants.Kind kind) {
        String name = kind.variant().location().getPath();
        return ENTITIES.register(name, () -> EntityType.Builder.<GhastPaintingEntity>of(
                        (type, level) -> new GhastPaintingEntity(type, level, kind), MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(10)
                .updateInterval(Integer.MAX_VALUE)
                .build(name));
    }

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        // 属性也注册在 mod 总线上，但走的是专门的事件而不是注册表：
        // EntityAttributeCreationEvent 触发时 EntityType 已经挂好，正好是它需要的时机
        bus.addListener(ModEntities::onEntityAttributeCreation);
        bus.addListener(ModEntities::onRegisterSpawnPlacements);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(TOWER_GHAST.get(), TowerGhastEntity.createAttributes().build());
        event.put(STRING.get(), StringEntity.createAttributes().build());
        event.put(GHASTFRUITY.get(), GhastFruityEntity.createAttributes().build());
        event.put(GHAST_ELITE.get(), GhastEliteEntity.createAttributes().build());
        event.put(LIVING_ARMOR.get(), LivingArmorEntity.createAttributes().build());
    }

    /**
     * 自然生成的位置与判定
     *
     * <p>和原版恶魂一样落在「地表」上（{@code ON_GROUND} + 不含树叶的高度图），
     * 于是它出生在树冠间的空地或地面上，再自己飘起来；判定见
     * {@link GhastFruityEntity#checkGhastFruitySpawnRules}
     *
     * <p>{@code REPLACE} 只是给新注册的实体类型填表用的：这张表里原本没有 ghastfruity，
     * 事件实现会直接把这条判定放进去，不涉及别的模组
     *
     * <p>活盔甲也要一条：它出现在暗森林群系的 {@code spawners} 里，而 NeoForge 启动时会检查
     * 「刷怪表里有、{@code SpawnPlacements} 里没有」的实体并<b>报 ERROR</b>（不注册就等于放行一切位置）。
     * 这里给它原版敌对生物那一套（非和平、够暗、脚下方块合法、两格空），
     * 「只站霞草 / 霞土」那一条另在 {@code DarkForestSpawns} 里按群系否决
     */
    private static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                GHASTFRUITY.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GhastFruityEntity::checkGhastFruitySpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                GHAST_ELITE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GhastEliteEntity::checkGhastEliteSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                LIVING_ARMOR.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * 试验物品 011 摆下来的那只盘子
     *
     * <p>尺寸就是外形：14 像素见方、1 像素厚（{@code 14/16 x 1/16 x 14/16}）。它是一张不会动、不走重力的
     * 薄片，位置在摆下来那一刻由 {@code Experiment011Item} 按脚下那块方块的碰撞箱定好，所以
     * {@code updateInterval} 给 20 就够：不动的东西只有「盘子上换了东西」这一件事要同步，
     * 而那一条走的是 {@code SynchedEntityData}，与位置包无关
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Experiment011PlateEntity>> EXPERIMENT_011_PLATE =
            ENTITIES.register("experiment_011_plate", () -> EntityType.Builder.<Experiment011PlateEntity>of(
                            Experiment011PlateEntity::new, MobCategory.MISC)
                    .sized(14.0F / 16.0F, 1.0F / 16.0F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("experiment_011_plate"));
}
