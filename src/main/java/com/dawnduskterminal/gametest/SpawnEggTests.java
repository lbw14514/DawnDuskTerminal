package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 刷怪蛋必须真的指向本模组的实体
 *
 * <p>刷怪蛋用的是 NeoForge 的 {@code DeferredSpawnEggItem}：构造时只拿一个实体类型的 {@code Supplier}，
 * 真正的绑定发生在其内部的 {@code FMLCommonSetupEvent} 里（填 {@code SpawnEggItem.byId} 的反查表）。
 * 这张表没填上时，把蛋拿去点刷怪箱会得到 {@code null} 实体类型，或者发射器把蛋吞掉，
 * 而创造栏里看起来完全正常 —— 所以这里从反查表往回查一遍
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SpawnEggTests {
    @GameTest(template = "ddt_empty")
    public static void eggsPointAtTheirEntities(GameTestHelper helper) {
        helper.assertTrue(spawns(ModItems.TOWER_GHAST_SPAWN_EGG.get(), ModEntities.TOWER_GHAST.get()),
            "tower_ghast_spawn_egg must spawn dawnduskterminal:tower_ghast and be reachable through SpawnEggItem#byId");
        helper.assertTrue(spawns(ModItems.STRING_SPAWN_EGG.get(), ModEntities.STRING.get()),
            "string_spawn_egg must spawn dawnduskterminal:string and be reachable through SpawnEggItem#byId");
        helper.succeed();
    }

    /** 蛋的默认实体类型、物品栈上的实体类型、以及按实体类型反查回来的蛋，三者是同一对 */
    private static boolean spawns(Item item, EntityType<?> type) {
        if (!(item instanceof SpawnEggItem egg)) {
            return false;
        }
        ItemStack stack = new ItemStack(egg);
        return egg.getType(stack) == type && egg.spawnsEntity(stack, type) && SpawnEggItem.byId(type) == egg;
    }

    private SpawnEggTests() {}
}
