# 世界生成

维度 id `dawnduskterminal:chrono`

生成类型 `minecraft:noise`

群系源 `dawnduskterminal:palette`

地形设置 `dawnduskterminal:chrono`

思路是把原版主世界世界生成整份移植进本模组命名空间，再把方块换成自己的调色板

相关文件都在 `src/main/resources/data/dawnduskterminal/`

## 维度类型

文件 `dimension_type/chrono.json`

`fixed_time` 为 12000，锁在黄昏，维度内没有昼夜

`bed_works` 为 true，允许睡觉，只重置重生点

`coordinate_scale` 为 1.0

`has_skylight` 为 true

`has_ceiling` 与 `has_raids` 为 false

`ambient_light` 为 0.0

`height` 与 `logical_height` 都是 384

`min_y` 为 -64

`effects` 指向 `minecraft:overworld`，天空与雾色走原版特效

`monster_spawn_light_level` 是 0 到 7 的 uniform

## 地形设置

文件 `worldgen/noise_settings/chrono.json`

`default_block` 是 `dawnduskterminal:canopy_stone`

`default_fluid` 是 `dawnduskterminal:portal_fluid`，等级 0

`aquifers_enabled` 是 true

`sea_level` 是 63

`ore_veins_enabled` 是 true

`lava` 噪声指向 `dawnduskterminal:aquifer_lava`

`noise` 的 `height` 384，`min_y` -64

`noise_router` 取自移植后的原版主世界路由

`surface_rule` 是一张按群系分支的大表，默认产物是 `canopy_stone`

`spawn_target` 沿用原版主世界

## 移植文件

`tools/port_overworld_worldgen.py` 从 Gradle 缓存里的反编译客户端 jar 抽取原版
`density_function` `noise` `noise_settings`，改写命名空间后写入本模组

`density_function/` 含 `overworld/` 子目录

`noise/` 是移植过来的噪声参数，只有 `noise/temperature.json` 的 `firstOctave` 改过，见「温度噪声的频率是手调的」

`multi_noise_biome_source_parameter_list/` 是原版主世界与下界的多噪声参数表副本

`configured_carver/` 是洞穴与峡谷

`configured_feature/` 与 `placed_feature/` 是移植过来的特征

移植脚本把群系里的放置特征引用、以及放置特征里的配置特征引用都改写到 `dawnduskterminal:` 命名空间

`configured_feature` 内部嵌套的引用不在改写范围内，所以 `random_selector` 的 `feature` 与 `default` 两个键指向原版的那些得手工补，见「已知问题」

## 调色板群系源

`world/PaletteBiomeSource`，注册为 `dawnduskterminal:palette`

维度 JSON 里的 `biome_source` 有三个字段：`preset`（原版主世界参数表 `minecraft:overworld`）、`palette`（`#dawnduskterminal:palette_biomes`）与 `extra`（追加的气候点，可省）

选格用的是原版那套算法：把 `preset` 的参数表拿来，末尾接上 `extra` 的几条，一起交给 `Climate.ParameterList.findValue`。原版算法一个字没改

`extra` 的条目形状比着内联 `multi_noise` 写：`temperature` `humidity` `continentalness` `erosion` `weirdness` 五段跨度加一个 `biome`，深度由代码按点值 0 与 1 各插一份（跟 `OverworldBiomeBuilder.addSurfaceBiome` 一致，写成跨度会让群系随 y 跳）

`getNoiseBiome` 拿到群系后，再把 `minecraft:` 命名空间的结果换成同名副本

换名按路径匹配，例如 `minecraft:plains` 变成 `dawnduskterminal:plains`

只有一处例外改指了格子：热档的 `minecraft:desert` 换成 `dawnduskterminal:white_land`（白地），见下文「白地接管昼区」。蓝沙沙漠在 `extra` 里，见「蓝沙沙漠在极夜」

群系落在哪一带由温度决定，群系源里没有任何按坐标判群系的特例，见「温带分带与温度映射」

副本携带本模组的方块特征表，所以维度在生成期就铺自己的方块，而不是事后打补丁

这句话只对一部分特征成立：57 个 `configured_feature` 里 46 个引用了模组方块或模组方块标签（`ore_*` `glow_lichen` `ice_patch` `patch_berry_bush` `swamp_oak` 等），其余 11 个是树，只摆原版原木与树叶

地形的方块替换不靠特征表，靠 `noise_settings` 里的地表规则

`worldgen/biome/` 里除 `floatrees` `white_land` 外都是与主世界群系同名的副本

副本里的内容不等于同名的原版群系：暮色森林的群系直接写进对应的原版位置，ID 不改，显示名靠 lang。每个位置现在装的是什么，账本在 `docs/biomes.md`

`floatrees` 不走替换表，采样深度低于 -1.6 时直接返回，早于选格

替换表在第一次使用时构建，因为 biome source 在数据包解析期创建，那一刻标签 holder 还没有 key

### 蓝沙沙漠在极夜

`dawnduskterminal:blue_desert` 是自建群系，地表是蓝色的 `road_sand`，气温按雪地设（`temperature` `-0.45`、`has_precipitation` `true`），特征表里没有仙人掌、枯灌木与沙漠水井（原版沙漠那份带），只剩绿洲那点甘蔗与南瓜

它的格子是自己的一条 `extra`：温度 `[-2, -1]`（极夜）、湿度 `[-1, -0.35]`（干的那半）。极夜带的另一半湿度留给极夜平原 `polar_dusk`，于是极夜里「干的地方是蓝沙沙漠，湿的地方是平原」，两者都是自建群系、都有自己的格子

`floatrees` 仍在选格之前（按 `depth`），所以极夜的空岛还是空岛

夜区（`-0.8 ~ -0.6`）不含蓝沙，那一带全是雪地族：雪原 冰刺 雪峰 冰川

门槛不在 `-0.45` 这个数上，在 `0.15`：`Biome#warmEnoughToRain` 是 `getTemperature(pos) >= 0.15`，`coldEnoughToSnow` 就是它的取反，`shouldSnow` 与 `shouldFreeze` 也走同一个判断。`ChronoFreezeFeature` 靠它们铺 `canopy_frost` 与 `frozen_canopy`，所以任何小于 `0.15` 的温度都行，原版沙漠那份 `2.0` 与 `false` 才会让蓝沙一片霜都没有

`getTemperature` 只在 y>80 时继续变冷，不会把 `-0.45` 抬回 `0.15` 以上

### 恶地只在昼区

`dawnduskterminal:badlands` 一族走原版气候表里恶地那几格，而恶地要求温度落进热档（`0.55 ~ 1.0`），所以它只出现在昼区（`0.6 ~ 0.8`）的台地侵蚀档

温带、寒带、热带的温度都由 `ChronoTemperature` 的分段映射钉住，够不到热档，因此这三带里不会出现恶地

### 白地接管昼区

`dawnduskterminal:white_land` 占昼区（`0.6 ~ 0.8`），是恶魂支线 T2 群系的位置

这一带的温度被映射钉在 `0.55 ~ 1.0`，而原版气候表在这档里五档湿度全是 `desert`，所以白天带默认落地就是沙漠。白地靠在替换表里把那格改指过去：`replacements()` 里 `minecraft:desert → dawnduskterminal:white_land`

台地侵蚀档的恶地族不受影响，所以昼区是「白地为主，恶地成片点缀」

白地是裸地，地表规则铺 `siltstone`，特征是恶地副本削掉地表装饰之后的样子，矿石与洞穴照旧

`temperature` 是 `2.0`、`has_precipitation` 是 `false`，所以不积雪也不长霜

### 萤火虫森林

`dawnduskterminal:birch_forest` 装的是暮色森林 `firefly_forest` 的移植结果（树 结构 灌木 浆果 萤火虫粒子都原样），`temperature` 0.5、`has_precipitation` `true`

ID 仍是 `birch_forest`，因为占位不改注册名，看 ID 就知道它顶的是哪格。显示名在 lang 里改成「萤火虫森林」

### 红草树林

`dawnduskterminal:old_growth_birch_forest` 装的是上面那份萤火虫森林内容，只把草地与树叶染红：`grass_color` 与 `foliage_color` 给的是红色，`temperature` 提到 `0.8`

同样不改 ID，显示名在 lang 里改成「红草树林」。这样 `birch_tall` 这个特征就没有人引用了，见下文「特征移植」

### 黑森林腹地

`dawnduskterminal:dark_forest` 就是暮色森林的 `dark_forest_center`，见 `tools/port_tf_biomes.py` 的对照表

树叶按 `ChronoFoliageColors` 固定染成 `0xE94E14`，草方块走暮色森林的 `dark_forest_center` 调色器

### 天门松

`configured_feature/tree/skygate_pine.json` 是文竹式分层的树：20~30 格直筒树干，4~8 根枝撒在树干 28% 到 90% 的高度上，每根枝顶一团压扁的樱花叶团。

树干用模组自己的 `dawnduskterminal:skygate_branching`（`world/SkygateBranchTrunkPlacer`）：原版 `cherry_trunk_placer` 最多三根枝且贴顶，暮色森林的 `branching_trunk_placer` 分枝是逐格往上叠的连排，都铺不满树干中下部，所以高度是代码里算的。枝的方向按方位角逐根随机，落点取在圆周上再走单格轴向的斜线，所以斜枝（45 度、30 度这类）也会长。可调项：

| 字段 | 作用 |
|---|---|
| `branch_count` | 分枝根数，`IntProvider` |
| `branch_length` | 每根枝在圆周上的伸出半径，`IntProvider` |
| `branch_band` | `{min, max}`，分枝高度带占树干高度的比例 |
| `branch_rise_chance` | 每走一格同时抬高一格的概率 |
| `radius_offsets.branch` | 每根枝的树冠水平尺寸偏移，`IntProvider`，逐枝抖动 |
| `radius_offsets.crown` | 树干顶上那团的水平尺寸偏移，固定值，比枝顶大一档用来封顶 |

叶团是 `minecraft:cherry_foliage_placer`：`height` 取到该 placer 允许的下限 4（原版樱花是 5），垂直尺寸就定死在这儿；`radius` 随树浮动，再叠上 `radius_offsets`，所以横向比原版樱花大一圈而且每团不一样大。注意 placer 内部的 `i = radius + radiusOffset - 1`，最宽的一层在下数第二层、半径就是 `i`：`radius` 取 3、`branch` 取 -1 时 `i` 只剩 1，叶团会缩成 3x3 的薄片，别再往下调。

挂藤用原版的 `minecraft:leave_vine`：它在树叶侧面的空位挂藤，再往下垂一到四格，正好是「藤长在树叶边上」。它只有一个 `probability`，是**每片树叶每个侧面各掷一次**，所以密度随树冠大小线性涨：`0.25`（原版丛林树的取值）在这棵树上是几百个藤方块，`0.01` 实测 16 个藤方块、7 条藤（叶约 450 片）。调密度按线性换算。

别用 `minecraft:trunk_vine`：它挂的是树干，每根原木每面 2/3，二十多格的树干会挂成藤柱。本模组曾经自己写过 `sparse_leaf_vine`，按「条数」随机挑树叶再往旁边挂，结果绝大多数随机数落在叶团内部（旁边不是空气），实际挂出来的条数远少于配置值，已删除。

`minimum_size` 是 `limit 1` / `lower_size 0` / `upper_size 2`，跟原版樱花一致；旧配置的 `limit 20` + `upper_size 5` 等于要求树干高处 11x11 全是空地，树几乎长不出来。

形状由 `SkygatePineTests` 断言：树高 20~30、分枝至少覆盖四个不同高度且同时落在树干下半与上三分之一。

## 温带分带与温度映射

`NoiseChunkTemperatureMixin` 把维度的温度密度函数包进 `ChronoTemperature`

包装点在 `NoiseChunk.cachedClimateSampler`，所以只有区块落盘的群系带映射；结构判定与 `/locate biome` 读的 `RandomState.sampler()` 不带，两边会判出不同群系，补救见「两个采样器」

### 七带与断点

`ChronoTemperature` 用六个断点把 `param` 切成七带，每带线性映射到原版气候表的一段温度：

| 带 | `param` | 温度映射 | 实宽（`maxDistance 5000`） |
|---|---|---|---|
| 极夜 | `<= -0.8` | `-1.00 ~ -2.00`（冻结档再往外，`extra` 的极夜平原与蓝沙沙漠） | 1000+ |
| 夜区 | `-0.8 ~ -0.6` | `-1.00 ~ -0.45`（冻结档：雪原 冰刺 雪峰 冰川） | 1000 |
| 寒带 | `-0.6 ~ -0.3` | `-0.45 ~ -0.15`（针叶林 平原 云杉） | 1500 |
| 温带 | `-0.3 ~ 0.3` | `-0.15 ~ 0.20`（平原 森林 桦树 黑森林） | 3000 |
| 热带 | `0.3 ~ 0.6` | `0.20 ~ 0.55`（热带草原 丛林） | 1500 |
| 昼区 | `0.6 ~ 0.8` | `0.55 ~ 1.00`（热档：白地 恶地） | 1000 |
| 极昼 | `>= 0.8` | `1.00 ~ 2.00`（热档再往外，`extra` 的极昼群系） | 1000+ |

断点用温度档自己给出的数：`-1.0 -0.45 -0.15 0.20 0.55 1.0`。首尾相接，所以整条映射在 `param` 上连续，不需要 clamp

于是每一带只对应原版一档温度：针叶林要求温度 `<= -0.15`，而热带的温度下界是 `0.20`，中间隔着一个整温带，刷不进去

### 带内噪声

映射之上叠的是原版温度噪声乘 `ChronoTemperature.SPREAD`（`0.08`）

噪声不决定分带，只让分带边界弯折：带内温度已经被映射钉死在一档里，噪声再大也只在档内晃，只有边界两侧各有约 `SPREAD` 宽的过渡

温带即使算上噪声也到不了沙漠与雪原的门槛：上界 `0.20 + 0.08 < 0.55`，下界 `-0.15 - 0.08 > -0.45`。`WorldgenTests` 钉住这两条

想把边界拉直就把 `SPREAD` 调成 0；调大到 `0.15` 以上，夜里会开始往温带渗雪原

### 温度噪声的频率是手调的

`noise/temperature.json` 是从原版 `minecraft:temperature` 一字不差拷过来的，但**频率改过**：`firstOctave` 从 `-10` 抬到 `-7`，`amplitudes` 不动

原版主世界没「带边界」这回事，温度噪声本身就是气候，所以它用 `firstOctave -10`：波长 `4096 / 1024` 格。这个尺度比七带里任何一带（`1000 ~ 3000` 格）都宽，放在晨昏线里只会让边界在几公里的尺度上慢慢漂，带内部与带边上都看不出结构

抬到 `-7` 后波长 `512 / 128` 格（`2^7 / xz_scale 0.25` 与 `2^5 / xz_scale 0.25`）：宽的那个让边界在带内凹凹凸凸，窄的那个把「跟晨昏线平行」的群系边界打散。幅度一个字没改，所以抖动幅度、`±4.4444` 的值域、泄漏上界全都不变

重跑 `tools/port_overworld_worldgen.py` 会把原版的 `-10` 写回来（脚本是忠实拷贝，没有例外表），`WorldgenTests.temperatureNoiseRunsAtTheBandScale` 就是那时候的报警

### 交界抖动

光靠上面那条噪声撑不起交界：`512` 格对它自己要打散的边界来说仍偏宽，站在交界上看还是能看出一条弯得很缓的线

所以断点两侧再叠一层 `ChronoTemperature.seamDither`：三个八度的值噪声，波长是 `SEAM_WIDTH` 的 `1 / 0.3 / 0.1`（默认 `5000` 下即 `400 / 120 / 40` 格），权重 `1 / 0.5 / 0.25`，幅度 `SEAM_DITHER`（`0.09`），再乘一个按到最近断点距离算的权重（断点上 1，离开 `SEAM_WIDTH` 即 `0.08` 个 `param`、默认约 400 格后归 0，中间 smoothstep）

交界于是长成两侧群系互相咬进去的楔形与小飞地；带心不吃抖动，那一带内的群系分布与只叠 `SPREAD` 时一样

`SEAM_WIDTH` 和 `SEAM_DITHER` 的取舍：`SEAM_DITHER` 越大楔子越深，但同时留出的余量越小（见下一条）；`SEAM_WIDTH` 定的是「交界有多宽」，调到 `0.15` 以上会把窄带（夜区 昼区 极带都只有 `0.2` 宽）从中间吃掉，整带都变成抖动区

带宽与抖动波长都写在 `param` 尺度上，跟七带的宽度一样，所以两者都跟着 `maxDistance` 一起缩放：`maxDistance` 从 `5000` 改成 `2500`，带宽减半，抖动波长跟着减半，交界不会相对变粗。代码里没有任何以格为单位的抖动常数——`seamDither` 先把坐标除回 `param` 尺度（除数与 `ChronoLine.param` 用的那个一致），再在 `param` 空间里算值噪声

抖动是 `param` 与 `(x, z)` 的纯函数：不读注册表、不读配置，跨区块、跨线程、重开存档都是同一张图；用 smoothstep 插值值噪声，4 格内变化不到 `0.01`，不会在林子里切出格子状断层

交界抖动与 `SPREAD` 同时满幅，所以泄漏的上界是两者之和（`0.17`）：温带上界 `0.20 + 0.17 < 0.55`，下界 `-0.15 - 0.17 > -0.45`，仍然够不到沙漠与雪原。`WorldgenTests` 钉住这两条

### 值域

温度噪声原本是 `shifted_noise`，噪声 `dawnduskterminal:temperature`，`amplitudes` 为 `[1.5, 0, 1, 0, 0, 0]`，`firstOctave` 为 `-7`（手调过，见「温度噪声的频率是手调的」），声明值域 `±4.4444`

中间五带输出落在 `[-1.17, 1.17]`（带端点 ± `SPREAD` ± `SEAM_DITHER`）；两个极带继续往外推，`param` 被钳到 `±1` 时温度到 `ChronoTemperature.POLAR_TEMPERATURE`（`2.0`），所以 `minValue` / `maxValue` 是 `±2.0`。极带内抖动的余量够大：`param` `0.9` 处温度 `1.5`，减满幅抖动还有 `1.41`，稳稳留在 `extra` 的 `[1, 2]` 里

`|param| >= 0.8` 的两个极带不叠 `SPREAD` 那条低频噪声：它一压就可能把极带边界推出 `extra` 的 `±1 ~ ±2` 跨度，边界变成一条毛边。交界抖动不受这条限制，极带边界（`±0.8`）照样吃它，所以极昼极夜与昼区夜区之间也会互相咬合

### 想加群系有两条路

**一条是 `extra`**：原版参数表只覆盖 `[-1, 1]` 的温度，出界的地盘是空的，所以在 `extra` 里写一条温度跨度越界的条目，它就一定赢过原版最外侧那一行。极昼极夜就是这么做的——`ChronoTemperature` 把 `param` 推到 `±1` 时温度到 `±2`，正好落进 `[1, 2]` 与 `[-2, -1]` 两条

**另一条是替换表**：温度带内部没有空地，想在那儿加群系只能顶掉某一格，白地走的这条，见 `docs/biomes.md` 的替换链

湿度 大陆性 侵蚀 怪异度原版都铺满 `-1 ~ 1`（大陆性到 `-1.2 ~ 1.0`），往这些方向加点永远输，所以 `extra` 实际只能按温度出界来用

想彻底不按六参数排（像暮色森林那样自己算布局），得重写整个 `getNoiseBiome`，本模组不用

### 超界温度照样选得出群系

`Climate.ParameterList.findValue` 按六个参数的平方距离之和取最小（`Climate.ParameterPoint.fitness`），单项距离是 `Climate.Parameter.distance(long)`：值落在 `[min, max]` 内是 0，落在外面就是离最近端点的差

所以 `1.5` 或 `-1.4` 不会出错也不会退回默认值：表里没有对应跨度时就选最近那一行，有 `extra` 的条目就选中它。极昼极夜靠这一条落地

## 标签

`tags/worldgen/biome/palette_biomes.json` 列出全部群系副本

`tags/block/` 是本模组方块标签，供移植过的特征引用

`data/minecraft/tags/worldgen/biome/` 把副本挂进结构生成与刷怪规则

`data/minecraft/tags/block/` 把模组方块加进原版石类 土类 沙类 树叶等标签

`data/twilightforest/tags/worldgen/biome/` 把副本挂进暮色森林结构的群系标签，目前只有 `valid_hollow_tree_biomes`

## 世界生成补丁

数据包够不到的原版硬编码由 mixin 接管，逐条见 `architecture.md`

`LavaBandPaletteMixin` 处理 y 以下整条岩浆带

`LavaAquiferPaletteMixin` 处理深部岩浆含水层

`PillarsFluidBlockMixin` 让石柱洞穴保持干燥

`NoiseChunkVeinPaletteMixin` 处理大矿脉的花岗岩与凝灰岩

`NoiseChunkTemperatureMixin` 给温度噪声加晨昏线偏移，群系随昼夜侧分布

`MineshaftHeightMixin` 与 `MineshaftChestLootMixin` 限制废弃矿井高度并换战利品表

识别维度统一靠噪声设置的 `default_fluid` 是不是 `portal_fluid`

## 结构

`structure/StructureExclusions` 是白名单，只放行废弃传送门 沉船 前哨站 沙漠神殿 雪屋 废弃矿井，以及暮色森林的树洞 倒下空心木 蘑菇城堡 营地 树篱迷宫 空心矿山

`StructureExclusionMixin` 排除不在白名单里的结构

同一文件里的 `UNMAPPED` 与 `LANDMARKS` 记录未放行的暮色森林结构与地标，只服务日志，不影响行为

### 高大暮色橡树

高大暮色橡树（Robust Twilight Oak，`twilightforest:hollow_tree` 结构，`hollow_oak_sapling` 长出的那棵）不是 placed_feature，不走群系特征表，所以白名单放行还不够：它的群系选择器是 `#twilightforest:valid_hollow_tree_biomes`，那份标签原本只列暮色森林自己的群系

`data/twilightforest/tags/worldgen/biome/valid_hollow_tree_biomes.json` 用 `replace: false` 把 forest birch_forest old_growth_birch_forest flower_forest dark_forest mushroom_fields savanna 七份副本追加进去。结构在 `findValidGenerationPoint` 里问的正是这个 holder set，标签丢了巨树就静默消失，所以 `WorldgenTests.hollowTreesAcceptTheChronoBiomes` 钉住这件事

结构集 `twilightforest:hollow_tree` 的 spacing 7 separation 5，池子里一半权重是 `twilightforest:swamp_hollow_tree`，那个结构把群系写死为 `twilightforest:swamp`，在晨昏线里不存在，所以实际密度大约再减半

### 群系标签不够：两个采样器

曾经的问题：温度偏移包在 `NoiseChunk.cachedClimateSampler` 里，区块落盘的群系走这条带偏移的路，而树洞判定读的 `context.randomState().sampler()` 没有偏移。两边会给出不同群系——结构看到草原，地表实际是红沙漠或雪原，巨树于是长在沙漠中间。实测一个存档：24 棵里有 14 棵的地表群系是 `dawnduskterminal:red_desert`（那个群系后来删了，现在同样的位置是白地）

`HollowTreeSurfaceBiomeMixin` 在 `findValidGenerationPoint` 返回处用同样包过温度的采样器复算一次，不在标签里就取消。注入必须写 `cancellable = true`，否则 `setReturnValue` 会在世界生成线程里抛 `CancellationException`。注入点只能选 `RETURN`：`Structure.findValidGenerationPoint` 上已有 `StructureExclusionMixin` 的 `HEAD` 注入

试过把偏移整体搬到 `RandomState`（`router` 与 `sampler` 一起包装）来根治，能跑通但性能崩了：`NoiseChunk` 会为包装器再建一层噪声插值，`locate structure` 从 0.1 秒变成单刻超过 60 秒、直接触发看门狗，于是退回结构级复核

## 高度

地形高度范围 -64 到 320

原版主世界的密度函数被整份移植，所以地形形态与原版一致

空岛与地下浮岛还在：`sloped_cheese` 里叠加的 `sky_island_density` 与 `subterranean` 分别管 y 高处与 y<0

早期的四带设计（大陆 空置域 空岛 地下岛）在接管地形噪声那一次被整份替换，`VoidRegionFeature` 一并删除

## 已知问题

`data/dawnduskterminal/tags/worldgen/biome/` 下的旧标签只保留 `palette_biomes`

### 引用指向哪个注册表

`random_selector` 的两个位置看着像，指向的注册表不同：

| 形态 | 指向 |
|---|---|
| `{"feature": "X", "placement": [...]}` 里的 `X` | `configured_feature` |
| 裸字符串 `"default": "X"` 或不在内联对象里的 `"feature": "X"` | `placed_feature` |

移植脚本只改了「群系 → 放置特征」和「放置特征 → 配置特征」两层，`configured_feature` 内部的引用没动

后果不是报错而是静默：模组副本把树下的土换成了 `dawnduskterminal:rosy_dirt`，原版那份不会，所以同一片林子里一半的树长在霞土上，另一半长在原版泥土上

已经补完，两件事：

- 内联对象里指向原版的配置特征引用改成模组副本
- 裸字符串指向的那几个原版放置特征（`birch_bees_0002` `oak_bees_002` `jungle_tree` 等 11 个）在模组里补了对应的 `placed_feature` 副本，再把引用指过去

模组现在剩 `swamp_oak` 一个 `configured_feature` 副本没人引用

`birch_tall` 也要算上，但它有个自己那份 `placed_feature` 引用着，而那份 `placed_feature` 已经没群系要了，工具的字符串匹配看不出这种间接孤儿

`tools/check_worldgen_refs.py --list-dead` 会打印它的判定结果

`tools/check_worldgen_refs.py --list-dead` 会打印这两个名字

### 没人引用的放置特征

有 18 个放置特征副本没有任何群系引用：`ore_coal_lower` `ore_coal_upper` `ore_copper` `ore_copper_large` `ore_gold` `ore_gold_extra` `ore_gold_lower` `ore_iron_middle` `ore_iron_small` `ore_iron_upper` `bamboo_light` `dark_forest_vegetation` `flower_flower_forest` `flower_swamp` `red_mushroom_swamp` `trees_swamp` `forest_flowers` `patch_grass_forest`

它们在原版主世界群系里，而本维度的群系特征表已经整份换成暮色森林内容（`tools/port_tf_biomes.py`），所以煤 铜 金 铁四种矿在本维度不生成，改由暮色森林的浆果矿与模组自己的矿替代

这是设计选择不是 bug，但副本本身是移植残留，删不删待定

## 改地形注意

改世界生成后建议删掉 `run/<端>/world` 重建

天光烘焙进区块，改天光后老存档要重算

`AquiferTests` 与 `WorldgenTests` 会断言调色板与流体，改前先看测试
