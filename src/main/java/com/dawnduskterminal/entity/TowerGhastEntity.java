package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 高塔恶魂：模型、贴图与动画都来自为 Yes Steve Model 制作的那套 Bedrock 文件
 *
 * <h2>动画</h2>
 * 模型包按 YSM 的原样布局放在 {@code assets/dawnduskterminal/ysm/tower_ghast/} 下，运行时解析，
 * 不做任何离线转换。动画文件里的 Molang 表达式由 {@code client/animation} 那套运行时逐帧求值：
 * 其中 {@code ysm.first_order} / {@code ysm.second_order} 是按名字记忆状态的递归滤波器，
 * {@code ysm.bone_rot} 读上一帧的骨骼变换，{@code timeline} 是一段每帧执行的语句程序——
 * 这三类在无状态的环境里都复现不出来，也正是布条、袖口与发丝那点次级运动的全部来源。
 *
 * <h2>分层</h2>
 * YSM 同时播放一个主动画加最多八个 {@code pre_parallel} 与八个 {@code parallel} 片段。注册顺序
 * 就是语义（见 {@code client/animation/YsmAnimatable}）：{@code pre_parallel} 最先、主动画居中、
 * {@code parallel} 最后，最后那一批是<strong>叠加</strong>在主动画之上的加法混合。
 *
 * <p>主动画的选择在这里：死亡压过受击，受击压过移动，与 YSM 的宿主状态机一致
 * （它把死亡放在 {@code Priority.HIGHEST}、受击放在 2，都高于基础移动）。
 *
 * @see com.dawnduskterminal.client.TowerGhastRenderer
 */
public class TowerGhastEntity extends PathfinderMob {
    public TowerGhastEntity(EntityType<? extends TowerGhastEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.23D)
            .add(Attributes.FOLLOW_RANGE, 16.0D)
            .add(Attributes.STEP_HEIGHT, 0.6D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // AvoidEntityGoal 的泛型上限是 PathfinderMob，而伤害只可能来自玩家，所以只值得躲玩家
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /**
     * 主动画的选择，抽成静态方法是为了能直接测：<strong>顺序就是优先级</strong>
     *
     * <p>死亡与受击都要压过移动，与 YSM 的状态机一致（它把死亡放在 {@code Priority.HIGHEST}、
     * 受击放在 2，都高于基础移动）
     *
     * @return 片段名，交给 {@code client/TowerGhastRenderer} 注册的主动画控制器
     */
    public static String mainAnimation(TowerGhastEntity entity, boolean moving) {
        if (entity.isDeadOrDying()) {
            return "death";
        }
        if (entity.hurtTime > 0) {
            return "attacked";
        }
        if (!moving) {
            return "idle";
        }
        // YSM 普通移动用 walk，冲刺用 run，这里能拿到的信号只有实体自身水平速度，
        // 就用它来代替那个判断
        return entity.getDeltaMovement().horizontalDistance() > 0.12D ? "run" : "walk";
    }

    /**
     * 待机与受击用模型包自带的配音
     *
     * <p>模型包的 {@code sounds/} 里有 7 条待机（{@code id1-7}）与 5 条受击（{@code bad1-5}），
     * 变体交给 {@code sounds.json} 的同一个事件随机挑，所以这里只报事件名
     *
     * <p>死亡没有对应配音（包里那个 {@code attack.ogg} 是攻击用的，而这只生物没有攻击行为），
     * 所以死亡仍用原版恶魂的
     *
     * <p>不再覆写 {@code getVoicePitch}：那是为了让原版恶魂音效听起来低一点，换成真正的语音之后
     * 半速播放只会把台词拉长
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.TOWER_GHAST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.TOWER_GHAST_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    /** 像命名过的生物一样留在世界里，免得用来测试的对象悄悄消失 */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
