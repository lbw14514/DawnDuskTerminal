package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 主动画的选择顺序
 *
 * <p>死亡要压过受击，受击要压过移动。这套模型包自带 {@code attacked} 与 {@code death} 两个片段，
 * 而 YSM 的宿主状态机也是这么分的优先级；顺序写错的话受击时会继续播 idle，死亡时会用 GeckoLib 那套
 * 通用倒地
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class TowerGhastAnimationTests {
    @GameTest(template = "ddt_empty")
    public static void deathBeatsHurtBeatsMoving(GameTestHelper helper) {
        Level level = helper.getLevel();
        TowerGhastEntity ghast = new TowerGhastEntity(ModEntities.TOWER_GHAST.get(), level);

        helper.assertTrue(clipOf(ghast, true).equals("walk") || clipOf(ghast, true).equals("run"),
            "a healthy moving ghast must use walk or run");
        helper.assertTrue(clipOf(ghast, false).equals("idle"),
            "a healthy still ghast must idle");

        ghast.hurtTime = 5;
        helper.assertTrue(clipOf(ghast, true).equals("attacked"),
            "a hurt ghast must use attacked even while moving");

        ghast.setHealth(0.0F);
        helper.assertTrue(clipOf(ghast, true).equals("death"),
            "a dying ghast must use death even while hurt");
        helper.succeed();
    }

    private static String clipOf(TowerGhastEntity ghast, boolean moving) {
        return TowerGhastEntity.mainAnimation(ghast, moving);
    }
}
