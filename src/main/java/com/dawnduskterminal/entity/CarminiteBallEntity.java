package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModSounds;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 砷铅铁反应权杖发射的弹体
 *
 * <p>用 {@link AbstractArrow} 而不是自写一个弹射实体：重力 空气阻力 命中判定 归因都在父类里，
 * 这里只需要决定「命中之后做什么」。父类的 leftOwner 判定保证它不会在第一帧打中发射者
 *
 * <p>命中后留下一个 {@link CarminiteFieldEntity}，伤害与边缘粒子都由那个实体接管。源 mod 生成的是
 * 一只无 AI 飞行生物，这里换成普通 Entity，理由见 {@link CarminiteFieldEntity}
 *
 * <p>附魔都在命中那一帧从 {@link #getWeaponItem()} 读：父类在构造时已经把权杖复制了一份。
 * 穿透由父类构造时按武器自动写入，这里只管「有穿透就不销毁」与「同一只生物只打一次」。
 * 多重射击按源 mod 的数值分裂出若干颗小子弹，小子弹没有武器来源，所以附魔等级全是 0
 */
public class CarminiteBallEntity extends AbstractArrow implements ItemSupplier {
    /** 弹体基础伤害，命中伤害是 ceil(速度 × 它)，源 mod 是 8 */
    private static final double BASE_DAMAGE = 8.0D;
    /** 分裂弹的基础伤害，源 mod 是 4 */
    private static final double SPLIT_BASE_DAMAGE = 4.0D;
    /** 分裂弹的速度，源 mod 是 0.3 */
    private static final float SPLIT_SPEED = 0.3F;
    /** 多重射击每级最多分裂几颗，源 mod 是 rand(0, 6 × 等级) 含两端 */
    private static final int MULTISHOT_MAX = 6;
    /**
     * 附魔等级上限。原版数据里就是这几个数，但命令或数据包可以给更高等级，
     * 一颗弹分裂上千颗会把服务端刻直接拖死，所以这里按原版上限截断
     */
    private static final int MAX_POWER = 5;

    private static final int MAX_FLAME = 1;
    private static final int MAX_PUNCH = 2;
    private static final int MAX_MULTISHOT = 1;
    /** 粒子的挂点比实体位置高一点，源 mod 用的是 y + 0.2 */
    private static final double PARTICLE_Y_OFFSET = 0.2D;
    /** 弹体本体粒子每多少 tick 出一次，拖尾是每 tick 都出 */
    private static final long BALL_PARTICLE_PERIOD = 7L;
    /** 每 tick 抵消掉一部分重力，源 mod 的飞行过程就是给 yd 加这个数 */
    private static final double LIFT_PER_TICK = 0.04D;

    /** 穿透时同一只生物只能吃一次，父类的私有集合只有在调 super.onHitEntity 时才会更新 */
    private final Set<Integer> piercedEntities = new HashSet<>();

    public CarminiteBallEntity(EntityType<? extends CarminiteBallEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setBaseDamage(BASE_DAMAGE);
    }

    public CarminiteBallEntity(Level level, LivingEntity owner, @Nullable ItemStack firedFromWeapon) {
        // 第四个参数是父类要写进 NBT 的「拾取物」栈，不能传空栈：AbstractArrow#addAdditionalSaveData 用
        // ItemStack.CODEC 写它，空栈会直接抛 Cannot encode empty ItemStack，存档时整只弹体丢掉
        super(
                ModEntities.CARMINITE_BALL.get(),
                owner,
                level,
                new ItemStack(ModItems.CARMINITE_BALL.get()),
                firedFromWeapon);
        this.pickup = Pickup.DISALLOWED;
        this.setBaseDamage(BASE_DAMAGE);
        this.setSoundEvent(ModSounds.CARMINITE_FIELD.get());
    }

    /** 读某个附魔的等级，没有武器来源或没附魔时是 0 */
    public static int enchantmentLevel(@Nullable ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        for (var entry : stack.getTagEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /** 渲染器（{@code ThrownItemRenderer}）要从这里拿外观。源 mod 的显示物品带附魔光效 */
    @Override
    public ItemStack getItem() {
        ItemStack stack = new ItemStack(ModItems.CARMINITE_BALL.get());
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.CARMINITE_BALL.get());
    }

    @Override
    public void tick() {
        super.tick();
        this.applyLift();
        if (this.level().isClientSide) {
            double y = this.getY() + PARTICLE_Y_OFFSET;
            this.level().addParticle(ModParticles.CARMINITE_TRACE.get(), this.getX(), y, this.getZ(), 0.0D, 0.0D, 0.0D);
            if (this.level().getGameTime() % BALL_PARTICLE_PERIOD == 0L) {
                this.level()
                        .addParticle(ModParticles.CARMINITE_BALL.get(), this.getX(), y, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 不在液体里时抹掉大部分重力，弹体会缓慢下滑而不是直接砸下去，源 mod 同款 */
    private void applyLift() {
        if (this.level().getBlockState(this.blockPosition()).getBlock() instanceof LiquidBlock) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, motion.y + LIFT_PER_TICK, motion.z);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !this.piercedEntities.contains(target.getId());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (!this.level().isClientSide) {
            double speed = this.getDeltaMovement().length();
            int damage = Mth.ceil(Mth.clamp(speed * this.getBaseDamage(), 0.0D, Integer.MAX_VALUE));
            hit.hurt(this.fieldDamageSource(), (float) damage);
            if (this.getPierceLevel() > 0) {
                this.piercedEntities.add(hit.getId());
            }
        }
        // 穿透不销毁弹体，方块命中仍然销毁（源 mod 两条路径就是不一致）
        this.detonate(hit, this.getPierceLevel() <= 0);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        this.detonate(null, true);
    }

    /**
     * 命中点上方一格生成反应场，按多重射击甩出分裂弹，然后按需消失
     *
     * @param hitEntity 被命中的实体，方块命中时是 null。分裂弹的发射者用它，和源 mod 一致
     * @param discard   是否销毁弹体
     */
    private void detonate(@Nullable Entity hitEntity, boolean discard) {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        Level level = this.level();
        LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
        if (level instanceof ServerLevel server) {
            server.sendParticles(
                    ModParticles.CARMINITE_BALL.get(),
                    this.getX(),
                    this.getY() + PARTICLE_Y_OFFSET,
                    this.getZ(),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
        CarminiteFieldEntity.spawn(
                level,
                this.getX(),
                this.getY() + 1.0D,
                this.getZ(),
                owner,
                this.enchantment(Enchantments.POWER, MAX_POWER),
                this.enchantment(Enchantments.FLAME, MAX_FLAME),
                this.enchantment(Enchantments.PUNCH, MAX_PUNCH));
        this.spawnSplitBalls(hitEntity instanceof LivingEntity living ? living : owner);
        level.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                ModSounds.CARMINITE_FIELD.get(),
                SoundSource.NEUTRAL,
                1.5F,
                1.0F);
        if (discard) {
            this.discard();
        }
    }

    private void spawnSplitBalls(@Nullable LivingEntity owner) {
        int multishot = this.enchantment(Enchantments.MULTISHOT, MAX_MULTISHOT);
        if (multishot <= 0 || owner == null) {
            return;
        }
        int count = Mth.nextInt(this.random, 0, MULTISHOT_MAX * multishot);
        for (int i = 0; i < count; i++) {
            // 没有武器来源：父类不能传空栈，只能传 null，分裂弹因此不带任何附魔
            CarminiteBallEntity split = new CarminiteBallEntity(this.level(), owner, null);
            split.setPos(this.getX(), this.getY() + 1.0D, this.getZ());
            split.setBaseDamage(SPLIT_BASE_DAMAGE);
            split.setSilent(true);
            split.shoot(
                    Mth.nextDouble(this.random, -1.0D, 1.0D),
                    Mth.nextDouble(this.random, 0.0D, 1.0D),
                    Mth.nextDouble(this.random, -1.0D, 1.0D),
                    SPLIT_SPEED,
                    0.0F);
            this.level().addFreshEntity(split);
        }
    }

    /** 读附魔等级并截到原版上限 */
    private int enchantment(ResourceKey<Enchantment> enchantment, int max) {
        return Math.min(max, enchantmentLevel(this.getWeaponItem(), enchantment));
    }

    private DamageSource fieldDamageSource() {
        return this.damageSources().source(ModDamageTypes.CARMINITE_FIELD, this.getOwner(), this);
    }
}
