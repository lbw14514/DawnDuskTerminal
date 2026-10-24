// JNI ABI 表面 fuzz 目标 不属于构建产物
//
// 直接把 ddt_native.cpp 整份 include 进来 用一张假 JNI 函数表喂 nativeCreateMesh
// 与 nativeTransform 这样 null 数组 长度不匹配 负 count 缓冲区容量不足 越界
// piece 与 NaN/Inf 顶点都能走到真实入口 不用起 JVM
//
// libFuzzer 构建（Linux / macOS 的 clang）:
//   clang++ -O1 -g -std=c++20 -fsanitize=fuzzer,address \
//     -I<JDK>/include -Isrc/main/native/posix tools/native/fuzz_abi.cpp -o fuzz_abi
//   ./fuzz_abi -max_total_time=60
//
// Windows / Zig 没有 libFuzzer 运行时 用固定畸形语料入口:
//   zig c++ -O1 -g -std=c++20 -D_LIBCPP_ENABLE_EXPERIMENTAL -DDDT_FUZZ_STANDALONE \
//     -I<JDK>/include -Isrc/main/native/win32 tools/native/fuzz_abi.cpp -o fuzz_abi.exe
//   ./fuzz_abi.exe
//
// fuzz 目标只跑在本机 不打进 jar

#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif

#include <cstdint>
#include <cstring>
#include <limits>
#include <vector>

#include "../../src/main/native/ddt_native.cpp"

namespace {

enum class FakeKind {
    FloatArray,
    IntArray,
    Buffer,
};

struct FakeObject {
    FakeKind kind;
    std::vector<jfloat> floats;
    std::vector<jint> ints;
    std::vector<std::uint8_t> bytes;
    jlong capacity = 0;
};

FakeObject* fakeObject(jobject value) {
    return reinterpret_cast<FakeObject*>(value);
}

jint JNICALL fakeGetArrayLength(JNIEnv*, jarray array) {
    if (array == nullptr) {
        return -1;
    }
    const FakeObject* object = reinterpret_cast<const FakeObject*>(array);
    if (object->kind == FakeKind::FloatArray) {
        return static_cast<jint>(object->floats.size());
    }
    if (object->kind == FakeKind::IntArray) {
        return static_cast<jint>(object->ints.size());
    }
    return -1;
}

jfloat* JNICALL fakeGetFloatArrayElements(JNIEnv*, jfloatArray array, jboolean*) {
    if (array == nullptr) {
        return nullptr;
    }
    FakeObject* object = fakeObject(array);
    if (object->kind != FakeKind::FloatArray) {
        return nullptr;
    }
    // 空数组也给一个非空指针 让零顶点网格能走到建网格而不是被这里提前拒掉
    static jfloat empty = 0.0f;
    return object->floats.empty() ? &empty : object->floats.data();
}

void JNICALL fakeReleaseFloatArrayElements(JNIEnv*, jfloatArray, jfloat*, jint) {}

jint* JNICALL fakeGetIntArrayElements(JNIEnv*, jintArray array, jboolean*) {
    if (array == nullptr) {
        return nullptr;
    }
    FakeObject* object = fakeObject(array);
    if (object->kind != FakeKind::IntArray) {
        return nullptr;
    }
    static jint empty = 0;
    return object->ints.empty() ? &empty : object->ints.data();
}

void JNICALL fakeReleaseIntArrayElements(JNIEnv*, jintArray, jint*, jint) {}

jlong JNICALL fakeGetDirectBufferCapacity(JNIEnv*, jobject buffer) {
    if (buffer == nullptr) {
        return -1;
    }
    const FakeObject* object = fakeObject(buffer);
    return object->kind == FakeKind::Buffer ? object->capacity : -1;
}

void* JNICALL fakeGetDirectBufferAddress(JNIEnv*, jobject buffer) {
    if (buffer == nullptr) {
        return nullptr;
    }
    FakeObject* object = fakeObject(buffer);
    if (object->kind != FakeKind::Buffer || object->bytes.empty()) {
        return nullptr;
    }
    return object->bytes.data();
}

jstring JNICALL fakeNewStringUTF(JNIEnv*, const char*) {
    static int sentinel = 0;
    return reinterpret_cast<jstring>(&sentinel);
}

JNINativeInterface_ makeTable() {
    JNINativeInterface_ table{};
    table.GetArrayLength = fakeGetArrayLength;
    table.GetFloatArrayElements = fakeGetFloatArrayElements;
    table.ReleaseFloatArrayElements = fakeReleaseFloatArrayElements;
    table.GetIntArrayElements = fakeGetIntArrayElements;
    table.ReleaseIntArrayElements = fakeReleaseIntArrayElements;
    table.GetDirectBufferCapacity = fakeGetDirectBufferCapacity;
    table.GetDirectBufferAddress = fakeGetDirectBufferAddress;
    table.NewStringUTF = fakeNewStringUTF;
    return table;
}

JNIEnv* fakeEnv() {
    static JNINativeInterface_ table = makeTable();
    static JNIEnv env = [] {
        JNIEnv value{};
        value.functions = &table;
        return value;
    }();
    return &env;
}

struct Reader {
    const std::uint8_t* data;
    std::size_t size;
    std::size_t position = 0;

    std::uint8_t u8() {
        return position < size ? data[position++] : 0;
    }

    std::uint16_t u16() {
        const std::uint16_t low = u8();
        const std::uint16_t high = u8();
        return static_cast<std::uint16_t>(low | (high << 8));
    }

    std::uint32_t u32() {
        const std::uint32_t a = u8();
        const std::uint32_t b = u8();
        const std::uint32_t c = u8();
        const std::uint32_t d = u8();
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    jfloat f32() {
        const std::uint32_t bits = u32();
        jfloat value;
        std::memcpy(&value, &bits, sizeof(value));
        return value;
    }
};

FakeObject floatArray(const std::vector<jfloat>& values) {
    FakeObject object{FakeKind::FloatArray};
    object.floats = values;
    return object;
}

FakeObject intArray(const std::vector<jint>& values) {
    FakeObject object{FakeKind::IntArray};
    object.ints = values;
    return object;
}

FakeObject buffer(std::vector<std::uint8_t> bytes) {
    FakeObject object{FakeKind::Buffer};
    object.capacity = static_cast<jlong>(bytes.size());
    object.bytes = std::move(bytes);
    return object;
}

std::vector<std::uint8_t> floatBytes(const std::vector<jfloat>& values) {
    std::vector<std::uint8_t> bytes(values.size() * sizeof(jfloat));
    if (!values.empty()) {
        std::memcpy(bytes.data(), values.data(), bytes.size());
    }
    return bytes;
}

std::vector<std::uint8_t> intBytes(const std::vector<jint>& values) {
    std::vector<std::uint8_t> bytes(values.size() * sizeof(jint));
    if (!values.empty()) {
        std::memcpy(bytes.data(), values.data(), bytes.size());
    }
    return bytes;
}

// 整个进程复用同一个池 否则每次迭代建线程会让 fuzz 速度掉两个数量级
jlong sharedPool() {
    static jlong handle = Java_com_dawnduskterminal_client_NativeMesh_nativeCreatePool(
        fakeEnv(), nullptr, 2);
    return handle;
}

} // namespace

extern "C" int LLVMFuzzerTestOneInput(const std::uint8_t* data, std::size_t size) {
    if (data == nullptr || size < 8) {
        return 0;
    }

    Reader reader{data, size};
    JNIEnv* env = fakeEnv();
    const std::uint8_t control = reader.u8();
    const std::uint8_t mutation = reader.u8();

    const int vertexCount = reader.u8() % 64;
    const int pieceCount = reader.u8() % 16;
    const int declaredPieces = reader.u8() % 16;

    std::vector<jfloat> vertices(static_cast<std::size_t>(vertexCount) * ddt::kVertexFloats);
    for (jfloat& value : vertices) {
        value = reader.f32();
    }
    // 偶尔让顶点数组长度不是 8 的倍数 覆盖 nativeCreateMesh 的布局拒绝分支
    if ((mutation & 0x10) != 0) {
        vertices.resize(vertices.size() + static_cast<std::size_t>(reader.u8() % 7) + 1);
    }

    std::vector<jint> offsets(static_cast<std::size_t>(pieceCount));
    std::vector<jint> counts(static_cast<std::size_t>(pieceCount));
    std::vector<jint> flags(static_cast<std::size_t>(pieceCount));
    for (int i = 0; i < pieceCount; ++i) {
        // 大部分生成合法区间 再用 mutation 位偶尔打坏 保证能走到内核
        const int offset = vertexCount > 0 ? static_cast<int>(reader.u16() % vertexCount) : 0;
        const int count = vertexCount > offset
            ? static_cast<int>(reader.u16() % (vertexCount - offset + 1))
            : 0;
        offsets[i] = offset;
        counts[i] = count;
        flags[i] = static_cast<jint>(reader.u8());
        if ((mutation & 0x01) != 0) {
            offsets[i] = -1;
        }
        if ((mutation & 0x02) != 0) {
            counts[i] = vertexCount + 1;
        }
        if ((mutation & 0x04) != 0) {
            offsets[i] = vertexCount + 1;
        }
    }
    // 让 offsets/counts/flags 的长度偶尔与 declaredPieces 不一致
    if ((control & 0x01) != 0 && pieceCount > 0) {
        offsets.resize(static_cast<std::size_t>(declaredPieces));
    }
    if ((control & 0x02) != 0 && pieceCount > 0) {
        counts.resize(static_cast<std::size_t>(declaredPieces));
    }
    if ((control & 0x04) != 0 && pieceCount > 0) {
        flags.resize(static_cast<std::size_t>(declaredPieces));
    }

    FakeObject vertexArray = floatArray(vertices);
    FakeObject offsetArray = intArray(offsets);
    FakeObject countArray = intArray(counts);
    FakeObject flagArray = intArray(flags);

    jlong mesh = Java_com_dawnduskterminal_client_NativeMesh_nativeCreateMesh(
        env, nullptr,
        (control & 0x08) != 0 ? nullptr : reinterpret_cast<jfloatArray>(&vertexArray),
        (control & 0x10) != 0 ? nullptr : reinterpret_cast<jintArray>(&offsetArray),
        (control & 0x20) != 0 ? nullptr : reinterpret_cast<jintArray>(&countArray),
        (control & 0x40) != 0 ? nullptr : reinterpret_cast<jintArray>(&flagArray));

    const jlong pool = (control & 0x80) != 0 ? 0 : sharedPool();
    if (mesh != 0) {
        const int batchCount = static_cast<int>(reader.u8() % 16);
        const int transformCount = (reader.u8() & 0x01) != 0 ? -batchCount : batchCount;

        std::vector<jfloat> matrices(
            static_cast<std::size_t>(batchCount) * ddt::kMatrixFloats);
        for (jfloat& value : matrices) {
            value = reader.f32();
        }
        std::vector<jfloat> normals(
            static_cast<std::size_t>(batchCount) * ddt::kNormalFloats);
        for (jfloat& value : normals) {
            value = reader.f32();
        }
        std::vector<jint> pieceIds(static_cast<std::size_t>(batchCount));
        for (jint& value : pieceIds) {
            value = static_cast<jint>(reader.u8()) - 8;
        }
        std::vector<jfloat> output(
            static_cast<std::size_t>(vertexCount) * ddt::kVertexFloats);
        for (jfloat& value : output) {
            value = 0.0f;
        }

        FakeObject matrixBuffer = buffer(floatBytes(matrices));
        FakeObject normalBuffer = buffer(floatBytes(normals));
        FakeObject idBuffer = buffer(intBytes(pieceIds));
        FakeObject outputBuffer = buffer(floatBytes(output));
        if ((mutation & 0x08) != 0 && !outputBuffer.bytes.empty()) {
            outputBuffer.bytes.resize(outputBuffer.bytes.size() / 2);
            outputBuffer.capacity = static_cast<jlong>(outputBuffer.bytes.size());
        }

        const jlong poolHandle = (control & 0x80) != 0 ? -1 : pool;
        Java_com_dawnduskterminal_client_NativeMesh_nativeTransform(
            env, nullptr,
            poolHandle, mesh,
            reinterpret_cast<jobject>(&matrixBuffer),
            reinterpret_cast<jobject>(&normalBuffer),
            reinterpret_cast<jobject>(&idBuffer),
            transformCount,
            reinterpret_cast<jobject>(&outputBuffer));

        // 再走一次错句柄与空参数 确认失败路径不碰野指针
        Java_com_dawnduskterminal_client_NativeMesh_nativeTransform(
            env, nullptr, -1, -1, nullptr, nullptr, nullptr, 0, nullptr);
    }

    if (mesh != 0) {
        Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyMesh(env, nullptr, mesh);
    }
    Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyMesh(env, nullptr, -1);
    return 0;
}

#ifdef DDT_FUZZ_STANDALONE

int main() {
    JNIEnv* env = fakeEnv();
    (void)Java_com_dawnduskterminal_client_NativeMesh_nativeAbiVersion(env, nullptr);
    (void)Java_com_dawnduskterminal_client_NativeMesh_nativeVertexFloats(env, nullptr);
    (void)Java_com_dawnduskterminal_client_NativeMesh_nativeMatrixFloats(env, nullptr);
    (void)Java_com_dawnduskterminal_client_NativeMesh_nativeNormalFloats(env, nullptr);
    (void)Java_com_dawnduskterminal_client_NativeMesh_nativeKernelName(env, nullptr);
    for (int i = 0; i < 20; ++i) {
        const jlong pool = Java_com_dawnduskterminal_client_NativeMesh_nativeCreatePool(
            env, nullptr, i % 5);
        Java_com_dawnduskterminal_client_NativeMesh_nativeDestroyPool(env, nullptr, pool);
    }

    std::vector<std::uint8_t> buffer(2048);

    // 几个固定畸形输入
    const std::vector<std::vector<std::uint8_t>> seeds = {
        {},
        {0},
        std::vector<std::uint8_t>(7, 0),
        std::vector<std::uint8_t>(64, 0),
        std::vector<std::uint8_t>(256, 0xFF),
        std::vector<std::uint8_t>(256, 0x7F),
    };
    for (const std::vector<std::uint8_t>& seed : seeds) {
        LLVMFuzzerTestOneInput(seed.data(), seed.size());
    }

    std::uint32_t state = 0x9E3779B9u;
    for (int iteration = 0; iteration < 5000; ++iteration) {
        for (std::uint8_t& byte : buffer) {
            state = state * 1664525u + 1013904223u;
            byte = static_cast<std::uint8_t>(state >> 24);
        }
        std::size_t length = buffer.size();
        if ((iteration % 7) == 0) {
            length = static_cast<std::size_t>(iteration % 64);
        }
        LLVMFuzzerTestOneInput(buffer.data(), length);
    }
    return 0;
}

#endif
