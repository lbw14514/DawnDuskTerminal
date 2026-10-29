# 群系占位对照

## 约定：占位不改注册名

群系副本的 ID 始终等于它所顶替的原版群系。若需了解某一格原本是什么群系，查看 ID 即可得知；玩家看到的名字由语言文件决定，待定稿之后再统一修改。

因此，移植暮色森林群系时，实际做法是在 `birch_forest.json` 中装入 `firefly_forest` 的内容，而不是新建 `firefly_forest.json`。只有完全没有同名原版格子的自建群系才使用新 ID，即 `floatrees`、`white_land`、`blue_desert`、`polar_dawn` 与 `polar_dusk`。

## 替换链

维度不新增气候格：`dawnduskterminal:palette` 群系源的 delegate 就是原版主世界气候表。

1. 原版气候表选出 `minecraft:X`。
2. 同名的副本 `dawnduskterminal:X` 取而代之，前提是该副本位于 `palette_biomes` 标签之中。
3. 仅有一格需要再改写，即 `PaletteBiomeSource#replacements()`。

| 原版格子 | 实际群系 | 为什么改 |
|---|---|---|
| `minecraft:desert` | `dawnduskterminal:white_land` 白地 | 昼区的温度被固定在热档，气候表在该档下的五档湿度全部为沙漠，因此若不改指则整片区域均为沙漠 |

改指只决定某一格装入哪一个副本，格子的选择仍由气候表按六要素计算得出。极带（`|param| >= 0.8`）无需改指：该区域的温度超出了原版参数表的覆盖范围，自建群系直接在 `extra` 中占据了空位。

## 分带

群系归属于哪一带并不由替换表决定，而是由温度决定：`ChronoTemperature` 把 `param` 分段映射到原版气候表的温度档，每一带只对应一档，详见 `docs/worldgen.md` 的「温带分带与温度映射」。

| 带 | `param` | 群系族 |
|---|---|---|
| 极夜 | `<= -0.8` | 极夜平原 `polar_dusk` 与蓝色沙漠 `blue_desert`（即 `extra` 中温度 `-2 ~ -1` 的两条，按湿度区分） |
| 夜区 | `-0.8 ~ -0.6` | 雪原 `snowy_plains` `snowy_taiga` `ice_spikes` 雪峰 冰川 |
| 寒带 | `-0.6 ~ -0.3` | `taiga` `plains` `old_growth_spruce_taiga` `grove` |
| 温带 | `-0.3 ~ 0.3` | `plains` `forest` `birch_forest` `dark_forest` `meadow` |
| 热带 | `0.3 ~ 0.6` | `savanna` `jungle` `sparse_jungle` `bamboo_jungle` |
| 昼区 | `0.6 ~ 0.8` | 白地 + 恶地族 |
| 极昼 | `>= 0.8` | 极昼平原 `polar_dawn`（即 `extra` 中温度 `1 ~ 2` 的那一条） |

## 装了暮色森林内容的副本

这些副本的来源脚本是 `tools/port_tf_biomes.py`。该脚本保留副本原有的 `carvers`，把 `minecraft:*` 放置特征改指为 `dawnduskterminal:*`，而 `twilightforest:*` 则直接引用。

| 原版 ID | 装的暮色群系 | 当前显示名 | 设定稿名 |
|---|---|---|---|
| `dark_forest` | `dark_forest_center` | 黑森林 | 黑森林腹地 |
| `birch_forest` | `firefly_forest` | 萤火虫森林 | 萤火虫森林 |
| `flower_forest` | `dense_forest` | 繁花森林 | 繁密暮色森林 |
| `jungle` | `enchanted_forest` | 丛林 | 魔法森林 |
| `swamp` | `fire_swamp` | 沼泽 | 火焰沼泽 |
| `plains` | `clearing` | 平原 | 暮光平原 |
| `savanna` | `oak_savannah` | 热带草原 | 橡木热带草原 |
| `snowy_plains` | `glacier` | 雪原 | 暮色冰川 |
| `windswept_hills` | `highlands` | 风袭丘陵 | 荆棘高地 |
| `forest` | `twilightforest:forest` | 森林 | 暮色森林 |

表格最后一行的 `forest` 不在该脚本的对照表之内，是手工移植的结果。设定稿一列中的名字都还没有写入语言文件。

## 已换内容但未安装暮色群系

| 原版 ID | 内容 | 显示名 |
|---|---|---|
| `old_growth_birch_forest` | 将萤火虫森林那份移植再染红，`grass_color` 与 `foliage_color` 取红色，`temperature` 0.8 | 红草树林 |

## 自建群系

这些群系没有同名的原版格子，只能使用新 ID。

| 模组 ID | 显示名 | 位置 | 说明 |
|---|---|---|---|
| `floatrees` | 天门 | 全维度 | 不经过替换表，采样深度低于 -1.6 时直接返回，早于 delegate |
| `white_land` | 白地 | 昼区 `0.6 <= param < 0.8` | 裸地，地表为 `siltstone`，属于恶魂支线 T2；由替换表接管原版沙漠那一格 |
| `blue_desert` | 蓝色沙漠 | 极夜 `param <= -0.8` 的干湿度一半 | 蓝沙地，地表为 `road_sand`，带雪；对应 `extra` 中温度 `-2 ~ -1`、湿度 `-1 ~ -0.35` 的那一条 |
| `polar_dawn` | 极昼平原 | 极昼 `param >= 0.8` | 占位，内容依照白地；温度 `2.0`、不降水，天穹接近白昼。由 `extra` 中 `[1, 2]` 那一条选中 |
| `polar_dusk` | 极夜平原 | 极夜 `param <= -0.8` | 占位，内容依照白地；温度 `-0.45`、降水 `true`，因此会积雪结霜，天穹被压暗。由 `extra` 中 `[-2, -1]` 那一条选中 |

这几份副本与 `forest` 一样都是手工接入的，`tools/port_overworld_worldgen.py` 不会生成它们。重新运行该脚本会再次生成一份 `desert.json` 副本，并把 `dawnduskterminal:desert` 写入调色板标签；而那一格已经改指白地，因此在重新生成之后，需要删除多出来的 `desert.json` 与标签中的对应条目。

## 换皮

其余 41 份副本同名同内容，只更换了地表方块与矿石特征：

- 恶地族 `badlands` `eroded_badlands` `wooded_badlands`
- 雪地族 `snowy_beach` `snowy_slopes` `snowy_taiga` `ice_spikes` `frozen_peaks` `jagged_peaks` `grove`
- 海洋族 `ocean` `deep_ocean` `cold_ocean` `deep_cold_ocean` `lukewarm_ocean` `deep_lukewarm_ocean` `frozen_ocean` `deep_frozen_ocean` `warm_ocean`
- 河流与岸 `river` `frozen_river` `beach` `stony_shore`
- 其余 `sunflower_plains` `meadow` `cherry_grove` `mushroom_fields` `stony_peaks` `windswept_forest` `windswept_gravelly_hills` `windswept_savanna` `bamboo_jungle` `sparse_jungle` `mangrove_swamp` `taiga` `old_growth_pine_taiga` `old_growth_spruce_taiga` `savanna_plateau` `deep_dark` `dripstone_caves` `lush_caves`

唯一的例外是 `desert` 的同名副本被改成了自建 ID `blue_desert`，因此它不再包含原版沙漠的仙人掌、枯灌木与沙漠水井，也不再经由替换表接管（它的格子位于 `extra` 之中）。

## 尚未落地的名字

以下名称在语言文件中已有对应的键，但 `worldgen/biome/` 中尚无同名的群系文件，因此当前没有任何气候格子会选中它们：

- `day`：昼区平原。
- `normal`：晨昏平原。
- `night`：夜区平原。
- `refusal_ocean`：拒绝之海。
- `dawn_zone`：晨区。
- `dusk_zone`：昏区。
- `solidcanopy_ocean`：凝固苍穹的海洋。
- `earth_light`：地光。
- `sky_island`：空岛。

`polar_dawn`（极昼平原）与 `polar_dusk`（极夜平原）虽然同样在语言文件中设有键，但已具备对应的群系文件，属于自建群系，见前文的自建群系表。

## 附录：原版方块到模组方块

以下为地表调色板的命名对照，所列方块均定义在 `ModTerrainBlocks` 中：

| 原版 | 模组 | id |
|---|---|---|
| 草方块 | 霞草 | `rosy_grass` |
| 泥土 | 霞土 | `rosy_dirt` |
| 石头 | 苍穹石 | `canopy_stone` |
| 花岗岩 | 赭石 | `ochre_block` |
| 闪长岩 | 绿片岩 | `greenschist` |
| 安山岩 | 榴辉岩 | `eclogite` |
| 深板岩 | 紫苏岩 | `charnockite` |
| 凝灰岩 | 暮光石 | `twilight_stone` |
| 沙子 | 路之沙 | `road_sand` |
| 红沙 | 不醒沙 | `unconscious_sand` |
| 砂岩 | 粉砂岩 | `siltstone` |
| 水 | 不稳定液体 | `portal_fluid` |
| 岩浆 | 热情的暮光 | `enthu_fluid` |
| 冰 | 凝固的苍穹 | `frozen_canopy` |
| 雪 | 苍穹霜 | `canopy_frost` |
