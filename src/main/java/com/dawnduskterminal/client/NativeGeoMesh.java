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
 * ?? GeckoLib ?????????
 *
 * <p>???? 8 ? float ?? ?? UV ?? piece ???? ??????????
 * piece ???? {@link IdentityHashMap} ?? ?? {@link GeoCube} ??????? record
 * {@code equals} ?????? ??????? Map ??
 *
 * <p>????????????? ?????????????????? piece ???????
 * ???????????????????????? ??
 */
public final class NativeGeoMesh {
    private final BakedGeoModel model;
    private final long handle;
    private final int[] offsets;
    private final int[] counts;
    private final Map<GeoCube, Integer> pieceIds;
    private final int vertexCount;

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
            throw new IllegalStateException("GeckoLib ??????????");
        }

        int[] offsetArray = offsets.toArray();
        int[] countArray = counts.toArray();
        int[] flagArray = flags.toArray();
        float[] vertexArray = vertexData.toArray();
        long handle = NativeMesh.createMesh(vertexArray, offsetArray, countArray, flagArray);
        if (handle == 0L) {
            throw new IllegalStateException("native ??????");
        }

        return new NativeGeoMesh(model, handle, offsetArray, countArray, pieceIds,
            vertexData.size / 8);
    }

    private static void collectBone(GeoBone bone, FloatList vertices, IntList offsets,
                                    IntList counts, IntList flags, Map<GeoCube, Integer> pieceIds) {
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
     * ? GeckoLib {@code RenderUtil.fixInvertedFlatCube} ???????
     *
     * <p>?????? X Y Z ? ???????????
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
        NativeMesh.destroyMesh(this.handle);
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
