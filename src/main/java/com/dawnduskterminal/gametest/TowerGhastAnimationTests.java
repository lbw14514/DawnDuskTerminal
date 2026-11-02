package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.YsmPackUser;
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

        helper.assertTrue(
                clipOf(ghast, true).equals("walk") || clipOf(ghast, true).equals("run"),
                "a healthy moving ghast must use walk or run");
        helper.assertTrue(clipOf(ghast, false).equals("idle"), "a healthy still ghast must idle");

        ghast.hurtTime = 5;
        helper.assertTrue(clipOf(ghast, true).equals("attacked"), "a hurt ghast must use attacked even while moving");

        ghast.setHealth(0.0F);
        helper.assertTrue(clipOf(ghast, true).equals("death"), "a dying ghast must use death even while hurt");
        helper.succeed();
    }

    /**
     * 外观开关的同步格式：{@code hat=1.0;cloth=2.0}，同名键覆盖、每次修改递增序号
     *
     * <p>格式一旦变了客户端就解析不出来（表现为「命令没反应」），所以在这里钉住。
     */
    @GameTest(template = "ddt_empty")
    public static void roamingBlobRoundTrips(GameTestHelper helper) {
        TowerGhastEntity ghast = new TowerGhastEntity(ModEntities.TOWER_GHAST.get(), helper.getLevel());
        checkRoaming(helper, ghast);
        // 同一套同步通道被所有模型包实体共用，大恶魂也要能走通
        checkRoaming(helper, new GhastEliteEntity(ModEntities.GHAST_ELITE.get(), helper.getLevel()));
        helper.succeed();
    }

    private static void checkRoaming(GameTestHelper helper, YsmPackUser ghast) {
        ghast.setRoaming("hat", 1);
        ghast.setRoaming("cloth", 2);
        helper.assertTrue("hat=1.0;cloth=2.0".equals(ghast.roamingBlob()), "开关应累积：" + ghast.roamingBlob());
        helper.assertTrue(ghast.roamingSeq() == 2, "每次修改都要递增序号");
        ghast.setRoaming("hat", 0);
        helper.assertTrue("hat=0.0;cloth=2.0".equals(ghast.roamingBlob()), "同名键应覆盖：" + ghast.roamingBlob());
    }

    private static String clipOf(TowerGhastEntity ghast, boolean moving) {
        return TowerGhastEntity.mainAnimation(ghast, moving);
    }
}
