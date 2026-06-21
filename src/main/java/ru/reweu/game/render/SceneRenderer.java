package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static ru.reweu.game.render.ShaderRender.renderWorldMeshes;

import java.util.Collections;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.render.pipeline.GltfCarInstancesRenderer;
import ru.reweu.game.render.pipeline.PropMeshesRasterPass;
import ru.reweu.game.render.pipeline.ShadowDepthPass;
import ru.reweu.game.render.rt.RayTraceRenderer;

/**
 * Оркестрация растрового кадра: тени, небо, мир, glTF, пропы (делегирование по SRP).
 */
public final class SceneRenderer {

    private final List<Mesh[]> landscapeMeshes;
    private final LitFrameServices litFrame;
    private final ShaderProgram gltfShaderProgram;
    private final ShaderProgram skyShader;
    private final RayTraceRenderer rayTrace;
    private final InstancingDemoRenderer instancingDemo;

    private final ShadowDepthPass shadowDepthPass;
    private final GltfCarInstancesRenderer gltfCars;
    private final PropMeshesRasterPass propPass;

    private final Matrix4f tmpLandscapeModel = new Matrix4f();

    // Optimization: cache viewport size to avoid glViewport() every frame
    private int cachedViewportW = -1;
    private int cachedViewportH = -1;

    public SceneRenderer(
        List<Mesh[]> landscapeMeshes,
        List<Mesh[]> propMeshes,
        List<Vector3f> propWorldPositions,
        ShaderProgram gltfShaderProgram,
        List<GltfScene> gltfScenes,
        List<Vector3f> gltfWorldPositions,
        List<Float> gltfScales,
        LitFrameServices litFrame,
        ShaderProgram meshDepthShader,
        ShaderProgram gltfDepthShader,
        ShaderProgram skyShader,
        RayTraceRenderer rayTrace,
        InstancingDemoRenderer instancingDemo
    ) {
        this.landscapeMeshes = landscapeMeshes;
        this.litFrame = litFrame;
        this.gltfShaderProgram = gltfShaderProgram;
        this.skyShader = skyShader;
        this.rayTrace = rayTrace;
        this.instancingDemo = instancingDemo;

        List<GltfScene> scenes = gltfScenes != null ? gltfScenes : Collections.emptyList();
        List<Vector3f> positions = gltfWorldPositions != null ? gltfWorldPositions : Collections.emptyList();
        List<Float> scales = gltfScales != null ? gltfScales : Collections.emptyList();
        List<Vector3f> propPos = propWorldPositions != null ? propWorldPositions : Collections.emptyList();
        if (propMeshes.size() != propPos.size()) {
            throw new IllegalStateException(
                "propMeshes size " + propMeshes.size() + " != propWorldPositions size " + propPos.size());
        }

        this.shadowDepthPass = new ShadowDepthPass(
            landscapeMeshes,
            propMeshes,
            propPos,
            scenes,
            positions,
            scales,
            meshDepthShader,
            gltfDepthShader
        );
        this.gltfCars = new GltfCarInstancesRenderer(scenes, positions, scales);
        this.propPass = new PropMeshesRasterPass(propMeshes, propPos);
    }

    public void renderTransparent(
        LightingFrame lit,
        Matrix4f view,
        Matrix4f projection,
        Vector3f cameraWorldPosition,
        float deltaTime,
        int framebufferWidth,
        int framebufferHeight
    ) {
        RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
        
        // Reset per-frame state
        if (gltfCars != null) {
            gltfCars.resetFrame();
        }
        
        if (rayTrace != null) {
            rayTrace.render(lit, view, projection, cameraWorldPosition, framebufferWidth, framebufferHeight);
            return;
        }

        DirectionalShadowMap sm = litFrame.getShadowMap();
        sm.updateLightMatrices(view, projection, lit.sunDirection(), GameConfig.NEAR_PLANE, GameConfig.FAR_PLANE);

        boolean shadowPass = rs.isShadowsEnabled();
        shadowDepthPass.sortMeshGroupsForShadowMaps(rs);

        if (shadowPass) {
            shadowDepthPass.renderCascades(sm, rs);
        }
        
        // Cache viewport to avoid redundant GL calls
        int vw = Math.max(1, framebufferWidth);
        int vh = Math.max(1, framebufferHeight);
        if (cachedViewportW != vw || cachedViewportH != vh) {
            glViewport(0, 0, vw, vh);
            cachedViewportW = vw;
            cachedViewportH = vh;
        }

        boolean worldShadowSample = rs.isShadowsEnabled();
        boolean gltfShadowSample = rs.isShadowsEnabled() && !GameConfig.GLTF_DEBUG_DISABLE_SHADOWS;

        if (rs.isDrawSky() && skyShader != null) {
            SkyRenderer.draw(skyShader, view, projection, litFrame.getEnvironmentIbl(), lit);
        }

        litFrame.prepareFrame(lit, view, projection, worldShadowSample);

        glDisable(GL_BLEND);
        glDepthMask(true);
        if (rs.isDrawLandscape()) {
            for (Mesh[] meshGroup : landscapeMeshes) {
                if (meshGroup.length == 0) {
                    continue;
                }
                float scale = meshGroup[0].getScale();
                tmpLandscapeModel.identity()
                    .translate(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f)
                    .scale(scale);
                renderWorldMeshes(litFrame.getWorldShaderProgram(), meshGroup, tmpLandscapeModel, view, projection, true);
            }
        }

        if (gltfShaderProgram != null && gltfCars.hasInstances() && rs.isDrawGltfCars()) {
            gltfCars.renderOpaquePass(
                gltfShaderProgram,
                litFrame,
                lit,
                view,
                projection,
                gltfShadowSample,
                deltaTime
            );
        }

        if (rs.isDrawProps()) {
            propPass.render(litFrame.getWorldShaderProgram(), view, projection, cameraWorldPosition);
        }

        boolean propsUsedWorldShader = rs.isDrawProps() && propPass.anyNonEmptyGroup();

        if (gltfShaderProgram != null && gltfCars.hasInstances() && rs.isDrawGltfCars()) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(true);
            gltfCars.renderBlendPass(
                gltfShaderProgram,
                litFrame,
                lit,
                view,
                projection,
                gltfShadowSample,
                propsUsedWorldShader
            );
            glDepthMask(true);
            glDisable(GL_BLEND);
        }

        if (instancingDemo != null) {
            glDisable(GL_BLEND);
            glDepthMask(true);
            instancingDemo.render(view, projection);
        }
    }
}
