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
import static ru.reweu.game.render.ShaderRender.renderWorldMeshesDepth;
import static ru.reweu.game.render.ShaderRender.renderWorldMeshesTransparentSorted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.render.rt.RayTraceRenderer;

/**
 * Ландшафт и дополнительные модели (мир — один шейдер); опционально glTF PBR runtime.
 */
public final class SceneRenderer {

    private final List<Mesh[]> landscapeMeshes;
    private final List<Mesh[]> propMeshes;
    private final Vector3f propWorldPosition;
    private final ShaderProgram gltfShaderProgram;
    private final List<GltfScene> gltfScenes;
    private final List<Vector3f> gltfWorldPositions;
    private final List<Float> gltfScales;
    private final WorldRenderer worldRenderer;
    private final ShaderProgram meshDepthShader;
    private final ShaderProgram gltfDepthShader;
    private final ShaderProgram skyShader;
    private final RayTraceRenderer rayTrace;

    public SceneRenderer(
        List<Mesh[]> landscapeMeshes,
        List<Mesh[]> propMeshes,
        Vector3f propWorldPosition,
        ShaderProgram gltfShaderProgram,
        List<GltfScene> gltfScenes,
        List<Vector3f> gltfWorldPositions,
        List<Float> gltfScales,
        WorldRenderer worldRenderer,
        ShaderProgram meshDepthShader,
        ShaderProgram gltfDepthShader,
        ShaderProgram skyShader,
        RayTraceRenderer rayTrace
    ) {
        this.landscapeMeshes = landscapeMeshes;
        this.propMeshes = propMeshes;
        this.propWorldPosition = propWorldPosition;
        this.gltfShaderProgram = gltfShaderProgram;
        this.gltfScenes = gltfScenes != null ? gltfScenes : Collections.emptyList();
        this.gltfWorldPositions = gltfWorldPositions != null ? gltfWorldPositions : Collections.emptyList();
        this.gltfScales = gltfScales != null ? gltfScales : Collections.emptyList();
        this.worldRenderer = worldRenderer;
        this.meshDepthShader = meshDepthShader;
        this.gltfDepthShader = gltfDepthShader;
        this.skyShader = skyShader;
        this.rayTrace = rayTrace;
    }

    public void renderTransparent(
        Matrix4f view,
        Matrix4f projection,
        Vector3f cameraWorldPosition,
        float deltaTime,
        int framebufferWidth,
        int framebufferHeight
    ) {
        RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
        LightingFrame lit = SceneLighting.frame(rs);
        if (rayTrace != null) {
            rayTrace.render(lit, view, projection, cameraWorldPosition, framebufferWidth, framebufferHeight);
            return;
        }

        DirectionalShadowMap sm = worldRenderer.getShadowMap();
        float shadowCx = GameConfig.shadowOrthoCenterX();
        float shadowCz = GameConfig.shadowOrthoCenterZ();
        int nForShadow = Math.min(gltfScenes.size(), Math.min(gltfWorldPositions.size(), gltfScales.size()));
        if (nForShadow >= 1 && rs.isDrawGltfCars()) {
            float sumX = 0f;
            float sumZ = 0f;
            for (int i = 0; i < nForShadow; i++) {
                Vector3f p = gltfWorldPositions.get(i);
                sumX += p.x;
                sumZ += p.z;
            }
            float inv = 1f / nForShadow;
            shadowCx = GameConfig.shadowOrthoCenterXFromAvg(sumX * inv);
            shadowCz = GameConfig.shadowOrthoCenterZFromAvg(sumZ * inv);
        }
        sm.updateLightMatrices(lit.sunDirection(), shadowCx, shadowCz);
        Matrix4f lightSpace = sm.getLightSpaceMatrix();

        boolean shadowPass = rs.isShadowsEnabled();
        if (shadowPass) {
            sm.bindForWriting();
            meshDepthShader.use();
            meshDepthShader.setUniform("lightSpaceMatrix", lightSpace);
            if (rs.isDrawLandscape()) {
                for (Mesh[] meshGroup : landscapeMeshes) {
                    if (meshGroup.length == 0) {
                        continue;
                    }
                    float scale = meshGroup[0].getScale();
                    Matrix4f model = new Matrix4f()
                        .translate(new Vector3f(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f))
                        .scale(scale);
                    renderWorldMeshesDepth(meshDepthShader, meshGroup, model, lightSpace);
                }
            }
            if (rs.isDrawProps()) {
                for (Mesh[] meshGroup : propMeshes) {
                    if (meshGroup.length == 0) {
                        continue;
                    }
                    float scale = meshGroup[0].getScale();
                    Matrix4f model = new Matrix4f()
                        .translate(propWorldPosition)
                        .scale(scale);
                    renderWorldMeshesDepth(meshDepthShader, meshGroup, model, lightSpace);
                }
            }
            if (rs.isDrawGltfCars()) {
                int nGltf = Math.min(gltfScenes.size(), Math.min(gltfWorldPositions.size(), gltfScales.size()));
                for (int gi = 0; gi < nGltf; gi++) {
                    GltfScene scene = gltfScenes.get(gi);
                    if (scene == null) {
                        continue;
                    }
                    Vector3f pos = gltfWorldPositions.get(gi);
                    float sc = gltfScales.get(gi);
                    Matrix4f root = new Matrix4f().translate(pos).scale(sc);
                    scene.renderShadowDepth(gltfDepthShader, root, lightSpace);
                }
            }
            sm.endWrite();
        }
        glViewport(0, 0, Math.max(1, framebufferWidth), Math.max(1, framebufferHeight));

        boolean worldShadowSample = rs.isShadowsEnabled();
        boolean gltfShadowSample = rs.isShadowsEnabled() && !GameConfig.GLTF_DEBUG_DISABLE_SHADOWS;

        if (rs.isDrawSky() && skyShader != null) {
            SkyRenderer.draw(skyShader, view, projection, worldRenderer.getEnvironmentIbl(), lit);
        }

        worldRenderer.prepareFrame(lit, view, projection, worldShadowSample);

        glDisable(GL_BLEND);
        glDepthMask(true);
        if (rs.isDrawLandscape()) {
            for (Mesh[] meshGroup : landscapeMeshes) {
                if (meshGroup.length == 0) {
                    continue;
                }
                float scale = meshGroup[0].getScale();
                Matrix4f model = new Matrix4f()
                    .translate(new Vector3f(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f))
                    .scale(scale);
                renderWorldMeshes(worldRenderer.getWorldShaderProgram(), meshGroup, model, view, projection, true);
            }
        }

        int nGltf = Math.min(gltfScenes.size(), Math.min(gltfWorldPositions.size(), gltfScales.size()));
        if (gltfShaderProgram != null && !gltfScenes.isEmpty() && rs.isDrawGltfCars()) {
            if (deltaTime > 0f) {
                for (GltfScene scene : gltfScenes) {
                    if (scene != null) {
                        scene.updateAnimation(deltaTime);
                    }
                }
            }
            worldRenderer.prepareFrameFor(
                lit,
                gltfShaderProgram,
                view,
                projection,
                gltfShadowSample
            );
            for (int gi = 0; gi < nGltf; gi++) {
                GltfScene scene = gltfScenes.get(gi);
                if (scene == null) {
                    continue;
                }
                Vector3f pos = gltfWorldPositions.get(gi);
                float sc = gltfScales.get(gi);
                Matrix4f root = new Matrix4f().translate(pos).scale(sc);
                scene.render(gltfShaderProgram, root, view, projection, true);
            }
        }

        if (rs.isDrawProps()) {
        for (Mesh[] meshGroup : propMeshes) {
            if (meshGroup.length == 0) {
                continue;
            }
            float scale = meshGroup[0].getScale();
            Matrix4f model = new Matrix4f()
                .translate(propWorldPosition)
                .scale(scale);

            List<Mesh> overlay = new ArrayList<>();
            List<Mesh> solid = new ArrayList<>();
            for (Mesh m : meshGroup) {
                if (m.isTransparentOverlayPass()) {
                    overlay.add(m);
                } else {
                    solid.add(m);
                }
            }
            Mesh[] solidArr = solid.toArray(new Mesh[0]);
            if (solidArr.length > 0) {
                glDisable(GL_BLEND);
                glDepthMask(true);
                renderWorldMeshes(worldRenderer.getWorldShaderProgram(), solidArr, model, view, projection, true);
            }
            if (!overlay.isEmpty()) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                renderWorldMeshesTransparentSorted(
                    worldRenderer.getWorldShaderProgram(), overlay, model, view, projection, cameraWorldPosition);
                glDepthMask(true);
            }
            glDisable(GL_BLEND);
        }
        }

        if (gltfShaderProgram != null && !gltfScenes.isEmpty() && rs.isDrawGltfCars()) {
            worldRenderer.prepareFrameFor(
                lit,
                gltfShaderProgram,
                view,
                projection,
                gltfShadowSample
            );
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            /* Запись глубины для BLEND: иначе «оболочка» без depth не перекрывает салон; стёкла при этом
               по-прежнему смешиваются по альфе. Многослойное стекло может потребовать сортировки. */
            glDepthMask(true);
            int nGltfBlend = Math.min(gltfScenes.size(), Math.min(gltfWorldPositions.size(), gltfScales.size()));
            for (int gi = 0; gi < nGltfBlend; gi++) {
                GltfScene scene = gltfScenes.get(gi);
                if (scene == null) {
                    continue;
                }
                Vector3f pos = gltfWorldPositions.get(gi);
                float sc = gltfScales.get(gi);
                Matrix4f root = new Matrix4f().translate(pos).scale(sc);
                scene.render(gltfShaderProgram, root, view, projection, false);
            }
            glDepthMask(true);
            glDisable(GL_BLEND);
        }
    }
}
