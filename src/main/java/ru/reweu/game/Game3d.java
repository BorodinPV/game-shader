package ru.reweu.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.gui.FPSCounter;
import ru.reweu.game.gui.PauseMenu;
import java.nio.file.Path;
import ru.reweu.game.gltf.GltfPbrRenderer;
import ru.reweu.game.gltf.GltfRenderConfig;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.loader.ModelLoader;
import ru.reweu.game.loader.ModelLoaderReport;
import ru.reweu.game.loader.ResourceLoader;
import ru.reweu.game.render.BrdfLutTexture;
import ru.reweu.game.render.DirectionalShadowMap;
import ru.reweu.game.render.InstancingDemoRenderer;
import ru.reweu.game.render.ibl.EnvironmentIbl;
import ru.reweu.game.render.ibl.IblEquirectLoader;
import ru.reweu.game.render.LightingFrame;
import ru.reweu.game.render.LitFrameUniformCache;
import ru.reweu.game.render.RenderErrorLog;
import ru.reweu.game.render.SceneLighting;
import ru.reweu.game.render.SceneRenderer;
import ru.reweu.game.render.ShaderProgram;
import ru.reweu.game.render.SkyRenderer;
import ru.reweu.game.render.WorldRenderer;
import ru.reweu.game.render.rt.RayTraceRenderer;
import ru.reweu.game.world.TerrainSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL13C.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Game3d {

    private long window;
    private ShaderProgram worldShaderProgram;
    private Camera camera;
    private float lastX = 400;
    private float lastY = 300;
    private boolean firstMouse = true;

    private final List<ru.reweu.game.loader.Mesh[]> landscape = new ArrayList<>();
    private final List<ru.reweu.game.loader.Mesh[]> propMeshes = new ArrayList<>();
    /** Позиции для каждого элемента {@link #propMeshes} (машина Assimp и т.д.). */
    private final List<Vector3f> propInstancePositions = new ArrayList<>();
    /** Позиция Mustang / Assimp-пропа; индекс 0 в {@link #gltfWorldPositions}. */
    private final Vector3f propPosition = new Vector3f();
    /** Позиция AE86; индекс 1. Меняйте X/Z при движении — Y обновляет рельеф. */
    private final Vector3f toyotaWorldPosition = new Vector3f();
    private final List<GltfScene> gltfScenes = new ArrayList<>();
    private final List<Vector3f> gltfWorldPositions = new ArrayList<>();
    private final List<Float> gltfScales = new ArrayList<>();
    private TerrainSurface terrainSurface;
    private WorldRenderer worldRenderer;
    private SceneRenderer sceneRenderer;
    private ShaderProgram gltfShaderProgram;
    private DirectionalShadowMap shadowMap;
    private BrdfLutTexture brdfLutTexture;
    private ShaderProgram meshDepthShader;
    private ShaderProgram gltfDepthShader;
    private EnvironmentIbl environmentIbl;
    private ShaderProgram skyShaderProgram;
    private RayTraceRenderer rayTraceRenderer;
    private InstancingDemoRenderer instancingDemo;

    private float lastFrameTime;
    private float deltaTime;
    private FPSCounter fpsCounter;
    private PauseMenu pauseMenu;
    private boolean menuOpen;
    private boolean escKeyDown;

    private final int[] framebufferWidthArr = new int[1];
    private final int[] framebufferHeightArr = new int[1];

    public void run() {
        System.out.println("Starting LWJGL " + Version.getVersion() + "!");
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (System.getenv("GLFW_FORCE_WAYLAND") == null
            && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        }
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        long primaryMonitor = glfwGetPrimaryMonitor();
        int windowWidth = 1280;
        int windowHeight = 720;
        if (primaryMonitor != NULL) {
            GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);
            if (vidmode != null) {
                windowWidth = vidmode.width();
                windowHeight = vidmode.height();
            }
        }

        glfwDefaultWindowHints();
        if (GameConfig.RAY_TRACE_ENABLED) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        }
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        window = glfwCreateWindow(windowWidth, windowHeight, "Simple Cube", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(GameConfig.effectiveVsync() ? 1 : 0);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_FRAMEBUFFER_SRGB);
        if (GL.getCapabilities().OpenGL32) {
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
        }
        SkyRenderer.init();
        ShaderProgram.addCleanupHook(GltfPbrRenderer::removeUniformCacheForProgram);
        Mesh.setLandscapeTextureScale(GameConfig.LANDSCAPE_TEXTURE_SCALE);
        GltfPbrRenderer.setConfig(new GltfRenderConfig(
            GameConfig.GLTF_EXTENSION_FALLBACK_MIN_ROUGHNESS,
            GameConfig.GLTF_DEBUG_DISABLE_NORMAL_MAP,
            GameConfig.effectiveGltfDebugVisualizeMode()
        ));

        fpsCounter = new FPSCounter(window);
        fpsCounter.init();
        pauseMenu = new PauseMenu(window);
        pauseMenu.init();

        worldShaderProgram = new ShaderProgram(
            "/shaders/vertex_shader.glsl",
            "/shaders/fragment_shader.glsl"
        );
        shadowMap = new DirectionalShadowMap(
            GameConfig.effectiveShadowMapSize(),
            GameConfig.effectiveShadowCascadeCount(),
            GameConfig.SHADOW_CASCADE_LAMBDA,
            GameConfig.FOV_DEGREES,
            GameConfig.NEAR_PLANE,
            GameConfig.FAR_PLANE,
            SceneLighting.frame().sunDirection()
        );
        brdfLutTexture = new BrdfLutTexture();
        java.io.File hdrFile = ResourceLoader.tryLoadResourceAsFile(GameConfig.IBL_HDR_EQUIRECT);
        int equirectTex = IblEquirectLoader.createEquirectTexture(
            hdrFile != null ? hdrFile.toPath() : null
        );
        environmentIbl = new EnvironmentIbl(equirectTex);
        meshDepthShader = new ShaderProgram("/shaders/mesh_depth.vert", "/shaders/mesh_depth.frag");
        gltfDepthShader = new ShaderProgram("/shaders/pbr_gltf_shadow.vert", "/shaders/mesh_depth.frag");
        GltfPbrRenderer.initJointBlock(gltfDepthShader);
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
            propInstancePositions.add(propPosition);
            if (GameConfig.isDumpAssimpReportOnStart()) {
                ModelLoaderReport.dumpLoadedMeshesSummary(propMeshes.get(propMeshes.size() - 1));
            }
        }
        if (GameConfig.DRAW_LANDSCAPE && !landscape.isEmpty()) {
            terrainSurface = TerrainSurface.fromMeshes(
                landscape.get(0), new Vector3f(0f, GameConfig.LANDSCAPE_OFFSET_Y, 0f));
        } else {
            terrainSurface = TerrainSurface.flatPlane(0f);
        }
        float px = GameConfig.FORD_MUSTANG_WORLD_X;
        float pz = GameConfig.FORD_MUSTANG_WORLD_Z;
        float h = terrainSurface.heightAt(px, pz);
        propPosition.set(
            px,
            Float.isFinite(h) ? h + GameConfig.FORD_MUSTANG_ABOVE_TERRAIN : 1f,
            pz
        );

        gltfWorldPositions.clear();
        gltfScales.clear();
        if (!gltfScenes.isEmpty()) {
            float tx = GameConfig.TOYOTA_AE86_WORLD_X;
            float tz = GameConfig.TOYOTA_AE86_WORLD_Z;
            float h2 = terrainSurface.heightAt(tx, tz);
            toyotaWorldPosition.set(
                tx,
                Float.isFinite(h2) ? h2 + GameConfig.TOYOTA_AE86_ABOVE_TERRAIN : 1f,
                tz
            );
            gltfWorldPositions.add(propPosition);
            float baseScale = GameConfig.FORD_MUSTANG_MODEL_SCALE;
            gltfScales.add(baseScale);
            gltfWorldPositions.add(toyotaWorldPosition);
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

        skyShaderProgram = new ShaderProgram("/shaders/sky_pass.vert", "/shaders/sky_pass.frag");
        worldRenderer = new WorldRenderer(worldShaderProgram, shadowMap, brdfLutTexture, environmentIbl);
        worldRenderer.setAppConfig(
            GameConfig.LANDSCAPE_TEXTURE_SCALE,
            GameConfig.FAR_PLANE,
            new LitFrameUniformCache.ShadowUniformConfig(
                GameConfig.effectiveShadowBiasScaleForProgram(true),
                GameConfig.effectiveShadowBiasScaleForProgram(false),
                GameConfig.effectiveGltfShadowReceiveFloor(),
                GameConfig.effectiveDiagnosticGltfNoIblOcclusion(),
                GameConfig.effectiveShadowPcfUseShadingNormal()
            )
        );
        if (GameConfig.effectiveInstancingDemoEnabled()) {
            instancingDemo = new InstancingDemoRenderer();
        }
        RayTraceRenderer rt = null;
        if (GameConfig.RAY_TRACE_ENABLED) {
            if (!GL.getCapabilities().OpenGL43) {
                RenderErrorLog.warn("Ray tracing: OpenGL 4.3+ required (compute shaders).");
            } else {
                try {
                    rt = new RayTraceRenderer(
                        landscape,
                        gltfScenes,
                        gltfWorldPositions,
                        gltfScales,
                        propMeshes,
                        propInstancePositions
                    );
                    rayTraceRenderer = rt;
                } catch (Exception e) {
                    RenderErrorLog.warn("Ray trace init failed", e);
                }
            }
        }
        sceneRenderer = new SceneRenderer(
            landscape,
            propMeshes,
            propInstancePositions,
            gltfShaderProgram,
            gltfScenes,
            gltfWorldPositions,
            gltfScales,
            worldRenderer,
            meshDepthShader,
            gltfDepthShader,
            skyShaderProgram,
            rt,
            instancingDemo
        );

        camera = new Camera(new Vector3f(0.0f, 1.0f, 3.0f), new Vector3f(0.0f, 1.0f, 0.0f), 90.0f, 0.0f);
        snapCameraToTerrain();
        lastFrameTime = (float) glfwGetTime();

        glfwSetCursorPosCallback(window, this::mouseCallback);
        glfwSetMouseButtonCallback(window, this::mouseButtonCallback);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        RenderErrorLog.checkGl("after init GL state");
        GameConfig.logRenderConfig();

        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void loop() {

        while (!glfwWindowShouldClose(window)) {
            fpsCounter.update();
            float currentFrame = (float) glfwGetTime();
            deltaTime = currentFrame - lastFrameTime;
            lastFrameTime = currentFrame;

            processInput();

            syncCarHeightFromTerrain();
            snapCameraToTerrain();

            RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
            LightingFrame lit = SceneLighting.frame(rs);
            Vector3f clearRgb = lit.clearColor();
            glClearColor(clearRgb.x, clearRgb.y, clearRgb.z, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f view = camera.getViewMatrix();
            glfwGetFramebufferSize(window, framebufferWidthArr, framebufferHeightArr);
            int fbW = framebufferWidthArr[0];
            int fbH = framebufferHeightArr[0];
            float aspect = (float) fbW / (float) Math.max(1, fbH);
            Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(GameConfig.FOV_DEGREES),
                aspect,
                GameConfig.NEAR_PLANE,
                GameConfig.FAR_PLANE
            );

            sceneRenderer.renderTransparent(
                lit,
                view,
                projection,
                camera.getPosition(),
                deltaTime,
                fbW,
                fbH
            );

            if (rs.isShowFpsOverlay()) {
                fpsCounter.render();
            }
            if (menuOpen) {
                pauseMenu.render();
            }
            glfwSwapBuffers(window);
            RenderErrorLog.checkGl("frame");
            glfwPollEvents();
        }

        cleanup();
    }

    private void processInput() {
        if (!menuOpen) {
            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
                camera.processKeyboard(GLFW_KEY_W, deltaTime);
            }
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
                camera.processKeyboard(GLFW_KEY_S, deltaTime);
            }
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
                camera.processKeyboard(GLFW_KEY_A, deltaTime);
            }
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
                camera.processKeyboard(GLFW_KEY_D, deltaTime);
            }
        }

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            if (!escKeyDown) {
                escKeyDown = true;
                menuOpen = !menuOpen;
                glfwSetInputMode(window, GLFW_CURSOR, menuOpen ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                if (menuOpen) {
                    firstMouse = true;
                } else {
                    pauseMenu.resetPointerState();
                    RuntimeGraphicsSettings.persistCurrent();
                }
            }
        } else {
            escKeyDown = false;
        }
    }

    /**
     * Подстраивает Y машин под рельеф по текущим X/Z. Вызывайте после изменения X/Z (физика, ввод).
     */
    private void syncCarHeightFromTerrain() {
        if (!gltfScenes.isEmpty()) {
            float px = propPosition.x;
            float pz = propPosition.z;
            float h = terrainSurface.heightAt(px, pz);
            if (Float.isFinite(h)) {
                propPosition.y = h + GameConfig.FORD_MUSTANG_ABOVE_TERRAIN;
            }
            float tx = toyotaWorldPosition.x;
            float tz = toyotaWorldPosition.z;
            float h2 = terrainSurface.heightAt(tx, tz);
            if (Float.isFinite(h2)) {
                toyotaWorldPosition.y = h2 + GameConfig.TOYOTA_AE86_ABOVE_TERRAIN;
            }
        }
    }

    /** Мировая позиция первой glTF-машины (Mustang); меняйте X/Z для движения. */
    public Vector3f getFirstCarWorldPosition() {
        return propPosition;
    }

    /** Вторая glTF-машина (AE86). */
    public Vector3f getSecondCarWorldPosition() {
        return toyotaWorldPosition;
    }

    private void snapCameraToTerrain() {
        Vector3f p = camera.getPosition();
        float h = terrainSurface.heightAt(p.x, p.z);
        if (Float.isFinite(h)) {
            p.y = h + GameConfig.CAMERA_EYE_HEIGHT;
        }
    }

    private void cleanup() {
        RuntimeGraphicsSettings.persistCurrent();
        if (pauseMenu != null) {
            pauseMenu.cleanup();
        }
        if (fpsCounter != null) {
            fpsCounter.cleanup();
        }
        for (GltfScene gs : gltfScenes) {
            if (gs != null) {
                gs.cleanup();
            }
        }
        gltfScenes.clear();
        if (gltfShaderProgram != null) {
            gltfShaderProgram.cleanup();
        }
        if (meshDepthShader != null) {
            meshDepthShader.cleanup();
        }
        if (gltfDepthShader != null) {
            gltfDepthShader.cleanup();
        }
        if (skyShaderProgram != null) {
            skyShaderProgram.cleanup();
        }
        SkyRenderer.cleanup();
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
        worldShaderProgram.cleanup();
    }

    private void mouseButtonCallback(long win, int button, int action, int mods) {
        if (button != GLFW_MOUSE_BUTTON_LEFT || !menuOpen) {
            return;
        }
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(win, x, y);
        if (action == GLFW_PRESS) {
            pauseMenu.handlePointerDown(x[0], y[0]);
        } else if (action == GLFW_RELEASE) {
            boolean slider = pauseMenu.handlePointerUp(x[0], y[0]);
            if (slider) {
                RuntimeGraphicsSettings.persistCurrent();
            } else if (pauseMenu.handleClick(x[0], y[0])) {
                RuntimeGraphicsSettings.persistCurrent();
            }
        }
    }

    private void mouseCallback(long window, double xpos, double ypos) {
        if (menuOpen) {
            pauseMenu.handlePointerMove(xpos, ypos);
            return;
        }
        if (firstMouse) {
            lastX = (float) xpos;
            lastY = (float) ypos;
            firstMouse = false;
        }

        float xOffset = (float) xpos - lastX;
        float yOffset = lastY - (float) ypos;

        lastX = (float) xpos;
        lastY = (float) ypos;

        camera.processMouseMovement(xOffset, yOffset);
    }

    public static void main(String[] args) throws Exception {
        new Game3d().run();
    }
}
