# 世界生成

维度的 id 为 `dawnduskterminal:chrono`，其生成类型为 `minecraft:noise`，群系源为 `dawnduskterminal:palette`，地形设置为 `dawnduskterminal:chrono`。

该维度的实现思路是：将原版主世界的世界生成整份移植进本模组的命名空间，再将其中的方块替换为本模组的调色板方块。相关的数据文件均位于 `src/main/resources/data/dawnduskterminal/` 目录下。

## 维度类型

维度类型的定义写在文件 `dimension_type/chrono.json` 中。

其中 `fixed_time` 为 12000，即将时间锁定在黄昏，维度内不存在昼夜变化；`bed_works` 为 true，即允许睡觉，但睡眠只重置重生点。`coordinate_scale` 为 1.0，`has_skylight` 为 true，而 `has_ceiling` 与 `has_raids` 为 false。`ambient_light` 为 0.0。`height` 与 `logical_height` 都是 384，`min_y` 为 -64。`effects` 指向 `minecraft:overworld`，因此天空与雾色沿用原版特效。`monster_spawn_light_level` 是 0 到 7 的 uniform。

## 地形设置

地形设置的定义写在文件 `worldgen/noise_settings/chrono.json` 中。

其中 `default_block` 是 `dawnduskterminal:canopy_stone`，`default_fluid` 是 `dawnduskterminal:portal_fluid`，等级为 0。`aquifers_enabled` 是 true，`sea_level` 是 63，`ore_veins_enabled` 是 true。`lava` 噪声指向 `dawnduskterminal:aquifer_lava`。`noise` 的 `height` 为 384，`min_y` 为 -64。`noise_router` 取自移植后的原版主世界路由。`surface_rule` 是一张按群系分支的大表，其默认产物是 `canopy_stone`。`spawn_target` 沿用原版主世界。

## 移植文件

`tools/port_overworld_worldgen.py` 从 Gradle 缓存中的反编译客户端 jar 里抽取原版的 `density_function`、`noise` 与 `noise_settings`，并在改写命名空间后写入本模组。

`density_function/` 目录包含 `overworld/` 子目录。

`noise/` 目录存放移植过来的噪声参数，其中只有 `noise/temperature.json` 的 `firstOctave` 经过改动，详见「温度噪声频率经过手工调整」。

`multi_noise_biome_source_parameter_list/` 目录存放原版主世界与下界的多噪声参数表副本。

`configured_carver/` 目录存放洞穴与峡谷的配置。

`configured_feature/` 与 `placed_feature/` 目录存放移植过来的特征。

移植脚本把群系中的放置特征引用，以及放置特征中的配置特征引用，都改写到 `dawnduskterminal:` 命名空间。

`configured_feature` 内部嵌套的引用不在改写范围之内，因此 `random_selector` 中 `feature` 与 `default` 两个键指向原版的部分需要手工补齐，详见「已知问题」。

## 调色板群系源

调色板群系源由 `world/PaletteBiomeSource` 实现，注册为 `dawnduskterminal:palette`。

维度 JSON 中的 `biome_source` 有三个字段：`preset` 为原版主世界的参数表 `minecraft:overworld`，`palette` 为 `#dawnduskterminal:palette_biomes`，`extra` 为追加的气候点，可以省略。

选择气候格时沿用原版的算法：取出 `preset` 的参数表，在末尾接上 `extra` 中的若干条目，一并交给 `Climate.ParameterList.findValue`。该算法相对原版未作任何改动。

`extra` 的条目形状参照内联 `multi_noise` 的写法：`temperature` `humidity` `continentalness` `erosion` `weirdness` 五段跨度，外加一个 `biome`；深度由代码按点值 0 与 1 各插入一份，与 `OverworldBiomeBuilder.addSurfaceBiome` 的做法一致，若将深度写成跨度，则群系会随 y 跳变。

`getNoiseBiome` 取得群系之后，再把 `minecraft:` 命名空间下的结果替换为同名副本。

换名按路径匹配，例如 `minecraft:plains` 变为 `dawnduskterminal:plains`。

只有一处例外改指了其他格子：热档的 `minecraft:desert` 被换成 `dawnduskterminal:white_land`（白地），详见下文「白地接管昼区」；蓝沙沙漠则位于 `extra` 中，详见「蓝沙沙漠在极夜」。

群系落在哪一带由温度决定，群系源中不存在任何按坐标判定群系的特例，详见「温带分带与温度映射」。

群系副本携带本模组的方块特征表，因此维度在生成期即铺设本模组的方块，而不是在事后进行修补。

上述陈述只对一部分特征成立：57 个 `configured_feature` 中有 46 个引用了模组方块或模组方块标签（`ore_*` `glow_lichen` `ice_patch` `patch_berry_bush` `swamp_oak` 等），其余 11 个是树，只放置原版原木与树叶。

地形的方块替换不依赖特征表，而是依靠 `noise_settings` 中的地表规则。

`worldgen/biome/` 目录中除 `floatrees` `white_land` 之外，其余都是与主世界群系同名的副本。

副本中的内容并不等于同名的原版群系：暮色森林的群系直接写入对应的原版位置，ID 不作改动，显示名依靠 lang 文件设置。每个位置当前装载的内容，记录在 `docs/biomes.md` 中。

`floatrees` 不经过替换表：当采样深度低于 -1.6 时直接返回，这一判断早于选格阶段。

替换表在首次使用时构建，其原因是 biome source 在数据包解析期即被创建，而在那一刻标签 holder 尚未持有 key。

### 蓝沙沙漠在极夜

`dawnduskterminal:blue_desert` 是自建群系，其地表为蓝色的 `road_sand`，气温按雪地设定（`temperature` 为 `-0.45`，`has_precipitation` 为 `true`）；该群系的特征表中没有仙人掌、枯灌木与沙漠水井（原版沙漠的特征表中带有这三项），只保留绿洲的甘蔗与南瓜。

该群系的气候格由一条自建的 `extra` 条目给出：温度为 `[-2, -1]`（极夜），湿度为 `[-1, -0.35]`（偏干的那一半）。极夜带的另一半湿度留给极夜平原 `polar_dusk`，因此在极夜区中「干的地方是蓝沙沙漠，湿的地方是平原」，两者都是自建群系，且各自拥有自己的格子。

`floatrees` 的判断仍在选格之前（依据 `depth`），因此极夜区的空岛仍然保持空岛形态。

夜区（`-0.8 ~ -0.6`）不含蓝沙，该带全部属于雪地族，即雪原、冰刺、雪峰与冰川。

判定门槛并不在 `-0.45` 这个数值上，而在 `0.15` 上：`Biome#warmEnoughToRain` 的判断式为 `getTemperature(pos) >= 0.15`，`coldEnoughToSnow` 即取其反，`shouldSnow` 与 `shouldFreeze` 也使用同一判断。`ChronoFreezeFeature` 依靠这些判断铺设 `canopy_frost` 与 `frozen_canopy`，因此任何小于 `0.15` 的温度都可以满足条件；原版沙漠所用的 `2.0` 与 `false` 才使得蓝沙沙漠完全没有霜。

`getTemperature` 只在 y>80 时继续变冷，不会把 `-0.45` 抬升回 `0.15` 以上。

### 恶地只在昼区

`dawnduskterminal:badlands` 一族沿用原版气候表中恶地所在的那几格，而恶地要求温度落入热档（`0.55 ~ 1.0`），因此它只出现在昼区（`0.6 ~ 0.8`）的台地侵蚀档中。

温带、寒带与热带的温度都由 `ChronoTemperature` 的分段映射固定，无法达到热档，因此这三个带内不会出现恶地。

### 白地接管昼区

`dawnduskterminal:white_land` 占据昼区（`0.6 ~ 0.8`），是恶魂支线 T2 群系所在的位置。

该带的温度被映射固定在 `0.55 ~ 1.0`，而原版气候表在这一档中的五档湿度全部对应 `desert`，因此昼区默认落地的群系就是沙漠。白地通过替换表把该格改指过去：在 `replacements()` 中有 `minecraft:desert → dawnduskterminal:white_land`。

台地侵蚀档中的恶地族不受该替换影响，因此昼区的构成是「白地为主，恶地成片点缀」。

白地是裸地，其地表规则铺设 `siltstone`；其特征是恶地副本去掉地表装饰之后的结果，矿石与洞穴保持不变。

`temperature` 是 `2.0`，`has_precipitation` 是 `false`，因此该群系既不积雪也不长霜。

### 萤火虫森林

`dawnduskterminal:birch_forest` 装载的是暮色森林 `firefly_forest` 的移植结果，其中的树、结构、灌木、浆果与萤火虫粒子都保持原样；该群系的 `temperature` 为 0.5，`has_precipitation` 为 `true`。

其 ID 仍为 `birch_forest`，因为占位并不改动注册名，从 ID 即可看出它替代的是哪一格。显示名在 lang 文件中改为「萤火虫森林」。

### 红草树林

`dawnduskterminal:old_growth_birch_forest` 装载的是上文那份萤火虫森林内容，只把草地与树叶染成红色：`grass_color` 与 `foliage_color` 给出的是红色，`temperature` 提高到 `0.8`。

该群系同样不改动 ID，显示名在 lang 文件中改为「红草树林」。这样一来，`birch_tall` 这个特征就没有任何引用者，详见下文「特征移植」。

### 黑森林腹地

`dawnduskterminal:dark_forest` 对应的就是暮色森林的 `dark_forest_center`，详见 `tools/port_tf_biomes.py` 中的对照表。

树叶由 `ChronoFoliageColors` 固定染成 `0xE94E14`，草方块则使用暮色森林 `dark_forest_center` 的调色器。

### 天门松

`configured_feature/tree/skygate_pine.json` 描述的是文竹式分层的树：树干为 20~30 格的直筒，4~8 根枝分布在树干 28% 到 90% 的高度上，每根枝的顶端托着一团压扁的樱花叶团。

树干由模组自有的 `dawnduskterminal:skygate_branching`（`world/SkygateBranchTrunkPlacer`）生成：原版 `cherry_trunk_placer` 最多只生成三根枝，且贴附于顶部；暮色森林的 `branching_trunk_placer` 其分枝则是逐格向上叠加的连排。两者都无法铺满树干的中下部，因此树干高度在代码中计算。枝的方向按方位角逐根随机确定，落点取在圆周上，再沿三维 Bresenham 直线走过去：每步只落一块原木，主轴每步一格、副轴按比例进位，因此 45 度、30 度这类斜枝也会生长，而且一根枝只占一条线宽。这套打法与暮色森林苍穹木的 `twilightforest:branching_trunk_placer` 一致（其 `VoxelBresenhamIterator` 同理）；不应用逐格单轴步进——斜向时每前进一格要落两块原木，枝会显得粗。抬升量只在起枝时掷一次，枝上不再补竖木。可调项如下：

| 字段 | 作用 |
|---|---|
| `branches.provider` | 枝使用的方块，`BlockStateProvider`。取 `dawnduskterminal:skygate_pine_wood` |
| `branches.count` | 分枝根数，`IntProvider` |
| `branches.length` | 每根枝在圆周上的伸出半径，`IntProvider` |
| `branches.band` | `{min, max}`，分枝高度带占树干高度的比例 |
| `branches.rise_chance` | 每根枝抬起一段的概率，抬起的高度为 1 到 `steps / 3 + 1` 格，不超过树干顶 |
| `radius_offsets.branch` | 每根枝的树冠水平尺寸偏移，`IntProvider`，逐枝抖动 |
| `radius_offsets.crown` | 树干顶上那团的水平尺寸偏移，固定值，比枝顶大一档用来封顶 |

分枝参数收在 `branches` 一个对象里，而不是平铺在 `trunk_placer` 下：1.21.1 的 `RecordCodecBuilder` 最多只能拼到八项，加上 `branches.provider` 之后平铺字段是九项，编不出来。

枝用的不是原木而是天门松木：原木两端的横截面贴图会露在斜枝的侧面上，木用的是原木侧面贴图铺满六面，与苍穹木的做法一致。

树形的断言在 `SkygatePineTests`：树高为 20~30，分枝至少覆盖四个不同的高度，且这些高度同时落在树干的下半部与上三分之一；离干的位置只准出现天门松木、不得出现原木，也不得竖直叠块，用来钉住「侧枝只占一块厚」。

叶团使用 `minecraft:cherry_foliage_placer`：`height` 取到该 placer 允许的下限 4（原版樱花取 5），垂直尺寸至此确定；`radius` 随树浮动，再叠加 `radius_offsets`，因此横向尺寸比原版樱花大一圈，并且每团的尺寸并不相同。需要注意的是，placer 内部的计算为 `i = radius + radiusOffset - 1`，最宽的一层位于自下往上第二层，其半径即 `i`：`radius` 取 3、`branch` 取 -1 时 `i` 只剩 1，叶团会缩成 3x3 的薄片，因此不应继续调低。

挂藤使用原版的 `minecraft:leave_vine`：它在树叶侧面的空位挂出藤，再向下垂落一到四格，恰好形成「藤长在树叶边上」的效果。该 placer 只有一个 `probability`，其含义是**每片树叶的每个侧面各掷一次**，因此密度随树冠大小线性增长：`0.25`（原版丛林树的取值）在这棵树上会生成几百个藤方块，而 `0.01` 实测生成 16 个藤方块、7 条藤（叶约 450 片）。调整密度时应按线性关系换算。

不应使用 `minecraft:trunk_vine`：它挂附于树干，每根原木的每一面都有 2/3 的挂藤概率，二十多格的树干会被挂成藤柱。本模组曾经自行实现过 `sparse_leaf_vine`，它按「条数」随机挑选树叶再向旁边挂藤，结果绝大多数随机数落在叶团内部（其旁边并非空气），实际挂出的条数远少于配置值，该实现已被删除。

`minimum_size` 是 `limit 1` / `lower_size 0` / `upper_size 2`，与原版樱花一致；旧配置的 `limit 20` 加 `upper_size 5` 等于要求树干高处 11x11 的范围全部为空地，树几乎无法生长。

## 温带分带与温度映射

温度偏移施加在 `PaletteBiomeSource#getNoiseBiome` 中：该处照原样复制原版 `Climate.Sampler#sample` 的六项，仅把温度替换为 `ChronoTemperature.shift`。

因此区块落盘、结构判定、`/locate biome`、刷怪与雕刻读取的是同一个群系函数。`RandomState` 与 `NoiseChunk` 未作任何改动，`router` 不会进入包装器，`locate structure` 也不会再因插值层而变慢。

采样器只提供原版温度，偏移不依赖该温度来自哪一个采样器，详见「偏移仅在一处施加」。

### 七带与断点

`ChronoTemperature` 用六个断点把 `param` 切成七个带，每个带线性映射到原版气候表中的一段温度：

| 带 | `param` | 温度映射 | 实宽（`maxDistance 5000`） |
|---|---|---|---|
| 极夜 | `<= -0.8` | `-1.00 ~ -2.00`（冻结档再往外，`extra` 的极夜平原与蓝沙沙漠） | 1000+ |
| 夜区 | `-0.8 ~ -0.6` | `-1.00 ~ -0.45`（冻结档：雪原 冰刺 雪峰 冰川） | 1000 |
| 寒带 | `-0.6 ~ -0.3` | `-0.45 ~ -0.15`（针叶林 平原 云杉） | 1500 |
| 温带 | `-0.3 ~ 0.3` | `-0.15 ~ 0.20`（平原 森林 桦树 黑森林） | 3000 |
| 热带 | `0.3 ~ 0.6` | `0.20 ~ 0.55`（热带草原 丛林） | 1500 |
| 昼区 | `0.6 ~ 0.8` | `0.55 ~ 1.00`（热档：白地 恶地） | 1000 |
| 极昼 | `>= 0.8` | `1.00 ~ 2.00`（热档再往外，`extra` 的极昼群系） | 1000+ |

断点取用温度档自身给出的数值：`-1.0 -0.45 -0.15 0.20 0.55 1.0`。各段首尾相接，因此整条映射在 `param` 上连续，不需要 clamp。

因此每一个带只对应原版的一档温度：针叶林要求温度 `<= -0.15`，而热带的温度下界是 `0.20`，两者之间隔着一整个温带，针叶林不会生成于热带。

### 带内噪声

在映射之上叠加的是原版温度噪声乘以 `ChronoTemperature.SPREAD`（`0.08`）的结果。

噪声不决定分带，只让分带边界发生弯折：带内温度已被映射固定在一档之内，噪声再大也只在该档内摆动，仅在边界两侧各形成约 `SPREAD` 宽的过渡。

温带即使计入噪声，也达不到沙漠与雪原的门槛：上界为 `0.20 + 0.08 < 0.55`，下界为 `-0.15 - 0.08 > -0.45`。`WorldgenTests` 对这两条界线作出断言。

若希望边界趋于平直，可将 `SPREAD` 设为 0；若将其增大到 `0.15` 以上，夜区就会开始向温带渗透雪原。

### 温度噪声频率经过手工调整

`noise/temperature.json` 是从原版 `minecraft:temperature` 逐字复制而来的，但**频率经过改动**：`firstOctave` 从 `-10` 提高到 `-7`，`amplitudes` 未作改动。

原版主世界并不存在「带边界」这一概念，温度噪声本身就是气候，因此它使用 `firstOctave -10`，即波长 `4096 / 1024` 格。该尺度比七带中的任何一个带（`1000 ~ 3000` 格）都宽，若用于晨昏线维度，只会使边界在数公里的尺度上缓慢漂移，无论在带内部还是带边上都看不出结构。

提高到 `-7` 之后，波长为 `512 / 128` 格（即 `2^7 / xz_scale 0.25` 与 `2^5 / xz_scale 0.25`）：其中较宽的一档使边界在带内起伏，较窄的一档把「与晨昏线平行」的群系边界打散。幅度未作任何改动，因此抖动幅度、`±4.4444` 的值域与泄漏上界全都不变。

重新运行 `tools/port_overworld_worldgen.py` 会写回原版的 `-10`（该脚本执行的是忠实拷贝，没有例外表），`WorldgenTests.temperatureNoiseRunsAtTheBandScale` 即用于在此时报警。

### 交界抖动

仅依靠上述噪声不足以支撑交界处的形态：`512` 格对于它自身需要打散的边界而言仍然偏宽，在交界处观察仍能看出一条弯曲很缓的线。

因此在断点两侧再叠加一层 `ChronoTemperature.seamDither`：它是三个八度的值噪声，波长是 `SEAM_WIDTH` 的 `1 / 0.3 / 0.1`（在默认 `5000` 下即 `400 / 120 / 40` 格），权重为 `1 / 0.5 / 0.25`，幅度为 `SEAM_DITHER`（`0.09`），再乘以一个按到最近断点的距离计算的权重（断点上为 1，离开 `SEAM_WIDTH` 即 `0.08` 个 `param`、默认约 400 格后归 0，中间采用 smoothstep 过渡）。

交界于是呈现为两侧群系互相侵入所形成的楔形与小片飞地；带心不参与抖动，该带内的群系分布与只叠加 `SPREAD` 时一致。

`SEAM_WIDTH` 和 `SEAM_DITHER` 的取舍关系如下：`SEAM_DITHER` 越大，楔形越深，但同时留出的余量越小（详见下一条）；`SEAM_WIDTH` 决定的是「交界有多宽」，若调到 `0.15` 以上，就会从中间侵蚀掉窄带（夜区、昼区与极带的宽度都只有 `0.2`），使整个带变成抖动区。

带宽与抖动波长都以 `param` 尺度表达，与七带的宽度相同，因此两者都随 `maxDistance` 一同缩放：`maxDistance` 从 `5000` 改为 `2500` 时，带宽减半，抖动波长随之减半，交界不会相对变粗。代码中不存在任何以格为单位的抖动常数——`seamDither` 先把坐标除回 `param` 尺度（除数与 `ChronoLine.param` 所用的一致），再在 `param` 空间中计算值噪声。

抖动是 `param` 与 `(x, z)` 的纯函数：它不读取注册表，也不读取配置，因此跨区块、跨线程以及重开存档时都是同一张图；值噪声采用 smoothstep 插值，4 格内的变化不到 `0.01`，不会在森林中切出格子状的断层。

交界抖动与 `SPREAD` 可能同时达到满幅，因此泄漏的上界是两者之和（`0.17`）：温带上界为 `0.20 + 0.17 < 0.55`，下界为 `-0.15 - 0.17 > -0.45`，仍然达不到沙漠与雪原的门槛。`WorldgenTests` 对这两条界线作出断言。

### 值域

温度噪声原本是 `shifted_noise`，其噪声为 `dawnduskterminal:temperature`，`amplitudes` 为 `[1.5, 0, 1, 0, 0, 0]`，`firstOctave` 为 `-7`（经过手工调整，详见「温度噪声频率经过手工调整」），声明的值域为 `±4.4444`。

中间五个带的输出落在 `[-1.17, 1.17]` 之内（带端点加减 `SPREAD` 与 `SEAM_DITHER`）；两个极带继续向外推移，`param` 被钳制到 `±1` 时温度达到 `ChronoTemperature.POLAR_TEMPERATURE`（`2.0`），因此 `minValue` / `maxValue` 为 `±2.0`。极带内抖动的余量足够大：在 `param` 为 `0.9` 处温度为 `1.5`，减去满幅抖动后仍有 `1.41`，稳定地留在 `extra` 的 `[1, 2]` 区间之内。

满足 `|param| >= 0.8` 的两个极带不叠加 `SPREAD` 对应的那条低频噪声：该噪声一旦压低，就可能把极带边界推出 `extra` 的 `±1 ~ ±2` 跨度，使边界变成毛边。交界抖动不受这一限制，极带边界（`±0.8`）仍然受其影响，因此极昼、极夜与昼区、夜区之间也会互相咬合。

### 新增群系的两种途径

**一条是 `extra`**：原版参数表只覆盖 `[-1, 1]` 的温度范围，越界的地带是空的，因此在 `extra` 中写入一条温度跨度越界的条目，它就必定胜过原版最外侧的那一行。极昼与极夜就是这样实现的——`ChronoTemperature` 把 `param` 推到 `±1` 时温度达到 `±2`，恰好落入 `[1, 2]` 与 `[-2, -1]` 两条。

**另一条是替换表**：温度带内部没有空白地带，若要在其中新增群系，只能替代掉某一格，白地采用的是这一条，详见 `docs/biomes.md` 中的替换链。

湿度、大陆性、侵蚀与怪异度在原版中都已铺满 `-1 ~ 1`（大陆性到 `-1.2 ~ 1.0`），在这些方向上追加条目必定处于劣势，因此 `extra` 实际上只能用于温度的越界情形。

若希望完全不按六个参数排布（例如像暮色森林那样自行计算布局），则必须重写整个 `getNoiseBiome`，本模组不采用这种做法。

### 超界温度仍然可以选出群系

`Climate.ParameterList.findValue` 以六个参数的平方距离之和最小者为选取依据（对应 `Climate.ParameterPoint.fitness`），其中单项距离由 `Climate.Parameter.distance(long)` 给出：数值落在 `[min, max]` 之内时距离为 0，落在范围之外时则为到最近端点的差值。

因此 `1.5` 或 `-1.4` 这类温度既不会出错，也不会退回默认值：表中没有对应跨度时选取最近的那一行，`extra` 中有条目时则选中该条目。极昼与极夜正是依靠这一机制得以生效。

## 标签

`tags/worldgen/biome/palette_biomes.json` 列出全部群系副本。

`tags/block/` 下是本模组的方块标签，供移植过的特征引用。

`data/minecraft/tags/worldgen/biome/` 把副本挂入结构生成与刷怪规则所用的标签。

`data/minecraft/tags/block/` 把模组方块加入原版的石类、土类、沙类与树叶等标签。

`data/twilightforest/tags/worldgen/biome/` 把副本挂入暮色森林结构的群系标签，目前仅有 `valid_hollow_tree_biomes`。

## 世界生成补丁

数据包无法触及的原版硬编码由 mixin 接管，逐条说明见 `architecture.md`。

`LavaBandPaletteMixin` 负责处理 y 以下的整条岩浆带。

`LavaAquiferPaletteMixin` 负责处理深部岩浆含水层。

`PillarsFluidBlockMixin` 使石柱洞穴保持干燥。

`NoiseChunkVeinPaletteMixin` 负责处理大矿脉的花岗岩与凝灰岩。

温度的晨昏线偏移不在此处实现，而是由群系源处理，详见「温带分带与温度映射」。

`MineshaftHeightMixin` 与 `MineshaftChestLootMixin` 用于限制废弃矿井的高度并替换其战利品表。

识别维度统一依靠噪声设置的 `default_fluid` 是否为 `portal_fluid`。

## 结构

`structure/StructureExclusions` 是白名单，只放行废弃传送门、沉船、前哨站、沙漠神殿、雪屋与废弃矿井，以及暮色森林的树洞、倒下空心木、蘑菇城堡、营地、树篱迷宫与空心矿山。

`StructureExclusionMixin` 负责排除不在白名单之内的结构。

同一文件中的 `UNMAPPED` 与 `LANDMARKS` 记录未放行的暮色森林结构与地标，它们仅服务于日志，不影响行为。

### 高大暮色橡树

高大暮色橡树（Robust Twilight Oak，`twilightforest:hollow_tree` 结构，即由 `hollow_oak_sapling` 长出的那一棵）不是 placed_feature，不经过群系特征表，因此仅有白名单放行还不够：它的群系选择器是 `#twilightforest:valid_hollow_tree_biomes`，而该标签原本只列出暮色森林自身的群系。

`data/twilightforest/tags/worldgen/biome/valid_hollow_tree_biomes.json` 以 `replace: false` 把 forest、birch_forest、old_growth_birch_forest、flower_forest、dark_forest、mushroom_fields 与 savanna 七份副本追加进去。结构在 `findValidGenerationPoint` 中查询的正是这个 holder set，一旦标签缺失，巨树就会静默消失，因此 `WorldgenTests.hollowTreesAcceptTheChronoBiomes` 对这一点作出断言。

结构集 `twilightforest:hollow_tree` 的 spacing 为 7，separation 为 5，其池子中一半的权重属于 `twilightforest:swamp_hollow_tree`，而该结构把群系固定为 `twilightforest:swamp`，该群系在晨昏线维度中并不存在，因此实际密度大约再减半。

### 偏移仅在一处施加

早先温度偏移包装在 `NoiseChunk.cachedClimateSampler` 之中，只有区块落盘的群系带有偏移；树洞判定所读取的 `context.randomState().sampler()` 不含偏移，于是两处判出不同的群系，巨树因此生成于沙漠区域内。当时改用 `HollowTreeSurfaceBiomeMixin` 仅为树洞重新计算一次，其他白名单结构与 `/locate biome` 的结果仍然是错的。

也曾尝试把偏移整体迁移到 `RandomState`（同时包装 `router` 与 `sampler`），该方案可以运行，但性能严重下降：包装器进入了 `NoiseChunk` 的噪声图，`locate structure` 的耗时从 0.1 秒变为单刻超过 60 秒，直接触发看门狗。

目前偏移只在 `PaletteBiomeSource#getNoiseBiome` 中执行一次。两个采样器在同一坐标给出的原版温度数值相同（仅仅是缓存包装不同），偏移又只存在于这一处，因此最终群系不可能再出现分叉。`NoiseChunkTemperatureMixin` 与 `HollowTreeSurfaceBiomeMixin` 已被删除，`ChronoTemperature` 也不再包装任何 `DensityFunction`。

### 温度缓存

`PaletteBiomeSource` 中有一份每线程八槽的缓存，其键为 `(quartX, quartZ)`，原因是晨昏线维度的温度与 y 无关（`noise_settings/chrono.json` 的 `y_scale` 与 `shift_y` 都是 0）。

原版 `LevelChunkSection#fillBiomesFromNoise` 的循环顺序为 x 在外层、y 在中层、z 在内层，同一个 `(x, z)` 在区块填充过程中会被查询 96 次，命中率超过 95%。当采样器更换（即换区块）或晨昏线的 `max_distance` 发生变化时，整个缓存表失效，因此不会跨区块混用数值，也不会持有旧 `NoiseChunk` 的函数图。

## 高度

地形高度范围为 -64 到 320。

原版主世界的密度函数被整份移植，因此地形形态与原版一致。

空岛与地下浮岛仍然保留：`sloped_cheese` 中叠加的 `sky_island_density` 与 `subterranean` 分别负责 y 的高处与 y<0 的部分。

早期的四带设计（大陆、空置域、空岛与地下岛）在接管地形噪声的那一次改动中被整份替换，`VoidRegionFeature` 一并被删除。

### 空岛判据与云层

`sky_island_density` 的两段 `y_clamped_gradient` 在 140~240 与 260~310 之外不再发生变化；当噪声整体偏高或偏低时，岛体只剩噪声零点附近的一薄片。

判据只有一条：格点的 `sky_island_density` 大于 0 且大于 `overworld/base_3d_noise`，就认为这一点的实心来自空岛。`sloped_cheese` 是两者的 `max`，所以山体在 140~196 与空岛带重叠的那一段靠 `base_3d_noise` 胜出被排除，不需要额外的高度、厚度或邻接扫描。实现见 `world/SkyIslandDensity`。

这两个函数实例从 `RandomState` 已经换过种子的函数树里抓。`RandomState` 会把 `HolderHolder` 换成直接持有者，引用键随之消失，所以按名字在生成期找不到它们；两个 JSON 各套一层值直通的 `dawnduskterminal:sky_island_marker`，按类型认。抓取挂在 `NoiseBasedChunkGenerator.createBiomes` 的 `HEAD`，不能挪到构造函数，那一刻注册表还没绑定。抓不到时判据一律返回 false，整个模组退化成「没有空岛」。

`SkyIslandClouds` 在 `FEATURES` 的末尾（即 `ChunkGenerator.applyBiomeDecoration` 的 `TAIL`）给每个空岛列挂云：从岛底下 1 到 9 格、横向 ±4 格的范围内，逐格算「距离衰减 + 三维噪声」，超过门槛就放 `twilightforest:wispy_cloud`。不看这一列在岛上的位置，边缘、平直与向下鼓出来的一律同一条判据，形状完全由世界坐标上的噪声决定，相邻区块各算各的也不会错位。厚度达到 3 的岛列另有概率把最底一层换成云，保证岛体至少留两格。

列顶读取的是 `WORLD_SURFACE_WG` 高度图。这套高度图在 `CARVERS` 之后不再被 `ProtoChunk.setBlockState` 更新，因此停留在纯地形的高度上，树叶与草不会把列顶撑高；若换成 `WORLD_SURFACE`，则会把树计算在内。

该处理不看群系，只看判据：晨昏线维度中任何群系的空岛都走同一条路径。

## 已知问题

`data/dawnduskterminal/tags/worldgen/biome/` 下的旧标签只保留 `palette_biomes` 一项。

### 引用指向哪个注册表

`random_selector` 的两个位置看起来相似，但指向的注册表不同：

| 形态 | 指向 |
|---|---|
| `{"feature": "X", "placement": [...]}` 里的 `X` | `configured_feature` |
| 裸字符串 `"default": "X"` 或不在内联对象里的 `"feature": "X"` | `placed_feature` |

移植脚本只改动了「群系 → 放置特征」与「放置特征 → 配置特征」两层，`configured_feature` 内部的引用未被改动。

其后果不是报错，而是静默出现：模组副本把树下的土换成了 `dawnduskterminal:rosy_dirt`，原版的那一份则不会替换，因此同一片林子中一半的树长在霞土上，另一半长在原版泥土上。

该问题已经补齐，具体有两项：

- 内联对象中指向原版的配置特征引用已改为模组副本。
- 裸字符串所指向的那几个原版放置特征（`birch_bees_0002` `oak_bees_002` `jungle_tree` 等 11 个）在模组中补上了对应的 `placed_feature` 副本，再把引用指向该副本。

模组目前剩下 `swamp_oak` 一个 `configured_feature` 副本没有任何引用者。

`birch_tall` 也应当计入，但它有一份属于自己的 `placed_feature` 引用着，而那份 `placed_feature` 已经没有群系需要，工具的字符串匹配无法识别这种间接孤儿。

`tools/check_worldgen_refs.py --list-dead` 会打印上述判定结果。

该命令同时会打印 `swamp_oak` 与 `birch_tall` 这两个名字。

### 没人引用的放置特征

有 18 个放置特征副本没有任何群系引用：`ore_coal_lower` `ore_coal_upper` `ore_copper` `ore_copper_large` `ore_gold` `ore_gold_extra` `ore_gold_lower` `ore_iron_middle` `ore_iron_small` `ore_iron_upper` `bamboo_light` `dark_forest_vegetation` `flower_flower_forest` `flower_swamp` `red_mushroom_swamp` `trees_swamp` `forest_flowers` `patch_grass_forest`。

这些特征位于原版主世界的群系中，而本维度的群系特征表已经整份换成暮色森林内容（`tools/port_tf_biomes.py`），因此煤、铜、金、铁四种矿在本维度不会生成，改由暮色森林的浆果矿与本模组自有的矿替代。

这是设计上的选择，并非 bug；但副本本身属于移植残留，是否删除尚未确定。

## 修改地形的注意事项

改动世界生成后，建议删除 `run/<端>/world` 并重新生成。

天光会被烘焙进区块，因此改动天光后，旧存档需要重新计算。

`AquiferTests` 与 `WorldgenTests` 会对调色板与流体作出断言，改动之前应先查看相关测试。
