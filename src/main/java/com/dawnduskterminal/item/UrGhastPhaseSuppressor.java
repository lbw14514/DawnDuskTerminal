package com.dawnduskterminal.item;

import net.minecraft.world.entity.LivingEntity;
import twilightforest.entity.boss.UrGhast;

/**
 * 把暮色恶魂（{@code twilightforest:ur_ghast}）从它的相位免伤里撬出来，只给试验物品 066 用
 *
 * <p>恶魂的免伤不在标签里，在 {@code UrGhast#hurt} 里：{@code if (isInTantrum()) amount /= 10}，
 * 而退出 tantrum 要靠「实扣掉的血」累计够 18 点——场上每 0.5 秒 7 点，打两下就攒满 18、进 tantrum，
 * 之后每下只剩 0.7，再退出要 26 下，等于永久锁死。方块自己（{@code GhastTrapBlockEntity#tickActive}）
 * 是每 tick 先 {@code setInTantrum(false)}、打完 {@code resetDamageUntilNextPhase()}（预算回 18），
 * 所以它那 7 点一直是满额。这里照做。
 *
 * <p>只碰相位预算，<b>不调 {@code setInTrap()}</b>：那一条会把 {@code inTrapCounter} 顶到 20，
 * 顺带在 {@code customServerAiStep} 里 {@code setTarget(null)}，等于把 BOSS 定住、清掉它的仇恨，
 * 那是另一档强度（强控），不在这个件里。
 *
 * <p>这两个方法在 {@code UrGhast} 上都是 {@code public}，因此直接引用类型调用，
 * 升级暮色森林时签名一变编译期就报错，而不是运行期悄悄降级
 */
public final class UrGhastPhaseSuppressor {
    /**
     * 对恶魂压一次相位：退出 tantrum、把「进下一相位还差多少血」的预算刷回 18
     *
     * @return 真的对它动手了就是 true；不是恶魂就是 false
     */
    public static boolean avoidTantrum(LivingEntity target) {
        if (!(target instanceof UrGhast urGhast)) {
            return false;
        }
        urGhast.setInTantrum(false);
        urGhast.resetDamageUntilNextPhase();
        return true;
    }

    private UrGhastPhaseSuppressor() {}
}
