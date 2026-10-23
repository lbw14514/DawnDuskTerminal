package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.inventory.Experiment004Menu;
import com.dawnduskterminal.inventory.Experiment051Menu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型。试验物品 004 的熔炉界面与 051 的拆解界面各一个
 *
 * <p>004 走原版的 {@code new MenuType(...)}：那套界面在客户端只拿一份空容器，内容与四个数据槽都靠同步下来，
 * 不需要方块坐标。051 靠坐标把方块实体找回来，坐标是服务端开界面时额外发的一段数据，原版工厂函数拿不到，
 * 所以用 {@link IMenuTypeExtension#create}
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<Experiment004Menu>> EXPERIMENT_004 =
        MENUS.register("experiment_004",
            () -> new MenuType<>(Experiment004Menu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<Experiment051Menu>> EXPERIMENT_051 =
        MENUS.register("experiment_051", () -> IMenuTypeExtension.create(Experiment051Menu::fromNetwork));

    private ModMenus() {}

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
