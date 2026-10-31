package com.dawnduskterminal.gametest;

import com.dawnduskterminal.client.YsmRuntimeContract;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 运行期损失清单的内容，以及「声明之后自检就通过」这条约定
 *
 * <p>清单由 {@code tools/ysm/runtime_contract.py} 从源动画生成，这里把那 14 条 perlin 通道钉死：
 * 生成器的判定规则一旦变化（比如把「源里是动态值」认成了常量），这条断言就会先失败，
 * 而不是等到游戏里发现触手不摆。第二项断言的是自检本身——没有任何声明的清单必须报满 14 条，
 * 否则「漏接物理层」就会被静默放过
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class YsmRuntimeContractTests {
    /** 大恶魂 {@code parallel0} 的 perlin：两条触手的偏航与滚转 + 五条腿的俯仰与滚转 */
    private static final Set<String> ELITE_PERLIN = Set.of(
        "lefttentacle#1", "lefttentacle#2",
        "righttentacle#1", "righttentacle#2",
        "leg_M#0", "leg_M#2",
        "leg_F_L#0", "leg_F_L#2",
        "leg_F_R#0", "leg_F_R#2",
        "leg_B_L#0", "leg_B_L#2",
        "leg_B_R#0", "leg_B_R#2");

    @GameTest(template = "ddt_empty")
    public static void eliteContractListsThePerlinChannels(GameTestHelper helper) {
        YsmRuntimeContract contract = YsmRuntimeContract.load("ghast_elite");

        Set<String> channels = new HashSet<>();
        for (YsmRuntimeContract.Channel channel : contract.poseChannels()) {
            channels.add(channel.bone() + "#" + channel.axis());
            helper.assertTrue(channel.clip().equals("parallel0"),
                "大恶魂只有 parallel0 在播放，清单里不该出现 " + channel.clip());
            helper.assertTrue(channel.expression().contains("perlin_noise"),
                "被归零的通道应当都是 perlin，实际是 " + channel.expression());
        }
        helper.assertTrue(channels.equals(ELITE_PERLIN),
            "清单里的通道与源动画不一致：" + channels);

        helper.assertTrue(contract.skippedClips().containsKey("parallel1"),
            "parallel1 没有播放，必须在清单里显式声明");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void declaringEveryChannelClearsTheAudit(GameTestHelper helper) {
        List<YsmRuntimeContract.Channel> channels = YsmRuntimeContract.load("ghast_elite").poseChannels();
        helper.assertTrue(channels.size() == ELITE_PERLIN.size(),
            "清单条目应当与断言表一致，实际 " + channels.size());

        YsmRuntimeContract undeclared = YsmRuntimeContract.load("ghast_elite");
        helper.assertTrue(undeclared.missing().size() == channels.size(),
            "没有声明时每条通道都应当算缺失");

        undeclared.declare("lefttentacle", 0);
        helper.assertTrue(undeclared.missing().size() == channels.size(),
            "声明一条源动画里没有动态值的通道不该清掉任何缺失项");

        YsmRuntimeContract declared = YsmRuntimeContract.load("ghast_elite");
        for (YsmRuntimeContract.Channel channel : channels) {
            declared.declare(channel.bone(), channel.axis());
        }
        helper.assertTrue(declared.missing().isEmpty(),
            "全部声明之后不该还有缺失项：" + declared.missing());
        helper.succeed();
    }
}
