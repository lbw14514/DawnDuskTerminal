package com.dawnduskterminal.item;

import com.dawnduskterminal.network.PrismBeamPayload;
import com.dawnduskterminal.registry.ModSounds;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 棱镜权杖
 *
 * <p>右键打一条瞬时的视线射线：从眼睛出发沿视线一直到第一个方块或生物，什么都没有就是
 * {@value #MAX_RANGE} 格。主射线打中生物是 {@value #BEAM_DAMAGE} 点魔法伤害，打中方块或生物之后
 * 从命中点再摇 {@code 0~5} 条分射线，每条 {@value #SUB_RAY_DAMAGE} 点伤害、长度
 * {@value #SUB_RAY_MIN_LENGTH}~{@value #SUB_RAY_MAX_LENGTH} 格：每条有
 * {@value #SEEK_CHANCE} 的概率去瞄命中点 {@value #TARGET_RADIUS} 格内<b>还没被瞄过</b>的生物，
 * 剩下的情况随机朝向。没打中任何东西就不分叉
 *
 * <p>耐久每发扣 1 点（创造模式不扣），冷却固定 {@value #COOLDOWN_TICKS} tick，也就是 2 秒
 *
 * <p>外观分两段。射线上铺的粒子照暮色森林黄昏权杖的弹体拖尾来：那一支（{@code TwilightWandBolt#makeTrail}）
 * 每 tick 在弹体附近撒 5 颗 {@code TFParticleType.MAGIC_EFFECT}，贴图是原版 {@code effect_7..0}
 * 的十六帧小光点，颜色每颗现摇——红 {@code (0.5 到 1) × 0.17}、绿 {@code × 0.80}、蓝 {@code × 0.69}，
 * 于是偏青绿的一串。这里用同一条公式、同一套贴图、同样恒满亮的粒子（{@link com.dawnduskterminal.client.PrismTraceParticle}），
 * 区别只是按整条线段均匀铺开，而不是跟着一颗会飞的弹体走
 *
 * <p>终点那一团是「重锤粒子」：原版重锤砸地走 {@code levelEvent(2013)}，客户端
 * {@code ParticleUtils#spawnSmashAttackParticles} 撒的是带方块状态的 {@code ParticleTypes.DUST_PILLAR}，
 * 也就是脚下那块方块碎成的一团尘。这里照搬同样的粒子；打中的是生物、或者射线停在半空时没有方块可取，
 * 就改用「凝固的苍穹」的方块贴图兜底，免得 DUST_PILLAR 抓到空气贴图
 *
 * <p>粒子不走 {@code sendParticles} 一颗一包：一条主射线加五条分射线最多要几百颗，而且
 * {@code sendParticles} 只发给调用点 32 格内的玩家，打满 64 格时开枪的人自己看不到终点。服务端只发一个
 * {@link PrismBeamPayload}（起点、主射线终点、各分射线终点，外加各自终点尘团要用的方块状态 id），
 * 位置与颜色由各客户端自己算，见 {@link com.dawnduskterminal.client.PrismBeamParticles}；
 * 紫水晶那一声仍在服务端放，主射线那一下只在终点超出 {@value #SOUND_RADIUS} 格时另外给使用者补一发 notification
 */
public class PrismStaffItem extends Item {
    /** 耐久 */
    public static final int DURABILITY = 128;

    public static final int COOLDOWN_TICKS = 30;

    /** 发射音的随机音高幅度：正负这个数，与暮色森林权杖那一声同一个做法 */
    public static final float LASER_PITCH_JITTER = 0.2F;

    /** 主射线伤害 */
    public static final float BEAM_DAMAGE = 8.0F;

    /** 分射线伤害 */
    public static final float SUB_RAY_DAMAGE = 6.0F;

    /** 什么都没打中时的射程 */
    public static final double MAX_RANGE = 64.0D;

    /** 分射线条数上限，实际条数是 {@code 0..MAX_SUB_RAYS} */
    public static final int MAX_SUB_RAYS = 5;

    /** 分射线找人时，以命中点为心搜多远 */
    public static final double TARGET_RADIUS = 8.0D;

    /** 分射线长度下限 */
    public static final double SUB_RAY_MIN_LENGTH = 8.0D;

    /** 分射线长度上限 */
    public static final double SUB_RAY_MAX_LENGTH = 16.0D;

    /** 分射线去找目标的概率，剩下的随机朝向 */
    public static final float SEEK_CHANCE = 0.7F;

    /** 终点粒子包发多远：主射线 64 格再加最多 16 格分射线，留一圈余量 */
    public static final double PARTICLE_RADIUS = 96.0D;

    /**
     * 原版世界音效的传播半径：{@code Level#playSound} 只送给这个距离内的玩家
     *
     * <p>主射线终点在这一圈里时开枪的人已经听到了世界那一声，不用再补 {@code notification}，
     * 否则 16 格内会同时收到两条（世界音 + 只发给自己的音），叠成一团
     */
    private static final double SOUND_RADIUS = 16.0D;

    /** 实体判定用的容差：与投掷物一样把包围盒胀开一点，擦边也算命中 */
    private static final double HIT_MARGIN = 0.3D;

    /** 分射线起点从命中点沿方向让开的距离，见 {@link #split} */
    private static final double SUB_RAY_START_OFFSET = 0.1D;

    public PrismStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            fire(server, player);
            if (!player.hasInfiniteMaterials()) {
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        // 两端都上冷却：客户端那一份让快捷栏立刻变灰，服务端那一份才是真的挡住连点
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(
                Component.translatable("item.dawnduskterminal.prism_staff.desc").withStyle(ChatFormatting.GRAY));
    }

    /** 打一发：主射线、终点效果、分叉、最后把粒子包的参数发出去 */
    private static void fire(ServerLevel level, Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 to = from.add(look.scale(MAX_RANGE));

        // 每发的音高现摇：暮色森林权杖那一声也是 (rand − rand) × 0.2 + 1，连打几发不会像一段循环采样
        RandomSource random = level.getRandom();
        float pitch = 1.0F + (random.nextFloat() - random.nextFloat()) * LASER_PITCH_JITTER;
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.PRISM_STAFF_LASER.get(),
                SoundSource.PLAYERS,
                1.0F,
                pitch);

        RayHit hit = trace(level, player, from, to, null);
        boolean connected = hit.entity() != null || hit.block() != null;
        if (hit.entity() != null) {
            hit.entity().hurt(level.damageSources().indirectMagic(player, player), BEAM_DAMAGE);
        }
        impactSound(level, hit.end(), player, 1.0F, from.distanceTo(hit.end()) > SOUND_RADIUS);
        PrismBeamPayload.Ray main = new PrismBeamPayload.Ray(hit.end(), particleStateId(hit.block()));

        List<PrismBeamPayload.Ray> splits = connected ? split(level, player, hit.end(), hit.entity()) : List.of();
        PacketDistributor.sendToPlayersNear(
                level,
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                PARTICLE_RADIUS,
                new PrismBeamPayload(from, main, splits));
    }

    /**
     * 从命中点摇分射线
     *
     * <p>目标池在摇之前就取好：命中点 {@value #TARGET_RADIUS} 格内、除自己与主目标之外的所有活物。
     * 每次真去瞄一个就从池里拿走一个，所以「尚未被瞄准」是天然的——同一只生物不会挨两条
     *
     * <p>70% 要瞄、但池子已经空了时落到随机朝向：规则是「有得瞄就去瞄」，不是「没得瞄就少一条」
     */
    private static List<PrismBeamPayload.Ray> split(
            ServerLevel level, Player player, Vec3 impact, @Nullable LivingEntity mainTarget) {
        RandomSource random = level.getRandom();
        int count = random.nextInt(MAX_SUB_RAYS + 1);
        if (count == 0) {
            return List.of();
        }
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(impact, impact).inflate(TARGET_RADIUS),
                candidate -> candidate != player
                        && candidate != mainTarget
                        && candidate.isAlive()
                        && !candidate.isSpectator()));
        List<PrismBeamPayload.Ray> rays = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vec3 direction;
            if (random.nextFloat() < SEEK_CHANCE && !candidates.isEmpty()) {
                LivingEntity target = candidates.remove(random.nextInt(candidates.size()));
                direction = target.getBoundingBox().getCenter().subtract(impact).normalize();
            } else {
                direction = randomDirection(random);
            }
            double length = SUB_RAY_MIN_LENGTH + random.nextDouble() * (SUB_RAY_MAX_LENGTH - SUB_RAY_MIN_LENGTH);
            // 起点沿方向让出一点点：命中点正好贴在主目标（或方块）的表面上，从那个点起算会把
            // 主目标自己或身后那块方块当成第一个命中，分射线就出不去了
            Vec3 start = impact.add(direction.scale(SUB_RAY_START_OFFSET));
            RayHit hit = trace(level, player, start, start.add(direction.scale(length)), mainTarget);
            if (hit.entity() != null) {
                hit.entity().hurt(level.damageSources().indirectMagic(player, player), SUB_RAY_DAMAGE);
            }
            // 分叉的终点也炸一团尘、也响一声，音量压一半：五条一起落地时不至于盖过主射线；
            // 那一声只发给附近的人，使用者那一侧不补 notification——五条一起响会叠成一团
            impactSound(level, hit.end(), player, 0.5F, false);
            rays.add(new PrismBeamPayload.Ray(hit.end(), particleStateId(hit.block())));
        }
        return rays;
    }

    /**
     * 一条射线的命中结果
     *
     * @param end 射线实际停下的位置：命中点、或者 {@code to}
     * @param entity 命中的生物，打中方块或什么都没打中时为 {@code null}
     * @param block 命中的方块状态，没打中方块（含打中生物）时为 {@code null}
     */
    private record RayHit(Vec3 end, @Nullable LivingEntity entity, @Nullable BlockState block) {}

    /**
     * 先夹方块再夹生物：方块射线只走到方块命中点为止，生物若在那之前就被打中，
     * 说明它在方块前面——这正是「第一个方块或者生物」的先后
     *
     * <p>分射线要传 {@code excluded = 主目标}：它们从主目标的表面出发，不放掉就会一出门就撞回主目标，
     * 既重复扣血又永远打不到瞄好的那只
     */
    private static RayHit trace(ServerLevel level, Player caster, Vec3 from, Vec3 to, @Nullable LivingEntity excluded) {
        BlockHitResult blockHit =
                level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        EntityHit entityHit = nearestEntity(level, caster, from, stop, excluded);
        if (entityHit != null) {
            return new RayHit(entityHit.position(), entityHit.entity(), null);
        }
        if (blockHit.getType() == HitResult.Type.MISS) {
            return new RayHit(stop, null, null);
        }
        return new RayHit(stop, null, level.getBlockState(blockHit.getBlockPos()));
    }

    private record EntityHit(LivingEntity entity, Vec3 position) {}

    /** 线段上最近的生物：包围盒胀开 {@value #HIT_MARGIN} 后逐个裁，取离起点最近的那个 */
    @Nullable
    private static EntityHit nearestEntity(
            ServerLevel level, Player caster, Vec3 from, Vec3 to, @Nullable LivingEntity excluded) {
        AABB search = new AABB(from, to).inflate(HIT_MARGIN + 1.0D);
        EntityHit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(
                caster,
                search,
                candidate -> candidate instanceof LivingEntity living
                        && living != excluded
                        && living.isAlive()
                        && living.isPickable()
                        && !living.isSpectator())) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(HIT_MARGIN).clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new EntityHit((LivingEntity) entity, hit.get());
            }
        }
        return best;
    }

    /**
     * 终点那一声紫水晶，外加终点尘团要用的方块状态
     *
     * <p>尘团照重锤砸地的 {@code DUST_PILLAR}：带方块状态的尘。没有方块可取（打中生物、停在半空）、
     * 或者那块方块根本不渲染贴图（屏障、光源这类 {@code RenderShape.INVISIBLE}）时换成
     * {@link ModTerrainBlocks#FROZEN_CANOPY}，否则 DUST_PILLAR 会去抓空气贴图。只发状态 id，
     * 粒子由客户端照 id 还原后自己撒，见 {@link com.dawnduskterminal.client.PrismBeamParticles}
     */
    private static int particleStateId(@Nullable BlockState hitState) {
        BlockState state = hitState;
        if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            state = ModTerrainBlocks.FROZEN_CANOPY.get().defaultBlockState();
        }
        return Block.getId(state);
    }

    /**
     * 终点那一声：世界音效只走原版的 {@value #SOUND_RADIUS} 格传播半径，打满 64 格时开枪的人自己听不到，
     * 所以主射线那一下在超出这个半径时再单独给使用者补一声（{@code notification}，只发给这一个人）；
     * 半径以内世界那一声已经送到，不再补，免得两条叠在一起
     */
    private static void impactSound(ServerLevel level, Vec3 pos, Player caster, float volume, boolean notifyCaster) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, volume, 1.0F);
        if (notifyCaster && caster instanceof ServerPlayer serverPlayer) {
            serverPlayer.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, volume, 1.0F);
        }
    }

    /** 球面上均匀的随机单位向量：三轴高斯再归一化，与试验物品 055 那条射线同一个做法 */
    private static Vec3 randomDirection(RandomSource random) {
        double x = random.nextGaussian();
        double y = random.nextGaussian();
        double z = random.nextGaussian();
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-4D) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }
        return new Vec3(x / length, y / length, z / length);
    }
}
