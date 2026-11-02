package com.dawnduskterminal.client;

import com.dawnduskterminal.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * 客户端这边「谁在放哪支曲子」的那张表：一个施放者一支
 *
 * <p>服务端只在两处发话（见 {@code network/RequiemStartPayload} 与 {@code network/RequiemStopPayload}）：
 * 开始按住时发一次「第几支」，之后每秒重发一次同样的包（让这一秒里才走进 32 格的人也听得见），
 * 松手时发一次停止。所以这里的启动必须是幂等的——同一个施放者已经有一支在放，就不再起第二支，
 * 否则每秒都会叠一层，几秒之后音量就炸了
 *
 * <p>表按实体 id 认人：同一个客户端附近可以有好几个人同时按住 095，互相之间不能串
 */
public final class RequiemSounds {
    private static final Map<Integer, RequiemSoundInstance> ACTIVE = new HashMap<>();

    private RequiemSounds() {}

    /** 起一支；同一个施放者已经有一支在响就什么都不做 */
    public static void start(int casterId, int track) {
        RequiemSoundInstance existing = ACTIVE.get(casterId);
        if (existing != null && !existing.isStopped()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity caster = level.getEntity(casterId);
        if (caster == null) {
            return;
        }
        SoundEvent event = ModSounds.requiemTrack(track);
        if (event == null) {
            return;
        }
        if (existing != null) {
            // 上一支已经自己收声了，先摘掉再换新的，表里不留半死不活的东西
            existing.end();
            ACTIVE.remove(casterId);
        }
        RequiemSoundInstance instance = new RequiemSoundInstance(event, caster);
        ACTIVE.put(casterId, instance);
        // 走 queueTickingSound：万一以后有谁在声引擎遍历 tickingSounds 的那一拍里起音
        // （055 的接下一遍就是这样），play 会把这一支加进同一个 List，直接 ConcurrentModificationException
        minecraft.getSoundManager().queueTickingSound(instance);
    }

    /** 收掉某个施放者那一支 */
    public static void stop(int casterId) {
        RequiemSoundInstance instance = ACTIVE.remove(casterId);
        if (instance != null) {
            instance.end();
        }
    }

    /** 断开连接时整批清掉：换存档之后不会再有哪一方来发停止包 */
    public static void clear() {
        ACTIVE.values().forEach(RequiemSoundInstance::end);
        ACTIVE.clear();
    }
}
