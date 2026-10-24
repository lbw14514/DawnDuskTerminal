package com.dawnduskterminal.client;

import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 把 GeckoLib 烘焙模型摊平成 native 内核认识的 piece
 *
 * <p>每个顶点固定 8 个 float 坐标 法线 UV 每条 piece 记录顶点区间与法线取反标志
 * piece 编号用 {@link IdentityHashMap} 以 {@link GeoCube} 的对象身份为键
 * 因为 record 的 {@code equals} 会把内容相同的两个 cube 合并 那样 Map 会串号
 *
 * <p>模型换了就重新 build 旧句柄必须 close
 * 一个渲染器只缓存当前模型的一份摊平结果
 */
public final class NativeGeoMesh {
    private final BakedGeoModel model;
    private final int[] offsets;
    private final int[] counts;
    private final Map<GeoCube, Integer> pieceIds;
    private final int vertexCount;

    private long handle;
    private boolean closed;

    private NativeGeoMesh(BakedGeoModel model, long handle,
                          int[] offsets, int[] counts,
                          Map<GeoCube, Integer> pieceIds, int vertexCount) {
        this.model = model;
        this.handle = handle;
        this.offsets = offsets;
        this.counts = counts;
        this.pieceIds = pieceIds;
        this.vertexCount = vertexCount;
    }

    /**
     * @throws IllegalStateException 模型里没有任何可见立方体
     * @throws NativeMesh.NativeMeshException native 不可用或建网格失败
     */
    public static NativeGeoMesh build(BakedGeoModel model) {
        FloatList vertexData = new FloatList();
        IntList offsets = new IntList();
        IntList counts = new IntList();
        IntList flags = new IntList();
        Map<GeoCube, Integer> pieceIds = new IdentityHashMap<>();

        for (GeoBone bone : model.topLevelBones()) {
            collectBone(bone, vertexData, offsets, counts, flags, pieceIds);
        }

        if (offsets.size == 0) {
            throw new IllegalStateException("GeckoLib 模型没有可渲染的立方体");
        }

        int[] offsetArray = offsets.toArray();
        int[] countArray = counts.toArray();
        int[] flagArray = flags.toArray();
        float[] vertexArray = vertexData.toArray();
        long handle = NativeMesh.createMesh(vertexArray, offsetArray, countArray, flagArray);

        return new NativeGeoMesh(model, handle, offsetArray, countArray, pieceIds,
            vertexData.size / NativeMesh.VERTEX_FLOATS);
    }

    private static void collectBone(GeoBone bone, FloatList vertices, IntList offsets,
                                    IntList counts, IntList flags, Map<GeoCube, Integer> pieceIds) {
        for (GeoCube cube : bone.getCubes()) {
            int offset = vertices.size / NativeMesh.VERTEX_FLOATS;
            int vertexCount = 0;
            Vector3f normal = null;

            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }
                normal = quad.normal();
                for (GeoVertex vertex : quad.vertices()) {
                    Vector3f position = vertex.position();
                    vertices.add(position.x());
                    vertices.add(position.y());
                    vertices.add(position.z());
                    vertices.add(normal.x());
                    vertices.add(normal.y());
                    vertices.add(normal.z());
                    vertices.add(vertex.texU());
                    vertices.add(vertex.texV());
                    vertexCount++;
                }
            }

            if (vertexCount == 0) {
                continue;
            }

            int pieceId = offsets.size;
            offsets.add(offset);
            counts.add(vertexCount);
            flags.add(flatFlags(cube));
            pieceIds.put(cube, pieceId);
        }

        for (GeoBone child : bone.getChildBones()) {
            collectBone(child, vertices, offsets, counts, flags, pieceIds);
        }
    }

    /**
     * 与 GeckoLib {@code RenderUtil.fixInvertedFlatCube} 的判定保持一致
     *
     * <p>三个坐标轴各算一位 只有当某个轴上的尺寸为零时才取反对应法线分量
     */
    private static int flatFlags(GeoCube cube) {
        // 保持与原内核一致的 float 比较 尺寸先收窄到 float 再判零
        float x = (float) cube.size().x();
        float y = (float) cube.size().y();
        float z = (float) cube.size().z();
        int flags = 0;
        if (y == 0.0f || z == 0.0f) {
            flags |= 1;
        }
        if (x == 0.0f || z == 0.0f) {
            flags |= 2;
        }
        if (x == 0.0f || y == 0.0f) {
            flags |= 4;
        }
        return flags;
    }

    public BakedGeoModel model() {
        return this.model;
    }

    public long handle() {
        return this.handle;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    public int id(GeoCube cube) {
        Integer id = this.pieceIds.get(cube);
        return id == null ? -1 : id;
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
        long current = this.handle;
        this.handle = 0L;
        NativeMesh.destroyMesh(current);
    }

    private static final class FloatList {
        private float[] data = new float[1024];
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
        private int[] data = new int[256];
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
