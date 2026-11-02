package com.dawnduskterminal.client;

import com.dawnduskterminal.geckolib3.geo.render.built.GeoBone;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoMesh;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;
import java.lang.ref.Cleaner;
import java.util.IdentityHashMap;
import java.util.Map;
import org.joml.Vector3f;

/**
 * 把模型摊平成 native 内核认识的 piece
 *
 * <p>每个顶点固定 8 个 float：坐标、法线、UV。每条 piece 记录顶点区间与法线取反标志，
 * 一个立方体就是一条 piece。顶点是<strong>骨骼局部空间</strong>的坐标：pivot 与立方体自身的旋转
 * 已经在 {@code GeoMesh} 建表时烘进去，运行时只需要乘上骨骼姿势矩阵，与 Java 路径一致。
 *
 * <p>piece 编号用 {@link IdentityHashMap} 以 {@link GeoMesh} 的对象身份为键，值是「立方体下标 → piece 编号」，
 * 因为同一个模型里的两块 {@code GeoMesh} 可能内容完全相同而对象不同，反过来同一块 {@code GeoMesh}
 * 也只该摊平一次。
 *
 * <p>模型换了就重新 build，旧句柄必须 close。
 */
public final class NativeGeoMesh {
    /**
     * F3+T 资源重载会把实体渲染器整体换掉，旧实例没人再调 close，只能靠 Cleaner 在不可达时回收 native 句柄
     * 每次重载约 550KB，不是小数目，而且没有别的兜底
     */
    private static final Cleaner CLEANER = Cleaner.create();

    /** 与 GeoMesh 建表时的 {@code Math.max(0.001f, ...)} 对应：边长等于该值的轴是退化的 */
    private static final float DEGENERATE = 0.0015F;

    private final GeoModel model;
    private final int[] offsets;
    private final int[] counts;
    private final Map<GeoMesh, int[]> pieces;
    private final int vertexCount;
    private final Cleanup cleanup;

    private boolean closed;

    private NativeGeoMesh(
            GeoModel model, long handle, int[] offsets, int[] counts, Map<GeoMesh, int[]> pieces, int vertexCount) {
        this.model = model;
        this.offsets = offsets;
        this.counts = counts;
        this.pieces = pieces;
        this.vertexCount = vertexCount;
        this.cleanup = new Cleanup(handle);
        CLEANER.register(this, this.cleanup);
    }

    /**
     * @throws IllegalStateException      模型里没有任何可见立方体
     * @throws NativeMesh.NativeMeshException native 不可用或建网格失败
     */
    public static NativeGeoMesh build(GeoModel model) {
        FloatList vertices = new FloatList();
        IntList offsets = new IntList();
        IntList counts = new IntList();
        IntList flags = new IntList();
        Map<GeoMesh, int[]> pieces = new IdentityHashMap<>();

        for (GeoBone bone : model.topLevelBones()) {
            collectBone(bone, vertices, offsets, counts, flags, pieces);
        }

        if (offsets.size == 0) {
            throw new IllegalStateException("模型没有可渲染的立方体");
        }

        long handle = NativeMesh.createMesh(vertices.toArray(), offsets.toArray(), counts.toArray(), flags.toArray());
        return new NativeGeoMesh(
                model, handle, offsets.toArray(), counts.toArray(), pieces, vertices.size / NativeMesh.VERTEX_FLOATS);
    }

    private static void collectBone(
            GeoBone bone,
            FloatList vertices,
            IntList offsets,
            IntList counts,
            IntList flags,
            Map<GeoMesh, int[]> pieces) {
        GeoMesh mesh = bone.cubes();
        if (mesh != null && mesh.getCubeCount() > 0 && !pieces.containsKey(mesh)) {
            int[] pieceIds = new int[mesh.getCubeCount()];
            for (int index = 0; index < pieceIds.length; index++) {
                int before = vertices.size;
                emitCube(mesh, index, vertices);
                int emitted = (vertices.size - before) / NativeMesh.VERTEX_FLOATS;
                if (emitted == 0) {
                    pieceIds[index] = -1;
                    continue;
                }
                pieceIds[index] = offsets.size;
                offsets.add(before / NativeMesh.VERTEX_FLOATS);
                counts.add(emitted);
                flags.add(flatFlags(mesh, index));
            }
            pieces.put(mesh, pieceIds);
        }
        for (GeoBone child : bone.children()) {
            collectBone(child, vertices, offsets, counts, flags, pieces);
        }
    }

    /**
     * 写出一个立方体的全部可见面，顶点顺序、UV 与法线取法与 {@code IGeoRenderer.renderCubesOfBone} 相同
     *
     * <p>镜像立方体（{@code faces} 的 bit6）在 Java 路径里是把三个法线整体取反，这里直接写进顶点，
     * 因此 native 侧不需要额外的镜像标志。
     */
    private static void emitCube(GeoMesh mesh, int index, FloatList vertices) {
        Vector3f p = mesh.position(index);
        Vector3f dx = mesh.dx(index);
        Vector3f dy = mesh.dy(index);
        Vector3f dz = mesh.dz(index);

        float x0 = p.x, y0 = p.y, z0 = p.z;
        float x1 = p.x + dx.x, y1 = p.y + dx.y, z1 = p.z + dx.z;
        float x2 = x1 + dy.x, y2 = y1 + dy.y, z2 = z1 + dy.z;
        float x3 = p.x + dy.x, y3 = p.y + dy.y, z3 = p.z + dy.z;
        float x4 = p.x + dz.x, y4 = p.y + dz.y, z4 = p.z + dz.z;
        float x5 = x1 + dz.x, y5 = y1 + dz.y, z5 = z1 + dz.z;
        float x6 = x2 + dz.x, y6 = y2 + dz.y, z6 = z2 + dz.z;
        float x7 = x3 + dz.x, y7 = y3 + dz.y, z7 = z3 + dz.z;

        Vector3f nx = new Vector3f(dy).cross(dz).normalize();
        Vector3f ny = new Vector3f(dz).cross(dx).normalize();
        Vector3f nz = new Vector3f(dx).cross(dy).normalize();
        int faces = mesh.faces(index);
        if ((faces & 0b1000000) != 0) {
            nx.negate();
            ny.negate();
            nz.negate();
        }

        if ((faces & 0b000001) != 0) { // DOWN
            addVertex(vertices, x5, y5, z5, -ny.x, -ny.y, -ny.z, mesh.downU0(index), mesh.downV1(index));
            addVertex(vertices, x4, y4, z4, -ny.x, -ny.y, -ny.z, mesh.downU1(index), mesh.downV1(index));
            addVertex(vertices, x0, y0, z0, -ny.x, -ny.y, -ny.z, mesh.downU1(index), mesh.downV0(index));
            addVertex(vertices, x1, y1, z1, -ny.x, -ny.y, -ny.z, mesh.downU0(index), mesh.downV0(index));
        }
        if ((faces & 0b000010) != 0) { // UP
            addVertex(vertices, x2, y2, z2, ny.x, ny.y, ny.z, mesh.upU0(index), mesh.upV1(index));
            addVertex(vertices, x3, y3, z3, ny.x, ny.y, ny.z, mesh.upU1(index), mesh.upV1(index));
            addVertex(vertices, x7, y7, z7, ny.x, ny.y, ny.z, mesh.upU1(index), mesh.upV0(index));
            addVertex(vertices, x6, y6, z6, ny.x, ny.y, ny.z, mesh.upU0(index), mesh.upV0(index));
        }
        if ((faces & 0b000100) != 0) { // NORTH
            addVertex(vertices, x1, y1, z1, -nz.x, -nz.y, -nz.z, mesh.northU0(index), mesh.northV1(index));
            addVertex(vertices, x0, y0, z0, -nz.x, -nz.y, -nz.z, mesh.northU1(index), mesh.northV1(index));
            addVertex(vertices, x3, y3, z3, -nz.x, -nz.y, -nz.z, mesh.northU1(index), mesh.northV0(index));
            addVertex(vertices, x2, y2, z2, -nz.x, -nz.y, -nz.z, mesh.northU0(index), mesh.northV0(index));
        }
        if ((faces & 0b001000) != 0) { // SOUTH
            addVertex(vertices, x4, y4, z4, nz.x, nz.y, nz.z, mesh.southU0(index), mesh.southV1(index));
            addVertex(vertices, x5, y5, z5, nz.x, nz.y, nz.z, mesh.southU1(index), mesh.southV1(index));
            addVertex(vertices, x6, y6, z6, nz.x, nz.y, nz.z, mesh.southU1(index), mesh.southV0(index));
            addVertex(vertices, x7, y7, z7, nz.x, nz.y, nz.z, mesh.southU0(index), mesh.southV0(index));
        }
        if ((faces & 0b010000) != 0) { // WEST
            addVertex(vertices, x0, y0, z0, -nx.x, -nx.y, -nx.z, mesh.westU0(index), mesh.westV1(index));
            addVertex(vertices, x4, y4, z4, -nx.x, -nx.y, -nx.z, mesh.westU1(index), mesh.westV1(index));
            addVertex(vertices, x7, y7, z7, -nx.x, -nx.y, -nx.z, mesh.westU1(index), mesh.westV0(index));
            addVertex(vertices, x3, y3, z3, -nx.x, -nx.y, -nx.z, mesh.westU0(index), mesh.westV0(index));
        }
        if ((faces & 0b100000) != 0) { // EAST
            addVertex(vertices, x5, y5, z5, nx.x, nx.y, nx.z, mesh.eastU0(index), mesh.eastV1(index));
            addVertex(vertices, x1, y1, z1, nx.x, nx.y, nx.z, mesh.eastU1(index), mesh.eastV1(index));
            addVertex(vertices, x2, y2, z2, nx.x, nx.y, nx.z, mesh.eastU1(index), mesh.eastV0(index));
            addVertex(vertices, x6, y6, z6, nx.x, nx.y, nx.z, mesh.eastU0(index), mesh.eastV0(index));
        }
    }

    private static void addVertex(FloatList vertices, float x, float y, float z, Vector3f normal, float u, float v) {
        addVertex(vertices, x, y, z, normal.x, normal.y, normal.z, u, v);
    }

    private static void addVertex(
            FloatList vertices, float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(u);
        vertices.add(v);
    }

    /**
     * 与 {@code RenderUtil.fixInvertedFlatCube} 的判定一致：某个轴上的尺寸为零时，
     * 转换后的法线在该轴上的分量要取反
     *
     * <p>尺寸在 {@code GeoMesh} 里被夹到 0.001，所以用长度判定退化轴。
     */
    private static int flatFlags(GeoMesh mesh, int index) {
        float x = mesh.dx(index).length();
        float y = mesh.dy(index).length();
        float z = mesh.dz(index).length();
        boolean xFlat = x <= DEGENERATE;
        boolean yFlat = y <= DEGENERATE;
        boolean zFlat = z <= DEGENERATE;
        int flags = 0;
        if (yFlat || zFlat) {
            flags |= 1;
        }
        if (xFlat || zFlat) {
            flags |= 2;
        }
        if (xFlat || yFlat) {
            flags |= 4;
        }
        return flags;
    }

    public GeoModel model() {
        return this.model;
    }

    public long handle() {
        return this.cleanup.handle();
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    /** @return 该立方体的 piece 编号，模型里没有时返回 -1 */
    public int id(GeoMesh mesh, int cubeIndex) {
        int[] pieceIds = this.pieces.get(mesh);
        if (pieceIds == null || cubeIndex < 0 || cubeIndex >= pieceIds.length) {
            return -1;
        }
        return pieceIds[cubeIndex];
    }

    public int offset(int pieceId) {
        return this.offsets[pieceId];
    }

    public int count(int pieceId) {
        return this.counts[pieceId];
    }

    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.cleanup.run();
    }

    /** 只持有 native 句柄，不引用 NativeGeoMesh 本身，否则 Cleaner 永远等不到不可达 */
    private static final class Cleanup implements Runnable {
        private volatile long handle;

        private Cleanup(long handle) {
            this.handle = handle;
        }

        private long handle() {
            return this.handle;
        }

        @Override
        public void run() {
            long current;
            synchronized (this) {
                current = this.handle;
                this.handle = 0L;
            }
            if (current != 0L) {
                NativeMesh.destroyMesh(current);
            }
        }
    }

    private static final class FloatList {
        private float[] data = new float[4096];
        private int size;

        private void add(float value) {
            if (this.size == this.data.length) {
                float[] grown = new float[this.data.length * 2];
                System.arraycopy(this.data, 0, grown, 0, this.size);
                this.data = grown;
            }
            this.data[this.size++] = value;
        }

        private float[] toArray() {
            float[] result = new float[this.size];
            System.arraycopy(this.data, 0, result, 0, this.size);
            return result;
        }
    }

    private static final class IntList {
        private int[] data = new int[512];
        private int size;

        private void add(int value) {
            if (this.size == this.data.length) {
                int[] grown = new int[this.data.length * 2];
                System.arraycopy(this.data, 0, grown, 0, this.size);
                this.data = grown;
            }
            this.data[this.size++] = value;
        }

        private int[] toArray() {
            int[] result = new int[this.size];
            System.arraycopy(this.data, 0, result, 0, this.size);
            return result;
        }
    }
}
