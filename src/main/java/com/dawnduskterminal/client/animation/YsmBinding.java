package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.client.animation.functions.BonePivotAbs;
import com.dawnduskterminal.client.animation.functions.BonePosition;
import com.dawnduskterminal.client.animation.functions.BoneRotation;
import com.dawnduskterminal.client.animation.functions.BoneScale;
import com.dawnduskterminal.client.animation.functions.EffectLevel;
import com.dawnduskterminal.client.animation.functions.EquippedEnchantmentLevel;
import com.dawnduskterminal.client.animation.functions.FirstOrderFunction;
import com.dawnduskterminal.client.animation.functions.ModVersion;
import com.dawnduskterminal.client.animation.functions.PerlinNoise;
import com.dawnduskterminal.client.animation.functions.RelativeBlockName;
import com.dawnduskterminal.client.animation.functions.SecondOrderFunction;
import com.dawnduskterminal.geckolib3.core.molang.binding.ContextBinding;
import com.dawnduskterminal.geckolib3.core.molang.builtin.query.EmptyFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.apache.commons.lang3.StringUtils;

/**
 * {@code ysm.*}：Yes Steve Model 的函数与变量表
 *
 * <p>YSM 的模型包把大部分逻辑写进 Molang 表达式，其中相当一部分挂在 {@code ysm.*} 下。这里按 YSM
 * 维基与它的宿主实现逐个对上；符号约定见 {@link BoneRotation} 与 {@link BonePivotAbs}。
 *
 * <p>两侧命名相反、容易写错的两项按 YSM 语义绑定，不按 GeckoLib 的：
 * {@code query.head_x_rotation} 在 YSM 里是<strong>偏航</strong>、{@code query.head_y_rotation} 是
 * <strong>俯仰</strong>（见 {@code QueryBinding}），而 {@code ysm.head_yaw} 与 {@code ysm.head_pitch}
 * 是不经 {@code query} 的快捷方式。
 *
 * <p>副作用类的函数（音效、粒子、键鼠）没有等价值，绑空实现：它们不产生取值，缺绑定则整条表达式
 * 求值失败并连带丢掉同一通道上的其余项。
 */
public class YsmBinding extends ContextBinding {
    public static final YsmBinding INSTANCE = new YsmBinding();

    @SuppressWarnings("resource")
    private YsmBinding() {
        function("mod_version", new ModVersion());
        function("equipped_enchantment_level", new EquippedEnchantmentLevel());
        function("effect_level", new EffectLevel());
        function("relative_block_name", new RelativeBlockName());

        function("bone_rot", new BoneRotation());
        function("bone_pos", new BonePosition());
        function("bone_scale", new BoneScale());
        function("bone_pivot_abs", new BonePivotAbs());

        function("first_order", new FirstOrderFunction());
        function("second_order", new SecondOrderFunction());
        function("perlin_noise", new PerlinNoise());

        // 副作用：音效、粒子、键鼠与调试输出。取值恒为 null，在数值上下文里按 0 处理
        function("particle", new EmptyFunction());
        function("abs_particle", new EmptyFunction());
        function("play_sound", new EmptyFunction());
        function("stop_sound", new EmptyFunction());
        function("keyboard", new EmptyFunction());
        function("mouse", new EmptyFunction());

        var("head_yaw", ctx -> ctx.data().netHeadYaw);
        var("head_pitch", ctx -> ctx.data().headPitch);
        var("weather", ctx -> getWeather(ctx.level()));
        var("dimension_name", ctx -> ctx.level().dimension().location().toString());
        var("fps", ctx -> Minecraft.getInstance().getFps());

        entityVar("is_passenger", ctx -> ctx.entity().isPassenger());
        entityVar("is_sleep", ctx -> ctx.entity().getPose() == Pose.SLEEPING);
        entityVar("is_sneak", ctx -> ctx.entity().onGround() && ctx.entity().getPose() == Pose.CROUCHING);
        entityVar("is_open_air", ctx -> isOpenAir(ctx.entity()));
        entityVar("eye_in_water", ctx -> ctx.entity().isUnderWater());
        entityVar("frozen_ticks", ctx -> ctx.entity().getTicksFrozen());
        entityVar("air_supply", ctx -> ctx.entity().getAirSupply());

        livingEntityVar("has_helmet", ctx -> getSlotValue(ctx.entity(), EquipmentSlot.HEAD));
        livingEntityVar("has_chest_plate", ctx -> getSlotValue(ctx.entity(), EquipmentSlot.CHEST));
        livingEntityVar("has_leggings", ctx -> getSlotValue(ctx.entity(), EquipmentSlot.LEGS));
        livingEntityVar("has_boots", ctx -> getSlotValue(ctx.entity(), EquipmentSlot.FEET));
        livingEntityVar("has_mainhand", ctx -> getSlotValue(ctx.entity(), EquipmentSlot.MAINHAND));
        livingEntityVar("has_offhand", ctx -> getSlotValue(ctx.entity(), EquipmentSlot.OFFHAND));
        livingEntityVar("is_riptide", ctx -> ctx.entity().isAutoSpinAttack());
        livingEntityVar("armor_value", ctx -> ctx.entity().getArmorValue());
        livingEntityVar("hurt_time", ctx -> ctx.entity().hurtTime);
        livingEntityVar("on_ladder", ctx -> ctx.entity().onClimbable());
        livingEntityVar("arrow_count", ctx -> ctx.entity().getArrowCount());
        livingEntityVar("stinger_count", ctx -> ctx.entity().getStingerCount());

        livingEntityVar("swim_speed", ctx -> ctx.entity().getAttributeValue(NeoForgeMod.SWIM_SPEED));

        var("elytra_rot_x", ctx -> 0);
        var("elytra_rot_y", ctx -> 0);
        var("elytra_rot_z", ctx -> 0);
        var("texture_name", ctx -> StringUtils.EMPTY);
    }

    private static boolean getSlotValue(LivingEntity entity, EquipmentSlot slot) {
        return !entity.getItemBySlot(slot).isEmpty();
    }

    private static int getWeather(ClientLevel world) {
        if (world.isThundering()) {
            return 2;
        } else if (world.isRaining()) {
            return 1;
        }
        return 0;
    }

    private static boolean isOpenAir(Entity entity) {
        BlockPos blockpos = entity.blockPosition();
        if (!entity.level().canSeeSky(blockpos)) {
            return false;
        }
        return entity.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos).getY() <= blockpos.getY();
    }
}
