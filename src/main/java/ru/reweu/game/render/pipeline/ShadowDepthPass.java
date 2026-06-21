package ru.reweu.game.render.pipeline;

import static ru.reweu.game.render.ShaderRender.renderWorldMeshesDepth;
import static ru.reweu.game.render.ShaderRender.sortMeshesByMaterialState;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.render.DirectionalShadowMap;
import ru.reweu.game.render.ShaderProgram;

/**
 * Один проход: запись каскадных карт глубины для направленного света (только ответственность depth pass).
 */
public final class ShadowDepthPass {

    private final List<Mesh[]> landscapeMeshes;
    private final List<Mesh[]> propMeshes;
    private final List<Vector3f> propWorldPositions;
    private final List<GltfScene> gltfScenes;
    private final List<Vector3f> gltfWorldPositions;
    private final List<Float> gltfScales;
    private final ShaderProgram meshDepthShader;
    private final ShaderProgram gltfDepthShader;
    private final int gltfInstanceCount;

    private final Matrix4f tmpLandscapeModel = new Matrix4f();
    private final Matrix4f tmpPropModel = new Matrix4f();
    private final Matrix4f tmpShadowRoot = new Matrix4f();

    public ShadowDepthPass(
        List<Mesh[]> landscapeMeshes,
        List<Mesh[]> propMeshes,
        List<Vector3f> propWorldPositions,
        List<GltfScene> gltfScenes,
        List<Vector3f> gltfWorldPositions,
        List<Float> gltfScales,
        ShaderProgram meshDepthShader,
        ShaderProgram gltfDepthShader
    ) {
        this.landscapeMeshes = landscapeMeshes;
        this.propMeshes = propMeshes;
        this.propWorldPositions = propWorldPositions;
        this.gltfScenes = gltfScenes;
        this.gltfWorldPositions = gltfWorldPositions;
        this.gltfScales = gltfScales;
        this.meshDepthShader = meshDepthShader;
        this.gltfDepthShader = gltfDepthShader;
        this.gltfInstanceCount = Math.min(
            this.gltfScenes.size(),
            Math.min(this.gltfWorldPositions.size(), this.gltfScales.size())
        );
    }

    /** Сортировка групп мешей перед циклом каскадов (меньше работы, чем на каскад). */
    public void sortMeshGroupsForShadowMaps(RuntimeGraphicsSettings rs) {
        if (rs.isDrawLandscape()) {
            for (Mesh[] meshGroup : landscapeMeshes) {
                if (meshGroup.length > 1) {
                    sortMeshesByMaterialState(meshGroup);
                }
            }
        }
        if (rs.isShadowsEnabled() && rs.isDrawProps()) {
            for (Mesh[] meshGroup : propMeshes) {
                if (meshGroup.length > 1) {
                    sortMeshesByMaterialState(meshGroup);
                }
            }
        }
    }

    public void renderCascades(DirectionalShadowMap sm, RuntimeGraphicsSettings rs) {
        for (int cascade = 0; cascade < sm.getCascadeCount(); cascade++) {
            sm.bindCascadeForWriting(cascade);
            Matrix4f lightSpace = sm.getLightSpaceMatrix(cascade);
            meshDepthShader.use();
            meshDepthShader.setUniform("lightSpaceMatrix", lightSpace);
            if (rs.isDrawLandscape()) {
                for (Mesh[] meshGroup : landscapeMeshes) {
                    if (meshGroup.length == 0) {
                        continue;
                    }
                    float scale = meshGroup[0].getScale();
                    tmpLandscapeModel.identity()
                        .translate(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f)
                        .scale(scale, scale * GameConfig.LANDSCAPE_Y_SCALE, scale);
                    renderWorldMeshesDepth(meshDepthShader, meshGroup, tmpLandscapeModel, lightSpace);
                }
            }
            if (rs.isDrawProps()) {
                for (int pi = 0; pi < propMeshes.size(); pi++) {
                    Mesh[] meshGroup = propMeshes.get(pi);
                    if (meshGroup.length == 0) {
                        continue;
                    }
                    float scale = meshGroup[0].getScale();
                    tmpPropModel.identity().translate(propWorldPositions.get(pi)).scale(scale);
                    renderWorldMeshesDepth(meshDepthShader, meshGroup, tmpPropModel, lightSpace);
                }
            }
            if (rs.isDrawGltfCars()) {
                for (int gi = 0; gi < gltfInstanceCount; gi++) {
                    GltfScene scene = gltfScenes.get(gi);
                    if (scene == null) {
                        continue;
                    }
                    Vector3f pos = gltfWorldPositions.get(gi);
                    float sc = gltfScales.get(gi);
                    tmpShadowRoot.identity().translate(pos).scale(sc);
                    scene.renderShadowDepth(gltfDepthShader, tmpShadowRoot, lightSpace);
                }
            }
        }
        sm.endWrite();
    }
}
