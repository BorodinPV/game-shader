package ru.reweu.game.render.pipeline;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.render.LightingFrame;
import ru.reweu.game.render.LitFrameServices;
import ru.reweu.game.render.ShaderProgram;

/**
 * Несколько экземпляров glTF-машин: анимация и непрозрачный / blend проходы (SRP: только glTF batch).
 */
public final class GltfCarInstancesRenderer {

    private final List<GltfScene> gltfScenes;
    private final List<Vector3f> gltfWorldPositions;
    private final List<Float> gltfScales;
    private final int instanceCount;
    private final Matrix4f tmpGltfRoot = new Matrix4f();

    // Optimization: track if animations were already updated this frame
    private boolean animationsUpdatedThisFrame = false;

    public GltfCarInstancesRenderer(
        List<GltfScene> gltfScenes,
        List<Vector3f> gltfWorldPositions,
        List<Float> gltfScales
    ) {
        this.gltfScenes = gltfScenes;
        this.gltfWorldPositions = gltfWorldPositions;
        this.gltfScales = gltfScales;
        this.instanceCount = Math.min(
            gltfScenes.size(),
            Math.min(gltfWorldPositions.size(), gltfScales.size())
        );
    }

    public boolean hasInstances() {
        return !gltfScenes.isEmpty();
    }

    // Reset animation update flag for next frame
    public void resetFrame() {
        animationsUpdatedThisFrame = false;
    }

    public void updateAnimations(float deltaTime) {
        if (deltaTime <= 0f) {
            return;
        }
        for (GltfScene scene : gltfScenes) {
            if (scene != null) {
                scene.updateAnimation(deltaTime);
            }
        }
    }

    public void renderOpaquePass(
        ShaderProgram gltfShaderProgram,
        LitFrameServices frame,
        LightingFrame lit,
        Matrix4f view,
        Matrix4f projection,
        boolean gltfShadowSample,
        float deltaTime
    ) {
        if (gltfShaderProgram == null || gltfScenes.isEmpty()) {
            return;
        }
        // Only update animations once per frame
        if (!animationsUpdatedThisFrame) {
            updateAnimations(deltaTime);
            animationsUpdatedThisFrame = true;
        }
        frame.prepareFrameFor(lit, gltfShaderProgram, view, projection, gltfShadowSample);
        for (int gi = 0; gi < instanceCount; gi++) {
            GltfScene scene = gltfScenes.get(gi);
            if (scene == null) {
                continue;
            }
            Vector3f pos = gltfWorldPositions.get(gi);
            float sc = gltfScales.get(gi);
            tmpGltfRoot.identity().translate(pos).scale(sc);
            scene.render(gltfShaderProgram, tmpGltfRoot, view, projection, true);
        }
    }

    /**
     * @param rebindPbrUniforms {@code true}, если после прохода мира нужно снова привязать IBL/тени к PBR.
     */
    public void renderBlendPass(
        ShaderProgram gltfShaderProgram,
        LitFrameServices frame,
        LightingFrame lit,
        Matrix4f view,
        Matrix4f projection,
        boolean gltfShadowSample,
        boolean rebindPbrUniforms
    ) {
        if (gltfShaderProgram == null || gltfScenes.isEmpty()) {
            return;
        }
        if (rebindPbrUniforms) {
            frame.prepareFrameFor(lit, gltfShaderProgram, view, projection, gltfShadowSample);
        } else {
            gltfShaderProgram.use();
        }
        int nGltfBlend = instanceCount;
        for (int gi = 0; gi < nGltfBlend; gi++) {
            GltfScene scene = gltfScenes.get(gi);
            if (scene == null) {
                continue;
            }
            Vector3f pos = gltfWorldPositions.get(gi);
            float sc = gltfScales.get(gi);
            tmpGltfRoot.identity().translate(pos).scale(sc);
            scene.render(gltfShaderProgram, tmpGltfRoot, view, projection, false);
        }
    }
}
