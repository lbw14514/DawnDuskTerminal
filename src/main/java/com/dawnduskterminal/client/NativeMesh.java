package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * native 网格变换内核
 *
 * <p>jar 里必然带着当前平台对应的库 加载失败直接抛错 不做纯 Java 兜底
 * 客户端渲染线程每次批处理只做一次 JNI 调用 顶点变换由库内的 C++ 线程池分块完成
 *
 * <p>内核不认识实体 方块 骨骼或立方体 只认识顶点区间 每段顶点配一个矩阵与一个法线矩阵
 * 任何需要按矩阵批量变换 position normal uv 的自定义渲染都可以走这里
 * 每个区间还能带三个法线取反标志 用于 GeckoLib 那种退化平面的法线修正
 */
public final class NativeMesh {
    private static final int MAX_THREADS = 4;
    private static long sharedPool;

    static {
        load();
    }

    private NativeMesh() {}

    private static void load() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String key;
        String library;

        if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                throw new IllegalStateException("没有为 windows-aarch64 构建 native 网格内核");
            }
            key = arch.contains("64") ? "windows-x86_64" : "windows-x86";
            library = "ddt_native.dll";
        } else if (os.contains("mac")) {
            key = arch.contains("aarch64") || arch.contains("arm64") ? "macos-aarch64" : "macos-x86_64";
            library = "libddt_native.dylib";
        } else if (os.contains("linux")) {
            key = arch.contains("aarch64") || arch.contains("arm64") ? "linux-aarch64" : "linux-x86_64";
            library = "libddt_native.so";
        } else {
            throw new IllegalStateException("没有为 " + os + "/" + arch + " 构建 native 网格内核");
        }

        String resource = "/native/" + key + "/" + library;
        try (InputStream input = NativeMesh.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("jar 里缺少 native 网格内核 " + resource);
            }
            Path directory = Files.createTempDirectory("dawnduskterminal-native");
            Path file = directory.resolve(library);
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            System.load(file.toAbsolutePath().toString());
            DawnDuskTerminal.LOGGER.info("DawnDuskTerminal native mesh kernel loaded from {}", resource);
        } catch (Throwable throwable) {
            throw new IllegalStateException("加载 native 网格内核失败 " + resource, throwable);
        }
    }

    /**
     * 全局共享的线程池 渲染线程只有一个 多个渲染器共用同一池不会互相阻塞出问题
     * 池在进程存活期间一直存在 不销毁
     */
    public static synchronized long sharedPool() {
        if (sharedPool == 0L) {
            sharedPool = createPool(Math.min(MAX_THREADS,
                Math.max(1, Runtime.getRuntime().availableProcessors())));
            if (sharedPool == 0L) {
                throw new IllegalStateException("native 网格线程池创建失败");
            }
        }
        return sharedPool;
    }

    public static long createPool(int threads) {
        return nativeCreatePool(Math.max(1, threads));
    }

    public static void destroyPool(long handle) {
        if (handle != 0L) {
            nativeDestroyPool(handle);
        }
    }

    /**
     * flags 低三位依次表示法线在 X Y Z 轴为负时是否取反
     */
    public static long createMesh(float[] vertices, int[] offsets, int[] counts, int[] flags) {
        return nativeCreateMesh(vertices, offsets, counts, flags);
    }

    public static void destroyMesh(long handle) {
        if (handle != 0L) {
            nativeDestroyMesh(handle);
        }
    }

    /**
     * @return 网格顶点总数；负数表示库内部失败
     */
    public static int transform(long pool, long mesh, float[] matrices, float[] normals,
                                int[] pieceIds, int count, ByteBuffer output, int outputCapacityFloats) {
        return nativeTransform(pool, mesh, matrices, normals, pieceIds, count, output, outputCapacityFloats);
    }

    private static native long nativeCreatePool(int threads);

    private static native void nativeDestroyPool(long handle);

    private static native long nativeCreateMesh(float[] vertices, int[] offsets, int[] counts, int[] flags);

    private static native void nativeDestroyMesh(long handle);

    private static native int nativeTransform(long pool, long mesh, float[] matrices, float[] normals,
                                              int[] pieceIds, int count, ByteBuffer output,
                                              int outputCapacityFloats);
}
