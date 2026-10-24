package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * native 顶点变换内核
 *
 * <p>jar 里必然带着当前平台对应的库 加载失败直接抛错 不做纯 Java 兜底
 * 客户端渲染线程只做一次 JNI 调用 顶点变换由库内的 C++ 线程池分块完成
 */
public final class NativeVertex {
    static {
        load();
    }

    private NativeVertex() {}

    private static void load() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String key;
        String library;

        if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                throw new IllegalStateException("没有为 windows-aarch64 构建 native 顶点内核");
            }
            key = arch.contains("64") ? "windows-x86_64" : "windows-x86";
            library = "ddt_vertex.dll";
        } else if (os.contains("mac")) {
            key = arch.contains("aarch64") || arch.contains("arm64") ? "macos-aarch64" : "macos-x86_64";
            library = "ddt_vertex.dylib";
        } else if (os.contains("linux")) {
            key = arch.contains("aarch64") || arch.contains("arm64") ? "linux-aarch64" : "linux-x86_64";
            library = "ddt_vertex.so";
        } else {
            throw new IllegalStateException("没有为 " + os + "/" + arch + " 构建 native 顶点内核");
        }

        String resource = "/native/" + key + "/" + library;
        try (InputStream input = NativeVertex.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("jar 里缺少 native 顶点内核 " + resource);
            }
            Path directory = Files.createTempDirectory("dawnduskterminal-native");
            Path file = directory.resolve(library);
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            System.load(file.toAbsolutePath().toString());
            DawnDuskTerminal.LOGGER.info("DawnDuskTerminal native vertex kernel loaded from {}", resource);
        } catch (Throwable throwable) {
            throw new IllegalStateException("加载 native 顶点内核失败 " + resource, throwable);
        }
    }

    public static long createPool(int threads) {
        return nativeCreatePool(Math.max(1, threads));
    }

    public static void destroyPool(long handle) {
        if (handle != 0L) {
            nativeDestroyPool(handle);
        }
    }

    public static long createMesh(float[] vertices, int[] offsets, int[] counts, float[] sizes) {
        return nativeCreateMesh(vertices, offsets, counts, sizes);
    }

    public static void destroyMesh(long handle) {
        if (handle != 0L) {
            nativeDestroyMesh(handle);
        }
    }

    /**
     * @return 顶点总数；负数表示库内部失败
     */
    public static int transform(long pool, long mesh, float[] matrices, float[] normals,
                                int[] cubeIds, int count, java.nio.ByteBuffer output, int outputCapacityFloats) {
        return nativeTransform(pool, mesh, matrices, normals, cubeIds, count, output, outputCapacityFloats);
    }

    private static native long nativeCreatePool(int threads);

    private static native void nativeDestroyPool(long handle);

    private static native long nativeCreateMesh(float[] vertices, int[] offsets, int[] counts, float[] sizes);

    private static native void nativeDestroyMesh(long handle);

    private static native int nativeTransform(long pool, long mesh, float[] matrices, float[] normals,
                                              int[] cubeIds, int count, java.nio.ByteBuffer output,
                                              int outputCapacityFloats);
}
