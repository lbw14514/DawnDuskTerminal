package com.dawnduskterminal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoCube;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * native 网格批处理
 *
 * <p>用法 在一个渲染批次开始前 {@link #begin(NativeGeoMesh)}
 * 每个 cube 自己把 pivot 与旋转应用到 {@link PoseStack} 之后调用
 * {@link #add(GeoCube, PoseStack, VertexConsumer, int, int, int)}
 * 批次结束时调用 {@link #flush(VertexConsumer)} 一次 JNI 把整批顶点算完
 *
 * <p>实体渲染器和方块实体渲染器都能用同一套流程 只要它们的 {@code renderCube}
 * 能拿到 {@link PoseStack} 当前矩阵 内核不关心调用方是实体还是方块
 *
 * <p>只在渲染线程使用 没有做并发保护
 * native 出错时抛出 {@link NativeMesh.NativeMeshException} 由调用方决定回退策略
 */
public final class NativeMeshBatch {
    private static final int INITIAL_CAPACITY = 1024;

    private long pool;
    private NativeGeoMesh mesh;
    private boolean active;
    private int count;

    private final float[] matrixScratch = new float[16];
    private final float[] normalScratch = new float[9];

    private float[] matrices = new float[INITIAL_CAPACITY * 16];
    private float[] normals = new float[INITIAL_CAPACITY * 9];
    private int[] pieceIds = new int[INITIAL_CAPACITY];
    private int[] lights = new int[INITIAL_CAPACITY];
    private int[] overlays = new int[INITIAL_CAPACITY];
    private int[] colours = new int[INITIAL_CAPACITY];
    private VertexConsumer[] buffers = new VertexConsumer[INITIAL_CAPACITY];

    private ByteBuffer output;
    private FloatBuffer outputFloats;

    public void begin(NativeGeoMesh mesh) {
        if (mesh == null || mesh.handle() == 0L) {
            throw new NativeMesh.NativeMeshException(NativeMesh.ERROR_INVALID_HANDLE,
                "native 网格句柄无效");
        }
        ensurePool();
        reset();
        this.mesh = mesh;
        this.active = true;
    }

    public boolean isActive() {
        return this.active;
    }

    /**
     * @return 这个 cube 不在 native 网格里 例如没有任何可见顶点 时返回 false
     */
    public boolean add(GeoCube cube, PoseStack poseStack, VertexConsumer buffer,
                       int packedLight, int packedOverlay, int colour) {
        if (!this.active) {
            throw new IllegalStateException("native 网格批处理没有开始");
        }
        int pieceId = this.mesh.id(cube);
        if (pieceId < 0) {
            return false;
        }

        ensureCapacity(this.count + 1);
        Matrix4f pose = poseStack.last().pose();
        pose.get(this.matrixScratch);
        int matrixOffset = this.count * 16;
        System.arraycopy(this.matrixScratch, 0, this.matrices, matrixOffset, 16);

        Matrix3f normal = poseStack.last().normal();
        normal.get(this.normalScratch);
        int normalOffset = this.count * 9;
        System.arraycopy(this.normalScratch, 0, this.normals, normalOffset, 9);

        int index = this.count++;
        this.pieceIds[index] = pieceId;
        this.lights[index] = packedLight;
        this.overlays[index] = packedOverlay;
        this.colours[index] = colour;
        this.buffers[index] = buffer;
        return true;
    }

    /**
     * 结束收集 不清空已收集的批次 之后仍然可以 flush
     */
    public void end() {
        this.active = false;
    }

    /**
     * @throws NativeMesh.NativeMeshException native 不可用或变换失败 此时批次已经清空
     */
    public void flush(VertexConsumer fallbackBuffer) {
        this.active = false;
        try {
            if (this.count == 0) {
                return;
            }

            ensureOutput();
            int result = NativeMesh.transform(
                this.pool, this.mesh.handle(),
                this.matrices, this.normals, this.pieceIds, this.count,
                this.output, this.output.capacity() / Float.BYTES);
            if (result < this.mesh.vertexCount()) {
                String detail = "native 只写出 " + result + " 个顶点 网格应有 " + this.mesh.vertexCount();
                NativeMesh.reportRuntimeFailure(detail);
                throw new NativeMesh.NativeMeshException(NativeMesh.ERROR_OUTPUT_TOO_SMALL, detail);
            }

            FloatBuffer output = this.outputFloats;
            for (int i = 0; i < this.count; i++) {
                int pieceId = this.pieceIds[i];
                int offset = this.mesh.offset(pieceId);
                int count = this.mesh.count(pieceId);
                VertexConsumer buffer = this.buffers[i] != null ? this.buffers[i] : fallbackBuffer;
                if (buffer == null) {
                    continue;
                }
                // 输出缓冲区按模型里的绝对顶点偏移写入 批处理可能跳过隐藏骨骼 不能顺序读
                output.position(offset * NativeMesh.VERTEX_FLOATS);
                int colour = this.colours[i];
                int overlay = this.overlays[i];
                int light = this.lights[i];
                for (int v = 0; v < count; v++) {
                    float x = output.get();
                    float y = output.get();
                    float z = output.get();
                    float nx = output.get();
                    float ny = output.get();
                    float nz = output.get();
                    float u = output.get();
                    float texV = output.get();
                    buffer.addVertex(x, y, z, colour, u, texV, overlay, light, nx, ny, nz);
                }
            }
        } finally {
            reset();
        }
    }

    private void ensurePool() {
        if (this.pool != 0L) {
            return;
        }
        this.pool = NativeMesh.sharedPool();
        if (this.pool == 0L) {
            throw new NativeMesh.NativeMeshException(NativeMesh.ERROR_UNAVAILABLE,
                "native 网格线程池不可用 " + NativeMesh.failureDescription());
        }
    }

    private void ensureCapacity(int needed) {
        if (needed <= this.pieceIds.length) {
            return;
        }
        int size = Math.max(needed, this.pieceIds.length * 2);
        this.matrices = Arrays.copyOf(this.matrices, size * 16);
        this.normals = Arrays.copyOf(this.normals, size * 9);
        this.pieceIds = Arrays.copyOf(this.pieceIds, size);
        this.lights = Arrays.copyOf(this.lights, size);
        this.overlays = Arrays.copyOf(this.overlays, size);
        this.colours = Arrays.copyOf(this.colours, size);
        this.buffers = Arrays.copyOf(this.buffers, size);
    }

    private void ensureOutput() {
        int floats = this.mesh.vertexCount() * NativeMesh.VERTEX_FLOATS;
        if (this.output == null || this.output.capacity() < floats * Float.BYTES) {
            this.output = ByteBuffer.allocateDirect(floats * Float.BYTES)
                .order(ByteOrder.nativeOrder());
            this.outputFloats = this.output.asFloatBuffer();
        }
    }

    private void reset() {
        int used = Math.min(this.count, this.buffers.length);
        this.count = 0;
        this.mesh = null;
        Arrays.fill(this.buffers, 0, used, null);
    }
}
