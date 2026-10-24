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
 * 高塔恶魂烘焙模型的扁平副本
 *
 * <p>每个顶点 8 个 float 位置 法线 UV 每个 cube 记录起点与顶点数
 * cube 的身份用 {@link IdentityHashMap} 保存 因为 {@link GeoCube} 是带数组字段的 record
 * {@code equals} 比较数组引用 不适合作为普通 Map 的键
 *
 * <p>这里的顶点还是 cube 局部坐标 真正的骨骼变换在渲染时由每个 cube 的最终矩阵完成
 */
public final class NativeTowerGhastMesh {
    private final BakedGeoModel model;
    private final long handle;
    private final int[] offsets;
    private final int[] counts;
    private final Map<GeoCube, Integer> cubeIds;
    private final int vertexCount;

    private NativeTowerGhastMesh(BakedGeoModel model, long handle,
                                 int[] offsets, int[] counts,
                                 Map<GeoCube, Integer> cubeIds, int vertexCount) {
        this.model = model;
        this.handle = handle;
        this.offsets = offsets;
        this.counts = counts;
        this.cubeIds = cubeIds;
        this.vertexCount = vertexCount;
    }

    public static NativeTowerGhastMesh build(BakedGeoModel model) {
        FloatList vertexData = new FloatList();
        IntList offsets = new IntList();
        IntList counts = new IntList();
        FloatList sizes = new FloatList();
        Map<GeoCube, Integer> cubeIds = new IdentityHashMap<>();

        for (GeoBone bone : model.topLevelBones()) {
            collectBone(bone, vertexData, offsets, counts, sizes, cubeIds);
        }

        if (offsets.size == 0) {
            throw new IllegalStateException("高塔恶魂模型没有可用的立方体");
        }

        int[] offsetArray = offsets.toArray();
        int[] countArray = counts.toArray();
        float[] sizeArray = sizes.toArray();
        float[] vertexArray = vertexData.toArray();
        long handle = NativeVertex.createMesh(vertexArray, offsetArray, countArray, sizeArray);
        if (handle == 0L) {
            throw new IllegalStateException("native 顶点网格创建失败");
        }

        return new NativeTowerGhastMesh(model, handle, offsetArray, countArray, cubeIds,
            vertexData.size / 8);
    }

    private static void collectBone(GeoBone bone, FloatList vertices, IntList offsets,
                                    IntList counts, FloatList sizes, Map<GeoCube, Integer> cubeIds) {
        for (GeoCube cube : bone.getCubes()) {
            int offset = vertices.size / 8;
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

            int cubeId = offsets.size;
            offsets.add(offset);
            counts.add(vertexCount);
            sizes.add((float) cube.size().x());
            sizes.add((float) cube.size().y());
            sizes.add((float) cube.size().z());
            cubeIds.put(cube, cubeId);
        }

        for (GeoBone child : bone.getChildBones()) {
            collectBone(child, vertices, offsets, counts, sizes, cubeIds);
        }
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
        Integer id = this.cubeIds.get(cube);
        return id == null ? -1 : id;
    }

    public int offset(int cubeId) {
        return this.offsets[cubeId];
    }

    public int count(int cubeId) {
        return this.counts[cubeId];
    }

    public void close() {
        NativeVertex.destroyMesh(this.handle);
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
