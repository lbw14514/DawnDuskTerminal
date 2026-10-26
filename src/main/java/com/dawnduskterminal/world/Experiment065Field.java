package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 试验物品 065 那一台反建器压出来的场：范围里打不出伤害
 *
 * <p>场是 17×17×17 的方块立方（{@link CubeField}），方块自己就是中心那一格。判定看的是<b>伤害由谁打出</b>，
 * 也就是 {@link DamageSource#getDirectEntity()}：那个实体在场里，这一下就没有伤害；场外打进来的照常生效。
 * 近战里「打出伤害的那个实体」就是攻击者本人，射弹里是那颗弹体本身——所以场里射出去的箭飞出场以后照常伤人，
 * 场外射进来的箭飞到人身上时已经在场里、反而打不动，这条是「攻击源自范围内」这句话的直接后果，规格由
 * {@code gametest/Experiment065Tests} 钉住
 *
 * <p>环境伤害不拦：摔落、火焰、虚空、仙人掌、饥饿、窒息这些伤害的来源上没有实体
 * （{@code getDirectEntity()} 与 {@code getEntity()} 都是空），场对它们不做任何判定
 */
public final class Experiment065Field {
    /** 这台机器的场，场心认 {@code dawnduskterminal:experiment_065} */
    private static final CubeField FIELD = CubeField.of(() -> ModBlocks.EXPERIMENT_065.get());

    private Experiment065Field() {}

    /** 登记一个场心：方块实体装载、方块刚摆下时各来一次 */
    public static void track(Level level, BlockPos pos) {
        FIELD.track(level, pos);
    }

    /** 撤掉一个场心：方块实体卸载、方块被挖掉或推走时各来一次 */
    public static void untrack(Level level, BlockPos pos) {
        FIELD.untrack(level, pos);
    }

    /** 服务器停下：整张表清干净 */
    public static void clear() {
        FIELD.clear();
    }

    /**
     * 伤害事件：打人的那个实体在场里就把这一下取消掉
     *
     * <p>只认 {@link LivingIncomingDamageEvent}：它在 {@code LivingEntity#hurt} 里排在无敌帧判定之后、
     * 一切减伤之前，取消掉等于这一下从来没发生过——没有伤害、没有击退、没有受击音、不消耗护盾耐久、
     * 也不进无敌帧。客户端那份 {@code hurt} 也会发这个事件，所以先挡掉非服务端
     */
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = damagingEntity(event.getSource());
        if (attacker == null) {
            // 环境伤害：来源上没有实体，场不管
            return;
        }
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        if (FIELD.covers(level, attacker.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /**
     * 这一下伤害「由谁打出」：有直接实体就是它（近战是攻击者，射弹是弹体，爆炸是那颗 tnt / 火球），
     * 没有直接实体才退到间接实体（也就是主人）
     */
    private static Entity damagingEntity(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct != null ? direct : source.getEntity();
    }
}
