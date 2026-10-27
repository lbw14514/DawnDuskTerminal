package com.dawnduskterminal.item;

import com.dawnduskterminal.DawnDuskTerminal;
import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;

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
 * <p>全部按<b>类名反射</b>调用：仓库约定是「代码里只用字符串 ID 引用暮色森林，没有
 * {@code import twilightforest.*}，缺 jar 仍可编译」，反射守住了这条；缺 jar 或方法改名时整段静默降级成普通伤害
 */
public final class UrGhastPhaseSuppressor {
    private static final String URGHAST_CLASS = "twilightforest.entity.boss.UrGhast";
    private static boolean resolved;
    private static boolean available;
    private static Class<?> urGhastClass;
    private static Method setInTantrum;
    private static Method resetDamageUntilNextPhase;

    /**
     * 对恶魂压一次相位：退出 tantrum、把「进下一相位还差多少血」的预算刷回 18
     *
     * @return 真的对它动手了就是 true；不是恶魂、没装暮色、或钩子调用失败都是 false
     */
    public static boolean avoidTantrum(LivingEntity target) {
        resolve();
        if (!available || !urGhastClass.isInstance(target)) {
            return false;
        }

        try {
            setInTantrum.invoke(target, Boolean.FALSE);
            resetDamageUntilNextPhase.invoke(target);
            return true;
        } catch (ReflectiveOperationException | RuntimeException problem) {
            available = false;
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 暮色恶魂的相位钩子调用失败，试验物品 066 之后按普通伤害走：{}", problem.toString());
            return false;
        }
    }

    /** 相位钩子能不能用：测试与日志用 */
    public static boolean available() {
        resolve();
        return available;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            urGhastClass = Class.forName(URGHAST_CLASS);
            setInTantrum = urGhastClass.getMethod("setInTantrum", boolean.class);
            resetDamageUntilNextPhase = urGhastClass.getMethod("resetDamageUntilNextPhase");
            available = true;
        } catch (ClassNotFoundException notInstalled) {
            // 没装暮色森林：这本就对普通目标满额，什么都不用做
            available = false;
        } catch (NoSuchMethodException renamed) {
            available = false;
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 暮色恶魂的相位钩子找不到，试验物品 066 按普通伤害走：{}", renamed.toString());
        }
    }

    private UrGhastPhaseSuppressor() {}
}
