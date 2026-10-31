# 开发与构建

## 环境

本项目的开发环境使用 JDK 21。

构建时使用仓库自带的 `gradlew`，并须保持该文件的可执行权限位。

目标游戏版本为 Minecraft 1.21.1。

NeoForge 与 ModDevGradle 的版本分别记录在 `gradle.properties` 与 `settings.gradle` 中。

## 暮色森林依赖

运行环境必须安装暮色森林 1.21.1。

构建期由 CurseMaven 拉取该依赖，坐标为 `curse.maven:the-twilight-forest-227639:7797302`，对应 1.21.1 所使用的 4.8.3345。

暮色森林自带的 modmaven 已于 2024-07-06 停止更新（该源上的最新版本为 4.5.2428），而 1.21.1 所使用的版本在该源上只有空目录，因此只能使用 CurseMaven 这个第三方代理；为此 `build.gradle` 使用 `exclusiveContent` 将 `curse.maven` 这一组仓库单独摘出。

暮色森林是必装依赖，Java 代码直接引用其类型（`TFEntities`、`TFBlocks`、`TFSounds`、`TFConfig` 等常量与相应实体类），因此升级暮色森林时签名变化会在编译期暴露。构建期需要联网从 CurseMaven 拉包；只有编译期没有常量的数据 ID 仍是字符串：`data/` 下的战利品表、配方、标签与群系，以及 Java 里引用的方块标签、群系标签、配置特征与结构 id。

## GeckoLib 依赖

运行环境必须安装 GeckoLib 4.9.3 以上版本，该版本对应 1.21.1。

构建期从 GeckoLib 官方 maven 拉取 `compileOnly` 与 `runtimeOnly` 两种依赖，版本写在 `gradle.properties` 的 `geckolib_version` 中。

其中 `compileOnly` 供编译使用，`runtimeOnly` 使开发运行（例如 `runClient` 等任务）也能加载该模组；若缺少后者，开发运行会报 `requires geckolib 4.9.3 or above`。

该依赖在 `neoforge.mods.toml` 中声明为 `required`，因此它与暮色森林一样属于硬依赖。

高塔恶魂（`entity/tower_ghast`）的模型、动画、贴图与动画引擎均来自 GeckoLib，其中三条资源路径直接写在 `client/TowerGhastRenderer` 中，不遵循 GeckoLib 的命名约定。

动画源文件是 Yes Steve Model 的 Bedrock 文件，须先经过 `tools/ysm/` 的转换与校验，详见 `tools/ysm/README.md`。

GeckoLib 声明 `geckoanimfix` 为不兼容依赖，两者不可同时安装。

## 开发用的客户端模组

`runClient` 会额外加载 JEI、拼音搜索（Just Enough Characters）、Xaero 小地图与 Xaero 世界地图、Jade、Chunky、Zume、Tweakerge（带前置 MaFgLib）、AppleSkin、BoccHUD 与 OpenYSM。上述模组仅用于开发阶段查看配方、查看地图、查看方块信息、预生成区块、缩放、调整 tweak、查看饱食度与状态条，以及与本模组的塔恶魂原始 Bedrock 动画进行对拍。这些模组不是本模组的依赖，既不参与编译，也不会进入产物。

JEI 与 Xaero 使用各自的官方 maven；拼音搜索与 OpenYSM 使用 CurseMaven（坐标为 `curse.maven:just-enough-characters-250702:9163765`，对应 4.5.30；以及 `curse.maven:openysm-unofficial-neoforge-port-1587175:8384243`，对应 2.6.6）；Jade、Chunky、Zume、Tweakerge、MaFgLib、AppleSkin 与 BoccHUD 使用 Modrinth maven（坐标形式为 `maven.modrinth:<项目 slug>:<版本号>`）。各模组的版本写在 `gradle.properties` 中。

Chunky 在 Modrinth 上为 1.4.23 发布了 fabric、forge 与 neoforge 三条版本号相同的文件，若按版本号取用则会拿到 forge 那一条，FML 只会打印一行 `Skipping jar ... is for Minecraft Forge ...` 并将其静默跳过；因此 `chunky_version` 填写的是 neoforge 那一条的版本 id `LuFhm4eU`。

上述模组统一放在 `clientDevMods` 这个 configuration 中，并通过 `tasks.named('runClient') { classpathProvider.from(...) }` 挂载到客户端运行的 classpath 上；`runServer`、`runData` 与 `runGameTestServer` 的 classpath 中不包含这些模组。

配置过程中曾遇到三个问题。其一，若在 run 的配置块中写 `dependencies { runtimeOnly ... }`，依赖会落到工程级的 `runtimeOnly` 上，导致所有 run 都会带上它；其二，run 的 `additionalRuntimeClasspathConfiguration` 只会写进 `legacyClasspath.txt`，FML 不会将其作为模组扫描，因此在游戏中根本不可见；其三，run 的 `classpath` 在任务执行时才从 `classpathProvider` 取值，若直接向 `classpath` 添加条目，该条目会被覆盖。

Xaero 的两个依赖都需要 `xaero.lib`，该依赖由它们的 pom 传递带上；JEI 所需的 `mezz_config` 以 jar-in-jar 形式自带在 JEI 的 jar 中，`net.mezzdev.config` 这个坐标指向它的库而非模组；Modrinth 的 pom 只包含元信息，不携带依赖，因此 Tweakerge 的前置 `mafglib` 在 `clientDevMods` 中被显式写了一条，而 BoccHUD 0.4.1 的前置同样是 MaFgLib，同一条即可同时满足两者。

Voxy 与 MiniHUD、Tweakeroo 在 1.21.1 上均没有 NeoForge 构建（Voxy 官方只发布 fabric 版本，其最早的版本为 1.20.4，此后直接跳到 1.21.6），如需使用须自行将 jar 放入 `run/client/mods`。

OpenYSM 在 1.21.1 NeoForge 上没有官方构建：官方 Modrinth 项目只发布 1.20.1 的 forge 与 fabric 版本，早先的非官方 Modrinth 页面已经下架，仅剩 CurseForge 上的第三方移植 1587175，即此处使用的这条坐标。

OpenYSM 自带的 GeckoLib 属于分叉版本，包名位于 `org.openysm.geckolib3` 与 `com/elfmcys/yesstevemodel/geckolib3` 之下，其 jar 中不存在 `software/bernie` 的引用，因此与本模组所依赖的 GeckoLib 4.9.3 不冲突；它的 mixin 只涉及原版以及 parcool、create、slashblade 这几个可选模组。

## 构建与运行

`./gradlew build` 用于构建，产物位于 `build/libs/`。

`./gradlew runClient` 的运行目录为 `run/client`。

`./gradlew runServer` 的运行目录为 `run/server`。

`./gradlew runData` 的运行目录为 `run/data`，输出到 `src/generated/resources`。

`./gradlew runGameTestServer` 的运行目录为 `run/gametest`。

首次构建会下载并反编译 Minecraft。

若仅有资源文件发生改动，任务可能报告 UP-TO-DATE，此时须验证 jar 中的实际内容。

依赖校验方面，仓库自带 `gradle/verification-metadata.xml`，在解析依赖时逐个核对 SHA-256，Twilight Forest 与 c2me 也在覆盖范围之内。新增或升级依赖后须运行 `./gradlew --write-verification-metadata sha256 compileJava` 更新校验值；由于 NeoForm 运行时的 `neoFormRuntimeDependenciesCompileClasspath` 在不同平台上解析出的模块元数据并不完全一致，至少还须再运行一次 `./gradlew --write-verification-metadata sha256 dependencies --configuration neoFormRuntimeDependenciesCompileClasspath`；LWJGL 的 native 包按平台分发，在 Windows 上生成校验值后须补上 Linux 与 macOS 的校验值，否则 CI 首次解析 native 时会报告缺少校验。

## VS Code 的 Gradle 任务

侧栏的 Gradle 任务按钮使用 Java 扩展自带的 JRE（25）启动 daemon，而 `build.gradle` 中的 `java.toolchain` 指定为 21，该 JRE 探测不到这一工具链，于是 `createMinecraftArtifacts` 直接报 `Cannot find a Java installation ... languageVersion=21`，导致 `runClient` 之类的任务一经点击即失败。

`gradle.properties` 中的 `org.gradle.java.installations.fromEnv=JAVA_HOME` 使自动探测也读取环境变量中的 JDK；`settings.gradle` 中的 `foojay-resolver-convention` 会在连一个 21 都找不到时自行下载。这两条配置均与具体路径无关，命令行的 `gradlew` 与 CI 同样受益。

`.vscode/settings.json` 属于本机配置，`.vscode/` 已列入 `.gitignore`；该文件通过 `java.import.gradle.java.home` 将 daemon 固定在本机的 JDK 21 上，更换机器时须修改其中的路径。

Java 不读取 `HTTP_PROXY` 环境变量，若要经由加速器访问网络，须在 `~/.gradle/gradle.properties` 中写入 `systemProp.http.proxyHost`、`systemProp.http.proxyPort`、`systemProp.https.proxyHost`、`systemProp.https.proxyPort` 与 `systemProp.http.nonProxyHosts`，Gradle 的下载才会经过代理；端口须随加速器调整。

### 点击 runClient 即失败的三种原因

第一种原因是上文所述的 JDK 探测问题，第二种原因是 wrapper 发行版无法下载。

`gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 一旦从 `8.14.3` 改成其他版本，`gradlew` 会先到 services.gradle.org 下载几十到一百多 MB 的发行版；本机直连的速率只有 20KB/s 上下，10 秒读超时一到便退出，报 `Downloading ... failed: timeout`，连配置阶段都无法进入，于是侧栏的 `runClient` 与 `build` 按钮全部失效。其中的 `retries=0` 使其一次即放弃。

本项目固定在 Gradle 8.14.3，本机 `~/.gradle/wrapper/dists/` 中已有缓存，**不得随意升级 wrapper**；确需升级时，应先把发行版下载到本机缓存再修改版本，并将 `networkTimeout` 提高到 `60000`、将 `retries` 提高到 3 以上。

第三种原因是 Zig 工具链：`installZig` 与 `buildNative` 位于 `processResources` 的上游，因此它们的失败同样表现为「点 runClient 没有反应」，详见下文「native 网格内核」中关于工具链位置的说明。

### native 网格内核

`src/main/native/ddt_native.cpp` 由纯 C++17 与 `std::thread` 实现，不含平台相关 API，因此五个平台的库都能够在同一台机器上编译出来。

内核只完成一件事：将一段顶点按一个 4x4 矩阵与一个 3x3 法线矩阵进行变换，并写入直接缓冲区。它不识别实体、方块、骨骼或立方体。

每一段顶点称为一个 piece，调用方给出顶点区间、矩阵与法线取反标志。该标志的低三位依次对应 X、Y、Z 轴，仅在对应法线为负时取反，用于复现 GeckoLib 对退化平面的修正。

`NativeGeoMesh` 负责把任意 GeckoLib 烘焙模型摊平为 piece，`NativeMeshBatch` 负责在一帧内完成收集并一次性提交 JNI。实体渲染器与方块实体渲染器都可以使用同一套流程。

#### 变换内核与 SIMD 分发

内核共有四种实现，均位于 `ddt_native.cpp` 中。

| 内核 | 平台 | 一次处理的顶点 | 说明 |
|---|---|---|---|
| `scalar` | 全部 | 1 | 基线实现，同时承担尾部余数的处理 |
| `sse2` | x86-64 | 4 | 128 位 4 路，属于 x86-64 基线 |
| `avx2+fma` | x86-64 且 CPU 支持 AVX2 与 FMA、内核保存 YMM 状态 | 8 | 256 位 8 路，乘法与加法合并为 FMA |
| `neon` | aarch64 | 4 | 128 位 4 路，ARMv8 基线即已包含 |

顶点采用 AoS 布局，每个顶点的 8 个 float 连续存放。内核先将 4 个或 8 个顶点转置为按通道排列的向量，对六个输出通道各计算一次之后再转置回去。每个输出通道都是一条乘加链，摊到 8 个顶点上即是一条 `_mm256_mul_ps` 加两条 `_mm256_fmadd_ps`。

法线取反不引入分支：将 `值 < 0` 的比较结果与 `0x80000000` 掩码相与后再异或，其结果与标量实现的 `-x` 逐位一致，`-0` 与 `NaN` 不会被翻转。

运行期只选择一次，选中的函数指针随 job 下发，每个 piece 直接调用该指针。

- x86-64 使用 `cpuid` 读取 SSE2、AVX、FMA 与 AVX2 的支持情况，再用 `xgetbv` 确认 XCR0 的 SSE 与 YMM 两位均已置位，即内核确实会保存 YMM 状态，否则不启用 AVX2。
- aarch64 不做探测，NEON 属于基线。
- 选择之前先用 16 个固定顶点与一个非单位矩阵同标量实现比对一遍，再补 13 个顶点运行一遍尾部路径；取反标志按 0 与 X、Y、Z 三个单轴分别运行，若只测一个组合，则有误的轴会被漏过。
- 判定使用 `withinKernelTolerance` 随数值量级放大的 `1e-4` 相对阈值，任一值超出即回退到标量实现。
- 结果写入日志 `DawnDuskTerminal native mesh kernel loaded ... (abi 3, kernel avx2+fma)`，代码中也可以通过 `NativeMesh.kernelName()` 读取。

AVX2 实现使用 `__attribute__((target("avx2,fma")))` 单独标注，基线编译不包含 AVX 指令，因此在老 CPU 上加载不会直接触发 `#UD`。

不使用 `target_clones` 与 ifunc：ifunc 属于 ELF 的特性，Windows 与 macOS 均不支持，因此三个平台统一使用函数指针表。

`tools/native/benchmark.cpp` 是数值校验与吞吐对照工具，它将 `ddt_native.cpp` 整份 include 进去，因此测量到的就是产物中的那份代码。编译需要 JDK 的 `include` 目录与本仓库的 `src/main/native/win32`（Linux 与 macOS 使用 `posix`）。

```
zig c++ -O3 -std=c++17 -I<JDK>/include -Isrc/main/native/win32 tools/native/benchmark.cpp -o benchmark
```

它与运行时自检共用 `fillKernelProbe` 与 `withinKernelTolerance`，不另行复制一份输入与阈值。

输出内容包括：当前 CPU 选中的内核、每个内核在「三轴全开」与「flags 为 0」两种输入下相对标量实现的最大绝对误差、单个大块顶点的吞吐，以及按线上形状（712 个 24 顶点 piece 各调用一次）计算的每帧耗时。其中 piece 那一组才是能够外推到游戏内的数字；若只运行大块，广播与逐 piece 调用的开销会被摊薄，所得收益偏于乐观。

**不要把测量数字写进 README**，不同机器与不同编译器版本之间的差距很大。

构建固定使用 Zig，`installZig` 任务会自动将其下载到 Gradle 用户目录的 `ddt-zig/<version>`（默认为 `~/.gradle/ddt-zig/`），此后不再重复下载，也不需要系统中预装编译器。

工具链及其缓存（`~/.gradle/ddt-zig-cache/`）均不放在工程目录中：工程目录带有 DSH 沙盒留下的目录权限项，Zig 在该处创建编译临时目录会被拒绝，报 `cache: manifest_create AccessDenied`；将同一份二进制移到用户目录下即恢复正常，该现象与 Zig 版本无关。

ziglang.org 的下载速率只有 20KB/s 上下，79MB 的包中途断流属于常态，因此 `installZig` 的下载带有断点续传、重试、zip/xz 结构校验与官方 SHA-256 校验，残缺的包会保留在原处等待下一次续传，不会像旧实现那样直到 tar 解压阶段才报错。更换 `ddt_zig_version` 时，必须把对应版本的官方 shasum 补充到 `build.gradle` 的 `zigKnownSha256` 中，或用 `-Pddt_zig_sha256=<hex>` 显式给出，否则 `installZig` 会拒绝下载。国内网络可以从 PyPI 镜像获取同一份工具链，其速率比 ziglang.org 快两个数量级；将其解压到 `~/.gradle/ddt-zig/<version>` 后，`installZig` 会直接跳过下载。

| target | triple | 产物 |
|---|---|---|
| `windows-x86_64` | `x86_64-windows-gnu` | `ddt_native.dll` |
| `linux-x86_64` | `x86_64-linux-gnu.2.17` | `libddt_native.so` |
| `linux-aarch64` | `aarch64-linux-gnu.2.17` | `libddt_native.so` |
| `macos-x86_64` | `x86_64-macos` | `libddt_native.dylib` |
| `macos-aarch64` | `aarch64-macos` | `libddt_native.dylib` |

`./gradlew build` 默认编译全部五个目标，因此在任何平台上构建，`build/libs/` 中的 jar 都是全平台通用的。

jar 中的 `native/<target>/` 一次性带齐全部平台，运行时由 `NativeMesh` 按 `os.name` 与 `os.arch` 选择对应的库。

构建期仍然采用硬失败策略：任何一个目标编译失败都会导致 `gradlew build` 失败，`release.yml` 也会校验五个平台的库都在 jar 中。

运行期分为加载阶段失败与运行阶段失败两条路径。

| 阶段 | 失败情形 | 处理 |
|---|---|---|
| 加载 | 平台无产物、资源缺失、`System.load` 失败 | 打印一次 warn，禁用 native，渲染改走 GeckoLib 的 Java 路径 |
| 加载 | ABI 版本或顶点/矩阵/法线布局不匹配 | 打印 error 并带上两边的常量，禁用 native |
| 加载 | ABI 校验通过后启动探针失败 | 打印 error，禁用 native |
| 运行 | JNI 抛出异常、返回负错误码、句柄失效 | 打印一次 error，整个进程熔断，自本帧起回退到 Java 路径 |
| 硬崩溃 | SIGSEGV 之类 | 进程内无法捕获，由下次启动时的崩溃标记作保底处理 |

`NativeMesh` 不实现纯 Java 的顶点变换，其 Java 路径就是 `TowerGhastRenderer.renderCube` 中 GeckoLib 自带的那套实现。

### ABI 协议

Java 侧的 `NativeMesh.ABI_VERSION` 与 native 侧的 `ddt::kAbiVersion` 必须一致，当前均为 3。

native 侧导出四个只读常量：`nativeAbiVersion`、`nativeVertexFloats`、`nativeMatrixFloats` 与 `nativeNormalFloats`。

自版本 3 起新增 `nativeKernelName`，它返回实际选中的变换内核名，仅用于日志，调用时会顺带触发内核自检。

加载后首先比对这四个常量，然后运行一个最小用例：建立一个包含两条顶点的网格，用单位矩阵变换一次，并逐 float 比对输入与输出。该最小用例断言的是逐位相等，因此 AVX2 的 FMA 舍入差异不会影响该探针。

任何 JNI 函数签名、参数含义、顶点布局、piece 布局或返回值语义的变化，都必须同时修改两处版本号。

版本号写在 `gradle.properties` 的 `ddt_native_abi_version` 中，构建时通过 `-DDDT_NATIVE_ABI_VERSION` 传入 C++。

`verifyNativeAbi` 任务会检查 `NativeMesh.ABI_VERSION` 与该属性是否一致，`check` 依赖该任务。

### 为什么 `nativeTransform` 接收直接缓冲区

矩阵、法线与 piece id 这三个入参都是 `ByteBuffer.allocateDirect` 产生的直接缓冲区，而非 Java 数组。

数组入参在 JNI 中会经过 `GetFloatArrayElements`，每个 piece 包含 16 个 float 的矩阵加 9 个 float 的法线，712 个 piece 每帧约需 73KB 拷贝，实测比直接缓冲区多约 10us。

直接缓冲区由 `NativeMeshBatch` 在构造与扩容时整块分配，此后每帧只修改内容，不再重新分配。

当批次容量超过初始的 1024 段时，`growInputs` 会把已经写入的段搬到新缓冲区中；不能只做重新分配，否则批次中途扩容会丢失先前收集的矩阵。

不得为简化实现而将这三个参数改回数组，也不得每帧新建缓冲区。

### 为什么没有采用 FFM

Java 21 的 `java.lang.foreign` 仍属于 preview API，编译与运行都需要 `--enable-preview`，class 文件会带上 preview 标记（minor version 65535），普通 JVM 启动时直接抛出 `UnsupportedClassVersionError`。

Minecraft 1.21.1 固定在 Java 21，模组无法要求玩家修改启动参数，因此 FFM 不能进入发布产物。

实测在相同条件下 FFM 与 JNI 的性能持平（712 piece：49.5 与 49.7 us），数组入参那 10us 的差距来自拷贝而非绑定方式，改用直接缓冲区即可获得该收益，无需引入 FFM。

### native 的失败与安全模式

加载成功时会在 `config/dawnduskterminal-native.crash-marker` 写入一个标记，正常退出时由 `deleteOnExit` 删除。

若上次运行期间进程发生硬崩溃，该标记会被保留，下次启动时直接禁用 native 并打印一条 error，玩家删除该文件即可重试。

手动关闭 native 的方式有三种，优先级从高到低如下。

| 方式 | 写法 |
|---|---|
| 配置 | `render.native_mesh = false` |
| 系统属性 | `-Ddawnduskterminal.disableNative=true` |
| 环境变量 | `DAWNDUSKTERMINAL_DISABLE_NATIVE=true` |

`-Ddawnduskterminal.forceNative=true` 可以单次忽略崩溃标记，用于排查问题。

native 侧所有导出函数都在 `try/catch(...)` 内返回错误码，C++ 异常不会穿过 JNI 边界。

句柄是自增 id 而非裸指针，失效、重复释放与类型错误均返回 `kInvalidHandle`，不会解引用野指针。

`nativeCreateMesh` 校验每一段 `offset/count` 是否都在顶点数组内，`nativeTransform` 校验三个入参直接缓冲区的容量、输出缓冲区容量与 piece id 范围。

`jni.h` 取自当前构建所用的 JVM，`jni_md.h` 使用仓库中的版本：`src/main/native/win32` 供 Windows 使用，`src/main/native/posix` 同时供 Linux 与 macOS 使用。

| 属性 | 作用 |
|---|---|
| `-Pddt_zig_version=0.13.0` | 更换 Zig 版本，非内置版本还须配合 `-Pddt_zig_sha256` |
| `-Pddt_zig_sha256=<hex>` | 覆盖当前目标的官方包 SHA-256 |

Zig 的产物先写入 `build/tools/native-work`，随后只把目标库复制到 `build/native/<target>/`，以避免 Windows 上顺带产生的 `.lib` 与 `.pdb` 被打进 jar。

Windows DLL 只依赖 UCRT 与 `kernel32.dll`，不需要 `libwinpthread-1.dll` 之类的运行库。

## 游戏内测试

`./gradlew runGameTestServer` 用于运行游戏内测试。

测试使用命名空间 `dawnduskterminal`，模板为 `ddt_empty`。

### 已知的失败与卡死

整轮测试应当全部通过，退出码为失败数，全部通过时为 0。

**结构所在区块等不到实体刻会导致整轮测试卡死**（该问题已由 `mixin/GameTestChunkWaitMixin` 作保底处理）

原版 `GameTestInfo.tick` 仅在 `chunksLoaded` 为真、或结构包围盒覆盖的每个区块都满足 `isPositionEntityTicking` 时才放置结构并运行测试。实测会出现区块已经强制加载（`hasChunk` 为真）却始终不进入实体刻的情况：此时该条件恒为假，测试既不开始也不超时（判定超时的 `tickInternal` 只在该条件为真之后才会被调用），于是进度格子中一直保留着空格。

而 `GameTestServer` 叠加的 `waitUntilNextTick` 不进入休眠，它会以每秒上万 tick 的频率重复打印同一行，日志曾增长到 102 MB，且永不退出。实测 10 次中约出现 3 次，每次都是排在网格最前面的几个测试（结构网格由 `GameTestServer.startTests` 在随机原点铺开）。

`GameTestChunkWaitMixin` 在等待满 5 秒后即将 `chunksLoaded` 置为真并放行。在健康的一轮中，几个 tick 之内条件即为真（GameTestServer 每秒运行几万 tick），因此几乎不会触发；触发时会打印一条 warn。修复前 21 次中有 3 次卡死，修复后 37 次全部运行完毕，其中 4 次由该保底措施挽回，通过数不变。

### WorldgenTests

`chronoDimensionTypeIsRegistered` 验证维度类型已注册。

`portedDensityFunctionsAreRegistered` 验证移植的密度函数已注册。

`portedNoiseParametersAreRegistered` 验证移植的噪声参数已注册。

`terrainNoiseIsSeedDerived` 验证地形噪声随种子变化。

`paletteBiomeCopiesAreRegistered` 验证群系副本已注册。

`paletteBiomeCopiesCarryOurFeatures` 验证群系副本携带本模组的特征。

`paletteBiomeSourceIsRegistered` 验证调色板群系源已注册。

`chronoDimensionDeclaresThePaletteSource` 验证维度 JSON 解析所得的即为调色板群系源，`extra` 与极带群系齐全。

`polarBandsUseTheExtraBiomes` 验证极带选用的是 `extra` 中的极昼与极夜群系。

`configuredFeaturesAreRegistered` 验证配置特征已注册。

`veinFillersUseThePalette` 验证矿脉填充使用调色板。

`paletteBiomeSourceSubstitutesThePreset` 验证群系源替换原版预设。

### AquiferTests

`terrainAndFluidUseTheModPalette` 验证地形与流体使用模组调色板。

`aquifersAreEnabledLikeVanilla` 验证含水层配置与原版一致。

`oreVeinsAreEnabledLikeVanilla` 验证大矿脉已开启。

### PortalShapeTests

`plantedPoolIsPortal` 验证带植物的水池判定为传送门。

`barrenPoolIsNotPortal` 验证荒水池不判定为传送门。

`poolWithoutPlantsIsNotPortal` 验证没有植物的水池不判定为传送门。

`loneWaterBlockIsNotPortal` 验证单个水方块不判定为传送门。

`buildFillsPoolWithPortalFluid` 验证建造时将水池替换为传送门流体。

`portalBlockSurvivesWithNeighbour` 验证有邻居的传送门方块可以存活。

`isolatedPortalBlockSelfHeals` 验证孤立的传送门方块可以自愈。

`portalFluidIsCompleteWithTwoNeighbours` 验证存在两个邻居即视为完整。

### FluidReactionTests

测试项 `portalFluidWithWater`。

测试项 `portalFluidWithLava`。

测试项 `portalFluidWithScorchingTwilight`。

测试项 `scorchingTwilightWithWater`。

测试项 `scorchingTwilightWithLava`。

测试项 `portalFluidWithFlowingWater`。

测试项 `flowingPortalFluidWithWater`。

### CanopyFrostMeltTests

`canopyFrostSurvivesTheLightThatMeltsVanillaSnow` 验证苍穹霜不会像原版雪那样在明亮光照下融化。

### TranslucentFaceCullingTests

`translucentCanopyCullsTheFaceBetweenTwoOfItself` 验证半透明方块之间会剔除面。

`cullingAgreesWithTheVanillaReference` 验证与原版参照一致。

`differentBlocksAreNotCulledAgainstEachOther` 验证不同方块之间不剔除面。

`portalFluidCullsTheFaceBetweenTwoOfItself` 验证传送门流体之间会剔除面。

### WroughtIronGlassTests

`wroughtIronGlassIsVanillaGlassClass` 验证该方块与原版玻璃属于同一个类（`TransparentBlock`），具有 `noOcclusion` 特性，不造成窒息，不遮挡视线，天光可直接穿过，硬度为 0.3。

`stackedGlassCullsTheFaceBetweenThem` 验证方块竖直叠放时中间的面被剔除、最上面那一块方块的顶面保留，并排两块之间的侧面同样被剔除；竖直连接纹理即依赖这一行为。

`wroughtIronGlassIsInTheCommonGlassTag` 验证该方块位于 `#c:glass_blocks` 标签中。

`wroughtIronGlassCraftsShapelesslyFromGlassAndWroughtIron` 验证无序配方占满九格，由 8 个原版玻璃与 1 个 `twilightforest:wrought_iron_bar` 合成，一次产出 8 个。

### Experiment076Tests

`blockReplacesThePlaceholder` 验证 076 不再是占位的空壳，其物品即 `dawnduskterminal:experiment_076` 那个方块的 `BlockItem`。

`placingItCreatesItsBlockEntity` 验证方块放置后即拥有自己的方块实体（渲染落点），服务端与客户端均不挂载 ticker，因为外观由渲染器按世界时间实时计算。

`itLightsTheRoom` 验证该方块是 15 级光源方块；在外观一侧，六个面也一律按满亮度绘制（`Experiment076Cube` 中不使用光照图），因此放置在洞穴中或夜间同样明亮。

`miningItRunsTheReactorReactionWithoutFakeBlocks` 验证仅在方块被玩家<b>挖掉</b>时才执行一场砷铅铁反应堆的反应（替换为其他方块、被爆炸破坏均不计入，所挂载的事件为 `playerWillDestroy`）：当计数推进过 5 时，3x3x3 范围内不存在任何一个假金块或假钻石块；当计数推进到 120 时，塌陷已将 `twilightforest:reactor_debris` 灌入壳内。该反应是全局的，运行结束后必须调用 `clear()` 并清理现场。

`assetsMatchTheBlock` 验证方块状态只有一个变体、方块模型为 cube_all 且其回退贴图是候选贴图之一（netherrack）、物品模型使用 `builtin/entity` 并完整写出方块所用的那套 `display` 姿势、战利品表掉落自身，以及该方块位于 `#minecraft:mineable/pickaxe` 标签中。

> 外观本身完全属于客户端实现（六个面每 tick 各抽取一张贴图，世界中的渲染由 `client/Experiment076Renderer` 负责，物品栏与手持的渲染由 `client/Experiment076ItemRenderer` 负责，几何部分共用 `client/Experiment076Cube`），服务端无法测试。

### PaintingTests

`variantIsThreeByTwo` 验证变体为 3x2，贴图指向 `dawnduskterminal:painting_ghast_1`。

`vanillaPaintingItemCannotPlaceIt` 验证该变体不在 `#minecraft:painting_variant/placeable` 中，原版画物品无法选中它。

`entityIsLockedToItsVariant` 验证本模组的画实体强制使用自己的变体，通过 `setVariant` 写入其他变体也无法替换，中键获取的是该实体自身的物品。

`vanillaPaintingRefusesTheVariant` 验证原版画实体拒绝该变体（`PaintingVariantGuardMixin`）。

`itemPlacesThePainting` 验证贴着 3x2 的墙放置后，实体类型、朝向、变体与包围盒均正确，物品数量减一。

## 指令

`/ddt` 需要权限等级 2。

`/ddt line` 打印当前位置的原点、法向、距离、参数、天光与维度信息。

`/ddt tp <param>` 按投影参数在 -1 到 1 之间传送到维度内。

## 移植脚本

`tools/port_overworld_worldgen.py` 从 Gradle 缓存中的反编译客户端 jar 抽取原版世界生成，改写命名空间后写入本模组。

`tools/port_tf_biomes.py` 将暮色森林的群系内容填入对应维度的群系副本，保留洞穴雕刻，并重映射特征引用。

`tools/check_tf_biomes.py` 校验移植后的群系，检查 JSON 合法性、是否残留 `minecraft:*` 特征，以及 `twilightforest:*` 特征能否在 TF jar 内解析。

`tools/check_worldgen_refs.py` 校验配置特征的引用，报告「模组已有同名副本却仍指向 `minecraft:`」的漏改情况，以及无人引用的副本。

漏改不会报错，只会静默生效：群系副本把土替换成了霞土，而原版那一份不会替换，于是同一片林地中会同时生长两种土。

`tools/ysm/ysm_to_geckolib.py` 将 Yes Steve Model 的 Bedrock 动画转换成 GeckoLib 能够编译的形式，并输出逐子句的审计报告。

`tools/ysm/validate_geckolib.py` 按 GeckoLib 的真实解析规则校验动画文件，列出会被静默跳过的片段；它同时是全仓库唯一的 GeckoLib 语法规则模块。

`tools/ysm/ysm_selftest.py` 是转换表的自测脚本，覆盖每一条转换规则。

`tools/ysm/` 的说明、转换表与实测数据均位于该目录下的 `README.md`。

## CI

`build.yml` 在 push main 与 v 标签以及 PR 时触发，运行 `./gradlew build` 并上传 jar。

`gametest.yml` 在 PR 时触发，运行两遍 `./gradlew runGameTestServer`：一遍使用原版环境，一遍把 c2me 1.21.1 NeoForge 构建放进 `run/gametest/mods`。

`release.yml` 只在 push `main` 与推送 `v*` 标签时触发。对 `main` 的推送会自动把 `mod_version` 的补丁号加一并提交回 `main`，随后用 Zig 交叉编译全平台 jar，校验五个 native 与嵌套的 c2me 都在 jar 中，并发布 GitHub Release。其他分支不再自动发版，需要时手动运行 `workflow_dispatch`。标签必须匹配 `gradle.properties` 的 `mod_version`。

c2me 本体采用 MIT 许可证，只有 `c2me-opts-accel-opencl` 为 All Rights Reserved，且没有打进产物。1.21.1 NeoForge 目前有官方单独发布的 OpenCL Acceleration Module devbuild，但它同样是 ARR，要求 Java 25，只支持服务端，而且 c2me 会依据驱动将设备列入黑名单；本机 gfx90c 实测被标记为 `BROKEN_DRIVER_AMD_GCN`，强行绕过会在 `amdocl64.dll` 编译阶段导致 JVM 崩溃。不得内置该模块，也不得承诺开箱可用。

c2me 由 `patchC2me` 从 `c2me_version` 解析后以嵌套 jar 的形式打进产物，保留官方全部模块。Manifest 手动写在 `META-INF/jarjar/metadata.json` 中，不得换成 ModDevGradle 的 `jarJar` 配置，因为本地补丁 jar 在配置期解析不到。

专用服固定种子 12345 的对照已经执行过：官方 c2me 与不安装 c2me 的 3072 列地表高度、群系与流体指纹逐字节一致。开发环境中曾出现 15 列的高度差，属于 dev classpath 组合导致的假阳性，不得据此删除 c2me 模块。

“内置 c2me 加外部同版本 c2me”的组合也经过实测：经 JarJar 协商后可正常启动、正常生成地形、正常停服，地形指纹仍与生产基线一致。

地形指纹探针 `ChronoParityProbe` 只在 `DDT_CHRONO_PARITY=1` 或 `-Dddt.chronoParity=true` 时运行，固定种子的专用服会写出 `ddt-chrono-fingerprint.txt` 并停服。

concurrency 只在 PR 上启用 cancel-in-progress。

## 常见问题与注意事项

`noise_settings/chrono.json` 的 `default_fluid` 必须是 `portal_fluid`，多个 mixin 依赖该字段识别维度。

`ore_veins_enabled` 须保持为 true，矿脉填充由 `NoiseChunkVeinPaletteMixin` 替换为调色板方块。

feature 引用与 `neoforge:remove_features` 的 ID 必须真实存在，否则注册表在加载期抛出错误。

1.21.1 中密度函数的字段是 `argument`，而非 `input`。

`gradlew` 的权限位必须是 100755，若权限位丢失，在 Linux 上会直接无法运行。

天光会烘焙进区块，修改天光后旧存档需要重新计算。

修改世界生成后，建议删除 `run/<端>/world` 并重新生成。

## 新增内容的放置位置

新增方块放置在 `block/`、`registry/ModTerrainBlocks` 或 `registry/ModBlocks` 中。

新增世界生成数据放置在 `src/main/resources/data/dawnduskterminal/worldgen/` 中。

新增自定义 Feature 放置在 `world/` 中，并注册在 `registry/ModFeatures` 中。

新增矿石放置在 `registry/ModChronoOres` 中。

新增物品放置在 `registry/ModItems` 中。

新增画放置在 `data/dawnduskterminal/painting_variant/` 与 `assets/dawnduskterminal/textures/painting/` 中，对应的实体与物品见 `entity/GhastPaintingEntity` 与 `item/GhastPaintingItem`。

新增流体放置在 `registry/ModFluids` 或 `registry/ModScorchingTwilight` 中。

新增配置放置在 `config/DdtConfig` 中。

新增指令放置在 `command/DdtCommands` 中。

新增进度放置在 `data/dawnduskterminal/advancement/` 中。
