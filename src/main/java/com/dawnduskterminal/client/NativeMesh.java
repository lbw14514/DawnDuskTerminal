package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.config.DdtConfig;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * native 网格变换内核的 Java 门面
 *
 * <p>加载成功前先做 ABI 校验与一次真实调用探针 任何一步失败都进入「未启用」状态
 * 调用方通过 {@link #isAvailable()} 判断之后再用 {@link #sharedPool()} 与 {@link #createMesh} 等接口
 *
 * <p>失败分两类
 * <ul>
 *   <li>加载类失败 平台没有产物 资源缺失 System.load 失败 ABI 不匹配 探针失败
 *       记一条日志后关闭 native 渲染器继续用 GeckoLib 的 Java 路径</li>
 *   <li>运行类失败 JNI 抛异常 返回错误码 句柄失效 立即熔断 native 并回退 Java 路径
 *       之后整个进程不再调用 native</li>
 * </ul>
 *
 * <p>进程内的 JNI 崩溃（SIGSEGV 之类）无法被 Java 捕获 为了不让一次崩溃变成每次启动都崩溃
 * 加载前会在配置目录写一个 crash marker 正常退出时由 deleteOnExit 清掉
 * 下次启动看到 marker 就直接禁用 native 并提示删除路径
 *
 * <p>客户端渲染线程每次批处理只做一次 JNI 调用 顶点变换由库内的 C++ 线程池分块完成
 */
public final class NativeMesh {
    /**
     * JNI 协议版本 与 src/main/native/ddt_native.cpp 的 kAbiVersion 必须一致
     * 构建脚本会校验它和 gradle.properties 的 ddt_native_abi_version
     *
     * <p>2 起 {@link #transform} 的矩阵 法线 piece id 从 Java 数组改成直接缓冲区
     * <p>3 起多一个 {@code nativeKernelName} 导出 用来记录实际选中的 SIMD 内核
     */
    public static final int ABI_VERSION = 3;

    /** 每个顶点 8 个 float x y z nx ny nz u v */
    public static final int VERTEX_FLOATS = 8;

    /** 每个批次单元 16 个 float 列主序 4x4 */
    public static final int MATRIX_FLOATS = 16;

    /** 每个批次单元 9 个 float 列主序 3x3 */
    public static final int NORMAL_FLOATS = 9;

    // 与 native 的 ddt::ErrorCode 一一对应 非负返回值才是顶点数
    public static final int ERROR_INVALID_ARGUMENT = -1;
    public static final int ERROR_INVALID_HANDLE = -2;
    public static final int ERROR_OUTPUT_TOO_SMALL = -3;
    public static final int ERROR_INTERNAL = -4;
    /** Java 侧状态错误 native 不会返回 */
    public static final int ERROR_UNAVAILABLE = -5;

    /** 712 piece 的 TowerGhast 扫描在 2 线程最好 4 线程反而慢约 15% 见 docs/development.md */
    private static final int MAX_THREADS = 2;
    private static final String DISABLE_PROPERTY = "dawnduskterminal.disableNative";
    private static final String FORCE_PROPERTY = "dawnduskterminal.forceNative";
    private static final String DISABLE_ENV = "DAWNDUSKTERMINAL_DISABLE_NATIVE";
    private static final String CRASH_MARKER_NAME = "dawnduskterminal-native.crash-marker";

    private static volatile boolean ready;
    private static volatile Failure failure;
    private static volatile String kernel = "";
    private static long sharedPool;

    static {
        initialise();
    }

    private NativeMesh() {}

    /**
     * 触发类初始化 让加载 校验与探针发生在客户端初始化阶段 而不是第一次渲染时
     */
    public static void preload() {
        // 空方法 类初始化在第一次静态成员访问时已经完成
    }

    public static boolean isAvailable() {
        return ready;
    }

    /**
     * 实际选中的顶点变换内核 avx2+fma sse2 neon scalar 之一 不可用时返回空串
     *
     * <p>加载期由 native 探测一次 CPU 能力并按结果选路
     * 这里只是把结果读出来 给日志和排查用
     */
    public static String kernelName() {
        return kernel;
    }

    /**
     * 给日志和排查用的失败说明 可用时返回空串
     */
    public static String failureDescription() {
        Failure current = failure;
        return current == null ? "" : current.describe();
    }

    private static void initialise() {
        if (!DdtConfig.nativeMeshEnabled()) {
            fail(Stage.CONFIG, "配置 render.native_mesh 为 false", null);
            return;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)
            || "true".equalsIgnoreCase(System.getenv(DISABLE_ENV))) {
            fail(Stage.CONFIG, "系统属性或环境变量显式关闭了 native", null);
            return;
        }

        Path marker = crashMarkerPath();
        if (marker != null && Files.isRegularFile(marker) && !Boolean.getBoolean(FORCE_PROPERTY)) {
            fail(Stage.CRASH_GUARD,
                "检测到上次运行留下的崩溃标记 " + marker
                    + " 本次已强制走 Java 路径 确认 native 稳定后删除该文件即可重新启用"
                    + " 或用 -D" + FORCE_PROPERTY + "=true 单次跳过",
                null);
            return;
        }

        Platform platform = platform();
        if (platform == null) {
            fail(Stage.UNSUPPORTED,
                "没有为 " + System.getProperty("os.name", "?") + "/"
                    + System.getProperty("os.arch", "?") + " 构建 native 网格内核", null);
            return;
        }

        String resource = "/native/" + platform.key() + "/" + platform.library();
        try (InputStream input = NativeMesh.class.getResourceAsStream(resource)) {
            if (input == null) {
                fail(Stage.MISSING, "jar 里缺少 native 网格内核 " + resource, null);
                return;
            }
            // 不再用临时目录：deleteOnExit 钩子逆序执行 删目录时 dll 还在里面 Windows 上 dll 也被映射着
            // 结果每次启动都残留一份。改成按平台与模组版本落的稳定缓存目录，同版本直接复用
            Path directory = nativeCacheDirectory(platform);
            Files.createDirectories(directory);
            Path file = directory.resolve(platform.library());
            Path checksum = directory.resolve(platform.library() + ".sha256");
            byte[] library = input.readAllBytes();
            // 只比长度会放过同长度的坏库：缓存文件与 jar 里的资源必须逐字节一致才复用
            String expected = sha256(library);
            if (!cachedLibraryMatches(file, expected)) {
                try {
                    Files.write(file, library,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException writeFailure) {
                    // 另一个实例正映射着同名 dll 时覆盖会失败，但复用前仍要确认内容没被动过
                    if (!cachedLibraryMatches(file, expected)) {
                        throw writeFailure;
                    }
                    DawnDuskTerminal.LOGGER.warn(
                        "DawnDuskTerminal 无法覆盖 native 缓存 {}，复用已有文件: {}",
                        file, writeFailure.toString());
                }
            }
            writeChecksumIfNeeded(checksum, expected);

            // 先落标记再碰 native 这样连 System.load 期间的硬崩溃也能在下次启动被识别
            createMarker(marker);

            try {
                System.load(file.toAbsolutePath().toString());
            } catch (Throwable throwable) {
                removeMarker(marker);
                fail(Stage.LOAD, "加载 " + resource + " 失败", throwable);
                return;
            }

            if (!verifyAbi()) {
                removeMarker(marker);
                return;
            }
            if (!probe()) {
                removeMarker(marker);
                return;
            }

            kernel = queryKernelName();
            ready = true;
            DawnDuskTerminal.LOGGER.info(
                "DawnDuskTerminal native mesh kernel loaded from {} (abi {}, kernel {})",
                resource, ABI_VERSION, kernel);
        } catch (Throwable throwable) {
            removeMarker(marker);
            fail(Stage.LOAD, "准备 native 网格内核 " + resource + " 失败", throwable);
        }
    }

    private static Platform platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.contains("x86_64") || arch.contains("amd64") || arch.equals("x64");

        if (os.contains("win")) {
            if (arm64 || !x64) {
                return null;
            }
            return new Platform("windows-x86_64", "ddt_native.dll");
        }
        if (os.contains("mac")) {
            if (arm64) {
                return new Platform("macos-aarch64", "libddt_native.dylib");
            }
            return x64 ? new Platform("macos-x86_64", "libddt_native.dylib") : null;
        }
        if (os.contains("linux")) {
            if (arm64) {
                return new Platform("linux-aarch64", "libddt_native.so");
            }
            return x64 ? new Platform("linux-x86_64", "libddt_native.so") : null;
        }
        return null;
    }

    /** 稳定缓存目录 平台与模组版本各一层 同版本启动直接复用 不随进程退出留下新副本 */
    private static Path nativeCacheDirectory(Platform platform) {
        return FMLPaths.CONFIGDIR.get()
            .resolve("dawnduskterminal")
            .resolve("native")
            .resolve(platform.key())
            .resolve(safeVersion());
    }

    private static String safeVersion() {
        String version;
        try {
            version = ModList.get().getModContainerById(DawnDuskTerminal.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("dev");
        } catch (Throwable throwable) {
            version = "dev";
        }
        return version.replaceAll("[^A-Za-z0-9._+-]", "_");
    }

    /** 缓存库与 jar 内资源逐字节一致才允许交给 System.load */
    private static boolean cachedLibraryMatches(Path file, String expected) {
        try {
            return Files.isRegularFile(file) && expected.equals(sha256(file));
        } catch (IOException exception) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) {
        MessageDigest digest = sha256Digest();
        digest.update(bytes);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** .sha256 只是给人看的旁路文件 复用判断始终以缓存库本身的摘要为准 */
    private static void writeChecksumIfNeeded(Path path, String expected) {
        try {
            if (Files.isRegularFile(path)
                && expected.equals(Files.readString(path, StandardCharsets.UTF_8).trim())) {
                return;
            }
        } catch (IOException ignored) {
            // 读不到就重写
        }
        try {
            Files.writeString(path, expected + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 无法写入 native 校验文件 {}: {}", path, exception.toString());
        }
    }

    /**
     * 版本与内存布局的握手 只要有一项对不上就不允许调用后续 JNI
     */
    private static boolean verifyAbi() {
        try {
            int abi = nativeAbiVersion();
            int vertexFloats = nativeVertexFloats();
            int matrixFloats = nativeMatrixFloats();
            int normalFloats = nativeNormalFloats();
            if (abi != ABI_VERSION || vertexFloats != VERTEX_FLOATS
                || matrixFloats != MATRIX_FLOATS || normalFloats != NORMAL_FLOATS) {
                fail(Stage.ABI, String.format(Locale.ROOT,
                    "ABI 不匹配 java(abi=%d 顶点=%d 矩阵=%d 法线=%d) native(abi=%d 顶点=%d 矩阵=%d 法线=%d)",
                    ABI_VERSION, VERTEX_FLOATS, MATRIX_FLOATS, NORMAL_FLOATS,
                    abi, vertexFloats, matrixFloats, normalFloats), null);
                return false;
            }
            return true;
        } catch (Throwable throwable) {
            fail(Stage.ABI, "读取 native ABI 失败", throwable);
            return false;
        }
    }

    /**
     * 内核名只是诊断信息 读不到不影响 native 可用性
     */
    private static String queryKernelName() {
        try {
            String name = nativeKernelName();
            return name == null || name.isBlank() ? "unknown" : name;
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 读取 native 内核名失败: {}", throwable.toString());
            return "unknown";
        }
    }

    /**
     * 加载后立即跑一遍最小用例
     *
     * <p>版本号只能证明双方常量一致 探针还能证明线程池 句柄表 参数校验 输出布局确实能工作
     * 避免把「能加载但一调用就错」的库留到实际渲染时才炸
     */
    private static boolean probe() {
        long pool = 0L;
        long mesh = 0L;
        try {
            pool = nativeCreatePool(1);
            if (pool == 0L) {
                fail(Stage.PROBE, "native 探针线程池创建失败", null);
                return false;
            }

            float[] vertices = {
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.25F, 0.5F,
                1.0F, 2.0F, 3.0F, 1.0F, 0.0F, 0.0F, 0.75F, 0.25F,
            };
            mesh = nativeCreateMesh(vertices, new int[] {0}, new int[] {2}, new int[] {0});
            if (mesh == 0L) {
                fail(Stage.PROBE, "native 探针网格创建失败", null);
                return false;
            }

            ByteBuffer output = ByteBuffer.allocateDirect(2 * VERTEX_FLOATS * Float.BYTES)
                .order(ByteOrder.nativeOrder());
            ByteBuffer matrixBuffer = directFloatBuffer(new float[] {
                1.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F,
            });
            ByteBuffer normalBuffer = directFloatBuffer(new float[] {
                1.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F,
                0.0F, 0.0F, 1.0F,
            });
            ByteBuffer pieceIdBuffer = ByteBuffer.allocateDirect(Integer.BYTES)
                .order(ByteOrder.nativeOrder());
            pieceIdBuffer.asIntBuffer().put(0, 0);
            int transformed = nativeTransform(pool, mesh, matrixBuffer, normalBuffer, pieceIdBuffer, 1, output);
            if (transformed != 2) {
                fail(Stage.PROBE, "native 探针返回 " + transformed, null);
                return false;
            }

            FloatBuffer result = output.asFloatBuffer();
            for (int i = 0; i < vertices.length; i++) {
                if (Float.floatToIntBits(result.get(i)) != Float.floatToIntBits(vertices[i])) {
                    fail(Stage.PROBE, "native 探针输出在第 " + i + " 个 float 与输入不一致", null);
                    return false;
                }
            }
            return true;
        } catch (Throwable throwable) {
            fail(Stage.PROBE, "native 探针执行失败", throwable);
            return false;
        } finally {
            if (mesh != 0L) {
                try {
                    nativeDestroyMesh(mesh);
                } catch (Throwable ignored) {
                    // 探针清理失败不改变结论
                }
            }
            if (pool != 0L) {
                try {
                    nativeDestroyPool(pool);
                } catch (Throwable ignored) {
                    // 同上
                }
            }
        }
    }

    /**
     * 把 float 数组装进按 native 字节序的直接缓冲区 只用于加载期探针
     */
    private static ByteBuffer directFloatBuffer(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * Float.BYTES)
            .order(ByteOrder.nativeOrder());
        buffer.asFloatBuffer().put(values);
        return buffer;
    }

    private static Path crashMarkerPath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(CRASH_MARKER_NAME);
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 无法定位配置目录 native 崩溃标记不会启用: {}", throwable.toString());
            return null;
        }
    }

    private static void createMarker(Path marker) {
        if (marker == null) {
            return;
        }
        try {
            Files.writeString(marker,
                "DawnDuskTerminal native mesh kernel was active.\n"
                    + "Delete this file to retry native rendering after a crash.\n",
                StandardCharsets.UTF_8);
            marker.toFile().deleteOnExit();
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 无法写入 native 崩溃标记 {}: {}", marker, throwable.toString());
        }
    }

    private static void removeMarker(Path marker) {
        if (marker == null) {
            return;
        }
        try {
            Files.deleteIfExists(marker);
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 无法删除 native 崩溃标记 {}: {}", marker, throwable.toString());
        }
    }

    private static synchronized void fail(Stage stage, String detail, Throwable cause) {
        ready = false;
        if (failure == null) {
            failure = new Failure(stage, detail, cause);
            switch (stage) {
                case CONFIG -> DawnDuskTerminal.LOGGER.info(
                    "DawnDuskTerminal native mesh kernel disabled: {}", detail);
                case CRASH_GUARD, RUNTIME, ABI, PROBE -> DawnDuskTerminal.LOGGER.error(
                    "DawnDuskTerminal native mesh kernel disabled, using the GeckoLib Java path: {}",
                    detail, cause);
                default -> DawnDuskTerminal.LOGGER.warn(
                    "DawnDuskTerminal native mesh kernel unavailable, using the GeckoLib Java path: {}",
                    detail, cause);
            }
        }
    }

    /**
     * 全局共享的线程池 渲染线程只有一个 多个渲染器共用同一池不会互相阻塞出问题
     * 池在进程存活期间一直存在 不销毁
     *
     * @return 0 表示 native 不可用或池创建失败 调用方应回退 Java 路径
     */
    public static synchronized long sharedPool() {
        if (!ready) {
            return 0L;
        }
        if (sharedPool == 0L) {
            try {
                sharedPool = createPool(Math.min(MAX_THREADS,
                    Math.max(1, Runtime.getRuntime().availableProcessors())));
            } catch (NativeMeshException exception) {
                // createPool 内部已经 fail 过一次 这里再显式标记 保证后续帧不会每帧重试建池
                fail(Stage.RUNTIME, "共享 native 线程池创建失败", exception);
                return 0L;
            }
        }
        return sharedPool;
    }

    public static long createPool(int threads) {
        requireReady();
        long handle;
        try {
            handle = nativeCreatePool(Math.max(1, threads));
        } catch (Throwable throwable) {
            throw runtimeFailure("创建线程池抛出异常", throwable);
        }
        if (handle == 0L) {
            throw runtimeFailure("native 线程池创建失败", null);
        }
        return handle;
    }

    public static void destroyPool(long handle) {
        if (handle == 0L) {
            return;
        }
        try {
            nativeDestroyPool(handle);
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 销毁 native 线程池失败: {}", throwable.toString());
        }
    }

    /**
     * flags 低三位依次表示法线在 X Y Z 轴为负时是否取反
     */
    public static long createMesh(float[] vertices, int[] offsets, int[] counts, int[] flags) {
        requireReady();
        long handle;
        try {
            handle = nativeCreateMesh(vertices, offsets, counts, flags);
        } catch (Throwable throwable) {
            throw runtimeFailure("创建网格抛出异常", throwable);
        }
        if (handle == 0L) {
            throw runtimeFailure("native 网格创建失败 参数或顶点布局不合法", null);
        }
        return handle;
    }

    public static void destroyMesh(long handle) {
        if (handle == 0L) {
            return;
        }
        try {
            nativeDestroyMesh(handle);
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 销毁 native 网格失败: {}", throwable.toString());
        }
    }

    /**
     * @return 网格顶点总数
     * @throws NativeMeshException native 不可用或返回错误码 调用方应回退 Java 路径
     *
     * <p>矩阵 法线 piece id 三个入参都是直接缓冲区 且容量按 count 截断
     * 缓冲区里前 count 段之外的内容不会被读到
     */
    public static int transform(long pool, long mesh, ByteBuffer matrices, ByteBuffer normals,
                                ByteBuffer pieceIds, int count, ByteBuffer output) {
        requireReady();
        int result;
        try {
            result = nativeTransform(pool, mesh, matrices, normals, pieceIds, count, output);
        } catch (Throwable throwable) {
            throw runtimeFailure("变换调用抛出异常", throwable);
        }
        if (result < 0) {
            throw runtimeFailure("native 变换失败: " + errorMessage(result), null);
        }
        return result;
    }

    private static void requireReady() {
        if (ready) {
            return;
        }
        Failure current = failure;
        throw new NativeMeshException(ERROR_UNAVAILABLE,
            current == null ? "native 网格内核不可用" : "native 网格内核不可用 " + current.describe());
    }

    private static NativeMeshException runtimeFailure(String detail, Throwable cause) {
        reportRuntimeFailure(detail, cause);
        return new NativeMeshException(ERROR_INTERNAL,
            "native 网格内核运行时故障 " + detail, cause);
    }

    /**
     * 供批处理层报告内核返回了逻辑上不可能的结果
     */
    static void reportRuntimeFailure(String detail) {
        fail(Stage.RUNTIME, detail, null);
    }

    private static void reportRuntimeFailure(String detail, Throwable cause) {
        fail(Stage.RUNTIME, detail, cause);
    }

    private static String errorMessage(int code) {
        return switch (code) {
            case ERROR_INVALID_ARGUMENT -> "参数不合法";
            case ERROR_INVALID_HANDLE -> "句柄已失效";
            case ERROR_OUTPUT_TOO_SMALL -> "输出缓冲区不足";
            case ERROR_INTERNAL -> "库内部异常";
            default -> "未知错误码 " + code;
        };
    }

    private record Platform(String key, String library) {}

    private enum Stage {
        CONFIG("配置"),
        UNSUPPORTED("平台不支持"),
        MISSING("资源缺失"),
        LOAD("动态库加载"),
        ABI("ABI 校验"),
        PROBE("启动探针"),
        CRASH_GUARD("崩溃保护"),
        RUNTIME("运行时");

        private final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    private record Failure(Stage stage, String detail, Throwable cause) {
        private String describe() {
            String suffix = cause == null ? "" : " (" + cause + ")";
            return "[" + stage.label + "] " + detail + suffix;
        }
    }

    /**
     * 加载失败与运行时故障统一用这个异常告诉渲染器「本帧不要再用 native」
     */
    public static final class NativeMeshException extends IllegalStateException {
        private final int code;

        NativeMeshException(int code, String message) {
            super(message);
            this.code = code;
        }

        NativeMeshException(int code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    // native 导出 名字与 ddt_native.cpp 的 Java_com_dawnduskterminal_client_NativeMesh_* 一一对应
    private static native int nativeAbiVersion();

    private static native int nativeVertexFloats();

    private static native int nativeMatrixFloats();

    private static native int nativeNormalFloats();

    /** 返回实际选中的变换内核名 调用它会触发一次内核自检 */
    private static native String nativeKernelName();

    private static native long nativeCreatePool(int threads);

    private static native void nativeDestroyPool(long handle);

    private static native long nativeCreateMesh(float[] vertices, int[] offsets, int[] counts, int[] flags);

    private static native void nativeDestroyMesh(long handle);

    /**
     * 变换一组顶点区间 结果写进 {@code output}
     *
     * <p>矩阵 法线 piece id 三个入参都是直接缓冲区 由调用方复用 不要再改成 Java 数组
     * 数组入参会让 native 每帧做一次 GetFloatArrayElements 拷贝 712 个 piece 约 73KB
     * 实测每帧多出约 10us 直接缓冲区把这部分省掉
     */
    private static native int nativeTransform(long pool, long mesh, ByteBuffer matrices, ByteBuffer normals,
                                              ByteBuffer pieceIds, int count, ByteBuffer output);
}
