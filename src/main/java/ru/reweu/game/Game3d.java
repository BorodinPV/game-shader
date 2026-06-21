package ru.reweu.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import ru.reweu.game.gltf.GltfPbrRenderer;
import ru.reweu.game.gltf.GltfRenderConfig;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.render.GameRenderPipeline;
import ru.reweu.game.render.LightingFrame;
import ru.reweu.game.render.RenderErrorLog;
import ru.reweu.game.render.SceneLighting;
import ru.reweu.game.render.ShaderProgram;
import ru.reweu.game.render.SkyRenderer;
import ru.reweu.game.gui.FPSCounter;
import ru.reweu.game.gui.PauseMenu;

import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
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
    private Camera camera;
    private GameSceneAssets assets;
    private GameRenderPipeline pipeline;
    private GameInputController inputController;

    private float lastFrameTime;
    private float deltaTime;
    private FPSCounter fpsCounter;
    private PauseMenu pauseMenu;
    private boolean menuOpen;

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

    private void initWindow() {
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
    }

    private void initGL() {
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

        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void init() {
        initWindow();
        initGL();

        fpsCounter = new FPSCounter(window);
        fpsCounter.init();
        pauseMenu = new PauseMenu(window);
        pauseMenu.init();

        assets = GameSceneAssets.load();
        pipeline = GameRenderPipeline.create(assets);

        camera = new Camera(new Vector3f(0.0f, 1.0f, 3.0f), new Vector3f(0.0f, 1.0f, 0.0f), 90.0f, 0.0f);
        inputController = new GameInputController();
        snapCameraToTerrain();
        lastFrameTime = (float) glfwGetTime();

        glfwSetCursorPosCallback(window, this::mouseCallback);
        glfwSetMouseButtonCallback(window, this::mouseButtonCallback);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        RenderErrorLog.checkGl("after init GL state");
        GameConfig.logRenderConfig();
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            fpsCounter.update();
            float currentFrame = (float) glfwGetTime();
            deltaTime = currentFrame - lastFrameTime;
            lastFrameTime = currentFrame;

            processInput();

            assets.syncCarHeightsFromTerrain();
            float terrainH = assets.terrain.heightAt(camera.getPosition().x, camera.getPosition().z);
            camera.updatePhysics(terrainH, GameConfig.CAMERA_EYE_HEIGHT, deltaTime);

            RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
            pipeline.worldRenderer().setFogActive(rs.isFogEnabled());
            if (rs.isRainEnabled()) {
                pipeline.rainRenderer().update(deltaTime, camera.getPosition());
            }
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

            pipeline.sceneRenderer().renderTransparent(
                lit,
                view,
                projection,
                camera.getPosition(),
                deltaTime,
                fbW,
                fbH
            );

            if (rs.isRainEnabled()) {
                pipeline.rainRenderer().render(view, projection);
            }

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
        boolean wasMenuOpen = menuOpen;
        inputController.update(window, camera, pauseMenu, deltaTime);
        menuOpen = inputController.isMenuOpen();
        if (menuOpen != wasMenuOpen && !menuOpen) {
            RuntimeGraphicsSettings.persistCurrent();
        }
    }

    private void snapCameraToTerrain() {
        Vector3f p = camera.getPosition();
        float h = assets.terrain.heightAt(p.x, p.z);
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
        SkyRenderer.cleanup();
        if (pipeline != null) {
            pipeline.close();
        }
        if (assets != null) {
            assets.close();
        }
    }

    private void mouseButtonCallback(long win, int button, int action, int mods) {
        inputController.handleMouseButton(win, button, action, pauseMenu);
    }

    private void mouseCallback(long window, double xpos, double ypos) {
        inputController.handleMouseMove(window, xpos, ypos, camera, pauseMenu);
    }

    public static void main(String[] args) throws Exception {
        new Game3d().run();
    }
}
