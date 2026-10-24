// C++20 native 网格变换内核
//
// 输入输出保持 AoS 每个顶点 8 个 float 连续放
// SIMD 内核先把 4 个或 8 个顶点转置成按通道排列的向量 算完再转回去
// SoA 原型在同一套 benchmark 上更慢 结论留在 docs/development.md

#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif

#include <jni.h>
#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <latch>
#include <memory>
#include <mutex>
#include <new>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#include <windows.h>
#elif defined(__linux__)
#include <pthread.h>
#include <sched.h>
#endif

// 变换内核的 SIMD 实现按架构编译 x86-64 的 SSE2 与 AVX2+FMA 走 target 属性
// aarch64 的 NEON 是基线 直接开
#if defined(__x86_64__) || defined(_M_X64)
#define DDT_ARCH_X86_64 1
#include <immintrin.h>
#elif defined(__aarch64__) || defined(_M_ARM64)
#define DDT_ARCH_AARCH64 1
#include <arm_neon.h>
#endif

// Java 侧 NativeMesh.ABI_VERSION 必须与这里一致
// 构建脚本用 -DDDT_NATIVE_ABI_VERSION=<gradle.properties> 覆盖 默认值只用于手工编译
#ifndef DDT_NATIVE_ABI_VERSION
#define DDT_NATIVE_ABI_VERSION 3
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

// 顶点变换内核
//
// 同一段变换有四种写法 标量基线 x86-64 的 SSE2 与 AVX2+FMA aarch64 的 NEON
// 顶点是 AoS 布局 内核先把若干个顶点转置成按通道排列的向量 算完再转回去
// 所以一次能同时算 4 个（SSE2 / NEON）或 8 个（AVX2）顶点
//
// 四种写法在同一组输入上必须给出相同结果 只允许 FMA 带来的 1ulp 级差异
// 启动时按 CPU 能力选一次 存成函数指针 之后每个 piece 都走选中的内核
using PieceKernel = void (*)(const Vertex* vertices, int count, const float* matrix,
                             const float* normalMatrix, int flags, float* out);

void transformScalar(const Vertex* vertices, int count, const float* matrix,
                     const float* normalMatrix, int flags, float* out) {
    const bool flipX = (flags & kFlipX) != 0;
    const bool flipY = (flags & kFlipY) != 0;
    const bool flipZ = (flags & kFlipZ) != 0;

    for (int i = 0; i < count; ++i) {
        const Vertex& vertex = vertices[i];

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

        float* target = out + static_cast<std::size_t>(i) * kVertexFloats;
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

#if defined(DDT_ARCH_X86_64)

// 4x4 转置 对合 输入是 4 行（每行 4 个 float）输出是 4 列
static inline void transpose4x4(const __m128& a0, const __m128& a1,
                                const __m128& a2, const __m128& a3,
                                __m128& c0, __m128& c1, __m128& c2, __m128& c3) {
    const __m128 t0 = _mm_unpacklo_ps(a0, a1);
    const __m128 t1 = _mm_unpackhi_ps(a0, a1);
    const __m128 t2 = _mm_unpacklo_ps(a2, a3);
    const __m128 t3 = _mm_unpackhi_ps(a2, a3);
    c0 = _mm_movelh_ps(t0, t2);
    c1 = _mm_movehl_ps(t2, t0);
    c2 = _mm_movelh_ps(t1, t3);
    c3 = _mm_movehl_ps(t3, t1);
}

template <bool kFlip>
__attribute__((target("sse2")))
static void transformSse2Impl(const Vertex* vertices, int count, const float* matrix,
                              const float* normalMatrix, int flags, float* out) {
    const __m128 m0 = _mm_set1_ps(matrix[0]);
    const __m128 m4 = _mm_set1_ps(matrix[4]);
    const __m128 m8 = _mm_set1_ps(matrix[8]);
    const __m128 m12 = _mm_set1_ps(matrix[12]);
    const __m128 m1 = _mm_set1_ps(matrix[1]);
    const __m128 m5 = _mm_set1_ps(matrix[5]);
    const __m128 m9 = _mm_set1_ps(matrix[9]);
    const __m128 m13 = _mm_set1_ps(matrix[13]);
    const __m128 m2 = _mm_set1_ps(matrix[2]);
    const __m128 m6 = _mm_set1_ps(matrix[6]);
    const __m128 m10 = _mm_set1_ps(matrix[10]);
    const __m128 m14 = _mm_set1_ps(matrix[14]);

    const __m128 n0 = _mm_set1_ps(normalMatrix[0]);
    const __m128 n1 = _mm_set1_ps(normalMatrix[1]);
    const __m128 n2 = _mm_set1_ps(normalMatrix[2]);
    const __m128 n3 = _mm_set1_ps(normalMatrix[3]);
    const __m128 n4 = _mm_set1_ps(normalMatrix[4]);
    const __m128 n5 = _mm_set1_ps(normalMatrix[5]);
    const __m128 n6 = _mm_set1_ps(normalMatrix[6]);
    const __m128 n7 = _mm_set1_ps(normalMatrix[7]);
    const __m128 n8 = _mm_set1_ps(normalMatrix[8]);

    const __m128 zero = _mm_setzero_ps();
    // 取反折进掩码 只翻负值的符号位 -0 与 NaN 的比较结果为假所以不会动
    const __m128 signBit = _mm_castsi128_ps(_mm_set1_epi32(static_cast<int>(0x80000000u)));
    const __m128 flipMaskX = kFlip && (flags & kFlipX) != 0 ? signBit : zero;
    const __m128 flipMaskY = kFlip && (flags & kFlipY) != 0 ? signBit : zero;
    const __m128 flipMaskZ = kFlip && (flags & kFlipZ) != 0 ? signBit : zero;

    const float* source = reinterpret_cast<const float*>(vertices);
    int i = 0;
    for (; i + 4 <= count; i += 4) {
        const float* p = source + static_cast<std::size_t>(i) * kVertexFloats;
        const __m128 r0 = _mm_loadu_ps(p + 0);
        const __m128 r1 = _mm_loadu_ps(p + 4);
        const __m128 r2 = _mm_loadu_ps(p + 8);
        const __m128 r3 = _mm_loadu_ps(p + 12);
        const __m128 r4 = _mm_loadu_ps(p + 16);
        const __m128 r5 = _mm_loadu_ps(p + 20);
        const __m128 r6 = _mm_loadu_ps(p + 24);
        const __m128 r7 = _mm_loadu_ps(p + 28);

        __m128 x, y, z, nx, ny, nz, u, v;
        transpose4x4(r0, r2, r4, r6, x, y, z, nx);
        transpose4x4(r1, r3, r5, r7, ny, nz, u, v);

        __m128 tx = _mm_mul_ps(m0, x);
        tx = _mm_add_ps(tx, _mm_mul_ps(m4, y));
        tx = _mm_add_ps(tx, _mm_mul_ps(m8, z));
        tx = _mm_add_ps(tx, m12);

        __m128 ty = _mm_mul_ps(m1, x);
        ty = _mm_add_ps(ty, _mm_mul_ps(m5, y));
        ty = _mm_add_ps(ty, _mm_mul_ps(m9, z));
        ty = _mm_add_ps(ty, m13);

        __m128 tz = _mm_mul_ps(m2, x);
        tz = _mm_add_ps(tz, _mm_mul_ps(m6, y));
        tz = _mm_add_ps(tz, _mm_mul_ps(m10, z));
        tz = _mm_add_ps(tz, m14);

        __m128 onx = _mm_mul_ps(n0, nx);
        onx = _mm_add_ps(onx, _mm_mul_ps(n3, ny));
        onx = _mm_add_ps(onx, _mm_mul_ps(n6, nz));

        __m128 ony = _mm_mul_ps(n1, nx);
        ony = _mm_add_ps(ony, _mm_mul_ps(n4, ny));
        ony = _mm_add_ps(ony, _mm_mul_ps(n7, nz));

        __m128 onz = _mm_mul_ps(n2, nx);
        onz = _mm_add_ps(onz, _mm_mul_ps(n5, ny));
        onz = _mm_add_ps(onz, _mm_mul_ps(n8, nz));

        if constexpr (kFlip) {
            onx = _mm_xor_ps(onx, _mm_and_ps(flipMaskX, _mm_cmplt_ps(onx, zero)));
            ony = _mm_xor_ps(ony, _mm_and_ps(flipMaskY, _mm_cmplt_ps(ony, zero)));
            onz = _mm_xor_ps(onz, _mm_and_ps(flipMaskZ, _mm_cmplt_ps(onz, zero)));
        }

        __m128 o0, o1, o2, o3, o4, o5, o6, o7;
        transpose4x4(tx, ty, tz, onx, o0, o1, o2, o3);
        transpose4x4(ony, onz, u, v, o4, o5, o6, o7);

        float* target = out + static_cast<std::size_t>(i) * kVertexFloats;
        _mm_storeu_ps(target + 0, o0);
        _mm_storeu_ps(target + 4, o4);
        _mm_storeu_ps(target + 8, o1);
        _mm_storeu_ps(target + 12, o5);
        _mm_storeu_ps(target + 16, o2);
        _mm_storeu_ps(target + 20, o6);
        _mm_storeu_ps(target + 24, o3);
        _mm_storeu_ps(target + 28, o7);
    }
    if (i < count) {
        transformScalar(vertices + i, count - i, matrix, normalMatrix, flags,
                        out + static_cast<std::size_t>(i) * kVertexFloats);
    }
}

static void transformSse2(const Vertex* vertices, int count, const float* matrix,
                          const float* normalMatrix, int flags, float* out) {
    if (flags != 0) {
        transformSse2Impl<true>(vertices, count, matrix, normalMatrix, flags, out);
    } else {
        transformSse2Impl<false>(vertices, count, matrix, normalMatrix, flags, out);
    }
}

// 8x8 转置 8 个 256 位寄存器 每个是一行（一个顶点的 8 个 float）
__attribute__((target("avx2")))
static inline void transpose8x8(__m256& r0, __m256& r1, __m256& r2, __m256& r3,
                                __m256& r4, __m256& r5, __m256& r6, __m256& r7) {
    const __m256 t0 = _mm256_unpacklo_ps(r0, r1);
    const __m256 t1 = _mm256_unpackhi_ps(r0, r1);
    const __m256 t2 = _mm256_unpacklo_ps(r2, r3);
    const __m256 t3 = _mm256_unpackhi_ps(r2, r3);
    const __m256 t4 = _mm256_unpacklo_ps(r4, r5);
    const __m256 t5 = _mm256_unpackhi_ps(r4, r5);
    const __m256 t6 = _mm256_unpacklo_ps(r6, r7);
    const __m256 t7 = _mm256_unpackhi_ps(r6, r7);

    const __m256 s0 = _mm256_shuffle_ps(t0, t2, _MM_SHUFFLE(1, 0, 1, 0));
    const __m256 s1 = _mm256_shuffle_ps(t0, t2, _MM_SHUFFLE(3, 2, 3, 2));
    const __m256 s2 = _mm256_shuffle_ps(t1, t3, _MM_SHUFFLE(1, 0, 1, 0));
    const __m256 s3 = _mm256_shuffle_ps(t1, t3, _MM_SHUFFLE(3, 2, 3, 2));
    const __m256 s4 = _mm256_shuffle_ps(t4, t6, _MM_SHUFFLE(1, 0, 1, 0));
    const __m256 s5 = _mm256_shuffle_ps(t4, t6, _MM_SHUFFLE(3, 2, 3, 2));
    const __m256 s6 = _mm256_shuffle_ps(t5, t7, _MM_SHUFFLE(1, 0, 1, 0));
    const __m256 s7 = _mm256_shuffle_ps(t5, t7, _MM_SHUFFLE(3, 2, 3, 2));

    r0 = _mm256_permute2f128_ps(s0, s4, 0x20);
    r1 = _mm256_permute2f128_ps(s1, s5, 0x20);
    r2 = _mm256_permute2f128_ps(s2, s6, 0x20);
    r3 = _mm256_permute2f128_ps(s3, s7, 0x20);
    r4 = _mm256_permute2f128_ps(s0, s4, 0x31);
    r5 = _mm256_permute2f128_ps(s1, s5, 0x31);
    r6 = _mm256_permute2f128_ps(s2, s6, 0x31);
    r7 = _mm256_permute2f128_ps(s3, s7, 0x31);
}

template <bool kFlip>
__attribute__((target("avx2,fma")))
static void transformAvx2Impl(const Vertex* vertices, int count, const float* matrix,
                              const float* normalMatrix, int flags, float* out) {
    const __m256 m0 = _mm256_set1_ps(matrix[0]);
    const __m256 m4 = _mm256_set1_ps(matrix[4]);
    const __m256 m8 = _mm256_set1_ps(matrix[8]);
    const __m256 m12 = _mm256_set1_ps(matrix[12]);
    const __m256 m1 = _mm256_set1_ps(matrix[1]);
    const __m256 m5 = _mm256_set1_ps(matrix[5]);
    const __m256 m9 = _mm256_set1_ps(matrix[9]);
    const __m256 m13 = _mm256_set1_ps(matrix[13]);
    const __m256 m2 = _mm256_set1_ps(matrix[2]);
    const __m256 m6 = _mm256_set1_ps(matrix[6]);
    const __m256 m10 = _mm256_set1_ps(matrix[10]);
    const __m256 m14 = _mm256_set1_ps(matrix[14]);

    const __m256 n0 = _mm256_set1_ps(normalMatrix[0]);
    const __m256 n1 = _mm256_set1_ps(normalMatrix[1]);
    const __m256 n2 = _mm256_set1_ps(normalMatrix[2]);
    const __m256 n3 = _mm256_set1_ps(normalMatrix[3]);
    const __m256 n4 = _mm256_set1_ps(normalMatrix[4]);
    const __m256 n5 = _mm256_set1_ps(normalMatrix[5]);
    const __m256 n6 = _mm256_set1_ps(normalMatrix[6]);
    const __m256 n7 = _mm256_set1_ps(normalMatrix[7]);
    const __m256 n8 = _mm256_set1_ps(normalMatrix[8]);

    const __m256 zero = _mm256_setzero_ps();
    // 取反折进掩码 只翻负值的符号位 -0 与 NaN 的比较结果为假所以不会动
    const __m256 signBit = _mm256_castsi256_ps(_mm256_set1_epi32(static_cast<int>(0x80000000u)));
    const __m256 flipMaskX = kFlip && (flags & kFlipX) != 0 ? signBit : zero;
    const __m256 flipMaskY = kFlip && (flags & kFlipY) != 0 ? signBit : zero;
    const __m256 flipMaskZ = kFlip && (flags & kFlipZ) != 0 ? signBit : zero;

    const float* source = reinterpret_cast<const float*>(vertices);
    int i = 0;
    for (; i + 8 <= count; i += 8) {
        const float* p = source + static_cast<std::size_t>(i) * kVertexFloats;
        __m256 r0 = _mm256_loadu_ps(p + 0);
        __m256 r1 = _mm256_loadu_ps(p + 8);
        __m256 r2 = _mm256_loadu_ps(p + 16);
        __m256 r3 = _mm256_loadu_ps(p + 24);
        __m256 r4 = _mm256_loadu_ps(p + 32);
        __m256 r5 = _mm256_loadu_ps(p + 40);
        __m256 r6 = _mm256_loadu_ps(p + 48);
        __m256 r7 = _mm256_loadu_ps(p + 56);
        transpose8x8(r0, r1, r2, r3, r4, r5, r6, r7);

        const __m256 x = r0;
        const __m256 y = r1;
        const __m256 z = r2;
        const __m256 u = r6;
        const __m256 v = r7;

        __m256 tx = _mm256_mul_ps(m0, x);
        tx = _mm256_fmadd_ps(m4, y, tx);
        tx = _mm256_fmadd_ps(m8, z, tx);
        tx = _mm256_add_ps(tx, m12);

        __m256 ty = _mm256_mul_ps(m1, x);
        ty = _mm256_fmadd_ps(m5, y, ty);
        ty = _mm256_fmadd_ps(m9, z, ty);
        ty = _mm256_add_ps(ty, m13);

        __m256 tz = _mm256_mul_ps(m2, x);
        tz = _mm256_fmadd_ps(m6, y, tz);
        tz = _mm256_fmadd_ps(m10, z, tz);
        tz = _mm256_add_ps(tz, m14);

        __m256 onx = _mm256_mul_ps(n0, r3);
        onx = _mm256_fmadd_ps(n3, r4, onx);
        onx = _mm256_fmadd_ps(n6, r5, onx);

        __m256 ony = _mm256_mul_ps(n1, r3);
        ony = _mm256_fmadd_ps(n4, r4, ony);
        ony = _mm256_fmadd_ps(n7, r5, ony);

        __m256 onz = _mm256_mul_ps(n2, r3);
        onz = _mm256_fmadd_ps(n5, r4, onz);
        onz = _mm256_fmadd_ps(n8, r5, onz);

        if constexpr (kFlip) {
            onx = _mm256_xor_ps(onx, _mm256_and_ps(flipMaskX, _mm256_cmp_ps(onx, zero, _CMP_LT_OQ)));
            ony = _mm256_xor_ps(ony, _mm256_and_ps(flipMaskY, _mm256_cmp_ps(ony, zero, _CMP_LT_OQ)));
            onz = _mm256_xor_ps(onz, _mm256_and_ps(flipMaskZ, _mm256_cmp_ps(onz, zero, _CMP_LT_OQ)));
        }

        r0 = tx;
        r1 = ty;
        r2 = tz;
        r3 = onx;
        r4 = ony;
        r5 = onz;
        r6 = u;
        r7 = v;
        transpose8x8(r0, r1, r2, r3, r4, r5, r6, r7);

        float* target = out + static_cast<std::size_t>(i) * kVertexFloats;
        _mm256_storeu_ps(target + 0, r0);
        _mm256_storeu_ps(target + 8, r1);
        _mm256_storeu_ps(target + 16, r2);
        _mm256_storeu_ps(target + 24, r3);
        _mm256_storeu_ps(target + 32, r4);
        _mm256_storeu_ps(target + 40, r5);
        _mm256_storeu_ps(target + 48, r6);
        _mm256_storeu_ps(target + 56, r7);
    }
    if (i < count) {
        transformScalar(vertices + i, count - i, matrix, normalMatrix, flags,
                        out + static_cast<std::size_t>(i) * kVertexFloats);
    }
    // 回到只含 SSE 的 JVM 代码前清掉 ymm 上半 避免 AVX-SSE 切换惩罚
    _mm256_zeroupper();
}

static void transformAvx2(const Vertex* vertices, int count, const float* matrix,
                          const float* normalMatrix, int flags, float* out) {
    if (flags != 0) {
        transformAvx2Impl<true>(vertices, count, matrix, normalMatrix, flags, out);
    } else {
        transformAvx2Impl<false>(vertices, count, matrix, normalMatrix, flags, out);
    }
}

struct CpuFeatures {
    bool sse2 = false;
    bool avx2 = false;
    bool fma = false;
};

static void readCpuid(unsigned leaf, unsigned subleaf, unsigned out[4]) {
#if defined(_MSC_VER)
    int registers[4];
    __cpuidex(registers, static_cast<int>(leaf), static_cast<int>(subleaf));
    for (int i = 0; i < 4; ++i) {
        out[i] = static_cast<unsigned>(registers[i]);
    }
#else
    __asm__ __volatile__("cpuid"
                         : "=a"(out[0]), "=b"(out[1]), "=c"(out[2]), "=d"(out[3])
                         : "a"(leaf), "c"(subleaf));
#endif
}

// 只在 OSXSAVE 已置位时调用 否则 xgetbv 本身就会 #UD
static unsigned long long readXcr0() {
#if defined(_MSC_VER)
    return _xgetbv(0);
#else
    unsigned low;
    unsigned high;
    __asm__ __volatile__("xgetbv" : "=a"(low), "=d"(high) : "c"(0));
    return (static_cast<unsigned long long>(high) << 32) | low;
#endif
}

static CpuFeatures cpuFeatures() {
    CpuFeatures features;
    unsigned registers[4] = {0, 0, 0, 0};

    readCpuid(0, 0, registers);
    const unsigned maxLeaf = registers[0];
    if (maxLeaf < 1) {
        return features;
    }

    readCpuid(1, 0, registers);
    features.sse2 = (registers[3] & (1u << 26)) != 0;
    const bool osxsave = (registers[2] & (1u << 27)) != 0;
    const bool avx = (registers[2] & (1u << 28)) != 0;
    const bool fma = (registers[2] & (1u << 12)) != 0;
    // XCR0 的 SSE 与 YMM 两位都置位才说明内核会保存 ymm 状态
    if (!osxsave || !avx || (readXcr0() & 0x6u) != 0x6u) {
        return features;
    }
    features.fma = fma;

    if (maxLeaf < 7) {
        return features;
    }
    readCpuid(7, 0, registers);
    features.avx2 = (registers[1] & (1u << 5)) != 0;
    return features;
}

#endif // DDT_ARCH_X86_64

#if defined(DDT_ARCH_AARCH64)

// 4x4 转置 对合 aarch64 基线就带 NEON 不需要运行期判断
static inline void transpose4x4Neon(const float32x4_t& a0, const float32x4_t& a1,
                                    const float32x4_t& a2, const float32x4_t& a3,
                                    float32x4_t& c0, float32x4_t& c1,
                                    float32x4_t& c2, float32x4_t& c3) {
    const float32x4x2_t t0 = vtrnq_f32(a0, a1);
    const float32x4x2_t t1 = vtrnq_f32(a2, a3);
    c0 = vcombine_f32(vget_low_f32(t0.val[0]), vget_low_f32(t1.val[0]));
    c1 = vcombine_f32(vget_low_f32(t0.val[1]), vget_low_f32(t1.val[1]));
    c2 = vcombine_f32(vget_high_f32(t0.val[0]), vget_high_f32(t1.val[0]));
    c3 = vcombine_f32(vget_high_f32(t0.val[1]), vget_high_f32(t1.val[1]));
}

template <bool kFlip>
static void transformNeonImpl(const Vertex* vertices, int count, const float* matrix,
                              const float* normalMatrix, int flags, float* out) {
    const float32x4_t m0 = vdupq_n_f32(matrix[0]);
    const float32x4_t m4 = vdupq_n_f32(matrix[4]);
    const float32x4_t m8 = vdupq_n_f32(matrix[8]);
    const float32x4_t m12 = vdupq_n_f32(matrix[12]);
    const float32x4_t m1 = vdupq_n_f32(matrix[1]);
    const float32x4_t m5 = vdupq_n_f32(matrix[5]);
    const float32x4_t m9 = vdupq_n_f32(matrix[9]);
    const float32x4_t m13 = vdupq_n_f32(matrix[13]);
    const float32x4_t m2 = vdupq_n_f32(matrix[2]);
    const float32x4_t m6 = vdupq_n_f32(matrix[6]);
    const float32x4_t m10 = vdupq_n_f32(matrix[10]);
    const float32x4_t m14 = vdupq_n_f32(matrix[14]);

    const float32x4_t n0 = vdupq_n_f32(normalMatrix[0]);
    const float32x4_t n1 = vdupq_n_f32(normalMatrix[1]);
    const float32x4_t n2 = vdupq_n_f32(normalMatrix[2]);
    const float32x4_t n3 = vdupq_n_f32(normalMatrix[3]);
    const float32x4_t n4 = vdupq_n_f32(normalMatrix[4]);
    const float32x4_t n5 = vdupq_n_f32(normalMatrix[5]);
    const float32x4_t n6 = vdupq_n_f32(normalMatrix[6]);
    const float32x4_t n7 = vdupq_n_f32(normalMatrix[7]);
    const float32x4_t n8 = vdupq_n_f32(normalMatrix[8]);

    const float32x4_t zero = vdupq_n_f32(0.0f);
    const uint32x4_t signBit = vdupq_n_u32(0x80000000u);
    const uint32x4_t flipMaskX = kFlip && (flags & kFlipX) != 0 ? signBit : vdupq_n_u32(0);
    const uint32x4_t flipMaskY = kFlip && (flags & kFlipY) != 0 ? signBit : vdupq_n_u32(0);
    const uint32x4_t flipMaskZ = kFlip && (flags & kFlipZ) != 0 ? signBit : vdupq_n_u32(0);

    const float* source = reinterpret_cast<const float*>(vertices);
    int i = 0;
    for (; i + 4 <= count; i += 4) {
        const float* p = source + static_cast<std::size_t>(i) * kVertexFloats;
        const float32x4_t r0 = vld1q_f32(p + 0);
        const float32x4_t r1 = vld1q_f32(p + 4);
        const float32x4_t r2 = vld1q_f32(p + 8);
        const float32x4_t r3 = vld1q_f32(p + 12);
        const float32x4_t r4 = vld1q_f32(p + 16);
        const float32x4_t r5 = vld1q_f32(p + 20);
        const float32x4_t r6 = vld1q_f32(p + 24);
        const float32x4_t r7 = vld1q_f32(p + 28);

        float32x4_t x, y, z, nx, ny, nz, u, v;
        transpose4x4Neon(r0, r2, r4, r6, x, y, z, nx);
        transpose4x4Neon(r1, r3, r5, r7, ny, nz, u, v);

        float32x4_t tx = vfmaq_f32(vmulq_f32(m0, x), m4, y);
        tx = vfmaq_f32(tx, m8, z);
        tx = vaddq_f32(tx, m12);

        float32x4_t ty = vfmaq_f32(vmulq_f32(m1, x), m5, y);
        ty = vfmaq_f32(ty, m9, z);
        ty = vaddq_f32(ty, m13);

        float32x4_t tz = vfmaq_f32(vmulq_f32(m2, x), m6, y);
        tz = vfmaq_f32(tz, m10, z);
        tz = vaddq_f32(tz, m14);

        float32x4_t onx = vfmaq_f32(vmulq_f32(n0, nx), n3, ny);
        onx = vfmaq_f32(onx, n6, nz);

        float32x4_t ony = vfmaq_f32(vmulq_f32(n1, nx), n4, ny);
        ony = vfmaq_f32(ony, n7, nz);

        float32x4_t onz = vfmaq_f32(vmulq_f32(n2, nx), n5, ny);
        onz = vfmaq_f32(onz, n8, nz);

        if constexpr (kFlip) {
            onx = vreinterpretq_f32_u32(veorq_u32(vreinterpretq_u32_f32(onx),
                                                  vandq_u32(vcltq_f32(onx, zero), flipMaskX)));
            ony = vreinterpretq_f32_u32(veorq_u32(vreinterpretq_u32_f32(ony),
                                                  vandq_u32(vcltq_f32(ony, zero), flipMaskY)));
            onz = vreinterpretq_f32_u32(veorq_u32(vreinterpretq_u32_f32(onz),
                                                  vandq_u32(vcltq_f32(onz, zero), flipMaskZ)));
        }

        float32x4_t o0, o1, o2, o3, o4, o5, o6, o7;
        transpose4x4Neon(tx, ty, tz, onx, o0, o1, o2, o3);
        transpose4x4Neon(ony, onz, u, v, o4, o5, o6, o7);

        float* target = out + static_cast<std::size_t>(i) * kVertexFloats;
        vst1q_f32(target + 0, o0);
        vst1q_f32(target + 4, o4);
        vst1q_f32(target + 8, o1);
        vst1q_f32(target + 12, o5);
        vst1q_f32(target + 16, o2);
        vst1q_f32(target + 20, o6);
        vst1q_f32(target + 24, o3);
        vst1q_f32(target + 28, o7);
    }
    if (i < count) {
        transformScalar(vertices + i, count - i, matrix, normalMatrix, flags,
                        out + static_cast<std::size_t>(i) * kVertexFloats);
    }
}

static void transformNeon(const Vertex* vertices, int count, const float* matrix,
                          const float* normalMatrix, int flags, float* out) {
    if (flags != 0) {
        transformNeonImpl<true>(vertices, count, matrix, normalMatrix, flags, out);
    } else {
        transformNeonImpl<false>(vertices, count, matrix, normalMatrix, flags, out);
    }
}

#endif // DDT_ARCH_AARCH64

// 自检与 tools/native/benchmark.cpp 共用的固定输入与误差判定
// 同一个下标永远得到同一个顶点 两个调用方看到的是同一组数据 不会各抄一份再漂移
void fillKernelProbe(Vertex* vertices, int count, float* matrix, float* normal) {
    for (int i = 0; i < count; ++i) {
        std::uint32_t state = 0x9E3779B9u + static_cast<std::uint32_t>(i) * 2654435761u;
        float values[kVertexFloats];
        for (float& value : values) {
            state = state * 1664525u + 1013904223u;
            value = static_cast<float>((state >> 9) & 0x3FFFu) / 512.0f - 16.0f;
        }
        vertices[i] = Vertex{values[0], values[1], values[2], values[3],
                             values[4], values[5], values[6], values[7]};
    }
    // 列主序 旋转加非均匀缩放加平移 法线矩阵取左上角 3x3
    static const float kMatrix[kMatrixFloats] = {
        0.8f, 0.36f, -0.48f, 0.0f,
        -0.6f, 0.48f, -0.64f, 0.0f,
        0.0f, 0.8f, 0.6f, 0.0f,
        3.0f, -2.0f, 5.0f, 1.0f,
    };
    static const float kNormal[kNormalFloats] = {
        0.8f, 0.36f, -0.48f,
        -0.6f, 0.48f, -0.64f,
        0.0f, 0.8f, 0.6f,
    };
    std::memcpy(matrix, kMatrix, sizeof(kMatrix));
    std::memcpy(normal, kNormal, sizeof(kNormal));
}

// 阈值随数值量级放大 写成 !(<=) 让 NaN 也判失败
bool withinKernelTolerance(float expected, float received) {
    const float difference = expected > received ? expected - received : received - expected;
    float magnitude = 1.0f;
    const float absExpected = expected < 0.0f ? -expected : expected;
    const float absReceived = received < 0.0f ? -received : received;
    if (absExpected > magnitude) {
        magnitude = absExpected;
    }
    if (absReceived > magnitude) {
        magnitude = absReceived;
    }
    return difference <= 1.0e-4f * magnitude;
}

// 选内核之前先用一组固定输入把候选实现和标量基线比一遍
// 转置写错 或者编译器在某个 target 上发了不对的指令 都会在这里掉回标量
// 16 个顶点正好两轮 AVX2 四轮 SSE2 再用 13 个顶点走一遍尾部标量路径
// 取反标志按 0 与三个单轴各跑一遍 只测单个组合会让写错的轴混过去
struct KernelProbe {
    Vertex vertices[16];
    float matrix[kMatrixFloats];
    float normal[kNormalFloats];
};

static const KernelProbe& kernelProbe() {
    static const KernelProbe probe = [] {
        KernelProbe data{};
        fillKernelProbe(data.vertices, 16, data.matrix, data.normal);
        return data;
    }();
    return probe;
}

static bool kernelAgrees(const PieceKernel candidate) {
    const KernelProbe& probe = kernelProbe();
    const int flagsCases[4] = {0, kFlipX, kFlipY, kFlipZ};
    const int counts[2] = {16, 13};
    float reference[16 * kVertexFloats];
    float actual[16 * kVertexFloats];
    for (const int flags : flagsCases) {
        for (const int count : counts) {
            transformScalar(probe.vertices, count, probe.matrix, probe.normal, flags, reference);
            candidate(probe.vertices, count, probe.matrix, probe.normal, flags, actual);
            for (int i = 0; i < count * kVertexFloats; ++i) {
                if (!withinKernelTolerance(reference[i], actual[i])) {
                    return false;
                }
            }
        }
    }
    return true;
}

struct KernelSelection {
    PieceKernel kernel;
    const char* name;
};

static KernelSelection chooseKernel() {
#if defined(DDT_ARCH_X86_64)
    const CpuFeatures features = cpuFeatures();
    if (features.avx2 && features.fma && kernelAgrees(transformAvx2)) {
        return {transformAvx2, "avx2+fma"};
    }
    if (features.sse2 && kernelAgrees(transformSse2)) {
        return {transformSse2, "sse2"};
    }
#elif defined(DDT_ARCH_AARCH64)
    if (kernelAgrees(transformNeon)) {
        return {transformNeon, "neon"};
    }
#endif
    return {transformScalar, "scalar"};
}

// 进程内只选一次 static 局部变量的初始化在 C++11 起就是线程安全的
static const KernelSelection& kernelSelection() {
    static const KernelSelection selection = chooseKernel();
    return selection;
}

// 亲和性默认关闭 只在环境变量显式打开时钉
// 实测硬亲和会让每帧睡醒的 worker 被绑在可能已经停核的 CPU 上
// 8 线程以上反而比不钉慢一个数量级 大小核与 SMT 机器上更不可控
// NUMA 机器上硬亲和只减少迁移 不代表内存就在本节点
// 网格由渲染线程分配 输出是 Java 直接缓冲区 两边都不受 worker 控制
static bool affinityEnabled() {
    static const bool enabled = [] {
        const char* value = std::getenv("DAWNDUSKTERMINAL_NATIVE_AFFINITY");
        if (value == nullptr || value[0] == '\0') {
            return false;
        }
        const char first = value[0];
        return !(first == '0' || first == 'f' || first == 'F'
                 || first == 'n' || first == 'N' || first == 'o' || first == 'O');
    }();
    return enabled;
}

#if defined(_WIN32)

// Windows 用 processor group 感知的接口 超过 64 个逻辑核时单个 mask 已经不够
// 只在有富余核时跳过第一颗 把 0 号核留给渲染线程
static void pinCurrentThread(int index, int count) {
    if (!affinityEnabled()) {
        return;
    }
    const WORD groups = GetActiveProcessorGroupCount();
    int total = 0;
    for (WORD group = 0; group < groups; ++group) {
        total += static_cast<int>(GetActiveProcessorCount(group));
    }
    if (total <= 1) {
        return;
    }
    int slot = total > count ? index + 1 : index;
    slot %= total;
    for (WORD group = 0; group < groups; ++group) {
        const int processors = static_cast<int>(GetActiveProcessorCount(group));
        if (slot < processors) {
            GROUP_AFFINITY affinity{};
            affinity.Group = group;
            affinity.Mask = static_cast<KAFFINITY>(1) << slot;
            SetThreadGroupAffinity(GetCurrentThread(), &affinity, nullptr);
            return;
        }
        slot -= processors;
    }
}

#elif defined(__linux__)

// Linux 从 sched_getaffinity 的允许集里挑 不假设 CPU 编号连续
// cgroup 限制配额时仍可能被 throttle 这里只解决迁移不解决配额
static void pinCurrentThread(int index, int count) {
    if (!affinityEnabled()) {
        return;
    }
    cpu_set_t allowed;
    CPU_ZERO(&allowed);
    if (sched_getaffinity(0, sizeof(allowed), &allowed) != 0) {
        return;
    }
    int cpus[CPU_SETSIZE];
    int total = 0;
    for (int cpu = 0; cpu < CPU_SETSIZE; ++cpu) {
        if (CPU_ISSET(cpu, &allowed)) {
            cpus[total++] = cpu;
        }
    }
    if (total <= 1) {
        return;
    }
    int slot = total > count ? index + 1 : index;
    slot %= total;
    cpu_set_t set;
    CPU_ZERO(&set);
    CPU_SET(cpus[slot], &set);
    pthread_setaffinity_np(pthread_self(), sizeof(set), &set);
}

#else

// macOS 没有公开的硬亲和 API thread_policy_set 的 affinity 只是建议值
static void pinCurrentThread(int, int) {}

#endif

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

/**
 * worker 池
 *
 * <p>每个 job 前按线程数把 piece 均分成连续区间 每个 worker 只跑自己的 [begin,end)
 * 不再用 fetch_add 抢同一个原子计数器 完成同步用 std::latch 不用 generation+CV
 * 线程用 std::jthread 析构自动 join
 *
 * <p>latch 是单次对象 每帧用一个 shared_ptr 持有 worker 里再拿一份
 * 最后一个引用释放时才析构 避免 wait 返回时还有 worker 停在 count_down 里面
 */
class Pool {
public:
    explicit Pool(int threadCount)
        : workerCount(threadCount < 1 ? 1 : (threadCount > kMaxThreads ? kMaxThreads : threadCount)) {
        workers.reserve(static_cast<std::size_t>(workerCount));
        try {
            for (int i = 0; i < workerCount; ++i) {
                workers.emplace_back([this, i] { worker(i); });
            }
        } catch (...) {
            shutdown();
            throw;
        }
    }

    ~Pool() {
        shutdown();
    }

    Pool(const Pool&) = delete;
    Pool& operator=(const Pool&) = delete;

    jint run(const Mesh* mesh, const float* matrices, const float* normals,
             const jint* pieceIds, int count, float* out, PieceKernel kernel) {
        if (mesh == nullptr || out == nullptr || kernel == nullptr || count < 0
            || (count > 0 && (matrices == nullptr || normals == nullptr || pieceIds == nullptr))) {
            return kInvalidArgument;
        }

        // 渲染线程之外也允许调用 这里串行化 避免两个 job 互相覆盖共享字段
        std::lock_guard<std::mutex> serialized(runMutex);

        auto latch = std::make_shared<std::latch>(static_cast<std::ptrdiff_t>(workers.size()));
        {
            std::lock_guard<std::mutex> lock(mutex);
            jobMesh = mesh;
            jobMatrices = matrices;
            jobNormals = normals;
            jobPieceIds = pieceIds;
            jobCount = count;
            jobOut = out;
            jobKernel = kernel;
            jobLatch = latch;
            invalid.store(false, std::memory_order_relaxed);
            ++generation;
        }
        startCv.notify_all();
        latch->wait();
        {
            std::lock_guard<std::mutex> lock(mutex);
            jobLatch.reset();
        }

        return invalid.load(std::memory_order_relaxed) ? kInvalidArgument : kOk;
    }

    jint run(const Mesh* mesh, const float* matrices, const float* normals,
             const jint* pieceIds, int count, float* out) {
        return run(mesh, matrices, normals, pieceIds, count, out, kernelSelection().kernel);
    }

private:
    static std::pair<int, int> partition(int index, int workers, int count) {
        const int base = count / workers;
        const int extra = count % workers;
        const int begin = index * base + (index < extra ? index : extra);
        const int end = begin + base + (index < extra ? 1 : 0);
        return {begin, end};
    }

    void shutdown() {
        {
            std::lock_guard<std::mutex> lock(mutex);
            stop = true;
        }
        startCv.notify_all();
        workers.clear();
    }

    void worker(int index) {
        pinCurrentThread(index, workerCount);
        std::uint64_t seenGeneration = 0;
        for (;;) {
            std::shared_ptr<std::latch> latch;
            const Mesh* mesh;
            const float* matrices;
            const float* normals;
            const jint* pieceIds;
            PieceKernel kernel;
            float* out;
            int count;
            {
                std::unique_lock<std::mutex> lock(mutex);
                startCv.wait(lock, [this, seenGeneration] {
                    return stop || generation != seenGeneration;
                });
                if (stop) {
                    return;
                }
                seenGeneration = generation;
                mesh = jobMesh;
                matrices = jobMatrices;
                normals = jobNormals;
                pieceIds = jobPieceIds;
                kernel = jobKernel;
                out = jobOut;
                count = jobCount;
                latch = jobLatch;
            }

            // C++ 异常不允许穿过 JNI 边界 任何线程内失败都只记标志 由 run 返回错误码
            try {
                const auto range = partition(index, workerCount, count);
                for (int i = range.first; i < range.second; ++i) {
                    transformPiece(mesh, matrices, normals, pieceIds[i], i, kernel, out);
                }
            } catch (...) {
                invalid.store(true, std::memory_order_relaxed);
            }

            if (latch) {
                latch->count_down();
            }
        }
    }

    void transformPiece(const Mesh* mesh, const float* matrices, const float* normals,
                        int pieceId, int batchIndex, PieceKernel kernel, float* out) {
        if (pieceId < 0 || static_cast<std::size_t>(pieceId) >= mesh->pieces.size()) {
            invalid.store(true, std::memory_order_relaxed);
            return;
        }
        const Piece& piece = mesh->pieces[static_cast<std::size_t>(pieceId)];
        if (piece.count <= 0) {
            return;
        }
        const std::size_t offset = static_cast<std::size_t>(piece.offset);
        kernel(mesh->vertices.data() + offset, piece.count,
               matrices + static_cast<std::size_t>(batchIndex) * kMatrixFloats,
               normals + static_cast<std::size_t>(batchIndex) * kNormalFloats,
               piece.flags, out + offset * kVertexFloats);
    }

    std::vector<std::jthread> workers;
    int workerCount;
    std::mutex runMutex;
    std::mutex mutex;
    std::condition_variable startCv;
    const Mesh* jobMesh = nullptr;
    const float* jobMatrices = nullptr;
    const float* jobNormals = nullptr;
    const jint* jobPieceIds = nullptr;
    PieceKernel jobKernel = nullptr;
    float* jobOut = nullptr;
    int jobCount = 0;
    std::shared_ptr<std::latch> jobLatch;
    std::atomic<bool> invalid{false};
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
 *
 * <p>2 起 nativeTransform 的矩阵 法线 piece id 三个入参从 Java 数组改成直接缓冲区
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

/**
 * 当前进程实际选中的变换内核名 avx2+fma sse2 neon scalar 之一
 *
 * <p>只用于日志与排查 加载期调用一次 顺带触发内核自检
 * 老库没有这个导出 但 ABI 版本对不上就先被拒了 不会走到这里
 */
JNIEXPORT jstring JNICALL
Java_com_dawnduskterminal_client_NativeMesh_nativeKernelName(JNIEnv* env, jclass) {
    try {
        return env->NewStringUTF(ddt::kernelSelection().name);
    } catch (...) {
        return nullptr;
    }
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
    jobject matrices, jobject normals, jobject pieceIds, jint count, jobject output) {
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

        // 三个入参都是直接缓冲区 渲染线程每帧只写一次 交给 native 直接读
        // 不走 GetFloatArrayElements 那趟每帧约 73KB 的拷贝
        const jlong requiredMatrixBytes =
            static_cast<jlong>(count) * ddt::kMatrixFloats * static_cast<jlong>(sizeof(float));
        const jlong requiredNormalBytes =
            static_cast<jlong>(count) * ddt::kNormalFloats * static_cast<jlong>(sizeof(float));
        const jlong requiredIdBytes = static_cast<jlong>(count) * static_cast<jlong>(sizeof(jint));
        if (env->GetDirectBufferCapacity(matrices) < requiredMatrixBytes
            || env->GetDirectBufferCapacity(normals) < requiredNormalBytes
            || env->GetDirectBufferCapacity(pieceIds) < requiredIdBytes) {
            return ddt::kInvalidArgument;
        }

        const jlong requiredFloats = static_cast<jlong>(mesh->vertices.size()) * ddt::kVertexFloats;
        const jlong capacityBytes = env->GetDirectBufferCapacity(output);
        if (capacityBytes < 0
            || capacityBytes < requiredFloats * static_cast<jlong>(sizeof(float))) {
            return ddt::kOutputTooSmall;
        }

        auto* matrixData = static_cast<const float*>(env->GetDirectBufferAddress(matrices));
        auto* normalData = static_cast<const float*>(env->GetDirectBufferAddress(normals));
        auto* idData = static_cast<const jint*>(env->GetDirectBufferAddress(pieceIds));
        auto* out = static_cast<float*>(env->GetDirectBufferAddress(output));
        if (matrixData == nullptr || normalData == nullptr || idData == nullptr || out == nullptr) {
            return ddt::kInvalidArgument;
        }

        const jint result = pool->run(mesh.get(), matrixData, normalData, idData, count, out);
        if (result < 0) {
            return result;
        }
        return static_cast<jint>(mesh->vertices.size());
    } catch (...) {
        return ddt::kInternalError;
    }
}

}
