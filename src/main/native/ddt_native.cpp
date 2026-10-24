#include <jni.h>

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

namespace ddt {
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

class Pool {
public:
    explicit Pool(int threadCount) {
        int count = threadCount < 1 ? 1 : threadCount;
        workers.reserve(static_cast<std::size_t>(count));
        for (int i = 0; i < count; ++i) {
            workers.emplace_back([this] { worker(); });
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

    void run(const Mesh* mesh, const float* matrices, const float* normals,
             const jint* pieceIds, int count, float* out) {
        std::unique_lock<std::mutex> lock(mutex);
        jobMesh = mesh;
        jobMatrices = matrices;
        jobNormals = normals;
        jobPieceIds = pieceIds;
        jobCount = count;
        jobOut = out;
        next.store(0, std::memory_order_relaxed);
        active = static_cast<int>(workers.size());
        ++generation;
        startCv.notify_all();
        doneCv.wait(lock, [this] { return active == 0; });
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

            for (int index = next.fetch_add(1, std::memory_order_relaxed);
                 index < count;
                 index = next.fetch_add(1, std::memory_order_relaxed)) {
                transformPiece(mesh, matrices, normals, pieceIds[index], index, out);
            }

            lock.lock();
            if (--active == 0) {
                doneCv.notify_all();
            }
        }
    }

    static void transformPiece(const Mesh* mesh, const float* matrices, const float* normals,
                               int pieceId, int batchIndex, float* out) {
        if (pieceId < 0 || pieceId >= static_cast<int>(mesh->pieces.size())) {
            return;
        }
        const Piece& piece = mesh->pieces[static_cast<std::size_t>(pieceId)];
        const float* matrix = matrices + batchIndex * 16;
        const float* normalMatrix = normals + batchIndex * 9;

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

            float* target = out + static_cast<std::size_t>(piece.offset + i) * 8;
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
    int active = 0;
    bool stop = false;
    std::uint64_t generation = 0;
};
} // namespace ddt

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeCreatePool(JNIEnv*, jclass, jint threadCount) {
    return reinterpret_cast<jlong>(new ddt::Pool(threadCount));
}

JNIEXPORT void JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyPool(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<ddt::Pool*>(handle);
}

JNIEXPORT jlong JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeCreateMesh(
    JNIEnv* env, jclass, jfloatArray vertexData, jintArray offsets, jintArray counts, jintArray flags) {
    const jsize vertexFloatCount = env->GetArrayLength(vertexData);
    const jsize pieceCount = env->GetArrayLength(offsets);
    if (vertexFloatCount % 8 != 0 || env->GetArrayLength(counts) != pieceCount
        || env->GetArrayLength(flags) != pieceCount) {
        return 0;
    }

    const int vertexCount = static_cast<int>(vertexFloatCount / 8);
    auto* mesh = new ddt::Mesh();
    mesh->vertices.resize(static_cast<std::size_t>(vertexCount));
    mesh->pieces.resize(static_cast<std::size_t>(pieceCount));

    float* vertexSource = env->GetFloatArrayElements(vertexData, nullptr);
    jint* offsetSource = env->GetIntArrayElements(offsets, nullptr);
    jint* countSource = env->GetIntArrayElements(counts, nullptr);
    jint* flagSource = env->GetIntArrayElements(flags, nullptr);

    std::memcpy(mesh->vertices.data(), vertexSource,
                static_cast<std::size_t>(vertexFloatCount) * sizeof(float));

    bool valid = true;
    for (jsize i = 0; i < pieceCount; ++i) {
        const int offset = static_cast<int>(offsetSource[i]);
        const int count = static_cast<int>(countSource[i]);
        if (offset < 0 || count < 0 || offset > vertexCount - count) {
            valid = false;
            break;
        }
        ddt::Piece& piece = mesh->pieces[static_cast<std::size_t>(i)];
        piece.offset = offset;
        piece.count = count;
        piece.flags = static_cast<int>(flagSource[i]);
    }

    env->ReleaseFloatArrayElements(vertexData, vertexSource, JNI_ABORT);
    env->ReleaseIntArrayElements(offsets, offsetSource, JNI_ABORT);
    env->ReleaseIntArrayElements(counts, countSource, JNI_ABORT);
    env->ReleaseIntArrayElements(flags, flagSource, JNI_ABORT);

    if (!valid) {
        delete mesh;
        return 0;
    }

    return reinterpret_cast<jlong>(mesh);
}

JNIEXPORT void JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyMesh(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<ddt::Mesh*>(handle);
}

JNIEXPORT jint JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeTransform(
    JNIEnv* env, jclass, jlong poolHandle, jlong meshHandle,
    jfloatArray matrices, jfloatArray normals, jintArray pieceIds, jint count,
    jobject output, jint outputCapacityFloats) {
    auto* pool = reinterpret_cast<ddt::Pool*>(poolHandle);
    auto* mesh = reinterpret_cast<ddt::Mesh*>(meshHandle);
    if (pool == nullptr || mesh == nullptr || count < 0) {
        return -1;
    }

    if (env->GetArrayLength(pieceIds) < count
        || env->GetArrayLength(matrices) < count * 16
        || env->GetArrayLength(normals) < count * 9) {
        return -1;
    }

    const jlong requiredFloats = static_cast<jlong>(mesh->vertices.size()) * 8;
    if (requiredFloats > static_cast<jlong>(outputCapacityFloats)) {
        return -1;
    }

    auto* out = static_cast<float*>(env->GetDirectBufferAddress(output));
    if (out == nullptr || env->GetDirectBufferCapacity(output) < requiredFloats * static_cast<jlong>(sizeof(float))) {
        return -1;
    }

    float* matrixSource = env->GetFloatArrayElements(matrices, nullptr);
    float* normalSource = env->GetFloatArrayElements(normals, nullptr);
    jint* idSource = env->GetIntArrayElements(pieceIds, nullptr);

    pool->run(mesh, matrixSource, normalSource, idSource, count, out);

    env->ReleaseFloatArrayElements(matrices, matrixSource, JNI_ABORT);
    env->ReleaseFloatArrayElements(normals, normalSource, JNI_ABORT);
    env->ReleaseIntArrayElements(pieceIds, idSource, JNI_ABORT);

    return static_cast<jint>(mesh->vertices.size());
}

}
