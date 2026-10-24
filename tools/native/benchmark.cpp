// 变换内核的数值校验与吞吐对照台 不属于构建产物
//
// 直接把 ddt_native.cpp 整份 include 进来 所以量到的就是 jar 里那份代码
// 输入与容差也复用库里的 fillKernelProbe 与 withinKernelTolerance 不另抄一份
// 编译方法见 docs/development.md 的「变换内核与 SIMD 分发」
//
// 输出四件事
// 1 当前 CPU 上实际会选中的内核
// 2 每个内核相对标量基线的最大绝对误差 超过共用容差判失败
// 3 单个大块顶点的纯吞吐
// 4 按线上形状（每帧约 712 个 24 顶点 piece 各调一次）计的每帧耗时
//
// 这个程序只在自己的进程里跑 不碰 JNI 加载 也不影响游戏

#include <chrono>
#include <cstdio>
#include <vector>

#include "../../src/main/native/ddt_native.cpp"

namespace {

constexpr int kVertexCount = 100000;
constexpr int kRounds = 200;

// NativeMeshBatch 一帧实际提交的 piece 数与每个 piece 的顶点数
constexpr int kPieceCount = 712;
constexpr int kPieceVertices = 24;
constexpr int kPieceRounds = 2000;

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

bool sameAsScalar(const Case& data, ddt::PieceKernel candidate, float* worst) {
    const int count = static_cast<int>(data.vertices.size());
    std::vector<float> reference(static_cast<std::size_t>(count) * ddt::kVertexFloats);
    std::vector<float> actual(reference.size());

    ddt::transformScalar(data.vertices.data(), count,
                         data.matrix, data.normal, data.flags, reference.data());
    candidate(data.vertices.data(), count,
              data.matrix, data.normal, data.flags, actual.data());

    float maximum = 0.0f;
    bool okay = true;
    for (std::size_t i = 0; i < reference.size(); ++i) {
        const float difference = reference[i] > actual[i] ? reference[i] - actual[i]
                                                          : actual[i] - reference[i];
        if (difference > maximum) {
            maximum = difference;
        }
        if (!ddt::withinKernelTolerance(reference[i], actual[i])) {
            okay = false;
        }
    }
    *worst = maximum;
    return okay;
}

double seconds(std::chrono::steady_clock::time_point start,
               std::chrono::steady_clock::time_point end) {
    return std::chrono::duration<double, std::nano>(end - start).count() / 1.0e9;
}

} // namespace

int main() {
    std::vector<Entry> entries;
    entries.push_back({"scalar", ddt::transformScalar});
    // 只把当前 CPU 真能执行的实现放进表 少了能力判断会在老机器上直接跑非法指令
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

    std::printf("selected kernel: %s\n", ddt::kernelSelection().name);

    const Case big = makeCase(kVertexCount, ddt::kFlipX | ddt::kFlipY | ddt::kFlipZ);

    std::printf("\ncorrectness vs scalar (%d vertices, all flip axes):\n", kVertexCount);
    for (const Entry& entry : entries) {
        float worst = 0.0f;
        const bool okay = sameAsScalar(big, entry.kernel, &worst);
        std::printf("  %-9s %s  max abs diff %.3e\n", entry.name, okay ? "ok  " : "FAIL", worst);
    }

    // 线上绝大多数 piece 的 flags 是 0 走的是不带掩码的分支 单独校验一遍
    const Case flat = makeCase(kVertexCount, 0);
    std::printf("\ncorrectness vs scalar (%d vertices, no flip):\n", kVertexCount);
    for (const Entry& entry : entries) {
        float worst = 0.0f;
        const bool okay = sameAsScalar(flat, entry.kernel, &worst);
        std::printf("  %-9s %s  max abs diff %.3e\n", entry.name, okay ? "ok  " : "FAIL", worst);
    }

    std::printf("\nblock throughput (%d vertices x %d rounds):\n", kVertexCount, kRounds);
    std::vector<float> output(static_cast<std::size_t>(kVertexCount) * ddt::kVertexFloats);
    double scalarSeconds = 0.0;
    for (const Entry& entry : entries) {
        const auto start = std::chrono::steady_clock::now();
        for (int round = 0; round < kRounds; ++round) {
            entry.kernel(big.vertices.data(), kVertexCount,
                         big.matrix, big.normal, big.flags, output.data());
        }
        const auto end = std::chrono::steady_clock::now();
        const double elapsed = seconds(start, end);
        if (entry.kernel == ddt::transformScalar) {
            scalarSeconds = elapsed;
        }
        const double vertices = static_cast<double>(kVertexCount) * kRounds;
        // 读 8 个 float 写 8 个 float
        const double bytes = vertices * ddt::kVertexFloats * sizeof(float) * 2.0;
        std::printf("  %-9s %7.2f ms  %7.1f Mvertices/s  %6.1f GB/s  %5.2fx\n",
                    entry.name, elapsed * 1.0e3, vertices / elapsed / 1.0e6,
                    bytes / elapsed / 1.0e9, scalarSeconds > 0.0 ? scalarSeconds / elapsed : 0.0);
    }

    // 线上形状 每个 piece 单独调一次 正常立方体的 flags 是 0
    const Case pieces = makeCase(kPieceCount * kPieceVertices, 0);
    std::printf("\npiece throughput (%d pieces x %d vertices x %d frames):\n",
                kPieceCount, kPieceVertices, kPieceRounds);
    std::vector<float> pieceOutput(static_cast<std::size_t>(pieces.vertices.size()) * ddt::kVertexFloats);
    double scalarFrameSeconds = 0.0;
    for (const Entry& entry : entries) {
        const auto start = std::chrono::steady_clock::now();
        for (int round = 0; round < kPieceRounds; ++round) {
            for (int piece = 0; piece < kPieceCount; ++piece) {
                const std::size_t offset = static_cast<std::size_t>(piece) * kPieceVertices;
                entry.kernel(pieces.vertices.data() + offset, kPieceVertices,
                             pieces.matrix, pieces.normal, pieces.flags,
                             pieceOutput.data() + offset * ddt::kVertexFloats);
            }
        }
        const auto end = std::chrono::steady_clock::now();
        const double elapsed = seconds(start, end);
        if (entry.kernel == ddt::transformScalar) {
            scalarFrameSeconds = elapsed;
        }
        const double frame = elapsed / kPieceRounds;
        std::printf("  %-9s %7.3f ms/frame  %7.1f frames/s  %5.2fx\n",
                    entry.name, frame * 1.0e3,
                    frame > 0.0 ? 1.0 / frame : 0.0,
                    scalarFrameSeconds > 0.0 ? scalarFrameSeconds / elapsed : 0.0);
    }

    // 防止整个循环被优化掉
    float sink = 0.0f;
    for (const float value : output) {
        sink += value;
    }
    for (const float value : pieceOutput) {
        sink += value;
    }
    std::printf("\nchecksum %.3f\n", sink);
    return 0;
}
