// 变换内核的数值校验与吞吐对照台 不属于构建产物
//
// 直接把 ddt_native.cpp 整份 include 进来 所以量到的就是 jar 里那份代码
// 输入与容差也复用库里的 fillKernelProbe 与 withinKernelTolerance 不另抄一份
//
// 输出四件事
// 1 当前 CPU 上实际会选中的内核
// 2 每个 SIMD 内核相对标量基线的最大绝对误差
// 3 大块顶点与线上形状（712 个 24 顶点 piece）的纯吞吐
// 4 线程数扫描 1 2 4 8 16
//
// 编译方法见 docs/development.md 的「变换内核与 SIMD 分发」
// 这个程序只在自己的进程里跑 不碰 JNI 加载 也不影响游戏

#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <thread>
#include <vector>

#include "../../src/main/native/ddt_native.cpp"

namespace {

constexpr int kTowerGhastPieces = 712;
constexpr int kPieceVertices = 24;
constexpr int kTowerGhastVertices = kTowerGhastPieces * kPieceVertices;
constexpr int kBlockVertices = 65536;
constexpr int kBlockRounds = 400;
constexpr int kFrames = 3000;
// 每组跑三次取最短 机器上有后台负载时单次结果会抖 10% 以上
constexpr int kTrials = 3;

struct Case {
    std::vector<ddt::Vertex> vertices;
    float matrix[ddt::kMatrixFloats];
    float normal[ddt::kNormalFloats];
    int flags;
};

Case makeCase(int count, int flags) {
    Case data;
    data.vertices.resize(static_cast<std::size_t>(count));
    ddt::fillKernelProbe(data.vertices.data(), count, data.matrix, data.normal);
    data.flags = flags;
    return data;
}

struct Entry {
    const char* name;
    ddt::PieceKernel kernel;
};

double seconds(std::chrono::steady_clock::time_point start,
               std::chrono::steady_clock::time_point end) {
    return std::chrono::duration<double, std::nano>(end - start).count() / 1.0e9;
}

float worstDifference(Case& data, ddt::PieceKernel candidate) {
    const int count = static_cast<int>(data.vertices.size());
    std::vector<float> reference(static_cast<std::size_t>(count) * ddt::kVertexFloats);
    std::vector<float> actual(reference.size());
    ddt::transformScalar(data.vertices.data(), count,
                         data.matrix, data.normal, data.flags, reference.data());
    candidate(data.vertices.data(), count,
              data.matrix, data.normal, data.flags, actual.data());
    float maximum = 0.0f;
    for (std::size_t i = 0; i < reference.size(); ++i) {
        const float difference = reference[i] > actual[i] ? reference[i] - actual[i]
                                                          : actual[i] - reference[i];
        if (difference > maximum) {
            maximum = difference;
        }
        if (!ddt::withinKernelTolerance(reference[i], actual[i])) {
            return -1.0f;
        }
    }
    return maximum;
}

// 显式禁止自动向量化 只用于对照“编译器自动”和“手写 AVX2”
// 库里的 transformScalar 是同一份循环 但让 -O3 自己决定向量化
void transformScalarNoVector(const ddt::Vertex* vertices, int count,
                             const float* matrix, const float* normalMatrix,
                             int flags, float* out) {
    const bool flipX = (flags & ddt::kFlipX) != 0;
    const bool flipY = (flags & ddt::kFlipY) != 0;
    const bool flipZ = (flags & ddt::kFlipZ) != 0;
#if defined(__clang__)
#pragma clang loop vectorize(disable) interleave(disable)
#endif
    for (int i = 0; i < count; ++i) {
        const ddt::Vertex& vertex = vertices[i];
        const float x = vertex.x;
        const float y = vertex.y;
        const float z = vertex.z;
        const float tx = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
        const float ty = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
        const float tz = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
        float nx = normalMatrix[0] * vertex.nx + normalMatrix[3] * vertex.ny + normalMatrix[6] * vertex.nz;
        float ny = normalMatrix[1] * vertex.nx + normalMatrix[4] * vertex.ny + normalMatrix[7] * vertex.nz;
        float nz = normalMatrix[2] * vertex.nx + normalMatrix[5] * vertex.ny + normalMatrix[8] * vertex.nz;
        if (flipX && nx < 0.0f) {
            nx = -nx;
        }
        if (flipY && ny < 0.0f) {
            ny = -ny;
        }
        if (flipZ && nz < 0.0f) {
            nz = -nz;
        }
        float* target = out + static_cast<std::size_t>(i) * ddt::kVertexFloats;
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

std::vector<float> makeTowerMatrices(const Case& data) {
    std::vector<float> matrices(static_cast<std::size_t>(kTowerGhastPieces) * ddt::kMatrixFloats);
    for (int piece = 0; piece < kTowerGhastPieces; ++piece) {
        float* target = matrices.data() + static_cast<std::size_t>(piece) * ddt::kMatrixFloats;
        std::memcpy(target, data.matrix, sizeof(data.matrix));
        // 每个 piece 一个不同的平移 避免矩阵永远命中同一行缓存
        target[12] += static_cast<float>(piece % 17) * 0.125f;
        target[13] -= static_cast<float>(piece % 11) * 0.25f;
        target[14] += static_cast<float>(piece % 7) * 0.5f;
    }
    return matrices;
}

std::vector<float> makeTowerNormals(const Case& data) {
    std::vector<float> normals(static_cast<std::size_t>(kTowerGhastPieces) * ddt::kNormalFloats);
    for (int piece = 0; piece < kTowerGhastPieces; ++piece) {
        std::memcpy(normals.data() + static_cast<std::size_t>(piece) * ddt::kNormalFloats,
                    data.normal, sizeof(data.normal));
    }
    return normals;
}

} // namespace

int main(int argc, char** argv) {
    bool affinity = false;
    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "--affinity") == 0) {
            affinity = true;
        }
    }
#if defined(_WIN32)
    _putenv_s("DAWNDUSKTERMINAL_NATIVE_AFFINITY", affinity ? "1" : "0");
#else
    setenv("DAWNDUSKTERMINAL_NATIVE_AFFINITY", affinity ? "1" : "0", 1);
#endif

    std::printf("selected kernel: %s\n", ddt::kernelSelection().name);
    std::printf("threads available: %u\n", std::thread::hardware_concurrency());
    std::printf("affinity: %s\n", affinity ? "on (best effort)" : "off");

    std::vector<Entry> entries;
    entries.push_back({"scalar (auto)", ddt::transformScalar});
    entries.push_back({"scalar (novec)", transformScalarNoVector});
#if defined(DDT_ARCH_X86_64)
    const ddt::CpuFeatures features = ddt::cpuFeatures();
    if (features.sse2) {
        entries.push_back({"sse2", ddt::transformSse2});
    }
    if (features.avx2 && features.fma) {
        entries.push_back({"avx2+fma", ddt::transformAvx2});
    }
#elif defined(DDT_ARCH_AARCH64)
    entries.push_back({"neon", ddt::transformNeon});
#endif

    Case check = makeCase(65536, ddt::kFlipX | ddt::kFlipY | ddt::kFlipZ);
    std::printf("\ncorrectness vs scalar (65536 vertices, all flip axes):\n");
    for (const Entry& entry : entries) {
        const float worst = worstDifference(check, entry.kernel);
        std::printf("  %-14s %s  max abs diff %.3e\n",
                    entry.name, worst >= 0.0f ? "ok  " : "FAIL",
                    worst >= 0.0f ? worst : 0.0f);
    }

    Case flat = makeCase(65536, 0);
    std::printf("\ncorrectness vs scalar (65536 vertices, no flip):\n");
    for (const Entry& entry : entries) {
        const float worst = worstDifference(flat, entry.kernel);
        std::printf("  %-14s %s  max abs diff %.3e\n",
                    entry.name, worst >= 0.0f ? "ok  " : "FAIL",
                    worst >= 0.0f ? worst : 0.0f);
    }

    const Case block = makeCase(kBlockVertices, ddt::kFlipX | ddt::kFlipY | ddt::kFlipZ);
    std::printf("\nblock throughput (%d vertices x %d rounds best of %d):\n",
                kBlockVertices, kBlockRounds, kTrials);
    std::vector<float> blockOutput(static_cast<std::size_t>(kBlockVertices) * ddt::kVertexFloats);
    double scalarSeconds = 0.0;
    for (const Entry& entry : entries) {
        double elapsed = std::numeric_limits<double>::max();
        for (int trial = 0; trial < kTrials; ++trial) {
            const auto start = std::chrono::steady_clock::now();
            for (int round = 0; round < kBlockRounds; ++round) {
                entry.kernel(block.vertices.data(), kBlockVertices,
                             block.matrix, block.normal, block.flags, blockOutput.data());
            }
            const auto end = std::chrono::steady_clock::now();
            elapsed = std::min(elapsed, seconds(start, end));
        }
        if (entry.kernel == ddt::transformScalar) {
            scalarSeconds = elapsed;
        }
        const double vertices = static_cast<double>(kBlockVertices) * kBlockRounds;
        // 读 8 个 float 写 8 个 float
        const double bytes = vertices * ddt::kVertexFloats * sizeof(float) * 2.0;
        std::printf("  %-14s %7.2f ms  %7.1f Mvertices/s  %6.1f GB/s  %5.2fx\n",
                    entry.name, elapsed * 1.0e3, vertices / elapsed / 1.0e6,
                    bytes / elapsed / 1.0e9,
                    scalarSeconds > 0.0 ? scalarSeconds / elapsed : 0.0);
    }

    const Case tower = makeCase(kTowerGhastVertices, 0);
    const std::vector<float> towerMatrices = makeTowerMatrices(tower);
    const std::vector<float> towerNormals = makeTowerNormals(tower);
    std::vector<float> towerOutput(
        static_cast<std::size_t>(kTowerGhastVertices) * ddt::kVertexFloats);
    std::printf("\ntower ghast single thread (%d pieces x %d vertices x %d frames best of %d):\n",
                kTowerGhastPieces, kPieceVertices, kFrames, kTrials);
    double towerScalarSeconds = 0.0;
    for (const Entry& entry : entries) {
        double frame = std::numeric_limits<double>::max();
        for (int trial = 0; trial < kTrials; ++trial) {
            const auto start = std::chrono::steady_clock::now();
            for (int currentFrame = 0; currentFrame < kFrames; ++currentFrame) {
                for (int piece = 0; piece < kTowerGhastPieces; ++piece) {
                    const std::size_t offset = static_cast<std::size_t>(piece) * kPieceVertices;
                    entry.kernel(
                        tower.vertices.data() + offset, kPieceVertices,
                        towerMatrices.data() + static_cast<std::size_t>(piece) * ddt::kMatrixFloats,
                        towerNormals.data() + static_cast<std::size_t>(piece) * ddt::kNormalFloats,
                        tower.flags, towerOutput.data() + offset * ddt::kVertexFloats);
                }
            }
            const auto end = std::chrono::steady_clock::now();
            frame = std::min(frame, seconds(start, end) / kFrames);
        }
        if (entry.kernel == ddt::transformScalar) {
            towerScalarSeconds = frame;
        }
        std::printf("  %-14s %8.2f us/frame  %7.1f frames/s  %5.2fx\n",
                    entry.name, frame * 1.0e6, 1.0 / frame,
                    towerScalarSeconds > 0.0 ? towerScalarSeconds / frame : 0.0);
    }

    // 线程扫描走真实的 Pool 包含共享池的 Java 侧同一套 submit/wait 路径
    ddt::Mesh mesh;
    mesh.vertices = tower.vertices;
    mesh.pieces.resize(kTowerGhastPieces);
    for (int piece = 0; piece < kTowerGhastPieces; ++piece) {
        mesh.pieces[piece] = ddt::Piece{piece * kPieceVertices, kPieceVertices, 0};
    }
    std::vector<jint> pieceIds(kTowerGhastPieces);
    for (int piece = 0; piece < kTowerGhastPieces; ++piece) {
        pieceIds[piece] = piece;
    }
    const ddt::PieceKernel selected = ddt::kernelSelection().kernel;

    std::printf("\nthread scan (%s, %d pieces x %d frames best of %d):\n",
                ddt::kernelSelection().name, kTowerGhastPieces, kFrames, kTrials);
    const unsigned hardware = std::max(1u, std::thread::hardware_concurrency());
    const int threadCounts[] = {1, 2, 4, 8, 16};
    double singleThreadFrame = 0.0;
    for (const int threads : threadCounts) {
        if (threads > static_cast<int>(hardware) || threads > ddt::kMaxThreads) {
            continue;
        }
        ddt::Pool pool(threads);
        for (int frame = 0; frame < 200; ++frame) {
            pool.run(&mesh, towerMatrices.data(), towerNormals.data(),
                     pieceIds.data(), kTowerGhastPieces, towerOutput.data(), selected);
        }
        double frame = std::numeric_limits<double>::max();
        for (int trial = 0; trial < kTrials; ++trial) {
            const auto start = std::chrono::steady_clock::now();
            for (int currentFrame = 0; currentFrame < kFrames; ++currentFrame) {
                const jint result = pool.run(&mesh, towerMatrices.data(), towerNormals.data(),
                                             pieceIds.data(), kTowerGhastPieces,
                                             towerOutput.data(), selected);
                if (result != ddt::kOk) {
                    std::printf("  pool run failed: %d\n", result);
                    return 1;
                }
            }
            const auto end = std::chrono::steady_clock::now();
            frame = std::min(frame, seconds(start, end) / kFrames);
        }
        if (threads == 1) {
            singleThreadFrame = frame;
        }
        std::printf("  %2d thread(s)   %8.2f us/frame  %7.1f frames/s  %5.2fx\n",
                    threads, frame * 1.0e6, 1.0 / frame,
                    singleThreadFrame > 0.0 ? singleThreadFrame / frame : 1.0);
    }

    // 防止整个循环被优化掉
    float sink = 0.0f;
    for (const float value : towerOutput) {
        sink += value;
    }
    for (const float value : blockOutput) {
        sink += value;
    }
    std::printf("\nchecksum %.3f\n", sink);
    return 0;
}
