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
    private static final int GRID_MIN_RESOLUTION = 4;
    private static final int GRID_MAX_RESOLUTION = 128;
    private static final int GRID_TARGET_TRIS_PER_CELL = 8;

    private static final class Tri {
        final Vector3f v0;
        final Vector3f v1;
        final Vector3f v2;
        final float minX;
        final float maxX;
        final float minZ;
        final float maxZ;
        final float nx;
        final float ny;
        final float nz;
        final float planeD;
        final boolean walkable;

        Tri(Vector3f v0, Vector3f v1, Vector3f v2) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            minX = Math.min(v0.x, Math.min(v1.x, v2.x));
            maxX = Math.max(v0.x, Math.max(v1.x, v2.x));
            minZ = Math.min(v0.z, Math.min(v1.z, v2.z));
            maxZ = Math.max(v0.z, Math.max(v1.z, v2.z));

            float e1x = v1.x - v0.x;
            float e1y = v1.y - v0.y;
            float e1z = v1.z - v0.z;
            float e2x = v2.x - v0.x;
            float e2y = v2.y - v0.y;
            float e2z = v2.z - v0.z;
            nx = e1y * e2z - e1z * e2y;
            ny = e1z * e2x - e1x * e2z;
            nz = e1x * e2y - e1y * e2x;
            float areaXz = Math.abs(e1x * e2z - e1z * e2x);
            walkable = areaXz >= 1e-10f && Math.abs(ny) >= MIN_NY;
            planeD = walkable ? nx * v0.x + ny * v0.y + nz * v0.z : 0f;
        }

        float sampleHeight(float x, float z) {
            if (!walkable
                || x < minX - EPS || x > maxX + EPS
                || z < minZ - EPS || z > maxZ + EPS
                || !pointInTriangleXZ(x, z, v0, v1, v2)) {
                return Float.NaN;
            }
            return (planeD - nx * x - nz * z) / ny;
        }
    }

    /** Uniform grid по XZ: в ячейку попадают индексы всех треугольников, чей AABB её пересекает. */
    private static final class SpatialGrid {
        final float originX;
        final float originZ;
        final float invCellSize;
        final int cols;
        final int rows;
        final int[][] cells;

        SpatialGrid(float originX, float originZ, float cellSize, int cols, int rows) {
            this.originX = originX;
            this.originZ = originZ;
            this.invCellSize = 1f / cellSize;
            this.cols = cols;
            this.rows = rows;
            this.cells = new int[cols * rows][];
        }

        void insert(int triIndex, Tri tri) {
            int minCol = cellIndexX(tri.minX);
            int maxCol = cellIndexX(tri.maxX);
            int minRow = cellIndexZ(tri.minZ);
            int maxRow = cellIndexZ(tri.maxZ);
            for (int row = minRow; row <= maxRow; row++) {
                for (int col = minCol; col <= maxCol; col++) {
                    int cell = row * cols + col;
                    int[] prev = cells[cell];
                    if (prev == null) {
                        cells[cell] = new int[] {triIndex};
                        continue;
                    }
                    int[] next = new int[prev.length + 1];
                    System.arraycopy(prev, 0, next, 0, prev.length);
                    next[prev.length] = triIndex;
                    cells[cell] = next;
                }
            }
        }

        int[] candidates(float x, float z) {
            int col = cellIndexX(x);
            int row = cellIndexZ(z);
            return cells[row * cols + col];
        }

        private int cellIndexX(float x) {
            int col = (int) Math.floor((x - originX) * invCellSize);
            if (col < 0) {
                return 0;
            }
            if (col >= cols) {
                return cols - 1;
            }
            return col;
        }

        private int cellIndexZ(float z) {
            int row = (int) Math.floor((z - originZ) * invCellSize);
            if (row < 0) {
                return 0;
            }
            if (row >= rows) {
                return rows - 1;
            }
            return row;
        }
    }

    private final Tri[] triangles;
    /** {@code NaN} — не плоскость; иначе константа Y без перебора треугольников. */
    private final float flatY;
    private final SpatialGrid grid;

    private TerrainSurface(Tri[] triangles, float flatY, SpatialGrid grid) {
        this.triangles = triangles;
        this.flatY = flatY;
        this.grid = grid;
    }

    /** Ставит {@code position.y} по высоте рельефа под текущими X/Z. */
    public void applyHeight(Vector3f position, float aboveTerrain) {
        float h = heightAt(position.x, position.z);
        if (Float.isFinite(h)) {
            position.y = h + aboveTerrain;
        }
    }

    /** Несколько объектов за один проход (камера, машины). */
    public void applyHeights(Vector3f[] positions, float[] aboveTerrain, int count) {
        for (int i = 0; i < count; i++) {
            applyHeight(positions[i], aboveTerrain[i]);
        }
    }

    /** Начальная постановка объекта на поверхность по X/Z. */
    public void placeOnSurface(Vector3f position, float x, float z, float aboveTerrain, float fallbackY) {
        float h = heightAt(x, z);
        position.set(x, Float.isFinite(h) ? h + aboveTerrain : fallbackY, z);
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
        Tri[] tris = {
            new Tri(v0, v1, v2),
            new Tri(v1, v3, v2)
        };
        return new TerrainSurface(tris, y, null);
    }

    public static TerrainSurface fromMeshes(Mesh[] meshes, Vector3f translation) {
        List<Tri> list = new ArrayList<>();
        Vector4f tmp = new Vector4f();
        Matrix4f model = new Matrix4f();
        for (Mesh mesh : meshes) {
            model.identity().translate(translation).scale(mesh.getScale());
            List<Vector3f> verts = mesh.getCollisionVertices();
            List<Integer> idx = mesh.getCollisionIndices();
            for (int i = 0; i < idx.size(); i += 3) {
                Vector3f w0 = transform(model, verts.get(idx.get(i)), tmp);
                Vector3f w1 = transform(model, verts.get(idx.get(i + 1)), tmp);
                Vector3f w2 = transform(model, verts.get(idx.get(i + 2)), tmp);
                list.add(new Tri(w0, w1, w2));
            }
        }
        return fromTriangles(list);
    }

    private static TerrainSurface fromTriangles(List<Tri> list) {
        Tri[] tris = list.toArray(Tri[]::new);
        SpatialGrid grid = buildSpatialGrid(tris);
        return new TerrainSurface(tris, Float.NaN, grid);
    }

    private static SpatialGrid buildSpatialGrid(Tri[] tris) {
        if (tris.length < 16) {
            return null;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int walkableCount = 0;
        for (Tri tri : tris) {
            if (!tri.walkable) {
                continue;
            }
            walkableCount++;
            minX = Math.min(minX, tri.minX);
            minZ = Math.min(minZ, tri.minZ);
            maxX = Math.max(maxX, tri.maxX);
            maxZ = Math.max(maxZ, tri.maxZ);
        }
        if (walkableCount == 0) {
            return null;
        }

        float extentX = Math.max(maxX - minX, 1f);
        float extentZ = Math.max(maxZ - minZ, 1f);
        int targetAxis = (int) Math.ceil(Math.sqrt(walkableCount / (float) GRID_TARGET_TRIS_PER_CELL));
        targetAxis = Math.max(GRID_MIN_RESOLUTION, Math.min(GRID_MAX_RESOLUTION, targetAxis));
        float cellSize = Math.max(extentX, extentZ) / targetAxis;
        int cols = Math.max(1, (int) Math.ceil(extentX / cellSize));
        int rows = Math.max(1, (int) Math.ceil(extentZ / cellSize));

        SpatialGrid grid = new SpatialGrid(minX, minZ, cellSize, cols, rows);
        for (int i = 0; i < tris.length; i++) {
            Tri tri = tris[i];
            if (tri.walkable) {
                grid.insert(i, tri);
            }
        }
        return grid;
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
        if (Float.isFinite(flatY)) {
            return flatY;
        }

        float bestY = Float.NEGATIVE_INFINITY;
        if (grid != null) {
            int[] candidates = grid.candidates(x, z);
            if (candidates != null) {
                for (int index : candidates) {
                    float y = triangles[index].sampleHeight(x, z);
                    if (Float.isFinite(y) && y > bestY) {
                        bestY = y;
                    }
                }
            }
        } else {
            for (Tri tri : triangles) {
                float y = tri.sampleHeight(x, z);
                if (Float.isFinite(y) && y > bestY) {
                    bestY = y;
                }
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
