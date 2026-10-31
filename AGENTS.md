# AGENTS.md

本文件面向在本仓库中工作的 AI 与协作者，提供最短的上手路径与必须遵守的硬约束。

玩家的使用说明位于 `README.md`。

开发的详细说明位于 `docs/`。

模组（mod）的世界设定位于 `docs/设定.md`，开发过程中可将其作为参考。

## 项目

本项目是一个 Minecraft 1.21.1 NeoForge 模组。

该模组新增「晨昏线」维度，世界被一条线划分为昼侧与夜侧。

太阳高度由坐标在该线法向上的投影决定，并且不随时间变化。

维度的地形与群系是这样得到的：将原版主世界的世界生成移植到本模组的命名空间，再用调色板群系源替换方块。

模组标识 `mod_id` 为 `dawnduskterminal`。

主类为 `com.dawnduskterminal.DawnDuskTerminal`。

Java 版本为 Java 21。

构建使用 NeoForge 21.1.252 与 ModDevGradle，版本号写在 `gradle.properties` 中。

## 目录

`src/main/java/com/dawnduskterminal/` 存放代码。

`src/main/resources/assets/dawnduskterminal/` 存放贴图、模型、语言文件与音效。

`src/main/resources/data/dawnduskterminal/` 存放维度、群系、地形、战利品、配方与进度。

`tools/` 存放移植、校验与动画转换所用的 Python 脚本。

`docs/` 存放开发文档。

## 构建与运行

构建命令为 `./gradlew build`，产物输出到 `build/libs/`。

客户端运行命令为 `./gradlew runClient`。

服务端运行命令为 `./gradlew runServer`。

数据生成命令为 `./gradlew runData`，输出到 `src/generated/resources`。

`./gradlew runGameTestServer` 用于运行游戏测试服务端。

`gradlew` 必须保持可执行位，因为 CI 在 Linux 上运行，权限位一旦丢失，构建将直接失败。

### 单一产物

最终用户只需要取得 `build/libs/dawnduskterminal-<version>.jar` 这一个文件。

该 jar 中同时包含以下两项内容。

| 内容 | 位置 |
|---|---|
| 5 个平台的 native 网格内核 | `native/windows-x86_64` `native/linux-x86_64` `native/linux-aarch64` `native/macos-x86_64` `native/macos-aarch64` |
| c2me（JarJar 嵌套） | `META-INF/jars/` 与 `META-INF/jarjar/metadata.json` |

本机构建即可得到全平台单文件：`buildNative` 使用 Zig 交叉编译，因此在任何宿主上执行 `./gradlew build`，都会把 5 个 native 一并打包进 jar。

构建流程分为四个步骤。

1. `installZig` 自动下载 Zig 到 Gradle 用户目录下的 `ddt-zig/<version>`（默认 `~/.gradle/ddt-zig/`），无需预装编译器，下载包经官方 SHA-256 校验。工具链与其缓存均保留在工程目录之外：工程目录带有 DSH 沙盒遗留的目录权限项，Zig 在其中创建编译临时目录会被拒绝（`cache: manifest_create AccessDenied`），而将同一份二进制放在用户目录下则工作正常。
2. `buildNative` 依次编译 `windows-x86_64` `linux-x86_64` `linux-aarch64` `macos-x86_64` `macos-aarch64`，产物输出到 `build/native/<target>/`。
3. `./gradlew build` 的 `processResources` 任务把 `build/native` 下的全部目录打进 jar。
4. `release.yml` 校验 5 个 `ddt_native` 均存在，缺少任意一个即直接失败。

`ddt_native` 是通用的网格变换内核，它只接收顶点区间、矩阵与法线取反标志，不认识实体与方块。`NativeGeoMesh` 负责 GeckoLib 模型的摊平，`NativeMeshBatch` 负责在一帧内收集并一次完成 JNI 提交，实体渲染器与方块渲染器共用该内核。

`-Pddt_zig_version` 用于更换 Zig 版本。构建期若任一目标编译失败，`build` 即直接失败；运行期若加载失败或 ABI 不匹配，则禁用 native 并回退到 GeckoLib 的 Java 渲染路径；若出现运行时错误，则熔断禁用；若发生硬崩溃，则由崩溃标记在下次启动时作为回退保障。相关内容见 `NativeMesh` 与 `docs/development.md`。

`jni_md.h` 由仓库自带：`src/main/native/win32` 供 Windows 使用，`src/main/native/posix` 供 Linux 与 macOS 使用。

Windows DLL 使用 Zig 的 mingw 目标构建，只依赖 UCRT 与 `kernel32.dll`，不得依赖 `libwinpthread-1.dll`。

c2me 由 `patchC2me` 从 `c2me_version` 所指定的 Modrinth Maven 坐标解析后嵌入 jar，默认保留官方全部模块。专用服务器的固定种子对照实验已经验证：官方 c2me 与不安装 c2me 时，地形高度、群系与流体指纹逐字节一致；开发环境中出现的 15 列差异是 dev classpath 组合造成的假阳性，因此不得据此移除模块。

不得使用 ModDevGradle 的 `jarJar` 配置嵌入 c2me，因为本地补丁 jar 在配置期无法解析。当前做法是手动编写 `META-INF/jarjar/metadata.json`，并让 `jar` 任务把补丁 jar 放入 `META-INF/jars/`。

玩家额外安装 c2me 时，由 JarJar 版本协商处理；已通过“内置加外部同版本”的实际测试，确认可以正常启动、正常生成地形与正常停服。

c2me 本体采用 MIT 许可证，`c2me-opts-accel-opencl` 为 All Rights Reserved 且未打进产物，嵌套 jar 中保留 `LICENSE-MIT.txt`。官方 1.21.1 OpenCL devbuild 同样为 ARR，要求 Java 25，并且只能外挂安装，因此不得内置，也不得随产物分发。

不得在 `README.md` 等面向使用者的文件中写入本机独有的环境信息。

## 暮色森林

运行时必须安装暮色森林 1.21.1。

构建期从 CurseMaven 拉取 `curse.maven:the-twilight-forest-227639:7797302`，该过程需要联网。

暮色森林自身的 modmaven 已于 2024-07-06 停止更新，1.21.1 所使用的 4.8.3345 在该源上仅有空目录。

暮色森林是必装依赖，Java 代码直接 `import twilightforest.*` 引用它的类型（注册项取 `TFEntities` / `TFBlocks` / `TFSounds` 等常量，方法直接调用）。编译期检查优先：暮色森林升级导致类名或签名变化时，构建直接失败，而不是运行期悄悄降级。

因此构建期必须有该 jar（`build.gradle` 的 `compileOnly`，来自 CurseMaven，需要联网）。

只有编译期没有常量的数据 ID 仍写成字符串：`data/` 下的战利品表、配方、标签与群系，以及 Java 里引用的方块标签、群系标签、配置特征与结构 id（这些由数据包定义，类型化不了）。

## GeckoLib

运行时必须安装 GeckoLib 4.9.3 以上版本，对应 1.21.1，并在 `neoforge.mods.toml` 中声明为 `required`。

构建期无需放置该 jar，依赖从 GeckoLib 官方 maven 以 `compileOnly` 方式拉取，版本写在 `gradle.properties` 的 `geckolib_version` 中。

高塔恶魂（`tower_ghast`）的模型与动画由 GeckoLib 驱动，三条资源路径在 `client/TowerGhastRenderer` 中直接写出，不遵循 GeckoLib 的命名约定。

动画源文件是 Yes Steve Model 的 Bedrock 文件，必须先经过 `tools/ysm/` 的转换与校验，详见 `tools/ysm/README.md`。

GeckoLib 将 `geckoanimfix` 声明为不兼容依赖，因此两者不得同时安装。

## 代码约定

每个注册类别对应一个 `registry/ModXxx` 类，并使用 `DeferredRegister`。

对应的注册必须在 `DawnDuskTerminal` 构造函数中调用 `register(modBus)` 完成。

资源 ID 一律使用 `DawnDuskTerminal.id(path)` 生成。

包名固定为 `com.dawnduskterminal`。

## 世界生成约定

维度 `dimension/chrono.json` 使用 `dawnduskterminal:palette` 群系源。

`PaletteBiomeSource` 将原版按气候选出的群系替换为同名副本，副本位于 `worldgen/biome/` 下。

`data/dawnduskterminal/tags/worldgen/biome/palette_biomes.json` 列出全部副本。

副本中的特征引用必须指向 `dawnduskterminal:` 命名空间。

数据包无法覆盖的硬编码部分由 `mixin/` 中的补丁接管，详见 `docs/worldgen.md`。

## 硬约束与常见问题

`noise_settings/chrono.json` 的 `default_fluid` 必须是 `dawnduskterminal:portal_fluid`，因为有多个 mixin 依靠它识别维度。

`AquiferTests` 与 `WorldgenTests` 断言地形、流体与矿脉均使用模组调色板。

feature 引用与 `neoforge:remove_features` 中的 ID 必须真实存在，否则注册表加载将失败，整个存档无法启动。

在 1.21.1 中，密度函数 JSON 字段是 `argument`，不是 `input`。

`runGameTestServer` 偶发卡死（区块等不到实体刻）时，由 `mixin/GameTestChunkWaitMixin` 作为回退保障，详见 `docs/development.md` 的「已知的失败与卡死」一节。

天光被烘焙进区块，因此修改天光后老存档需要重新计算。

修改世界生成后，建议删除 `run/<端>/world` 并重新生成。

## 测试

测试命令为 `./gradlew runGameTestServer`。

测试覆盖世界生成、传送门形状、流体反应、霜层与半透明面剔除。

详见 `docs/development.md`。

## 提交

提交说明使用中文。

格式为 `type(scope): 中文说明`，其中 subject 占一行，句末不加句号。

正文仅在需要携带 subject 无法容纳的信息时才添加。

## 分支与协作

推送前必须先执行 `git fetch origin`，并将当前分支 rebase 到最新的 `origin/main`。

冲突必须自行解决完毕后再推送，禁止推送带冲突标记的提交。

不得使用 merge 提交同步主干，以保持线性历史。

## 文档

`docs/README.md` 为文档索引。

`docs/architecture.md` 为架构总览。

`docs/worldgen.md` 为世界生成。

`docs/biomes.md` 为群系对照。

`docs/portal.md` 为传送门与流体。

`docs/crafting.md` 为合成配方与标签。

`docs/development.md` 为开发与构建。

`docs/configuration.md` 为配置项。

`docs/设定.md` 为世界观与内容设定。
