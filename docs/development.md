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

依赖校验：仓库带 `gradle/verification-metadata.xml`，解析依赖时逐个核对 SHA-256，Twilight Forest 与 c2me 也在覆盖范围内。新增或升级依赖后跑 `./gradlew --write-verification-metadata sha256 compileJava` 更新；NeoForm 运行时的 `neoFormRuntimeDependenciesCompileClasspath` 在不同平台解析出的模块元数据不完全一样，至少再跑一次 `./gradlew --write-verification-metadata sha256 dependencies --configuration neoFormRuntimeDependenciesCompileClasspath`；LWJGL 的 native 包按平台分发，Windows 上生成后记得补上 Linux 与 macOS 的校验值，否则 CI 第一次解析 native 时会报缺少校验

## VS Code 的 Gradle 任务

侧栏的 Gradle 任务按钮用 Java 扩展自带的 JRE（25）起 daemon，`build.gradle` 的 `java.toolchain` 21 探测不到，`createMinecraftArtifacts` 直接报 `Cannot find a Java installation ... languageVersion=21`，于是 `runClient` 之类一点就挂

`gradle.properties` 的 `org.gradle.java.installations.fromEnv=JAVA_HOME` 让自动探测也读环境变量里的 JDK，`settings.gradle` 的 `foojay-resolver-convention` 在一个 21 都找不到时自己下载，两条都与具体路径无关，命令行的 `gradlew` 与 CI 同样受益

`.vscode/settings.json` 是本机配置，`.vscode/` 在 `.gitignore` 里，用 `java.import.gradle.java.home` 把 daemon 钉在本机 JDK 21，换机器要改路径

Java 不读 `HTTP_PROXY` 环境变量，走加速器要在 `~/.gradle/gradle.properties` 里写 `systemProp.http.proxyHost` `systemProp.http.proxyPort` `systemProp.https.proxyHost` `systemProp.https.proxyPort` 与 `systemProp.http.nonProxyHosts`，Gradle 的下载才会经过代理，端口随加速器改

### 点 runClient 一点就挂的三种原因

第一种是上面那段 JDK 探测，第二种是 wrapper 发行版拉不下来

`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 一旦从 `8.14.3` 改成别的版本，`gradlew` 会先去 services.gradle.org 下几十到一百多 MB 的发行版，本机直连只有 20KB/s 上下，10 秒读超时一到就退出，报 `Downloading ... failed: timeout`，连配置阶段都进不去，于是侧栏的 `runClient` `build` 按钮全部失效。`retries=0` 让它一次就放弃

本项目停在 Gradle 8.14.3，本机 `~/.gradle/wrapper/dists/` 里有缓存，**不要随手升 wrapper**；真要升就先把发行版下到本机缓存里再改，并把 `networkTimeout` 提到 `60000`、`retries` 提到 3 以上

第三种是 Zig 工具链：`installZig` 与 `buildNative` 在 `processResources` 的上游，所以它们的失败同样表现成「点 runClient 没反应」，见下面「native 网格内核」里关于工具链位置的说明
### native 网格内核

`src/main/native/ddt_native.cpp` 是 C++20 线程用 `std::jthread` 完成同步用 `std::latch`
亲和性只在 Windows 与 Linux 上做 best-effort 且默认关闭 macOS 直接跳过
其余仍是纯计算代码 五个平台的库都能在同一台机器上编出来

内核只做一件事 把一段顶点按一个 4x4 矩阵与一个 3x3 法线矩阵变换 写进直接缓冲区 它不认识实体 方块 骨骼或立方体

每段顶点叫一个 piece 调用方给出顶点区间 矩阵和法线取反标志 标志低三位依次对应 X Y Z 轴 只在对应法线为负时取反 用来复现 GeckoLib 退化平面的修正

piece 的顶点区间必须互不重叠 native 不做重叠检查 并发写同一段输出是调用方错误 `NativeGeoMesh` 摊平出来的区间天然不重叠

`NativeGeoMesh` 把任意 GeckoLib 烘焙模型摊平成 piece `NativeMeshBatch` 负责一帧内收集并一次 JNI 提交 实体渲染器和方块实体渲染器都能用同一套流程

#### 变换内核与 SIMD 分发

内核有四种实现 都在 `ddt_native.cpp` 里

| 内核 | 平台 | 一次处理的顶点 | 说明 |
|---|---|---|---|
| `scalar` | 全部 | 1 | 基线 也是尾部余数的兜底 |
| `sse2` | x86-64 | 4 | 128 位 4 路 x86-64 基线 |
| `avx2+fma` | x86-64 且 CPU 支持 AVX2 与 FMA 内核保存 YMM 状态 | 8 | 256 位 8 路 乘加合并成 FMA |
| `neon` | aarch64 | 4 | 128 位 4 路 ARMv8 基线就带 |

顶点是 AoS 布局 每个顶点 8 个 float 连续放着 内核先把 4 个或 8 个顶点转置成按通道排列的向量 六个输出通道各算一次再转置回去 每个输出通道都是一条乘加链 摊到 8 个顶点上就是一条 `_mm256_mul_ps` 加两条 `_mm256_fmadd_ps`

法线取反折进掩码 把 `值 < 0` 的比较结果与 `0x80000000` 掩码相与再异或 和标量的 `-x` 逐位一致 `-0` 与 `NaN` 不会被翻 翻转由模板参数分成 `kFlip` 为真与假两份实例 循环里没有分支

运行期只选一次 选中的函数指针随 job 下发 每个 piece 直接调用

- x86-64 用 `cpuid` 读 SSE2 AVX FMA AVX2 再用 `xgetbv` 确认 XCR0 的 SSE 与 YMM 两位都置位 也就是内核真的会保存 YMM 状态 否则不开 AVX2
- aarch64 不探测 NEON 是基线
- 选之前先用 16 个固定顶点与一个非单位矩阵跟标量比一遍 再补 13 个顶点走一遍尾部路径 取反标志按 0 与 X Y Z 三个单轴分别跑 只测一个组合会让写错的轴混过去
- 判定用 `withinKernelTolerance` 随数值量级放大的 `1e-4` 相对阈值 任一值超出就退回标量
- 结果写进日志 `DawnDuskTerminal native mesh kernel loaded ... (abi 3, kernel avx2+fma)` 代码里也能读 `NativeMesh.kernelName()`

AVX2 实现用 `__attribute__((target("avx2,fma")))` 单独标注 基线编译不带 AVX 指令 老 CPU 上加载不会直接 `#UD`

不用 `target_clones` 与 ifunc ifunc 是 ELF 的东西 Windows 与 macOS 都没有 三平台统一用函数指针表

#### 线程池与完成同步

每个 job 开始前按线程数把 piece 均分成连续区间 每个 worker 只跑自己的 `[begin,end)`
不再用 `fetch_add` 抢同一个原子计数器 完成同步用 `std::latch(workers.size())`
latch 是单次对象 每帧 `make_shared` 一份 worker 里再拿一份 最后一个引用释放时才析构 避免 `wait` 返回时还有 worker 停在 `count_down` 里面
启动仍用 `condition_variable` 加 generation 唤醒 worker 线程用 `std::jthread` 析构自动 join

Zig 0.13 自带的 libc++ 把 `stop_token` 与 `jthread` 标成实验接口 构建固定加 `-D_LIBCPP_ENABLE_EXPERIMENTAL` 否则 `<thread>` 里没有 `std::jthread`

#### benchmark

`tools/native/benchmark.cpp` 是数值校验与吞吐对照台 它把 `ddt_native.cpp` 整份 include 进去 量到的就是产物里那份代码 编译要 JDK 的 `include` 与本仓库的 `src/main/native/win32` Linux 与 macOS 用 `posix`

```
zig c++ -O3 -std=c++20 -D_LIBCPP_ENABLE_EXPERIMENTAL -I<JDK>/include -Isrc/main/native/win32 tools/native/benchmark.cpp -o benchmark
```

它和运行时自检共用 `fillKernelProbe` 与 `withinKernelTolerance` 不另抄一份输入与阈值
默认不钉核 加 `--affinity` 才是打开亲和性的那一组

以下数字是一次快照 机器是 AMD Ryzen 7 5800H 8 核 16 线程 Windows Zig 0.13.0 `-O3`
不同 CPU 不同编译器版本差距很大 **不要把数字当成保证值 也不要写进 README**

大块 65536 顶点 400 轮 三轴全开

每组跑三次取最短 抵消机器上的后台负载

| 实现 | 总耗时 | Mvertices/s | GB/s | 相对 scalar auto |
|---|---|---|---|---|
| scalar auto | 105.61 ms | 248.2 | 15.9 | 1.00x |
| scalar novec | 132.25 ms | 198.2 | 12.7 | 0.80x |
| sse2 | 67.86 ms | 386.3 | 24.7 | 1.56x |
| avx2+fma | 67.15 ms | 390.4 | 25.0 | 1.57x |

线上形状 712 个 piece 每个 24 顶点 3000 帧

| 实现 | 每帧 | frames/s | 相对 scalar auto |
|---|---|---|---|
| scalar auto | 65.19 us | 15340.8 | 1.00x |
| scalar novec | 84.11 us | 11889.5 | 0.78x |
| sse2 | 43.80 us | 22833.6 | 1.49x |
| avx2+fma | 38.61 us | 25898.8 | 1.69x |

手写 AVX2 相对编译器自动向量化约 1.6 到 1.7 倍 相对禁用向量化的标量在大块上约 2.0 倍 在 24 顶点 piece 上约 2.2 倍
SSE2 在大块上已经追平 AVX2 在 24 顶点的小 piece 上 AVX2 还有约 13% 优势 因为逐 piece 调用让编译器没法跨 piece 做更好的调度

线程扫描 同一份 712 piece 负载 固定用选中的 avx2+fma 内核 默认不钉核

| 线程数 | 每帧 | frames/s | 相对 1 线程 |
|---|---|---|---|
| 1 | 38.35 us | 26074.0 | 1.00x |
| 2 | 26.68 us | 37482.3 | 1.44x |
| 4 | 30.72 us | 32556.3 | 1.25x |
| 8 | 48.12 us | 20782.6 | 0.80x |
| 16 | 65.51 us | 15265.2 | 0.59x |

这个负载的最佳点在 2 线程 4 线程反而慢约 15% 再往上唤醒与调度开销超过分块收益
Java 侧共享池上限从 4 改成 2 与扫描结果一致

#### SoA 原型为什么没留下

另做了一版临时 SoA 原型 没有留在最终树里
`nativeCreateMesh` 时把顶点摊成 8 条连续通道 输出也走 SoA 内核完全去掉转置 Java 侧按通道读
同一套 benchmark 的结果 原型只跑了单次 数值只用于看方向 结论在多次运行里一致

| 实现 | 大块 65536 | TowerGhast 712x24 |
|---|---|---|
| AoS scalar | 96.41 ms | 70.37 us/frame |
| AoS avx2+fma | 50.62 ms | 38.95 us/frame |
| SoA 输入 AoS 输出 | 50.15 ms | 39.15 us/frame |
| SoA scalar | 123.05 ms | 158.40 us/frame |
| SoA avx2+fma | 129.68 ms | 43.17 us/frame |

全 SoA 输出在大块上比 AoS AVX2 慢约 2.6 倍 真实形状也慢约 11%
转置不是瓶颈 8 条输入流加 8 条输出流把预取器和 TLB 压垮了
混合布局只追平 AoS 所以生产代码保留 AoS 这条数据比“教科书布局”重要

#### 线程亲和性

默认关闭 只有环境变量 `DAWNDUSKTERMINAL_NATIVE_AFFINITY=1` 才钉
Windows 用 `SetThreadGroupAffinity` 走 processor group 感知的路径 Linux 从 `sched_getaffinity` 的允许集里选核再 `pthread_setaffinity_np` macOS 没有公开的硬亲和 API 直接 no-op

同一台机器打开亲和性后的线程扫描

| 线程数 | 每帧 | frames/s | 相对 1 线程 |
|---|---|---|---|
| 1 | 41.36 us | 24175.6 | 1.00x |
| 2 | 29.41 us | 34002.8 | 1.41x |
| 4 | 87.68 us | 11405.5 | 0.47x |
| 8 | 154.13 us | 6488.1 | 0.27x |
| 16 | 1894.28 us | 527.9 | 0.02x |

2 线程时钉核已略差于不钉核 4 线程起就崩 8 线程以上慢一个数量级
原因是 worker 每帧会睡 硬亲和把唤醒绑在可能已经停核的 CPU 上 唤醒延迟压过了迁移成本
大小核与 SMT 机器上还会更不可控 所以这项功能保留 默认关

NUMA 机器上硬亲和只减少迁移 不代表内存就在本节点
网格由渲染线程分配 输出是 Java 直接缓冲区 两边都不受 worker 控制 不要指望钉核能带来 NUMA 局部性

#### JNI ABI fuzz

`tools/native/fuzz_abi.cpp` 直接把 `ddt_native.cpp` include 进来 用一张假 `JNIEnv` 函数表喂 `nativeCreateMesh` 与 `nativeTransform`
覆盖 null 数组 长度不匹配 负 count 缓冲区容量不足 越界 piece 与 NaN/Inf 顶点

Linux 与 macOS 用 clang 的 libFuzzer

```
clang++ -O1 -g -std=c++20 -D_LIBCPP_ENABLE_EXPERIMENTAL -fsanitize=fuzzer,address -I<JDK>/include -Isrc/main/native/posix tools/native/fuzz_abi.cpp -o fuzz_abi
./fuzz_abi -max_total_time=60
```

Windows 没有 libFuzzer 运行时 用固定畸形语料入口

```
zig c++ -O1 -g -std=c++20 -D_LIBCPP_ENABLE_EXPERIMENTAL -DDDT_FUZZ_STANDALONE -I<JDK>/include -Isrc/main/native/win32 tools/native/fuzz_abi.cpp -o fuzz_abi.exe
./fuzz_abi.exe
```

fuzz 目标只在本机跑 不打进 jar **不要把测量数字写进 README**

构建固定走 Zig，`installZig` 任务会自动下载到 Gradle 用户目录的 `ddt-zig/<version>`（默认 `~/.gradle/ddt-zig/`），之后不再重复下载，不需要系统里预装编译器

工具链与它的缓存（`~/.gradle/ddt-zig-cache/`）都不放工程目录：工程目录带 DSH 沙盒留下的目录权限项，zig 在那儿建编译临时目录会被拒，报 `cache: manifest_create AccessDenied`，同一份二进制搬到用户目录下就正常，与 Zig 版本无关

ziglang.org 只有 20KB/s 上下，79MB 的包中途断流是常态，所以 `installZig` 的下载带断点续传、重试、zip/xz 结构校验与官方 SHA-256 校验，残包会留在原地等下一次续传，不会像旧实现那样拖到 tar 解压才报错。换 `ddt_zig_version` 时必须把对应版本的官方 shasum 补进 `build.gradle` 的 `zigKnownSha256`，或用 `-Pddt_zig_sha256=<hex>` 显式给出，否则 `installZig` 拒绝下载。国内网络可以从 PyPI 镜像取同一份工具链，比 ziglang.org 快两个数量级，解压到 `~/.gradle/ddt-zig/<version>` 后 `installZig` 直接跳过下载

| target | triple | 产物 |
|---|---|---|
| `windows-x86_64` | `x86_64-windows-gnu` | `ddt_native.dll` |
| `linux-x86_64` | `x86_64-linux-gnu.2.17` | `libddt_native.so` |
| `linux-aarch64` | `aarch64-linux-gnu.2.17` | `libddt_native.so` |
| `macos-x86_64` | `x86_64-macos` | `libddt_native.dylib` |
| `macos-aarch64` | `aarch64-macos` | `libddt_native.dylib` |

`./gradlew build` 默认编全部五个，所以无论在哪个平台构建，`build/libs/` 里的 jar 都是全平台通用的

jar 里的 `native/<target>/` 一次带齐，运行时由 `NativeMesh` 按 `os.name` 与 `os.arch` 挑对应的库

构建期仍是硬失败：任何一个目标编不出来都会让 `gradlew build` 失败，`release.yml` 也会校验五个平台都在 jar 里

运行期分成加载类失败与运行类失败两条路径

| 阶段 | 失败情形 | 处理 |
|---|---|---|
| 加载 | 平台无产物、资源缺失、`System.load` 失败 | warn 一次，禁用 native，渲染走 GeckoLib 的 Java 路径 |
| 加载 | ABI 版本或顶点/矩阵/法线布局不匹配 | error 带上两边常量，禁用 native |
| 加载 | ABI 通过后启动探针失败 | error，禁用 native |
| 运行 | JNI 抛异常、返回负错误码、句柄失效 | error 一次，整个进程熔断，本帧起回退 Java 路径 |
| 硬崩溃 | SIGSEGV 之类 | 进程内无法捕获，由下次启动的崩溃标记兜底 |

`NativeMesh` 不做纯 Java 顶点变换，Java 路径就是 `TowerGhastRenderer.renderCube` 里 GeckoLib 自带的那套实现

### ABI 协议

Java 侧 `NativeMesh.ABI_VERSION` 与 native 侧 `ddt::kAbiVersion` 必须一致，当前都是 3

native 导出四个只读常量：`nativeAbiVersion` `nativeVertexFloats` `nativeMatrixFloats` `nativeNormalFloats`

3 起多一个 `nativeKernelName`，返回实际选中的变换内核名，只用于日志，调用时顺带触发内核自检

加载后先比对这四个常量，再跑一个最小用例：建一个两条顶点的网格，用单位矩阵变换一次，逐 float 比对输入输出。最小用例断言的是逐位相等，所以 AVX2 的 FMA 舍入差异不会影响探针

任何 JNI 函数签名、参数含义、顶点布局、piece 布局或返回值语义变化都要同时改两处版本号

版本号写在 `gradle.properties` 的 `ddt_native_abi_version`，构建时通过 `-DDDT_NATIVE_ABI_VERSION` 传进 C++

`verifyNativeAbi` 任务会检查 `NativeMesh.ABI_VERSION` 与这个属性一致，`check` 依赖它

### 为什么 `nativeTransform` 收直接缓冲区

矩阵 法线 piece id 三个入参是 `ByteBuffer.allocateDirect` 出来的直接缓冲区，不是 Java 数组

数组入参在 JNI 里会走 `GetFloatArrayElements`，每个 piece 16 个 float 矩阵加 9 个 float 法线，
712 个 piece 每帧约 73KB 拷贝，实测比直接缓冲区多约 10us

直接缓冲区由 `NativeMeshBatch` 在构造和扩容时整块分配，之后每帧只改内容不重新分配

批次容量超过初始的 1024 段时，`growInputs` 会把已经写进去的段搬到新缓冲区，
不能只重新分配，否则批次中途扩容会丢掉前面收集的矩阵

不要为了省事把这三个参数改回数组，也不要每帧新建缓冲区

### 为什么没有用 FFM

Java 21 的 `java.lang.foreign` 还是 preview API，编译和运行都要 `--enable-preview`，
class 文件带 preview 标记（minor version 65535），普通 JVM 启动直接 `UnsupportedClassVersionError`

Minecraft 1.21.1 锁在 Java 21，模组没法要求玩家改启动参数，所以 FFM 不能进发布产物

实测同样条件下 FFM 与 JNI 性能打平（712 piece：49.5 与 49.7 us），
数组入参那 10us 差距来自拷贝而不是绑定方式，换直接缓冲区就能拿到，不需要 FFM

### native 的失败与安全模式

加载成功时在 `config/dawnduskterminal-native.crash-marker` 写一个标记，正常退出由 `deleteOnExit` 删除

如果上次运行期间进程硬崩溃，标记会留下，下次启动直接禁用 native 并打一条 error，玩家删掉该文件即可重试

手动关闭 native 的三种方式，优先级从高到低

| 方式 | 写法 |
|---|---|
| 配置 | `render.native_mesh = false` |
| 系统属性 | `-Ddawnduskterminal.disableNative=true` |
| 环境变量 | `DAWNDUSKTERMINAL_DISABLE_NATIVE=true` |

`-Ddawnduskterminal.forceNative=true` 可以单次忽略崩溃标记，用于排查

native 侧所有导出函数都在 `try/catch(...)` 内返回错误码，C++ 异常不会穿过 JNI 边界

句柄是自增 id 而不是裸指针，失效、重复释放、类型拿错都返回 `kInvalidHandle`，不会解引用野指针

`nativeCreateMesh` 校验每一段 `offset/count` 都在顶点数组内，`nativeTransform` 校验三个入参直接缓冲区的容量、输出缓冲区容量与 piece id 范围

`jni.h` 取当前构建 JVM 的，`jni_md.h` 用仓库里的：`src/main/native/win32` 给 Windows，`src/main/native/posix` 同时给 Linux 与 macOS

| 属性 | 作用 |
|---|---|
| `-Pddt_zig_version=0.13.0` | 换 Zig 版本，非内置版本还要配 `-Pddt_zig_sha256` |
| `-Pddt_zig_sha256=<hex>` | 覆盖当前目标的官方包 SHA-256 |

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

`release.yml` 只在 push `main` 与推送 `v*` 标签时触发。`main` 自动把 `mod_version` 的补丁号加一并提交回 `main`，再用 Zig 交叉编译全平台 jar，校验五个 native 与嵌套的 c2me 都在 jar 里并发布 GitHub Release。其他分支不会再自动发版，需要时手动跑 `workflow_dispatch`。标签必须匹配 `gradle.properties` 的 `mod_version`

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
