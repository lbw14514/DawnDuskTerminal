package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 可选的 native 顶点变换内核
 *
 * <p>库不存在 加载失败 或平台没有构建产物时全部返回失败 调用方退回纯 Java 路径
 * 客户端渲染线程只做一次 JNI 调用 顶点变换由库内的 C++ 线程池分块完成
 */
public final class NativeVertex {
    private static final boolean AVAILABLE = load();

    private NativeVertex() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean load() {
        if (Boolean.getBoolean("ddt.disableNative")) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String key;
        String library;

        if (os.contains("win")) {
            key = arch.contains("64") ? "windows-x86_64" : "windows-x86";
            library = "ddt_vertex.dll";
        } else if (os.contains("mac")) {
            key = arch.contains("aarch64") ? "macos-aarch64" : "macos-x86_64";
            library = "ddt_vertex.dylib";
        } else if (os.contains("linux")) {
            key = arch.contains("aarch64") ? "linux-aarch64" : "linux-x86_64";
            library = "ddt_vertex.so";
        } else {
            return false;
        }

        String resource = "/native/" + key + "/" + library;
        try (InputStream input = NativeVertex.class.getResourceAsStream(resource)) {
            if (input == null) {
                return false;
            }
            Path directory = Files.createTempDirectory("dawnduskterminal-native");
            Path file = directory.resolve(library);
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            System.load(file.toAbsolutePath().toString());
            DawnDuskTerminal.LOGGER.info("DawnDuskTerminal native vertex kernel loaded from {}", resource);
            return true;
        } catch (Throwable throwable) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal native vertex kernel unavailable, using Java renderer: {}",
                throwable.toString());
            return false;
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
     * @return 顶点总数；失败返回 -1，调用方应退回 Java 变换
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
