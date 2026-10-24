# DawnDuskTerminal 开发要点

## 项目位置
- 源码 `c:\Users\admin\Desktop\ysm\DawnDuskTerminal`
- 部署点 `C:\pcl2\lm2\neo\.minecraft\versions\1.21.1-NeoForge_21.1.252\mods\dawnduskterminal-0.1.0.jar` 和 `C:\Users\admin\Desktop\dawnduskterminal-0.1.0.jar`
- 截图目录 `C:\pcl2\lm2\neo\.minecraft\versions\1.21.1-NeoForge_21.1.252\screenshots`
- 文档 `c:\Users\admin\Desktop\ysm\md一类文件\`
- 生成地形的脚本 `tools/port_overworld_worldgen.py`（本仓库内 从原版主世界移植）
- 换贴图脚本 `c:\Users\admin\Desktop\ysm\swap_textures.py`

## 环境
- MC 1.21.1 + NeoForge 21.1.252 + JDK 21
- Python `C:\Users\admin\AppData\Local\Python\pythoncore-3.14-64\python.exe`
- 测试服 25599 世界 `run/server/world` 每次删掉重建
- 跑 gametest：暮色森林 jar 放进 `run/gametest/mods/` 并建 `run/gametest/eula.txt`（`eula=true`）再 `gradlew runGameTestServer`
- **gametest 服务器里没有本模组的维度** `GameTestServer` 用 `WorldPresets.FLAT` 烤世界（`GameTestServer.java:97`），FLAT 预设只含原版三维度
  所以 `server.getLevel(dawnduskterminal:chrono)` 在 gametest 里恒为 null，涉及该维度的断言只能跳过
  想验证维度本身要跑 `runServer` 或客户端
- **`run/gametest/world` 不会自动清空** 就地改方块的测试第二次跑会看到上次的结果
  要么换随机区块 要么像已删除的 `paletteRemapRewritesAGeneratedChunk` 那样发现「已经是结果」就直接通过
- **gametest 偶尔卡在 `ChunkMap.processUnloads` 不退出** 进度条一直打但 `testTracker.isDone()` 恒为 false
  线程转储显示服务端线程在区块保存循环里烧 CPU 与本模组代码无关
  删掉 `run/gametest/world` 再跑即可 干净的一次运行是 31 项 329ms 全过
  （之后又加了 `CanopyFrostMeltTests` 现共 32 项 未跑 见下文）
  （会往 `run/gametest/logs/` 里刷上百 MB 进度条日志 卡住那次的两个 log 压缩后还有 3MB）

## 世界生成（已改为原版主世界移植）
维度不再自造地形 也不再分带

`src/main/resources/data/dawnduskterminal/worldgen/` 下全部由脚本生成 不要手改

| 目录 | 内容 |
|---|---|
| `noise/` | 42 个噪声参数 与主世界同名同参数 |
| `density_function/` | 19 个密度函数 主世界噪声路由器整套 |
| `noise_settings/chrono.json` | 主世界 `noise_settings/overworld.json` 的移植 |

生成脚本 `tools/port_overworld_worldgen.py`
- 从 Gradle 缓存的 `caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar` 提取原版 `data/minecraft/worldgen/`
- 把引用重新指向本命名空间 并校验每条引用都能解析
- `--check` 只校验不写盘

```
python tools/port_overworld_worldgen.py
python tools/port_overworld_worldgen.py --check
```

### 种子派生（关键机制）
`Noises.instantiate` 里建噪声场是

```
NormalNoise.create(randomFactory.fromHashOf(噪声键的资源位置), 参数)
```

而 `RandomState.random = 噪声设置.随机源.newInstance(世界种子).forkPositional()`（`RandomState.java:36`）

所以噪声场 = f(世界种子, 噪声键资源位置)
同一份参数注册到 `dawnduskterminal:` 下 噪声场就随之改变
维度地形于是有主世界的质感 却从世界种子派生 而不是同坐标的主世界复制品

数据包派生不到的部分（仍是原版那一份）
- `old_blended_noise` 基础三维噪声 引擎硬编码用 `minecraft:terrain` 播种（`RandomState.java:84-87`）
- 含水层与矿脉的位置随机 硬编码 `minecraft:aquifer` `minecraft:ore`（`RandomState.java:38-39`）
- `minecraft:zero` `minecraft:y` `minecraft:blend_*` 代码注册的单例

### 群系
维度直接用 `minecraft:multi_noise` 的主世界预设
自带群系、`neoforge/biome_modifier/`、`tags/worldgen/` 全部移除
`ChronoBiomeSource` `ModBiomeSources` `BiomeReplacement` 一并删除
`StructureExclusionMixin` 改用噪声设置键 `dawnduskterminal:chrono` 识别维度 不再看 BiomeSource

### forest 已替换为暮色森林森林（twilightforest:forest）
`dawnduskterminal:forest` 的内容已从原版森林换成 `twilightforest:forest` 的副本（id 不变 所以 PaletteBiomeSource、地表规则的 biome_is、palette_biomes 标签都不用动）
- carvers 沿用本 mod 的 `dawnduskterminal:cave/cave_extra_underground/canyon`（洞穴照常 不用 TF 的 tf_caves）
- effects/features/spawners 直接引用 `twilightforest:*`；`minecraft:*` 的 feature（disk_* / patch_* / freeze_top_layer）映射到 `dawnduskterminal:*` 以保持调色板
- 地表仍由噪声设置的 surface_rule 铺霞草/霞土 不受群系内容影响
- 源 JSON 取自实际 TF jar（`run/client/mods/twilightforest-1.21.1-4.8.3345-universal.jar` 内 `data/twilightforest/worldgen/biome/forest.json`）
- **坑** GitHub `1.21.x` 分支 HEAD 比 4.8.3345 新：其 `music` 是加权列表 `[{data,weight}]` 且带 `music_volume`，NeoForge 21.1.252 的 `BiomeSpecialEffects` 只认单个 `music` 对象、无 `music_volume`，照抄会世界创建崩溃（`Not a JSON object`）。以 jar 内实际 JSON 为准
- **注意** TF forest 的 `monster` 列表为空 替换后 chrono 森林里不刷原版敌对生物（TF 在自己的维度用自定义刷怪规则） 是否补回 vanilla monster 待定

### 更多 TF 群系批量移植（9 个，脚本生成）
`tools/port_tf_biomes.py`（转换）+ `tools/check_tf_biomes.py`（校验），映射如下（id 不变、carvers 沿用本 mod、`minecraft:*` feature 映射 `dawnduskterminal:*`、`twilightforest:*` 直接引用）：

| TF 群系 | → 本 mod 群系 |
|---|---|
| dark_forest_center 黑暗森林腹地 | dark_forest |
| firefly_forest 萤火虫森林 | birch_forest |
| dense_forest 繁密森林 | flower_forest |
| enchanted_forest 魔法森林 | jungle |
| fire_swamp 火焰沼泽 | swamp |
| clearing 暮光平原 | plains |
| oak_savannah 橡木热带草原 | savanna |
| glacier 极光冰川 | snowy_plains |
| highlands 荆棘高地 | windswept_hills |

- dark_forest_center / enchanted_forest 带自定义 `grass_color_modifier: "twilightforest:*"`，走 NeoForge 可扩展枚举（`@NamedEnum` + `IExtensibleEnum`）能解析，无需处理
- 10 步 `features` 数组合法（TF 自己就发 10 步的群系 dark_forest_center/highlands）

### TF 树叶着色（foliage color，client）
TF 的树叶着色不是群系 JSON 字段，而是 ASM 变换器给 `BiomeColors.FOLIAGE_COLOR_RESOLVER` 打补丁，再按 TF 自己的群系 key（`twilightforest:enchanted_forest` 等）查表。chrono 副本是 `dawnduskterminal:*`，查不到，所以树叶没被着色。
- `BiomeColorsFoliageMixin`（client mixin）在 `<clinit>` 包装 resolver，`ChronoFoliageColors` 复刻两个算法：
  - `jungle`（魔法森林）→ `enchanted`：基于 `getNearestCenterXZ` 确定性网格中心（不依赖世界种子/结构）的位置蓝色渐变
  - `dark_forest`（黑暗森林腹地）→ 固定 `0xE94E14`（TF 的 `darkForestCenterFoliage` 噪声分支 `(noise+1)/2 < -0.1` 恒 false，永远返回这个色）
- 只对这两个群系生效，其它（含 TF 自己维度）走原 resolver，不破坏 TF 的 ASM 补丁
- 反向查群系 key 用 `registryAccess().registryOrThrow(Registries.BIOME).getResourceKey(biome)`（`lookupOrThrow` 返回 RegistryLookup，没有 getResourceKey；TF 源码里写 lookupOrThrow 是它有 AT/不同版本）

### 温度晨昏偏移（temperature 换成晨昏线 param 的分段映射）
偏移点在 `PaletteBiomeSource.getNoiseBiome`：照抄 `Climate.Sampler.sample` 的六项 只把温度换成 `ChronoTemperature.shift`
于是区块落盘 结构判定 `/locate biome` 刷怪 雕刻走同一个群系函数 不会再出现两个采样器判出不同群系
- 不再包装 `RandomState.router` 或 `NoiseChunk` 的 `DensityFunction`，`locate structure` 不会被插值层拖慢
- 温度缓存在 `PaletteBiomeSource` 每线程八槽 键是 `(quartX, quartZ)`，前提是本维度温度与 y 无关
- 换采样器或 `max_distance` 变化时缓存整表失效
- 只有 chrono 维度受影响 主世界温度不受影响

### 方块调色板（两层）
能由世界生成数据直接写出的先写进数据 写不出的才在生成后补换

**第一层 数据：地形主体 / 含水层 / 地表规则**

`tools/port_overworld_worldgen.py` 末尾的 `apply_palette()` 把 `PALETTE` 应用到移植出来的
`noise_settings/chrono.json`：`default_block`、`default_fluid`、以及 `surface_rule` 里每个 `"Name"`
所以地形主体直接生成 `canopy_stone` 海洋与含水层直接生成 `portal_fluid` 地表直接铺模组方块
脚本 `--check` 会列出该文件还能铺出的所有方块 并断言没有调色板来源残留

`SurfaceSystem` 的两条相关行为（已反编译确认 换 `default_block`/`default_fluid` 不会破坏地表规则）
- `SurfaceSystem.java:129` 只要方块有非空流体就算「水面高度」 不查 `#minecraft:water` 标签
- `SurfaceSystem.java:149` 只对 `defaultBlock` 应用地表规则

**第二层 数据：维度自带的群系与特征副本**

特征是群系带的、群系是全局注册表 所以只在一个维度里换掉特征 唯一办法是让维度自带一套群系副本
`tools/port_overworld_worldgen.py` 的 `build_palette_layer()` 生成：

| 内容 | 数量 | 做法 |
|---|---|---|
| 群系副本 | 53 | 取 `#minecraft:is_overworld` 里的群系 只保留引用了受影响放置特征的 |
| 配置特征副本 | 52 | 先取引用了调色板方块的 再向上闭包 否则原版父特征仍指向原版子特征 |
| 放置特征副本 | 151 | 引用自带配置特征 再按群系实际用到剪枝 |
| 群系标签合并 | 50 | 原版群系标签里出现的原版群系 把对应副本也加进去 |

id 替换是逐层的：特征文件换方块 id 放置特征换配置特征 id 群系换放置特征 id

**第三层 代码：三个绕不开的点**

1. `PaletteBiomeSource`（类型 id `dawnduskterminal:palette`）
   原版气候表不在数据里：`MultiNoiseBiomeSourceParameterList.DIRECT_CODEC` 只有 `preset` 一个字段
   表由 `OverworldBiomeBuilder`（1146 行）在代码里生成 所以自带群系必须配一个群系源
   它委托原版 `multi_noise` 预设 再把每个 `minecraft:X` 按同名换成 `dawnduskterminal:X`
2. `LavaBandPaletteMixin`
   `NoiseBasedChunkGenerator.createFluidPicker` 把 `y < min(-54, seaLevel)` 固定成 `Blocks.LAVA`
   这层不由 `default_fluid` 决定 数据包改不了
   该方法是 `private static` 而 `@ModifyArg` 拿不到外层参数 所以用 `@Inject(HEAD, cancellable)` 整体接管
   维度判定用 `settings.defaultFluid()`（是 portal_fluid 即 chrono）
   **升级 Minecraft 时重点看这里** 它复制了原版逻辑
3. `LavaAquiferPaletteMixin`（配 `FluidStatusAccessor`）
   `Aquifer.NoiseBasedAquifer.computeFluidType` 把流体面 `<= -10` 的深含水层在 `aquifer_lava` 噪声
   `|x| > 0.3` 时硬编码成 `Blocks.LAVA` —— 这是独立于上面岩浆层的**第二处**硬编码岩浆，`createFluidPicker` 换不掉它
   同样 `@Inject(HEAD, cancellable)` 整体接管，把 `Blocks.LAVA` 换成 enthu_fluid
   维度判定看 `status.fluidType`（global picker 已被上面 mixin 换成 portal_fluid/enthu_fluid，原版水/岩浆则跳过）
   字段 `fluidType` 是包私有 所以靠 `FluidStatusAccessor` 的 `@Accessor` 读出来

**群系 id 是承重的** 70 个原版群系标签里有 50 个引用了被复制的群系
不合并就会丢掉结构生成与刷怪门控（村庄 要塞 袭击 雪傀儡融化 沼泽小屋 等）

**副作用** F3 与 `locate biome` 显示 `dawnduskterminal:plains` 而不是 `minecraft:plains`

**`PaletteBiomeSource` 的映射表必须惰性构建**（踩过大坑）
群系源是在数据包还在解析的时候被构造的 那一刻 `#tag` 吐出来的 holder **还没有 key**
`unwrapKey()` 是空的 于是 `ifPresent` 一个都不触发 映射表是空的
而等到第一个区块生成时标签早就绑定好了 `possibleBiomes()` 能正常报出 106 个
**表现**：`possibleBiomes` 正常、群系源类名正确、但区块群系全是 `minecraft:*` 特征全是原版
整个过程一声不响 所以现在改成 lazy 构建 并且为空时 `LOGGER.error` 报警

**`NoiseChunk.BlockStateFiller#calculate` 是 `@Nullable` 的**（也踩过）
返回 `null` 表示「这一格用默认方块」 是最常见的情况
包装它时把 `null` 喂进任何 remap 都会在几乎每一格 NPE 区块永远生成不完
所以包装逻辑在 `ChronoVeinPalette.wrap` 里 并有测试钉住 null 透传

**gametest 结构上抓不到这些** `GameTestServer` 用 `WorldPresets.FLAT` 烤世界 modded 维度根本不存在
所以「区块生成」这一类问题只能靠实机冒烟发现
排查手法（用完即删 别留在提交里）：在 `ServerStartedEvent` 里 `level.getChunk(0,0)`
打印群系源类名、`possibleBiomes` 大小、某个坐标的群系名 以及一个方块普查
`run/server` 需要 `eula.txt` 与 TF 的 jar 在 `run/server/mods/`

### 方块属性（后者复用前者）
模组方块用 `BlockBehaviour.Properties.ofFullCopy(原版方块)` 复用属性
标了「属性不变」的五个（rosy_grass rosy_dirt siltstone portal_fluid canopy_frost）保持原样

**`ofFullCopy` 会连掉战利品表引用一起复制** 原版方块的 `Properties.drops` 多为 null
所以复制品会回落到自己的 `data/<ns>/loot_table/blocks/<id>.json`
调色板里原本没有战利品表的九个方块（canopy_stone ochre greenschist eclogite charnockite
twilight_stone road_sand unconscious_sand siltstone）本次已补 否则挖了什么都不掉
`frozen_canopy` 故意不给 对齐冰的「挖了不掉落」

### portal_fluid 半透明渲染（对齐原版水）
贴图自带 alpha：`portal_fluid_still.png` `portal_fluid_flow.png` 整张 alpha 230/255
方块/流体的渲染层不注册就落进 solid 层 solid 层的着色器不看 alpha 于是这两张半透明贴图被画成不透明

**流体表面走的是流体渲染层不是方块模型** 链路是
`SectionCompiler:72` 读 `ItemBlockRenderTypes.getRenderLayer(FluidState)` → `TYPE_BY_FLUID`
所以 `models/block/portal_fluid.json` 里加 `render_type` 对流体表面没用 必须代码注册：

`DdtClient.onClientSetup`（`FMLClientSetupEvent`）把 `PORTAL_FLUID` 与 `PORTAL_FLOWING` 注册成
`RenderType.translucent()` 与 `ItemBlockRenderTypes` 静态块里 `Fluids.WATER`/`FLOWING_WATER` 的两条一致

- `setRenderLayer(Fluid, RenderType)` 只能在客户端 mod 加载期调用（`ClientModLoader.isLoading()`）
  所以挂 `FMLClientSetupEvent` 不能挂更晚的事件
- `setRenderLayer(Block, RenderType)` 那个重载已 deprecated **方块**的半透明改用模型 JSON 的
  `render_type` 字段（`ExtendedBlockModelDeserializer:66` → `NamedRenderTypeManager` 的
  `minecraft:translucent`）物品模型继承父模型 所以物品栏里也一起半透明

### portal_fluid 流动（对齐原版水）
`ModFluids` 里那两个匿名类的 `tick` 空实现已删 空 `tick` 等于流体永不扩散
现在走 `FlowingFluid.tick` `properties()` 里的 tickRate 5 / slopeFindDistance 4 /
levelDecreasePerBlock 1 / explosionResistance 100 本来就是原版水 `waterProperties` 的数值

- 维度里 `default_fluid` 就是 portal_fluid 所以海洋 含水层 泉眼 湖泊现在会像水一样流动找平
- `canConvertToSource(false)` 保持关闭（在 FluidType 上）两格相邻流体不会凭空生第三格
- `canBeReplacedWith` 用的是 `BaseFlowingFluid` 的实现（与自己比较）不是原版水的 `#minecraft:water`

### frozen_canopy / fragile_canopy 半透明
两者贴图 alpha 分别是 230/255 与 204/255 方块模型里加 `"render_type": "minecraft:translucent"`
（fluid 那套代码注册不适用于它们 见上）

`frozen_canopy` 复制冰的属性 本来带 `noOcclusion`
`fragile_canopy` 原本会遮挡邻居面 半透明下邻居那面被剔除 会直接看穿墙
所以补了 `.noOcclusion()`（与玻璃/冰/蜂蜜一致）代价是光现在会穿过它

### 半透明的**面剔除**（两个 canopy 方块曾经缺这一半）
`Block.shouldRenderFace`（`Block.java:207` 区块网格化的每一面都问它）的判断顺序是：

1. `state.skipRendering(邻居, 面)` → 剔除
2. `邻居.hidesNeighborFace(...)` → 剔除
3. `邻居.canOcclude()` → 走体素形状那套
4. 否则 **直接返回 true**（`Block.java:235`）

第 3 条被 `canOcclude()` 挡着 而半透明满方块必须是 `noOcclusion`（`canOcclude == false`）
所以对两个相邻的半透明方块来说 **只有第 1 条 `skipRendering` 能剔除它们之间那面**
缺了它 每一对相邻面的两面都会画出来 一整块 canopy 内部会变成一层叠一层的重影

原版玻璃和冰的答案在 `HalfTransparentBlock`：
`skipRendering` 返回 `邻居.is(this)` 冰是 `IceBlock extends HalfTransparentBlock`
玻璃是 `TransparentBlock/AbstractGlassBlock extends HalfTransparentBlock` 全部继承它

所以两个方块现在注册成 `HalfTransparentBlock`（`BLOCKS.registerBlock(name, HalfTransparentBlock::new, props)`）
不是纯 `Block` 也没用自定义子类 拷贝属性不等于拷贝类 原来只抄了 `Blocks.ICE` 的属性

- 只对齐**剔除** 没顺手拿 `TransparentBlock` 的天光/亮度（`propagatesSkylightDown`
  `getShadeBrightness` `getVisualShape`）那是"玻璃不算遮光"的另一件事 要的话再说
- 流体那一半本来就是对的：`LiquidBlockRenderer` 问
  `BlockState#shouldHideAdjacentFluidFace`（NeoForge 扩展）默认等于
  `邻居方块自己的流体类型.isSame(adjacentFluid)` 所以两块 portal_fluid 之间那个面早就剔除了

`gametest/TranslucentFaceCullingTests` 把这四条钉住了（同方块剔除 异方块不剔除
贴着的石头那面要留着 流体同流体剔除）**注意** `shouldHideAdjacentFluidFace` 返回 true 表示"该隐藏"
断言别写反

### 苍穹霜不再融化
原版 `SnowLayerBlock.randomTick`（`SnowLayerBlock.java:117`）整个方法体就是一句融化：

```
if (level.getBrightness(LightLayer.BLOCK, pos) > 11) { dropResources(...); removeBlock(pos, false); }
```

只看 **BLOCK 光**（方块光）不看天光 门槛是位置上的方块光 > 11

在这个维度里特别容易踩到 因为高光方块满地都是：
`portal_fluid` 是 15 级光（海洋 含水层 泉眼全是它）`blazing_block` 也是 15 级
所以水边的苍穹霜会一层层蒸发掉

处理：`world/CanopyFrostLayerBlock extends SnowLayerBlock` 把 `randomTick` 留空
属性仍然是 `Blocks.SNOW` 的逐字拷贝（含 `randomTicks()`）只是行为在类里砍掉 与别处同一个套路

另外两个本来就不融 不用管：
- `canopy_frost_block` 是纯 `Block` 雪块的融化同样来自 `randomTick` 的 override 它没有
- `frozen_canopy` 是 `HalfTransparentBlock` 不是 `IceBlock` 冰的融化在 `IceBlock.randomTick`（条件 `> 11 - lightBlock`）

`gametest/CanopyFrostMeltTests` 用对照组钉住：同一片方块光下原版雪必须融 苍穹霜必须不融
**这一条没跑过**（gametest runner 在本机会偶发卡着不收尾 见上）实机看更直接

### 热情的暮光去掉的岩浆行为
| 行为 | 原本在哪 | 处理 |
|---|---|---|
| 引燃实体 | `ScorchingTwilightBlock.entityInside` 调 `lavaHurt()` | 删掉 `lavaHurt()`；`entityInside` 现改为施加 6 点接触伤害（见下） |
| 引燃方块 | `ScorchingTwilightFluid.randomTick` 放 `BaseFireBlock` | 删掉 override |
| 岩浆粒子 | `ScorchingTwilightFluid.animateTick` 的 `ParticleTypes.LAVA` | 删掉 override |
| 滴落粒子 | `getDripParticle()` 返回 `DRIPPING_LAVA` | 改返回 null |
| 破坏方块时的岩浆滋声与烟 | `beforeDestroyingBlock` 的 `LevelEvent.LAVA_FIZZ`(1501) | 留空方法（`FlowingFluid` 里它是 abstract 不能删） |
| 清除周边可替换方块 | `ScorchingTwilightBlock.clearPlants` | 按用户要求删掉 |

**热情的暮光保留/新增的岩浆行为**（对照上面的「去掉」表，这些是想要的）
- 岩浆环境音效：`ScorchingTwilightFluid.animateTick` 放 `LAVA_POP`/`LAVA_AMBIENT`（不放 `ParticleTypes.LAVA` 粒子）
- 接触伤害：`ScorchingTwilightBlock.entityInside` 每次 `hurt` 6 点，走 `data/dawnduskterminal/damage_type/enthu_fluid.json`（`message_id: enthu_fluid`）
- 浸没雾：`ScorchingTwilightFluidExtensions.modifyFogColor` 返回 `0xFFF0FF`，`modifyFogRender` 用岩浆雾密度（0.25~1.0）
- 死亡讯息：`death.attack.enthu_fluid` = `%1$s被热情的拥抱了`

`isRandomlyTicking()` 现在只保留 `true`（无害），接触反应已改用 `FluidInteractionRegistry`
（`LiquidBlock.onPlace`/`neighborChanged` 触发，见 `world/FluidReactions.java`），不再依赖液体方块的 `randomTick`

**注意** 实体是否被点燃由 `NeoForgeMod.LAVA_TYPE` 决定（`Entity.isInLava()`）不看 `#minecraft:lava`
本流体有自己的 `FluidType` 所以本来就不会走岩浆烧伤那条路 真正在烧的是上面那个 `entityInside`

## 关键坑
- ~~**`default_fluid` 必须是 air**~~ 只适用于自造地形 现在照抄主世界 `water` + `sea_level 63`；水不会传送 触发传送的是 `portal_fluid` 方块
- ~~**`ore_veins_enabled` 必须 false**~~ 同上 只适用于自造地形 现在开 `true` 让原版矿脉正常生成
- 1.21.1 密度函数字段名是 `argument` 不是 `input`（squeeze/interpolated/abs/blend_density 都一样）
- `squeeze` 值域 `(x/2)^3*3` x=1 时只有 0.375 不是 1
- `vertical_gradient true_at_and_below absolute:0` 会填满所有 y<=0
- `gradle build` 对纯资源改动可能报 UP-TO-DATE 要验证 jar 内容
- **`neoforge:remove_features` 引用的 feature ID 必须真实存在**，引用不存在的 ID 会在注册表加载期抛 `Unbound values` 整个存档起不来
  （踩过 `twilightforest:thorn_rose`、`minecraft:*_blob` 都不存在）
- **绝对不要 `Get-Process java | Stop-Process`** 会杀 gradle daemon 卡死
  只杀 MC 服务端：`Get-Process java | Where-Object { (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine -match 'net.neoforged' } | Stop-Process -Force`
- PS 5.1 里长 heredoc 会崩 PSReadLine 改用**独立 .py 脚本文件**
- `git commit -m "中文"` 乱码 用 `[IO.File]::WriteAllText(路径,内容,[Text.UTF8Encoding]::new($false))` + `git commit -F`
- 1.21.1 原版资源可从 `C:\Users\admin\.gradle\caches\fabric-loom\1.21.1\net.neoforged.neoforge_21.1.235\minecraft-merged-game-resources.jar` 提取

## 已删除的自定义 Feature（历史）
随四层地形一起移除
`SkyIslandFeature` `SkyIslandLocator` `HollowPocketFeature` `RootPillarFeature` `TurfFeature`
`BedrockLayerFeature` `ChronoFeatureFilter` 及其注册、mixin 引用、gametest 与 biome_modifier

`ModChronoOres` 的 29 种 `chrono_ore_*` 方块曾留在注册表但已无世界生成入口 后已删除（它们只是原版矿石的别名 模型与战利品表都指向 `minecraft:`）

四层地形的密度函数 `mainland_*` `void_band` `underground_island` `sky_island_density` 已删除
`/ddt void` 与 `/ddt sky` 子指令已删除 保留 `/ddt line` 与 `/ddt tp`

## 用户偏好（重要）
- **写完诊断代码必须删掉再提交**
- 忘事时去 `/memories/repo/` 和本文件查
- 允许在工作区写记忆文件
- 用户会频繁给截图反馈 先看图再动手
- 贴图先用原版占位 美术后面替换

## 待办
- 落点只找露天地表（已完成 hasOpenSky）
- 天光 night=11 center=13 day=15
- 截图 12.29 显示天光 0 光照钳制可能没生效 待查
- 29 种 `chrono_ore_*` 已删除（它们只是原版矿石换命名空间 没有调色板替换）
- 结构排除已合并为 `StructureExclusions` 单一白名单（17 条）EXCLUDED 黑名单表已删 村庄与废弃矿井确认不生成
- 地形与群系都变了 旧存档必须删世界重建
- 极昼/极夜进度已从 biome 判定改为 `line_param` 的 |param| >= 0.6
