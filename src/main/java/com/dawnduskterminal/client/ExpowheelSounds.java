package com.dawnduskterminal.client;

import com.dawnduskterminal.entity.ExpowheelEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 博览轮舞那片飞轮的持续音：客户端挂在弹体身上的定位音
 *
 * <p>不是「发射那一刻在发射者脚下播一遍」。{@code expowheel_ambient} 长 9.98 秒，弹体只活 6 秒，
 * 如果按 {@code Level#playSound} 在出生点播，声音就钉在发射者的眼睛上，飞轮飞走了它还在原地响——
 * 听感上是「自己的身上在响」。
 *
 * <p>所以改用 {@link ExpowheelSoundInstance}（{@code EntityBoundSoundInstance} 的子类）：位置每帧跟到
 * 弹体身上，按 16 格线性衰减，最后 1 秒把音量收到 0 再结束，既不会钉在原地，也不会到点硬切。
 * 弹体在客户端一进世界（{@link EntityJoinLevelEvent}，包括随区块载入）就起一支，一支就够——
 * 素材比寿命长，不用接下一遍。
 */
public final class ExpowheelSounds {
    /** 已经起过的实例，只为断开连接时能一起收掉；弹体自己消失的会在下一次检查里摘掉 */
    private static final List<ExpowheelSoundInstance> PLAYING = new ArrayList<>();

    private ExpowheelSounds() {}

    /** 弹体进入客户端世界：起一支跟着它走的持续音 */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide || !(event.getEntity() instanceof ExpowheelEntity wheel)) {
            return;
        }
        pruneStopped();
        ExpowheelSoundInstance instance = new ExpowheelSoundInstance(wheel);
        PLAYING.add(instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    /** 断开连接时整批收声：换存档之后不会再有谁来收这一批 */
    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (ExpowheelSoundInstance instance : PLAYING) {
            minecraft.getSoundManager().stop(instance);
        }
        PLAYING.clear();
    }

    private static void pruneStopped() {
        Iterator<ExpowheelSoundInstance> iterator = PLAYING.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isStopped()) {
                iterator.remove();
            }
        }
    }
}
