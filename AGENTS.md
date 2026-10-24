# AGENTS.md

面向在本仓库工作的 AI 与协作者，给最短上手路径与硬约束

玩家说明在 `README.md`

开发细节在 `docs/`

mod 设定在 `docs/设定.md` 中，开发时可进行参考

历史笔记在 `DEVNOTES.md`，部分内容已过时

## 项目

Minecraft 1.21.1 NeoForge 模组

新增「晨昏线」维度，世界被一条线切成昼侧与夜侧

太阳高度由坐标在这条线法向上的投影决定，不随时间变化

维度的地形与群系是把原版主世界世界生成移植到本模组命名空间后，用调色板群系源替换方块

`mod_id` 为 `dawnduskterminal`

主类 `com.dawnduskterminal.DawnDuskTerminal`

Java 21

NeoForge 21.1.252 与 ModDevGradle，版本写在 `gradle.properties`

## 目录

`src/main/java/com/dawnduskterminal/` 代码

`src/main/resources/assets/dawnduskterminal/` 贴图 模型 语言 音效

`src/main/resources/data/dawnduskterminal/` 维度 群系 地形 战利品 配方 进度

`tools/` 移植 校验与动画转换用的 Python 脚本

`docs/` 开发文档

## 构建与运行

`./gradlew build`，产物在 `build/libs/`

`./gradlew runClient`

`./gradlew runServer`

`./gradlew runData`，输出到 `src/generated/resources`

`./gradlew runGameTestServer`

`gradlew` 必须保持可执行位，CI 在 Linux 上跑，权限位丢了会直接失败

### 单一产物

最终用户只拿 `build/libs/dawnduskterminal-<version>.jar` 一个文件

jar 里同时包含两样东西

| 内容 | 位置 |
|---|---|
| 5 个平台的 native 网格内核 | `native/windows-x86_64` `native/linux-x86_64` `native/linux-aarch64` `native/macos-x86_64` `native/macos-aarch64` |
| c2me（JarJar 嵌套） | `META-INF/jars/` 加 `META-INF/jarjar/metadata.json` |

本机构建就是全平台单文件：`buildNative` 用 Zig 交叉编译，任何宿主上 `./gradlew build` 都会把 5 个 native 一起打进 jar

1 `installZig` 自动下载 Zig 到 Gradle 用户目录的 `ddt-zig/<version>`（默认 `~/.gradle/ddt-zig/`），不需要预装编译器，下载包经官方 SHA-256 校验。工具链与它的缓存都留在工程目录外：工程目录带 DSH 沙盒留下的目录权限项，zig 在那儿建编译临时目录会被拒（`cache: manifest_create AccessDenied`），同一份二进制放到用户目录下就正常
2 `buildNative` 依次编 `windows-x86_64` `linux-x86_64` `linux-aarch64` `macos-x86_64` `macos-aarch64`，产物落 `build/native/<target>/`
3 `./gradlew build` 的 `processResources` 把 `build/native` 下全部目录打进 jar
4 `release.yml` 校验 5 个 `ddt_native` 都在，缺一个直接失败

`ddt_native` 是通用网格变换内核：只接顶点区间、矩阵与法线取反标志，不认识实体或方块。`NativeGeoMesh` 负责 GeckoLib 模型摊平，`NativeMeshBatch` 负责一帧内收集并一次 JNI 提交，实体与方块渲染器共用

`-Pddt_zig_version` 换 Zig 版本。构建期任何目标编不出来 `build` 直接失败；运行期加载失败或 ABI 不匹配会禁用 native 并回退 GeckoLib 的 Java 渲染路径，运行时错误则熔断禁用，硬崩溃由崩溃标记在下次启动兜底，见 `NativeMesh` 与 `docs/development.md`

`jni_md.h` 是仓库自带的：`src/main/native/win32` 给 Windows，`src/main/native/posix` 给 Linux 与 macOS

Windows DLL 用 Zig 的 mingw 目标，只依赖 UCRT 与 `kernel32.dll`，不能依赖 `libwinpthread-1.dll`

c2me 由 `patchC2me` 从 `c2me_version` 指定的 Modrinth Maven 坐标解析后嵌进 jar，默认保留官方全部模块。专用服固定种子对照已经验证官方 c2me 与不装 c2me 的地形高度 群系 流体指纹逐字节一致；开发环境里那 15 列差是 dev classpath 组合的假阳性，不要据此删模块

不要用 ModDevGradle 的 `jarJar` 配置嵌 c2me：本地补丁 jar 在配置期解析不到。当前做法是手动写 `META-INF/jarjar/metadata.json` 并让 `jar` 任务把补丁 jar 放进 `META-INF/jars/`

玩家额外安装 c2me 时由 JarJar 版本协商处理，已用“内置加外部同版本”实测能正常启动、正常生成地形、正常停服

c2me 本体是 MIT，`c2me-opts-accel-opencl` 是 All Rights Reserved 且没有打进产物，嵌套 jar 里保留 `LICENSE-MIT.txt`。官方 1.21.1 OpenCL devbuild 也是 ARR，要求 Java 25 且只能外挂安装，不要内置或随产物分发

不许在readme等文件中写本机独有环境！


## 暮色森林

运行必须装暮色森林 1.21.1

构建期从 CurseMaven 拉 `curse.maven:the-twilight-forest-227639:7797302`，需要联网

它自己的 modmaven 停更在 2024-07-06，1.21.1 用的 4.8.3345 在那个源上只有空目录

代码里只用字符串 ID 引用暮色森林，没有 `import twilightforest.*`

所以缺 jar 仍可编译，运行会缺注册项

`gradle.properties` 的 `twilight_dependency` 控制依赖类型，默认 `required`

## GeckoLib

运行必须装 GeckoLib 4.9.3 以上，对应 1.21.1，`neoforge.mods.toml` 里是 `required`

构建期不用放 jar，从 GeckoLib 官方 maven 拉 `compileOnly`，版本写在 `gradle.properties` 的 `geckolib_version`

高塔恶魂（`tower_ghast`）的模型与动画由它驱动，三条资源路径在 `client/TowerGhastRenderer` 里直接写出，不走它的命名约定

动画源文件是 Yes Steve Model 的 Bedrock 文件，先过 `tools/ysm/` 的转换与校验，见 `tools/ysm/README.md`

它把 `geckoanimfix` 声明为不兼容依赖，两者不能同时装

## 代码约定

每个注册类别一个 `registry/ModXxx`，用 `DeferredRegister`

在 `DawnDuskTerminal` 构造函数里调用对应的 `register(modBus)`

资源 ID 用 `DawnDuskTerminal.id(path)`

包名固定 `com.dawnduskterminal`

## 世界生成约定

维度 `dimension/chrono.json` 用 `dawnduskterminal:palette` 群系源

`PaletteBiomeSource` 把原版按气候选出的群系换成同名副本，副本在 `worldgen/biome/`

`data/dawnduskterminal/tags/worldgen/biome/palette_biomes.json` 列出全部副本

副本里的特征引用必须指向 `dawnduskterminal:` 命名空间

数据包够不到的硬编码由 `mixin/` 里的补丁接管，见 `docs/worldgen.md`

## 硬约束与坑

`noise_settings/chrono.json` 的 `default_fluid` 必须是 `dawnduskterminal:portal_fluid`，多个 mixin 靠它识别维度

`AquiferTests` 与 `WorldgenTests` 断言地形 流体 矿脉都走模组调色板

feature 引用与 `neoforge:remove_features` 的 ID 必须真实存在，否则注册表加载失败，整个存档起不来

1.21.1 密度函数 JSON 字段是 `argument`，不是 `input`

`runGameTestServer` 偶发卡死（区块等不到实体刻）由 `mixin/GameTestChunkWaitMixin` 兜底，见 `docs/development.md` 的「已知的失败与卡死」

天光烘焙进区块，改天光后老存档要重算

改世界生成后建议删掉 `run/<端>/world` 重建

## 测试

`./gradlew runGameTestServer`

覆盖世界生成 传送门形状 流体反应 霜层 半透明面剔除

见 `docs/development.md`

## 提交

提交说明用中文

格式 `type(scope): 中文说明`，subject 一行，句末不加句号

正文只在携带 subject 装不下的信息时添加

## 分支与协作

推送前先 `git fetch origin`，把当前分支 rebase 到最新的 `origin/main`

冲突自己解决完再推送，不许推送带冲突标记的提交

不要用 merge 提交同步主干，保持线性历史

## 文档

`docs/README.md` 文档索引

`docs/architecture.md` 架构总览

`docs/worldgen.md` 世界生成

`docs/portal.md` 传送门与流体

`docs/development.md` 开发与构建

`docs/configuration.md` 配置项
