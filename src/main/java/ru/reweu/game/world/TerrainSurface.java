package ru.reweu.game.world;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.reweu.game.loader.Mesh;

/**
 * Высота ландшафта под (x, z): треугольники в XZ + плоскость (не зависит от порядка вершин, в отличие от MT-луча).
 */
public final class TerrainSurface {

    private static final float EPS = 1e-5f;
    /** Почти вертикальные грани в XZ пропускаем — для них нельзя однозначно взять y по (x,z) */
    private static final float MIN_NY = 1e-3f;

    private static final class Tri {
        final Vector3f v0;
        final Vector3f v1;
        final Vector3f v2;
        final float minX;
        final float maxX;
        final float minZ;
        final float maxZ;

        Tri(Vector3f v0, Vector3f v1, Vector3f v2) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            minX = Math.min(v0.x, Math.min(v1.x, v2.x));
            maxX = Math.max(v0.x, Math.max(v1.x, v2.x));
            minZ = Math.min(v0.z, Math.min(v1.z, v2.z));
            maxZ = Math.max(v0.z, Math.max(v1.z, v2.z));
        }
    }

    private final List<Tri> triangles;

    private TerrainSurface(List<Tri> triangles) {
        this.triangles = triangles;
    }

    /**
     * Горизонтальная плоскость Y = {@code y} на большой площади — для камеры/пропа, когда ландшафт не грузится.
     */
    public static TerrainSurface flatPlane(float y) {
        float L = 1.0e6f;
        Vector3f v0 = new Vector3f(-L, y, -L);
        Vector3f v1 = new Vector3f(L, y, -L);
        Vector3f v2 = new Vector3f(-L, y, L);
        Vector3f v3 = new Vector3f(L, y, L);
        List<Tri> list = new ArrayList<>();
        list.add(new Tri(v0, v1, v2));
        list.add(new Tri(v1, v3, v2));
        return new TerrainSurface(list);
    }

    public static TerrainSurface fromMeshes(Mesh[] meshes, Vector3f translation) {
        List<Tri> list = new ArrayList<>();
        Vector4f tmp = new Vector4f();
        for (Mesh mesh : meshes) {
            Matrix4f model = new Matrix4f().translate(translation).scale(mesh.getScale());
            List<Vector3f> verts = mesh.getCollisionVertices();
            List<Integer> idx = mesh.getCollisionIndices();
            for (int i = 0; i < idx.size(); i += 3) {
                Vector3f w0 = transform(model, verts.get(idx.get(i)), tmp);
                Vector3f w1 = transform(model, verts.get(idx.get(i + 1)), tmp);
                Vector3f w2 = transform(model, verts.get(idx.get(i + 2)), tmp);
                list.add(new Tri(w0, w1, w2));
            }
        }
        return new TerrainSurface(list);
    }

    private static Vector3f transform(Matrix4f m, Vector3f v, Vector4f tmp) {
        tmp.set(v.x, v.y, v.z, 1f);
        m.transform(tmp);
        return new Vector3f(tmp.x, tmp.y, tmp.z);
    }

    /**
     * Высота поверхности под точкой (x, z). NaN, если под колонкой (x,z) нет «горизонтальной» грани.
     */
    public float heightAt(float x, float z) {
        float bestY = Float.NEGATIVE_INFINITY;
        for (Tri tri : triangles) {
            if (x < tri.minX - EPS || x > tri.maxX + EPS || z < tri.minZ - EPS || z > tri.maxZ + EPS) {
                continue;
            }
            Vector3f e1 = new Vector3f(tri.v1).sub(tri.v0);
            Vector3f e2 = new Vector3f(tri.v2).sub(tri.v0);
            Vector3f n = e1.cross(e2);
            float len = n.length();
            if (len < 1e-10f) {
                continue;
            }
            // Площадь проекции на XZ (удвоенная): |e1 × e2| по y в смысле 2D cross в xz
            float areaXz = Math.abs(e1.x * e2.z - e1.z * e2.x);
            if (areaXz < 1e-10f) {
                continue;
            }
            if (!pointInTriangleXZ(x, z, tri.v0, tri.v1, tri.v2)) {
                continue;
            }
            float ny = n.y;
            if (Math.abs(ny) < MIN_NY) {
                continue;
            }
            float nx = n.x;
            float nz = n.z;
            float d = nx * tri.v0.x + ny * tri.v0.y + nz * tri.v0.z;
            float y = (d - nx * x - nz * z) / ny;
            if (y > bestY) {
                bestY = y;
            }
        }
        if (bestY == Float.NEGATIVE_INFINITY) {
            return Float.NaN;
        }
        return bestY;
    }

    /** Точка внутри треугольника в плоскости XZ (одинаковый знак с тремя рёбрами). */
    private static boolean pointInTriangleXZ(float x, float z, Vector3f v0, Vector3f v1, Vector3f v2) {
        float ax = v0.x;
        float az = v0.z;
        float bx = v1.x;
        float bz = v1.z;
        float cx = v2.x;
        float cz = v2.z;
        float c1 = (bx - ax) * (z - az) - (bz - az) * (x - ax);
        float c2 = (cx - bx) * (z - bz) - (cz - bz) * (x - bx);
        float c3 = (ax - cx) * (z - cz) - (az - cz) * (x - cx);
        boolean neg = (c1 < -EPS) || (c2 < -EPS) || (c3 < -EPS);
        boolean pos = (c1 > EPS) || (c2 > EPS) || (c3 > EPS);
        return !(neg && pos);
    }
}
