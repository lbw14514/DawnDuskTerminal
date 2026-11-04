package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.world.DarkForestSpawns;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.init.TFBlocks;
import twilightforest.init.TFEntities;

/**
 * 暗森林的刷怪：群系列表里得有这五只，脚下方块决定谁能刷出来
 *
 * <p>三件事分开验：JSON 里的权重（读群系注册表）、方块判定（纯函数）、以及那条否决监听器
 * <b>不会管到别的群系</b>——测试世界是平原，所以监听器在那里必须是放行
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class DarkForestSpawnTests {
    /** 期望的权重：地面三只共 30，砷铅铁那两只共 30 */
    private static final Map<String, Integer> EXPECTED = Map.of(
            "dawnduskterminal:living_armor", 10,
            "twilightforest:king_spider", 10,
            "twilightforest:pinch_beetle", 10,
            "twilightforest:carminite_ghastling", 15,
            "twilightforest:carminite_ghastguard", 15);

    /**
     * 群系 JSON 的 {@code spawners.monster} 里正好是这五只、权重对得上
     *
     * <p>读的是<b>注册表里那份群系</b>而不是文件：这条用例挂了说明数据包没加载对，
     * 而不是某个数字写错
     */
    @GameTest(template = "ddt_empty")
    public static void biomeListsTheDarkForestSpawners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> biome =
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(DarkForestSpawns.BIOME);
        Map<String, Integer> actual = new HashMap<>();
        for (MobSpawnSettings.SpawnerData data :
                biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
            actual.put(
                    BuiltInRegistries.ENTITY_TYPE.getKey(data.type).toString(),
                    data.getWeight().asInt());
        }
        helper.assertTrue(
                actual.equals(EXPECTED),
                "dawnduskterminal:dark_forest monsters must be " + EXPECTED + ", got " + actual);
        helper.succeed();
    }

    /**
     * 方块判定：只有地面那三只被霞草 / 霞土 限制，砷铅铁那两只是自由的
     *
     * <p>不在这张表里的生物同样不受影响——比如僵尸站在石头上一律放行
     */
    @GameTest(template = "ddt_empty")
    public static void blockRulesFollowTheRequest(GameTestHelper helper) {
        BlockState rosyGrass = ModTerrainBlocks.ROSY_GRASS.get().defaultBlockState();
        BlockState rosyDirt = ModTerrainBlocks.ROSY_DIRT.get().defaultBlockState();
        BlockState darkLeaves = TFBlocks.DARK_LEAVES.get().defaultBlockState();
        BlockState hardenedDarkLeaves = TFBlocks.HARDENED_DARK_LEAVES.get().defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        for (EntityType<?> ground : new EntityType<?>[] {
            ModEntities.LIVING_ARMOR.get(), TFEntities.KING_SPIDER.get(), TFEntities.PINCH_BEETLE.get()
        }) {
            helper.assertTrue(DarkForestSpawns.allowsOn(ground, rosyGrass), ground + " must spawn on rosy grass");
            helper.assertTrue(DarkForestSpawns.allowsOn(ground, rosyDirt), ground + " must spawn on rosy dirt");
            helper.assertFalse(DarkForestSpawns.allowsOn(ground, darkLeaves), ground + " must not spawn on leaves");
            helper.assertFalse(DarkForestSpawns.allowsOn(ground, stone), ground + " must not spawn on stone");
        }
        // 砷铅铁那两只的地形限制已取消：树叶、霞草、石头，哪儿都放行
        for (EntityType<?> free :
                new EntityType<?>[] {TFEntities.CARMINITE_GHASTLING.get(), TFEntities.CARMINITE_GHASTGUARD.get()}) {
            helper.assertTrue(DarkForestSpawns.allowsOn(free, darkLeaves), free + " must spawn on dark leaves");
            helper.assertTrue(
                    DarkForestSpawns.allowsOn(free, hardenedDarkLeaves), free + " must spawn on hardened dark leaves");
            helper.assertTrue(DarkForestSpawns.allowsOn(free, rosyGrass), free + " must spawn on rosy grass");
            helper.assertTrue(DarkForestSpawns.allowsOn(free, stone), free + " must spawn on stone");
        }
        helper.assertTrue(
                DarkForestSpawns.allowsOn(EntityType.ZOMBIE, stone),
                "a mob outside the table must not be restricted, zombie on stone was denied");
        helper.succeed();
    }

    /**
     * 否决只在 {@code dawnduskterminal:dark_forest} 里生效
     *
     * <p>测试世界是平原：把一只活盔甲摆在石头地上跑一遍监听器，结果必须是默认（放行）。
     * 这条挡的是「改坏暮色森林自己的刷怪」那类事故
     */
    @GameTest(template = "ddt_empty")
    public static void vetoOnlyAppliesToTheDarkForest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob armor = ModEntities.LIVING_ARMOR.get().create(level);
        if (armor == null) {
            helper.fail("living_armor could not be created");
            return;
        }
        BlockPos pos = helper.absolutePos(new BlockPos(3, 4, 3));
        armor.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        MobSpawnEvent.PositionCheck event = new MobSpawnEvent.PositionCheck(armor, level, MobSpawnType.NATURAL, null);
        DarkForestSpawns.onPositionCheck(event);
        helper.assertTrue(
                event.getResult() == MobSpawnEvent.PositionCheck.Result.DEFAULT,
                "a spawn outside dawnduskterminal:dark_forest must not be touched, got " + event.getResult());
        armor.discard();
        helper.succeed();
    }

    private DarkForestSpawnTests() {}
}
