package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 药水。目前只有一瓶「稳定」，见 {@code docs/设定.md}：喝下之后身体被稳定在这个世界，足以抵抗不稳定液体的传送，
 * 代价是每抵抗一次都受伤（判定在 {@code PortalBlock#entityInside}，效果本身是纯标记，见 {@code StabilityEffect}）
 *
 * <p>药水颜色不单独写：{@code PotionContents#getColor()} 按可见效果的 {@code MobEffect#getColor()} 加权平均，
 * 而稳定是 {@code 0x7F159E}（也是传送门流体的雾色），只有这一条效果时平均出来就是它，与传送门同色
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(Registries.POTION, DawnDuskTerminal.MOD_ID);

    /** 8 分钟，也就是 9600 tick */
    public static final int STABILITY_DURATION = 8 * 60 * 20;

    /** 名字要与 {@code PotionItem} 拼出来的翻译键对上：{@code item.dawnduskterminal.stability_potion.effect.stability} */
    public static final DeferredHolder<Potion, Potion> STABILITY = POTIONS.register("stability",
        () -> new Potion("stability", new MobEffectInstance(ModEffects.STABILITY, STABILITY_DURATION)));

    private ModPotions() {}

    public static void register(IEventBus bus) {
        POTIONS.register(bus);
    }

    /**
     * 给药水物品补上默认的 {@code potion_contents}
     *
     * <p>不能在注册物品时写 {@code Item.Properties#component}：NeoForge 的注册顺序是原版注册表顺序，
     * 物品（第 7 个）先于药水（第 8 个），那时 {@link #STABILITY} 还没绑定，取 {@code get()} 会直接抛。
     * {@link ModifyDefaultComponentsEvent} 在全部注册表就绪之后才发，这时两头都拿得到；物品默认组件是
     * {@code Item#components()} 那张表，{@code new ItemStack(item)} 从它拷一份，所以创造栏、{@code /give}、
     * 各种掉出来的物品栈都会自带这瓶药
     */
    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modify(ModItems.STABILITY_POTION,
            builder -> builder.set(DataComponents.POTION_CONTENTS, new PotionContents(STABILITY)));
    }
}
