# 开发与构建

## 环境

JDK 21

用仓库自带的 `gradlew`，并保持它的可执行位

Minecraft 1.21.1

NeoForge 与 ModDevGradle 版本在 `gradle.properties` 与 `settings.gradle`

## 暮色森林依赖

运行时必须装暮色森林 1.21.1

构建期从 CurseMaven 拉，坐标 `curse.maven:the-twilight-forest-227639:7797302`，对应 1.21.1 的 4.8.3345

它自己的 modmaven 停更在 2024-07-06（最新 4.5.2428），1.21.1 用的版本在那个源上只有空目录，所以只能走 CurseMaven 这个第三方代理，`build.gradle` 用 `exclusiveContent` 把 `curse.maven` 这一组摘出来

代码里只按字符串 ID 引用暮色森林，没有 `import twilightforest.*`，所以缺依赖也能编译，功能会在运行时缺失（CI 现在也能拉到同一份 classpath）

`gradle.properties` 的 `twilight_dependency` 控制依赖类型，默认 `required`

## GeckoLib 依赖

运行时必须装 GeckoLib 4.9.3 以上，对应 1.21.1

从 GeckoLib 官方 maven 拉 `compileOnly` 与 `runtimeOnly`，版本写在 `gradle.properties` 的 `geckolib_version`

`compileOnly` 供编译，`runtimeOnly` 让开发运行（`runClient` 等）也加载它，少了后者开发运行会报 `requires geckolib 4.9.3 or above`

`neoforge.mods.toml` 里声明为 `required`，所以它和暮色森林一样是硬依赖

高塔恶魂（`entity/tower_ghast`）的模型 动画 贴图与动画引擎都来自它 三条资源路径在 `client/TowerGhastRenderer` 里直接写出，不走 GeckoLib 的命名约定

动画源文件是 Yes Steve Model 的 Bedrock 文件，要先过 `tools/ysm/` 的转换与校验，见 `tools/ysm/README.md`

GeckoLib 声明 `geckoanimfix` 为不兼容依赖，两者不能同时装

## 开发用的客户端模组

`runClient` 会额外加载 JEI、拼音搜索（Just Enough Characters）、Xaero 小地图与 Xaero 世界地图、Jade、Chunky、Zume、Tweakerge（带前置 MaFgLib）、AppleSkin、BoccHUD、OpenYSM，只供开发时看配方 看地图 看方块信息 预生成区块 缩放 调 tweak 看饱食度 看状态条 与对拍塔恶魂的原始 Bedrock 动画，不是本模组的依赖，不参与编译也不进产物

JEI 与 Xaero 走各自官方 maven，拼音搜索与 OpenYSM 走 CurseMaven（`curse.maven:just-enough-characters-250702:9163765`，对应 4.5.30；`curse.maven:openysm-unofficial-neoforge-port-1587175:8384243`，对应 2.6.6），Jade Chunky Zume Tweakerge MaFgLib AppleSkin BoccHUD 走 Modrinth maven（坐标是 `maven.modrinth:<项目 slug>:<版本号>`），版本写在 `gradle.properties`

Chunky 在 Modrinth 上给 1.4.23 发了 fabric forge neoforge 三条同版本号的文件，按版本号取会拿到 forge 那个，FML 打一行 `Skipping jar ... is for Minecraft Forge ...` 就静默跳过，所以 `chunky_version` 写的是 neoforge 那条的版本 id `LuFhm4eU`

它们在 `clientDevMods` 这个 configuration 里，由 `tasks.named('runClient') { classpathProvider.from(...) }` 挂到客户端运行的 classpath 上，`runServer` `runData` `runGameTestServer` 的 classpath 里没有它们

踩过的三个坑：run 里写 `dependencies { runtimeOnly ... }` 会落到工程级 `runtimeOnly`，所有 run 都带上；run 的 `additionalRuntimeClasspathConfiguration` 只写进 `legacyClasspath.txt`，FML 不当模组扫，游戏里根本看不见；run 的 `classpath` 在任务执行时才从 `classpathProvider` 取，直接往 `classpath` 上加会被覆盖

Xaero 两个依赖 `xaero.lib`，由它们的 pom 传递带上；JEI 要的 `mezz_config` 是 jar-in-jar 自带在 JEI 的 jar 里的，`net.mezzdev.config` 那个坐标是它的库不是模组；Modrinth 的 pom 只有元信息不带依赖，所以 Tweakerge 的前置 `mafglib` 在 `clientDevMods` 里显式写了一条，BoccHUD 0.4.1 的前置也是 MaFgLib，同一条就够

Voxy 与 MiniHUD Tweakeroo 在 1.21.1 上都没有 NeoForge 构建（Voxy 官方只发 fabric，最早 1.20.4，之后直接跳到 1.21.6），要自己把 jar 放进 `run/client/mods`

OpenYSM 在 1.21.1 NeoForge 上没有官方构建：官方 Modrinth 项目只发 1.20.1 forge 与 fabric，旧的那个非官方 Modrinth 页面已经下架，只剩 CurseForge 上的第三方移植 1587175，也就是这里用的那条坐标

它自带的 GeckoLib 是分叉，包名在 `org.openysm.geckolib3` 与 `com/elfmcys/yesstevemodel/geckolib3` 下，jar 里没有 `software/bernie` 的引用，与本模组依赖的 GeckoLib 4.9.3 不撞；它的 mixin 只碰原版与 parcool create slashblade 那几个可选模组

## 构建与运行

`./gradlew build`，产物在 `build/libs/`

`./gradlew runClient` 目录 `run/client`

`./gradlew runServer` 目录 `run/server`

`./gradlew runData` 目录 `run/data`，输出到 `src/generated/resources`

`./gradlew runGameTestServer` 目录 `run/gametest`

首次构建会下载并反编译 Minecraft

纯资源改动可能报 UP-TO-DATE，要验证 jar 里的内容

### native 网格内核

`src/main/native/ddt_native.cpp` 是纯 C++17 与 `std::thread`，不含平台 API，所以五个平台的库都能在同一台机器上编出来

内核只做一件事：把一段顶点按一个 4x4 矩阵与一个 3x3 法线矩阵变换，写进直接缓冲区。它不认识实体、方块、骨骼或立方体

每段顶点叫一个 piece，调用方给出顶点区间、矩阵和法线取反标志。标志低三位依次对应 X Y Z 轴，只在对应法线为负时取反，用来复现 GeckoLib 退化平面的修正

`NativeGeoMesh` 把任意 GeckoLib 烘焙模型摊平成 piece，`NativeMeshBatch` 负责一帧内收集并一次 JNI 提交。实体渲染器和方块实体渲染器都能用同一套流程

构建固定走 Zig，`installZig` 任务会自动下载到 `.gradle/zig/<version>`，之后不再重复下载，不需要系统里预装编译器

| target | triple | 产物 |
|---|---|---|
| `windows-x86_64` | `x86_64-windows-gnu` | `ddt_native.dll` |
| `linux-x86_64` | `x86_64-linux-gnu.2.17` | `libddt_native.so` |
| `linux-aarch64` | `aarch64-linux-gnu.2.17` | `libddt_native.so` |
| `macos-x86_64` | `x86_64-macos` | `libddt_native.dylib` |
| `macos-aarch64` | `aarch64-macos` | `libddt_native.dylib` |

`./gradlew build` 默认编全部五个，所以无论在哪个平台构建，`build/libs/` 里的 jar 都是全平台通用的

jar 里的 `native/<target>/` 一次带齐，运行时由 `NativeMesh` 按 `os.name` 与 `os.arch` 挑对应的库

没有纯 Java 兜底：库缺失、加载失败或变换返回错误都直接抛异常，构建期任何一个目标编不出来也会让 `gradlew build` 失败

`jni.h` 取当前构建 JVM 的，`jni_md.h` 用仓库里的：`src/main/native/win32` 给 Windows，`src/main/native/posix` 同时给 Linux 与 macOS

| 属性 | 作用 |
|---|---|
| `-Pddt_zig_version=0.13.0` | 换 Zig 版本 |

Zig 的产物先写进 `build/tools/native-work`，只把目标库拷进 `build/native/<target>/`，避免 Windows 上顺带产出的 `.lib` 与 `.pdb` 被打进 jar

Windows DLL 只依赖 UCRT 与 `kernel32.dll`，不需要 `libwinpthread-1.dll` 之类的运行库

## 游戏内测试

`./gradlew runGameTestServer`

命名空间 `dawnduskterminal`，模板 `ddt_empty`

### 已知的失败与卡死

整轮应当全绿，退出码是失败数，全绿时为 0

**结构所在区块等不到实体刻会把整轮测试挂死**（已由 `mixin/GameTestChunkWaitMixin` 兜底）

原版 `GameTestInfo.tick` 只在 `chunksLoaded` 为真、或结构包围盒覆盖的每个区块都 `isPositionEntityTicking` 时才放结构、跑测试。实测会出现区块已经强制加载（`hasChunk` 为真）却永远不进入实体刻的情况：那时这个条件永远为假，测试既不开始也不超时（判超时的 `tickInternal` 只在这个条件为真之后才会被调用），于是进度格子里一直留着空格

而 `GameTestServer` 叠加的 `waitUntilNextTick` 不睡觉，它会以每秒上万 tick 刷同一行，日志涨到过 102 MB，永不退出。实测 10 次里约 3 次，每次都是排在网格最前面的几个测试（结构网格由 `GameTestServer.startTests` 在随机原点铺开）

`GameTestChunkWaitMixin` 等满 5 秒就把 `chunksLoaded` 置真放行。健康的一轮里几个 tick 内条件就为真（GameTestServer 每秒几万 tick），所以几乎不触发；触发时打一条 warn。修复前 21 次里 3 次卡死，修复后 37 次全部跑完，其中 4 次由这个兜底救回，通过数不变

### WorldgenTests

`chronoDimensionTypeIsRegistered` 维度类型已注册

`portedDensityFunctionsAreRegistered` 移植的密度函数已注册

`portedNoiseParametersAreRegistered` 移植的噪声参数已注册

`terrainNoiseIsSeedDerived` 地形噪声随种子变化

`paletteBiomeCopiesAreRegistered` 群系副本已注册

`paletteBiomeCopiesCarryOurFeatures` 副本带本模组特征

`paletteBiomeSourceIsRegistered` 调色板群系源已注册

`chronoDimensionDeclaresThePaletteSource` 维度 JSON 解出来的就是调色板群系源，`extra` 与极带群系齐全
`polarBandsUseTheExtraBiomes` 极带选的是 `extra` 里的极昼极夜群系

`configuredFeaturesAreRegistered` 配置特征已注册

`veinFillersUseThePalette` 矿脉填充走调色板

`paletteBiomeSourceSubstitutesThePreset` 群系源替换原版预设

### AquiferTests

`terrainAndFluidUseTheModPalette` 地形与流体走模组调色板

`aquifersAreEnabledLikeVanilla` 含水层配置与原版一致

`oreVeinsAreEnabledLikeVanilla` 大矿脉已开启

### PortalShapeTests

`plantedPoolIsPortal` 带植物的水池是传送门

`barrenPoolIsNotPortal` 荒水池不是

`poolWithoutPlantsIsNotPortal` 没植物的水池不是

`loneWaterBlockIsNotPortal` 单个水方块不是

`buildFillsPoolWithPortalFluid` 建造会把水池换成传送门流体

`portalBlockSurvivesWithNeighbour` 有邻居的传送门方块存活

`isolatedPortalBlockSelfHeals` 孤立的传送门方块自愈

`portalFluidIsCompleteWithTwoNeighbours` 两个邻居即算完整

### FluidReactionTests

`portalFluidWithWater`

`portalFluidWithLava`

`portalFluidWithScorchingTwilight`

`scorchingTwilightWithWater`

`scorchingTwilightWithLava`

`portalFluidWithFlowingWater`

`flowingPortalFluidWithWater`

### CanopyFrostMeltTests

`canopyFrostSurvivesTheLightThatMeltsVanillaSnow` 苍穹霜不会像原版雪那样在亮光里融化

### TranslucentFaceCullingTests

`translucentCanopyCullsTheFaceBetweenTwoOfItself` 半透明方块之间剔除面

`cullingAgreesWithTheVanillaReference` 与原版参照一致

`differentBlocksAreNotCulledAgainstEachOther` 不同方块之间不剔除

`portalFluidCullsTheFaceBetweenTwoOfItself` 传送门流体之间剔除面

### PaintingTests

`variantIsThreeByTwo` 变体是 3x2，贴图指向 `dawnduskterminal:painting_ghast_1`

`vanillaPaintingItemCannotPlaceIt` 变体不在 `#minecraft:painting_variant/placeable` 里，原版画物品挑不到它

`entityIsLockedToItsVariant` 本模组的画实体强制用自己的变体，`setVariant` 塞别的进去也换不掉，中键给的是自己的物品

`vanillaPaintingRefusesTheVariant` 原版画实体拒绝这个变体（`PaintingVariantGuardMixin`）

`itemPlacesThePainting` 贴着 3x2 的墙放下后实体类型 朝向 变体 包围盒都对，物品减一

## 指令

`/ddt` 需要权限等级 2

`/ddt line` 打印当前位置的原点 法向 距离 参数 天光 维度

`/ddt tp <param>` 按投影参数在 -1 到 1 之间传送到维度内

## 移植脚本

`tools/port_overworld_worldgen.py` 从 Gradle 缓存里的反编译客户端 jar 抽取原版世界生成，改写命名空间后写入本模组

`tools/port_tf_biomes.py` 把暮色森林群系内容填进对应维度的群系副本，保留洞穴雕刻，重映射特征引用

`tools/check_tf_biomes.py` 校验移植群系，检查 JSON 合法 无残留 `minecraft:*` 特征 以及 `twilightforest:*` 特征能在 TF jar 内解析

`tools/check_worldgen_refs.py` 校验配置特征引用，报「模组有同名副本却仍指向 `minecraft:`」的漏改与没人引用的副本
漏改不会报错只会静默：副本把土换成了霞土，原版那份不会，同一片林子里两种土混着长

`tools/ysm/ysm_to_geckolib.py` 把 Yes Steve Model 的 Bedrock 动画转换成 GeckoLib 能编译的形式，并输出逐子句的审计报告

`tools/ysm/validate_geckolib.py` 按 GeckoLib 真实解析规则校验动画文件，列出会被静默跳过的片段 它同时是全仓库唯一的 GeckoLib 语法规则模块

`tools/ysm/ysm_selftest.py` 转换表的自测，覆盖每一条转换规则

`tools/ysm/` 的说明 转换表与实测数据都在它目录下的 `README.md`

## CI

`build.yml` 在 push main 与 v 标签 与 PR 触发，跑 `./gradlew build` 并上传 jar

`gametest.yml` 在 PR 触发，跑两遍 `./gradlew runGameTestServer`：一遍原版环境，一遍把 c2me 1.21.1 NeoForge 构建放进 `run/gametest/mods`

`release.yml` 在 v 标签触发，单个 ubuntu runner 用 Zig 交叉编译出全平台 jar，校验五个 native 与嵌套的 c2me 都在 jar 里，最后 `gh release create` 或 `gh release upload --clobber`

c2me 本体是 MIT，只有 `c2me-opts-accel-opencl` 是 All Rights Reserved，没有打进产物。1.21.1 NeoForge 现在有官方单独的 OpenCL Acceleration Module devbuild，但它同样是 ARR，要求 Java 25，只支持服务端，而且 c2me 会按驱动拉黑设备；本机 gfx90c 实测被标记 `BROKEN_DRIVER_AMD_GCN`，强行绕过会在 `amdocl64.dll` 编译阶段崩 JVM。不要内置，也不要承诺开箱可用

c2me 由 `patchC2me` 从 `c2me_version` 解析后以嵌套 jar 打进产物，保留官方全部模块。Manifest 手动写在 `META-INF/jarjar/metadata.json`，不要换成 ModDevGradle 的 `jarJar` 配置，本地补丁 jar 在配置期解析不到

专用服固定种子 12345 的对照已经跑过：官方 c2me 与不装 c2me 的 3072 列地表高度 群系 流体指纹逐字节一致。开发环境里出现过 15 列高度差，是 dev classpath 组合的假阳性，不要据此删 c2me 模块

“内置 c2me 加外部同版本 c2me”也实测过：JarJar 协商后正常启动 正常生成地形 正常停服，地形指纹仍是生产基线

地形指纹探针 `ChronoParityProbe` 只在 `DDT_CHRONO_PARITY=1` 或 `-Dddt.chronoParity=true` 时运行，固定种子专用服会写出 `ddt-chrono-fingerprint.txt` 并停服

concurrency 只在 PR 上 cancel-in-progress

## 踩坑

`noise_settings/chrono.json` 的 `default_fluid` 必须是 `portal_fluid`，多个 mixin 靠它识别维度

`ore_veins_enabled` 保持 true，矿脉填充由 `NoiseChunkVeinPaletteMixin` 换成调色板方块

feature 引用与 `neoforge:remove_features` 的 ID 必须真实存在，否则注册表加载期抛错

1.21.1 密度函数字段是 `argument`，不是 `input`

`gradlew` 必须是 100755，权限位丢了 Linux 上直接跑不了

天光烘焙进区块，改天光后老存档要重算

改世界生成后建议删掉 `run/<端>/world` 重建

## 新增内容放哪

新增方块放 `block/` 或 `registry/ModTerrainBlocks` 或 `registry/ModBlocks`

新增世界生成数据放 `src/main/resources/data/dawnduskterminal/worldgen/`

新增自定义 Feature 放 `world/`，注册在 `registry/ModFeatures`

新增矿石放 `registry/ModChronoOres`

新增物品放 `registry/ModItems`

新增画放 `data/dawnduskterminal/painting_variant/` 与 `assets/dawnduskterminal/textures/painting/`，实体与物品见 `entity/GhastPaintingEntity` `item/GhastPaintingItem`

新增流体放 `registry/ModFluids` 或 `registry/ModScorchingTwilight`

新增配置放 `config/DdtConfig`

新增指令放 `command/DdtCommands`

新增进度放 `data/dawnduskterminal/advancement/`
