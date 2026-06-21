package ru.reweu.game;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import ru.reweu.game.gltf.GltfPbrRenderer;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.loader.ModelLoader;
import ru.reweu.game.loader.ModelLoaderReport;
import ru.reweu.game.loader.ResourceLoader;
import ru.reweu.game.render.RenderErrorLog;
import ru.reweu.game.render.ShaderProgram;
import ru.reweu.game.world.TerrainSurface;

/**
 * Загрузка мира: ландшафт, машины, рельеф и их позиции.
 */
public final class GameSceneAssets implements AutoCloseable {

    public final List<Mesh[]> landscape = new ArrayList<>();
    public final List<Mesh[]> propMeshes = new ArrayList<>();
    public final List<Vector3f> propInstancePositions = new ArrayList<>();
    public final Vector3f mustangPosition = new Vector3f();
    public final Vector3f toyotaPosition = new Vector3f();
    public final List<GltfScene> gltfScenes = new ArrayList<>();
    public final List<Vector3f> gltfWorldPositions = new ArrayList<>();
    public final List<Float> gltfScales = new ArrayList<>();
    public final TerrainSurface terrain;
    public ShaderProgram gltfShaderProgram;

    private GameSceneAssets(TerrainSurface terrain) {
        this.terrain = terrain;
    }

    public static GameSceneAssets load() {
        List<Mesh[]> landscape = new ArrayList<>();
        List<Mesh[]> propMeshes = new ArrayList<>();
        List<Vector3f> propInstancePositions = new ArrayList<>();
        Vector3f mustangPosition = new Vector3f();
        List<GltfScene> gltfScenes = new ArrayList<>();
        ShaderProgram gltfShaderProgram = null;

        if (GameConfig.DRAW_LANDSCAPE) {
            landscape.add(ModelLoader.loadModel("/models/landscape/landscape.obj", 1f));
        }
        if (GameConfig.isDumpAssimpReportOnStart()) {
            ModelLoaderReport.dumpAssimpSceneReport(GameConfig.FORD_MUSTANG_1965_GLB);
        }

        if (GameConfig.USE_GLTF_NATIVE_LOADER) {
            gltfShaderProgram = new ShaderProgram("/shaders/pbr_gltf.vert", "/shaders/pbr_gltf.frag");
            GltfPbrRenderer.initJointBlock(gltfShaderProgram);
            try {
                Path mustangPath = ResourceLoader.loadResourceAsFile(GameConfig.FORD_MUSTANG_1965_GLB).toPath();
                gltfScenes.add(GltfScene.load(mustangPath));
                Path toyotaPath = ResourceLoader.loadResourceAsFile(GameConfig.TOYOTA_AE86_GLB).toPath();
                gltfScenes.add(GltfScene.load(toyotaPath));
            } catch (Exception e) {
                RenderErrorLog.warn("Failed to load glTF cars (Mustang, AE86)", e);
                throw new RuntimeException("Failed to load glTF cars", e);
            }
        } else {
            propMeshes.add(
                ModelLoader.loadModel(GameConfig.FORD_MUSTANG_1965_GLB, GameConfig.FORD_MUSTANG_MODEL_SCALE));
            propInstancePositions.add(mustangPosition);
            if (GameConfig.isDumpAssimpReportOnStart()) {
                ModelLoaderReport.dumpLoadedMeshesSummary(propMeshes.get(propMeshes.size() - 1));
            }
        }

        TerrainSurface terrainSurface;
        if (GameConfig.DRAW_LANDSCAPE && !landscape.isEmpty()) {
            terrainSurface = TerrainSurface.fromMeshes(
                landscape.get(0), new Vector3f(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f));
        } else {
            terrainSurface = TerrainSurface.flatPlane(0f);
        }

        GameSceneAssets assets = new GameSceneAssets(terrainSurface);
        assets.landscape.addAll(landscape);
        assets.propMeshes.addAll(propMeshes);
        assets.propInstancePositions.addAll(propInstancePositions);
        assets.mustangPosition.set(mustangPosition);
        assets.gltfScenes.addAll(gltfScenes);
        assets.gltfShaderProgram = gltfShaderProgram;
        assets.placeCarsOnTerrain();
        return assets;
    }

    private void placeCarsOnTerrain() {
        terrain.placeOnSurface(
            mustangPosition,
            GameConfig.FORD_MUSTANG_WORLD_X,
            GameConfig.FORD_MUSTANG_WORLD_Z,
            GameConfig.FORD_MUSTANG_ABOVE_TERRAIN,
            1f
        );

        gltfWorldPositions.clear();
        gltfScales.clear();
        if (gltfScenes.isEmpty()) {
            return;
        }

        terrain.placeOnSurface(
            toyotaPosition,
            GameConfig.TOYOTA_AE86_WORLD_X,
            GameConfig.TOYOTA_AE86_WORLD_Z,
            GameConfig.TOYOTA_AE86_ABOVE_TERRAIN,
            1f
        );

        gltfWorldPositions.add(mustangPosition);
        float baseScale = GameConfig.FORD_MUSTANG_MODEL_SCALE;
        gltfScales.add(baseScale);
        gltfWorldPositions.add(toyotaPosition);

        float ex0 = GltfScene.boundingMaxEdgeLength(gltfScenes.get(0));
        for (int i = 1; i < gltfScenes.size(); i++) {
            float ex = GltfScene.boundingMaxEdgeLength(gltfScenes.get(i));
            gltfScales.add(ex > 1e-8f ? baseScale * (ex0 / ex) : baseScale);
        }

        int n = gltfScenes.size();
        if (gltfWorldPositions.size() != n || gltfScales.size() != n) {
            throw new IllegalStateException(
                "gltfScenes size " + n + " must match gltfWorldPositions (" + gltfWorldPositions.size()
                    + ") and gltfScales (" + gltfScales.size() + ")");
        }
    }

    public void syncCarHeightsFromTerrain() {
        if (gltfScenes.isEmpty()) {
            return;
        }
        terrain.applyHeights(heightSyncPositions, heightSyncOffsets, heightSyncPositions.length);
    }

    /** Машины + камера: один проход по рельефу за кадр. */
    public void syncWorldHeights(Vector3f cameraPosition, Camera camera) {
        if (!gltfScenes.isEmpty()) {
            terrain.applyHeights(heightSyncPositions, heightSyncOffsets, heightSyncPositions.length);
        }
        terrain.applyHeight(cameraPosition, GameConfig.CAMERA_EYE_HEIGHT);
        camera.invalidateView();
    }

    private final Vector3f[] heightSyncPositions = new Vector3f[2];
    private final float[] heightSyncOffsets = {
        GameConfig.FORD_MUSTANG_ABOVE_TERRAIN,
        GameConfig.TOYOTA_AE86_ABOVE_TERRAIN
    };

    {
        heightSyncPositions[0] = mustangPosition;
        heightSyncPositions[1] = toyotaPosition;
    }

    @Override
    public void close() {
        for (GltfScene scene : gltfScenes) {
            if (scene != null) {
                scene.cleanup();
            }
        }
        gltfScenes.clear();
        if (gltfShaderProgram != null) {
            gltfShaderProgram.cleanup();
            gltfShaderProgram = null;
        }
    }
}
