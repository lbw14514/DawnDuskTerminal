package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModCreativeTabs;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 创造栏的顺序：试验物品栏必须紧挨在主栏目后面
 *
 * <p>NeoForge 的 {@link CreativeModeTabRegistry} 给创造栏做拓扑排序，比较器是注册名的字母序，
 * 而且没写 {@code tabsBefore}/{@code tabsAfter} 的栏目都只挂一条「排在最后一个原版栏目之后」的边，
 * 所以光靠注册先后（也就是 {@code ModCreativeTabs} 里字段的声明顺序）并不能让两个栏目挨在一起，
 * 别的模组的栏目会按名字插进来。要挨着就必须显式声明边，这里把那条声明钉住
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class CreativeTabTests {
    @GameTest(template = "ddt_empty")
    public static void experimentTabSitsRightAfterMainTab(GameTestHelper helper) {
        CreativeModeTab main = ModCreativeTabs.MAIN.get();
        CreativeModeTab experiment = ModCreativeTabs.EXPERIMENT_ITEMS.get();
        helper.assertTrue(
                experiment.tabsBefore.contains(DawnDuskTerminal.id("main")),
                "experiment_items must declare dawnduskterminal:main as a tab before it, got " + experiment.tabsBefore);

        List<CreativeModeTab> sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs();
        int mainIndex = sorted.indexOf(main);
        int experimentIndex = sorted.indexOf(experiment);
        helper.assertTrue(
                mainIndex >= 0 && experimentIndex >= 0,
                "both tabs must appear in the sorted tab list, main=" + mainIndex + " experiment=" + experimentIndex);
        helper.assertTrue(
                experimentIndex == mainIndex + 1,
                "experiment_items must sit right after main, got " + mainIndex + " and " + experimentIndex + " in "
                        + sorted.stream().map(CreativeModeTabRegistry::getName).toList());
        helper.succeed();
    }

    private CreativeTabTests() {}
}
