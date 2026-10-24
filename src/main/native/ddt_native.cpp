#include <jni.h>

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <new>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

// Java 侧 NativeMesh.ABI_VERSION 必须与这里一致
// 构建脚本用 -DDDT_NATIVE_ABI_VERSION=<gradle.properties> 覆盖 默认值只用于手工编译
#ifndef DDT_NATIVE_ABI_VERSION
#define DDT_NATIVE_ABI_VERSION 1
#endif

static_assert(DDT_NATIVE_ABI_VERSION >= 1, "DDT_NATIVE_ABI_VERSION must be positive");

namespace ddt {

// 顶点布局 x y z nx ny nz u v
constexpr jint kAbiVersion = DDT_NATIVE_ABI_VERSION;
constexpr jint kVertexFloats = 8;
constexpr jint kMatrixFloats = 16;
constexpr jint kNormalFloats = 9;
constexpr int kMaxThreads = 64;

// 与 Java 侧 NativeMesh 的 ERROR_* 一一对应 返回值 >= 0 才是顶点数
enum ErrorCode : jint {
    kOk = 0,
    kInvalidArgument = -1,
    kInvalidHandle = -2,
    kOutputTooSmall = -3,
    kInternalError = -4,
};

struct Vertex {
    float x;
    float y;
    float z;
    float nx;
    float ny;
    float nz;
    float u;
    float v;
};

/**
 * 一个刚性变换单元 每个单元共用同一个矩阵与法线矩阵
 *
 * <p>flags 低三位表示法线在某个轴为负时是否取反 原先只服务立方体的
 * sizeX/sizeY/sizeZ 判定改由调用方算好 内核不再理解几何形状
 */
struct Piece {
    int offset;
    int count;
    int flags;
};

struct Mesh {
    std::vector<Vertex> vertices;
    std::vector<Piece> pieces;
};

constexpr int kFlipX = 1;
constexpr int kFlipY = 2;
constexpr int kFlipZ = 4;

/**
 * 句柄表
 *
 * <p>Java 拿到的是自增 id 而不是裸指针 失效 重复释放 或拿错类型的句柄只会返回
 * kInvalidHandle 不会解引用野指针 shared_ptr 保证正在执行的调用不会被并发的销毁拖走
 */
template <typename T>
class HandleTable {
public:
    jlong add(std::shared_ptr<T> value) {
        std::lock_guard<std::mutex> lock(mutex);
        const jlong handle = next++;
        entries.emplace(handle, std::move(value));
        return handle;
    }

    std::shared_ptr<T> find(jlong handle) const {
        if (handle <= 0) {
            return nullptr;
        }
        std::lock_guard<std::mutex> lock(mutex);
        const auto entry = entries.find(handle);
        return entry == entries.end() ? nullptr : entry->second;
    }

    void remove(jlong handle) {
        if (handle <= 0) {
            return;
        }
        std::lock_guard<std::mutex> lock(mutex);
        entries.erase(handle);
    }

private:
    mutable std::mutex mutex;
    std::unordered_map<jlong, std::shared_ptr<T>> entries;
    jlong next = 1;
};

HandleTable<Mesh>& meshHandles() {
    static HandleTable<Mesh> table;
    return table;
}

class Pool {
public:
    explicit Pool(int threadCount) {
        const int count = threadCount < 1 ? 1 : (threadCount > kMaxThreads ? kMaxThreads : threadCount);
        workers.reserve(static_cast<std::size_t>(count));
        try {
            for (int i = 0; i < count; ++i) {
                workers.emplace_back([this] { worker(); });
            }
        } catch (...) {
            // 构造失败时 vector 里已经有 joinable 线程 直接展开会 terminate 先收干净再抛
            {
                std::lock_guard<std::mutex> lock(mutex);
                stop = true;
            }
            startCv.notify_all();
            for (std::thread& workerThread : workers) {
                if (workerThread.joinable()) {
                    workerThread.join();
                }
            }
            throw;
        }
    }

    ~Pool() {
        {
            std::lock_guard<std::mutex> lock(mutex);
            stop = true;
        }
        startCv.notify_all();
        for (std::thread& workerThread : workers) {
            if (workerThread.joinable()) {
                workerThread.join();
            }
        }
    }

    jint run(const Mesh* mesh, const float* matrices, const float* normals,
             const jint* pieceIds, int count, float* out) {
        if (mesh == nullptr || out == nullptr || count < 0
            || (count > 0 && (matrices == nullptr || normals == nullptr || pieceIds == nullptr))) {
            return kInvalidArgument;
        }

        // 渲染线程之外也允许调用 这里串行化 避免两个 job 互相覆盖共享字段
        std::lock_guard<std::mutex> serialized(runMutex);

        std::unique_lock<std::mutex> lock(mutex);
        jobMesh = mesh;
        jobMatrices = matrices;
        jobNormals = normals;
        jobPieceIds = pieceIds;
        jobCount = count;
        jobOut = out;
        next.store(0, std::memory_order_relaxed);
        invalid.store(false, std::memory_order_relaxed);
        active = static_cast<int>(workers.size());
        ++generation;
        startCv.notify_all();
        doneCv.wait(lock, [this] { return active == 0; });

        return invalid.load(std::memory_order_relaxed) ? kInvalidArgument : kOk;
    }

private:
    void worker() {
        std::uint64_t seenGeneration = 0;
        for (;;) {
            std::unique_lock<std::mutex> lock(mutex);
            startCv.wait(lock, [this, seenGeneration] {
                return stop || generation != seenGeneration;
            });
            if (stop) {
                return;
            }
            seenGeneration = generation;

            const Mesh* mesh = jobMesh;
            const float* matrices = jobMatrices;
            const float* normals = jobNormals;
            const jint* pieceIds = jobPieceIds;
            const int count = jobCount;
            float* out = jobOut;
            lock.unlock();

            // C++ 异常不允许穿过 JNI 边界 任何线程内失败都只记标志 由 run 返回错误码
            try {
                for (int index = next.fetch_add(1, std::memory_order_relaxed);
                     index < count;
                     index = next.fetch_add(1, std::memory_order_relaxed)) {
                    transformPiece(mesh, matrices, normals, pieceIds[index], index, out);
                }
            } catch (...) {
                invalid.store(true, std::memory_order_relaxed);
            }

            lock.lock();
            if (--active == 0) {
                doneCv.notify_all();
            }
        }
    }

    void transformPiece(const Mesh* mesh, const float* matrices, const float* normals,
                        int pieceId, int batchIndex, float* out) {
        if (pieceId < 0 || static_cast<std::size_t>(pieceId) >= mesh->pieces.size()) {
            invalid.store(true, std::memory_order_relaxed);
            return;
        }
        const Piece& piece = mesh->pieces[static_cast<std::size_t>(pieceId)];
        const float* matrix = matrices + static_cast<std::size_t>(batchIndex) * kMatrixFloats;
        const float* normalMatrix = normals + static_cast<std::size_t>(batchIndex) * kNormalFloats;

        for (int i = 0; i < piece.count; ++i) {
            const Vertex& vertex = mesh->vertices[static_cast<std::size_t>(piece.offset + i)];

            const float x = vertex.x;
            const float y = vertex.y;
            const float z = vertex.z;

            const float tx = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
            const float ty = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
            const float tz = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];

            float nx = normalMatrix[0] * vertex.nx + normalMatrix[3] * vertex.ny + normalMatrix[6] * vertex.nz;
            float ny = normalMatrix[1] * vertex.nx + normalMatrix[4] * vertex.ny + normalMatrix[7] * vertex.nz;
            float nz = normalMatrix[2] * vertex.nx + normalMatrix[5] * vertex.ny + normalMatrix[8] * vertex.nz;

            if (nx < 0.0f && (piece.flags & kFlipX) != 0) {
                nx = -nx;
            }
            if (ny < 0.0f && (piece.flags & kFlipY) != 0) {
                ny = -ny;
            }
            if (nz < 0.0f && (piece.flags & kFlipZ) != 0) {
                nz = -nz;
            }

            float* target = out + static_cast<std::size_t>(piece.offset + i) * kVertexFloats;
            target[0] = tx;
            target[1] = ty;
            target[2] = tz;
            target[3] = nx;
            target[4] = ny;
            target[5] = nz;
            target[6] = vertex.u;
            target[7] = vertex.v;
        }
    }

    std::vector<std::thread> workers;
    std::mutex runMutex;
    std::mutex mutex;
    std::condition_variable startCv;
    std::condition_variable doneCv;
    const Mesh* jobMesh = nullptr;
    const float* jobMatrices = nullptr;
    const float* jobNormals = nullptr;
    const jint* jobPieceIds = nullptr;
    float* jobOut = nullptr;
    int jobCount = 0;
    std::atomic<int> next{0};
    std::atomic<bool> invalid{false};
    int active = 0;
    bool stop = false;
    std::uint64_t generation = 0;
};

HandleTable<Pool>& poolHandles() {
    static HandleTable<Pool> table;
    return table;
}

/**
 * JNI 数组作用域 保证每一条提前返回的路径都会 Release
 */
class FloatArrayScope {
public:
    FloatArrayScope(JNIEnv* env, jfloatArray array) : env(env), array(array) {
        if (array != nullptr) {
            data = env->GetFloatArrayElements(array, nullptr);
        }
    }

    ~FloatArrayScope() {
        if (data != nullptr) {
            env->ReleaseFloatArrayElements(array, data, JNI_ABORT);
        }
    }

    FloatArrayScope(const FloatArrayScope&) = delete;
    FloatArrayScope& operator=(const FloatArrayScope&) = delete;

    jfloat* get() const {
        return data;
    }

private:
    JNIEnv* env;
    jfloatArray array;
    jfloat* data = nullptr;
};

class IntArrayScope {
public:
    IntArrayScope(JNIEnv* env, jintArray array) : env(env), array(array) {
        if (array != nullptr) {
            data = env->GetIntArrayElements(array, nullptr);
        }
    }

    ~IntArrayScope() {
        if (data != nullptr) {
            env->ReleaseIntArrayElements(array, data, JNI_ABORT);
        }
    }

    IntArrayScope(const IntArrayScope&) = delete;
    IntArrayScope& operator=(const IntArrayScope&) = delete;

    jint* get() const {
        return data;
    }

private:
    JNIEnv* env;
    jintArray array;
    jint* data = nullptr;
};

} // namespace ddt

extern "C" {

/**
 * ABI 协议
 *
 * <p>Java 在加载后先读这几个常量 与自己的编译期常量逐项比对
 * 版本或布局任何一个对不上就禁用 native 而不是冒险调用
 * 只要 JNI 函数签名 参数含义 顶点布局 片元布局 或返回值语义变了 就要 +1 kAbiVersion
 */
JNIEXPORT jint JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeAbiVersion(JNIEnv*, jclass) {
    return ddt::kAbiVersion;
}

JNIEXPORT jint JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeVertexFloats(JNIEnv*, jclass) {
    return ddt::kVertexFloats;
}

JNIEXPORT jint JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeMatrixFloats(JNIEnv*, jclass) {
    return ddt::kMatrixFloats;
}

JNIEXPORT jint JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeNormalFloats(JNIEnv*, jclass) {
    return ddt::kNormalFloats;
}

JNIEXPORT jlong JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeCreatePool(JNIEnv*, jclass, jint threadCount) {
    try {
        return ddt::poolHandles().add(std::make_shared<ddt::Pool>(threadCount));
    } catch (...) {
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyPool(JNIEnv*, jclass, jlong handle) {
    try {
        ddt::poolHandles().remove(handle);
    } catch (...) {
        // 销毁失败没有安全的补救方式 也不允许异常穿过 JNI 边界
    }
}

JNIEXPORT jlong JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeCreateMesh(
    JNIEnv* env, jclass, jfloatArray vertexData, jintArray offsets, jintArray counts, jintArray flags) {
    try {
        if (vertexData == nullptr || offsets == nullptr || counts == nullptr || flags == nullptr) {
            return 0;
        }

        const jsize vertexFloatCount = env->GetArrayLength(vertexData);
        const jsize pieceCount = env->GetArrayLength(offsets);
        if (vertexFloatCount < 0 || vertexFloatCount % ddt::kVertexFloats != 0 || pieceCount < 0
            || env->GetArrayLength(counts) != pieceCount
            || env->GetArrayLength(flags) != pieceCount) {
            return 0;
        }

        auto mesh = std::make_shared<ddt::Mesh>();
        mesh->vertices.resize(static_cast<std::size_t>(vertexFloatCount / ddt::kVertexFloats));
        mesh->pieces.resize(static_cast<std::size_t>(pieceCount));

        ddt::FloatArrayScope vertexSource(env, vertexData);
        ddt::IntArrayScope offsetSource(env, offsets);
        ddt::IntArrayScope countSource(env, counts);
        ddt::IntArrayScope flagSource(env, flags);
        if (vertexSource.get() == nullptr || offsetSource.get() == nullptr
            || countSource.get() == nullptr || flagSource.get() == nullptr) {
            return 0;
        }

        if (vertexFloatCount > 0) {
            std::memcpy(mesh->vertices.data(), vertexSource.get(),
                        static_cast<std::size_t>(vertexFloatCount) * sizeof(float));
        }

        const int vertexCount = static_cast<int>(mesh->vertices.size());
        for (jsize i = 0; i < pieceCount; ++i) {
            const int offset = static_cast<int>(offsetSource.get()[i]);
            const int count = static_cast<int>(countSource.get()[i]);
            if (offset < 0 || count < 0 || offset > vertexCount || count > vertexCount - offset) {
                return 0;
            }
            ddt::Piece& piece = mesh->pieces[static_cast<std::size_t>(i)];
            piece.offset = offset;
            piece.count = count;
            piece.flags = static_cast<int>(flagSource.get()[i]) & (ddt::kFlipX | ddt::kFlipY | ddt::kFlipZ);
        }

        return ddt::meshHandles().add(std::move(mesh));
    } catch (...) {
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyMesh(JNIEnv*, jclass, jlong handle) {
    try {
        ddt::meshHandles().remove(handle);
    } catch (...) {
        // 同 destroyPool
    }
}

JNIEXPORT jint JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeTransform(
    JNIEnv* env, jclass, jlong poolHandle, jlong meshHandle,
    jfloatArray matrices, jfloatArray normals, jintArray pieceIds, jint count,
    jobject output, jint outputCapacityFloats) {
    try {
        if (count < 0) {
            return ddt::kInvalidArgument;
        }
        if (matrices == nullptr || normals == nullptr || pieceIds == nullptr || output == nullptr) {
            return ddt::kInvalidArgument;
        }

        const std::shared_ptr<ddt::Pool> pool = ddt::poolHandles().find(poolHandle);
        const std::shared_ptr<ddt::Mesh> mesh = ddt::meshHandles().find(meshHandle);
        if (pool == nullptr || mesh == nullptr) {
            return ddt::kInvalidHandle;
        }

        const jlong requiredMatrices = static_cast<jlong>(count) * ddt::kMatrixFloats;
        const jlong requiredNormals = static_cast<jlong>(count) * ddt::kNormalFloats;
        if (static_cast<jlong>(env->GetArrayLength(pieceIds)) < static_cast<jlong>(count)
            || static_cast<jlong>(env->GetArrayLength(matrices)) < requiredMatrices
            || static_cast<jlong>(env->GetArrayLength(normals)) < requiredNormals) {
            return ddt::kInvalidArgument;
        }

        const jlong requiredFloats = static_cast<jlong>(mesh->vertices.size()) * ddt::kVertexFloats;
        if (static_cast<jlong>(outputCapacityFloats) < requiredFloats) {
            return ddt::kOutputTooSmall;
        }

        const jlong capacityBytes = env->GetDirectBufferCapacity(output);
        if (capacityBytes < 0
            || capacityBytes < requiredFloats * static_cast<jlong>(sizeof(float))) {
            return ddt::kOutputTooSmall;
        }

        auto* out = static_cast<float*>(env->GetDirectBufferAddress(output));
        if (out == nullptr) {
            return ddt::kInvalidArgument;
        }

        ddt::FloatArrayScope matrixSource(env, matrices);
        ddt::FloatArrayScope normalSource(env, normals);
        ddt::IntArrayScope idSource(env, pieceIds);
        if (matrixSource.get() == nullptr || normalSource.get() == nullptr || idSource.get() == nullptr) {
            return ddt::kInvalidArgument;
        }

        const jint result = pool->run(mesh.get(), matrixSource.get(), normalSource.get(),
                                      idSource.get(), count, out);
        if (result < 0) {
            return result;
        }
        return static_cast<jint>(mesh->vertices.size());
    } catch (...) {
        return ddt::kInternalError;
    }
}

}
