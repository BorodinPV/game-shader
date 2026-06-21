package ru.reweu.game.render;

import org.lwjgl.opengl.GL;
import ru.reweu.game.GameConfig;
import ru.reweu.game.GameSceneAssets;
import ru.reweu.game.gltf.GltfPbrRenderer;
import ru.reweu.game.render.ibl.EnvironmentIbl;
import ru.reweu.game.render.ibl.IblEquirectLoader;
import ru.reweu.game.render.rt.RayTraceRenderer;

/**
 * Шейдеры, IBL, тени и {@link SceneRenderer} — инициализация и освобождение ресурсов.
 */
public final class GameRenderPipeline implements AutoCloseable {

    private final ShaderProgram worldShaderProgram;
    private final ShaderProgram meshDepthShader;
    private final ShaderProgram gltfDepthShader;
    private final ShaderProgram skyShaderProgram;
    private final DirectionalShadowMap shadowMap;
    private final BrdfLutTexture brdfLutTexture;
    private final EnvironmentIbl environmentIbl;
    private final WorldRenderer worldRenderer;
    private final SceneRenderer sceneRenderer;
    private final RayTraceRenderer rayTraceRenderer;
    private final InstancingDemoRenderer instancingDemo;

    private GameRenderPipeline(
        ShaderProgram worldShaderProgram,
        ShaderProgram meshDepthShader,
        ShaderProgram gltfDepthShader,
        ShaderProgram skyShaderProgram,
        DirectionalShadowMap shadowMap,
        BrdfLutTexture brdfLutTexture,
        EnvironmentIbl environmentIbl,
        WorldRenderer worldRenderer,
        SceneRenderer sceneRenderer,
        RayTraceRenderer rayTraceRenderer,
        InstancingDemoRenderer instancingDemo
    ) {
        this.worldShaderProgram = worldShaderProgram;
        this.meshDepthShader = meshDepthShader;
        this.gltfDepthShader = gltfDepthShader;
        this.skyShaderProgram = skyShaderProgram;
        this.shadowMap = shadowMap;
        this.brdfLutTexture = brdfLutTexture;
        this.environmentIbl = environmentIbl;
        this.worldRenderer = worldRenderer;
        this.sceneRenderer = sceneRenderer;
        this.rayTraceRenderer = rayTraceRenderer;
        this.instancingDemo = instancingDemo;
    }

    public static GameRenderPipeline create(GameSceneAssets scene) {
        ShaderProgram worldShaderProgram = new ShaderProgram(
            "/shaders/vertex_shader.glsl",
            "/shaders/fragment_shader.glsl"
        );
        DirectionalShadowMap shadowMap = new DirectionalShadowMap(
            GameConfig.effectiveShadowMapSize(),
            GameConfig.effectiveShadowCascadeCount(),
            GameConfig.SHADOW_CASCADE_LAMBDA,
            GameConfig.FOV_DEGREES,
            GameConfig.NEAR_PLANE,
            GameConfig.FAR_PLANE,
            SceneLighting.frame().sunDirection()
        );
        BrdfLutTexture brdfLutTexture = new BrdfLutTexture();
        int equirectTex = IblEquirectLoader.createEquirectTexture(
            GameConfig.IBL_HDR_EQUIRECT != null
                ? ru.reweu.game.loader.ResourceLoader.tryLoadResourceAsFile(GameConfig.IBL_HDR_EQUIRECT) != null
                    ? ru.reweu.game.loader.ResourceLoader.tryLoadResourceAsFile(GameConfig.IBL_HDR_EQUIRECT).toPath()
                    : null
                : null
        );
        EnvironmentIbl environmentIbl = new EnvironmentIbl(equirectTex);
        ShaderProgram meshDepthShader = new ShaderProgram("/shaders/mesh_depth.vert", "/shaders/mesh_depth.frag");
        ShaderProgram gltfDepthShader = new ShaderProgram("/shaders/pbr_gltf_shadow.vert", "/shaders/mesh_depth.frag");
        GltfPbrRenderer.initJointBlock(gltfDepthShader);
        ShaderProgram skyShaderProgram = new ShaderProgram("/shaders/sky_pass.vert", "/shaders/sky_pass.frag");
        WorldRenderer worldRenderer = new WorldRenderer(worldShaderProgram, shadowMap, brdfLutTexture, environmentIbl);
        worldRenderer.setAppConfig(
            GameConfig.FAR_PLANE,
            new LitFrameUniformCache.ShadowUniformConfig(
                GameConfig.effectiveShadowBiasScaleForProgram(true),
                GameConfig.effectiveShadowBiasScaleForProgram(false),
                GameConfig.effectiveGltfShadowReceiveFloor(),
                GameConfig.effectiveDiagnosticGltfNoIblOcclusion(),
                GameConfig.effectiveShadowPcfUseShadingNormal()
            ),
            false
        );

        InstancingDemoRenderer instancingDemo = null;
        if (GameConfig.effectiveInstancingDemoEnabled()) {
            instancingDemo = new InstancingDemoRenderer();
        }

        RayTraceRenderer rayTraceRenderer = null;
        if (GameConfig.RAY_TRACE_ENABLED) {
            if (!GL.getCapabilities().OpenGL43) {
                RenderErrorLog.warn("Ray tracing: OpenGL 4.3+ required (compute shaders).");
            } else {
                try {
                    rayTraceRenderer = new RayTraceRenderer(
                        scene.landscape,
                        scene.gltfScenes,
                        scene.gltfWorldPositions,
                        scene.gltfScales,
                        scene.propMeshes,
                        scene.propInstancePositions
                    );
                } catch (Exception e) {
                    RenderErrorLog.warn("Ray trace init failed", e);
                }
            }
        }

        SceneRenderer sceneRenderer = new SceneRenderer(
            scene.landscape,
            scene.propMeshes,
            scene.propInstancePositions,
            scene.gltfShaderProgram,
            scene.gltfScenes,
            scene.gltfWorldPositions,
            scene.gltfScales,
            worldRenderer,
            meshDepthShader,
            gltfDepthShader,
            skyShaderProgram,
            rayTraceRenderer,
            instancingDemo
        );

        return new GameRenderPipeline(
            worldShaderProgram,
            meshDepthShader,
            gltfDepthShader,
            skyShaderProgram,
            shadowMap,
            brdfLutTexture,
            environmentIbl,
            worldRenderer,
            sceneRenderer,
            rayTraceRenderer,
            instancingDemo
        );
    }

    public SceneRenderer sceneRenderer() {
        return sceneRenderer;
    }

    @Override
    public void close() {
        if (meshDepthShader != null) {
            meshDepthShader.cleanup();
        }
        if (gltfDepthShader != null) {
            gltfDepthShader.cleanup();
        }
        if (skyShaderProgram != null) {
            skyShaderProgram.cleanup();
        }
        if (shadowMap != null) {
            shadowMap.cleanup();
        }
        if (brdfLutTexture != null) {
            brdfLutTexture.cleanup();
        }
        if (environmentIbl != null) {
            environmentIbl.cleanup();
        }
        if (rayTraceRenderer != null) {
            rayTraceRenderer.cleanup();
        }
        if (instancingDemo != null) {
            instancingDemo.cleanup();
        }
        if (worldShaderProgram != null) {
            worldShaderProgram.cleanup();
        }
    }
}
