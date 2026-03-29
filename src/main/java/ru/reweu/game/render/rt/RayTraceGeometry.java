package ru.reweu.game.render.rt;

import de.javagl.jgltf.model.v2.MaterialModelV2;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;

/**
 * Сборка треугольников в SSBO для compute-трассировки (упрощённый альбедо, без текстур).
 */
public final class RayTraceGeometry {

    private static final Vector3f LANDSCAPE_ALBEDO = new Vector3f(0.42f, 0.52f, 0.28f);
    private static final Vector3f PROP_ALBEDO = new Vector3f(0.55f, 0.15f, 0.12f);

    private RayTraceGeometry() {
    }

    /**
     * @return число треугольников, записанных в {@code dst} (16 float на треугольник)
     */
    public static int build(
        List<Mesh[]> landscapeMeshes,
        Matrix4f landscapeModel,
        List<GltfScene> gltfScenes,
        List<Matrix4f> gltfRoots,
        List<Mesh[]> propMeshes,
        Matrix4f propModel,
        int maxTriangles,
        FloatBuffer dst
    ) {
        int n = 0;
        n += appendMeshGroup(landscapeMeshes, landscapeModel, LANDSCAPE_ALBEDO, maxTriangles, dst);
        if (n >= maxTriangles) {
            return n;
        }
        boolean useGltf = gltfScenes != null && gltfRoots != null
            && !gltfScenes.isEmpty()
            && gltfScenes.size() == gltfRoots.size();
        if (useGltf) {
            for (int i = 0; i < gltfScenes.size(); i++) {
                GltfScene scene = gltfScenes.get(i);
                if (scene == null) {
                    continue;
                }
                n += appendGltf(scene, gltfRoots.get(i), maxTriangles - n, dst);
                if (n >= maxTriangles) {
                    return n;
                }
            }
        } else if (propMeshes != null && !propMeshes.isEmpty()) {
            n += appendMeshGroup(propMeshes, propModel, PROP_ALBEDO, maxTriangles - n, dst);
        }
        return n;
    }

    private static int appendMeshGroup(
        List<Mesh[]> groups,
        Matrix4f model,
        Vector3f defaultAlbedo,
        int room,
        FloatBuffer dst
    ) {
        int added = 0;
        if (groups == null) {
            return 0;
        }
        for (Mesh[] arr : groups) {
            if (arr == null) {
                continue;
            }
            for (Mesh m : arr) {
                if (added >= room) {
                    return added;
                }
                added += appendMesh(m, model, defaultAlbedo, room - added, dst);
            }
        }
        return added;
    }

    private static int appendMesh(Mesh mesh, Matrix4f model, Vector3f defaultAlbedo, int room, FloatBuffer dst) {
        List<Vector3f> verts = mesh.getCollisionVertices();
        List<Integer> idx = mesh.getCollisionIndices();
        if (verts == null || idx == null || idx.size() < 3) {
            return 0;
        }
        Vector3f alb = mesh.hasDiffuseTexture() ? new Vector3f(defaultAlbedo) : new Vector3f(mesh.getMaterialColor());
        if (alb.lengthSquared() < 1e-8f) {
            alb.set(defaultAlbedo);
        }
        int count = 0;
        Matrix4f m = model;
        for (int i = 0; i + 2 < idx.size(); i += 3) {
            if (count >= room) {
                break;
            }
            Vector3f a = transform(m, verts.get(idx.get(i)));
            Vector3f b = transform(m, verts.get(idx.get(i + 1)));
            Vector3f c = transform(m, verts.get(idx.get(i + 2)));
            putTri(dst, a, b, c, alb);
            count++;
        }
        return count;
    }

    private static Vector3f transform(Matrix4f model, Vector3f v) {
        Vector4f p = new Vector4f(v.x, v.y, v.z, 1f);
        model.transform(p);
        return new Vector3f(p.x, p.y, p.z);
    }

    private static void putTri(FloatBuffer dst, Vector3f a, Vector3f b, Vector3f c, Vector3f albedo) {
        dst.put(a.x).put(a.y).put(a.z).put(1f);
        dst.put(b.x).put(b.y).put(b.z).put(1f);
        dst.put(c.x).put(c.y).put(c.z).put(1f);
        dst.put(albedo.x).put(albedo.y).put(albedo.z).put(0f);
    }

    private static int appendGltf(GltfScene scene, Matrix4f root, int room, FloatBuffer dst) {
        float[] ng = new float[16];
        int added = 0;
        for (GltfScene.GltfDrawableInstance inst : scene.getInstances()) {
            if (added >= room) {
                break;
            }
            if (inst.mesh.material.getAlphaMode() == MaterialModelV2.AlphaMode.BLEND) {
                continue;
            }
            float[] pos = inst.mesh.cpuPositions;
            int[] ind = inst.mesh.cpuIndices;
            if (pos == null || ind == null) {
                continue;
            }
            inst.node.computeGlobalTransform(ng);
            Matrix4f world = new Matrix4f().set(ng);
            Matrix4f modelMat = new Matrix4f(root).mul(world);
            float[] bcf = inst.mesh.material.getBaseColorFactor();
            Vector3f alb = new Vector3f(0.75f, 0.75f, 0.75f);
            if (bcf != null && bcf.length >= 3) {
                alb.set(bcf[0], bcf[1], bcf[2]);
            }
            for (int i = 0; i + 2 < ind.length; i += 3) {
                if (added >= room) {
                    return added;
                }
                int i0 = ind[i] * 3;
                int i1 = ind[i + 1] * 3;
                int i2 = ind[i + 2] * 3;
                Vector3f a = transform(modelMat, new Vector3f(pos[i0], pos[i0 + 1], pos[i0 + 2]));
                Vector3f b = transform(modelMat, new Vector3f(pos[i1], pos[i1 + 1], pos[i1 + 2]));
                Vector3f c = transform(modelMat, new Vector3f(pos[i2], pos[i2 + 1], pos[i2 + 2]));
                putTri(dst, a, b, c, alb);
                added++;
            }
        }
        return added;
    }

    public static Matrix4f landscapeModel(List<Mesh[]> landscape) {
        Matrix4f m = new Matrix4f();
        if (landscape != null && !landscape.isEmpty() && landscape.get(0).length > 0) {
            float sc = landscape.get(0)[0].getScale();
            m.translate(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f).scale(sc);
        }
        return m;
    }

    public static Matrix4f gltfRootModel(Vector3f worldPosition, float scale) {
        return new Matrix4f()
            .translate(worldPosition)
            .scale(scale);
    }

    /**
     * Корень для одной glTF-машины (масштаб как у Mustang по умолчанию).
     */
    public static Matrix4f gltfRootModel(Vector3f propPosition) {
        return gltfRootModel(propPosition, GameConfig.FORD_MUSTANG_MODEL_SCALE);
    }

    public static List<Matrix4f> gltfRootModels(List<Vector3f> positions, List<Float> scales) {
        int n = Math.min(positions.size(), scales.size());
        List<Matrix4f> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(gltfRootModel(positions.get(i), scales.get(i)));
        }
        return out;
    }
}
