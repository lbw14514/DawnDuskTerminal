package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import twilightforest.init.TFEntities;

/**
 * 暗森林的刷怪：谁能在这个群系里刷出来，由<b>脚下那块方块</b>决定
 *
 * <h2>为什么规则写在这里而不是 JSON 里</h2>
 * 群系 JSON 的 {@code spawners} 只能写「什么生物、多少权重、一次几只」，它<b>没有办法表达「必须站在霞草或霞土上」</b>。
 * 生物那一侧的 {@code SpawnPlacementType} 也不行：那是按<b>实体类型</b>注册的全局表，暮色森林已经把
 * 国王蜘蛛、巨钳甲虫、砷铅铁恶灵、砷铅铁恶魂守卫都注册成 {@code ON_GROUND}，把它们改成「只认霞草」
 * 会把暮色森林自己那张地图里的刷怪一起改掉
 *
 * <p>所以分成两半：JSON 负责<b>选谁</b>（权重与数量，见 {@code worldgen/biome/dark_forest.json}），
 * 这里负责<b>否决</b>——用 NeoForge 的 {@link MobSpawnEvent.PositionCheck}，只在
 * {@code dawnduskterminal:dark_forest} 这一份群系里生效，站错了方块就直接判 {@code FAIL}
 *
 * <h2>规则</h2>
 *
 * <ul>
 *   <li>霞草 / 霞土上：国王蜘蛛、巨钳甲虫、活盔甲
 *   <li>砷铅铁恶灵、砷铅铁恶魂守卫：<b>没有地形限制</b>，这份群系里哪儿都能刷（要求已取消）
 * </ul>
 *
 * <p>只拦自然生成与区块生成那两条路（{@link MobSpawnType#NATURAL}、{@link MobSpawnType#CHUNK_GENERATION}）：
 * 刷怪蛋、{@code /summon}、刷怪笼是玩家自己摆的，不该被地形卡住。不在这张表里的生物一律放行
 */
public final class DarkForestSpawns {
    /** 本模组那份暗森林群系；维度里用的是调色板换出来的副本，所以不是 {@code minecraft:dark_forest} */
    public static final ResourceKey<Biome> BIOME =
            ResourceKey.create(Registries.BIOME, DawnDuskTerminal.id("dark_forest"));

    private DarkForestSpawns() {}

    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        Mob mob = event.getEntity();
        if (!event.getLevel().getBiome(mob.blockPosition()).is(BIOME)) {
            return;
        }
        if (!allowsOn(
                mob.getType(),
                event.getLevel().getBlockState(mob.blockPosition().below()))) {
            // Result 只有 SUCCEED / DEFAULT / FAIL：FAIL 就是「这一下不许生成」
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /**
     * 这一只肯不肯站在这种方块上
     *
     * <p>脚下取的是 {@code blockPosition().below()}：这才是「站在什么上面」
     *
     * <p>只有地面那三只看脚下；砷铅铁恶灵与砷铅铁恶魂守卫没有地形要求，走的是最后那条「一律放行」
     *
     * @param ground 生成位置脚下方块的方块状态
     */
    public static boolean allowsOn(EntityType<?> type, BlockState ground) {
        if (type == ModEntities.LIVING_ARMOR.get()
                || type == TFEntities.KING_SPIDER.get()
                || type == TFEntities.PINCH_BEETLE.get()) {
            return isRosyGround(ground);
        }
        return true;
    }

    private static boolean isRosyGround(BlockState ground) {
        return ground.is(ModTerrainBlocks.ROSY_GRASS.get()) || ground.is(ModTerrainBlocks.ROSY_DIRT.get());
    }
}
