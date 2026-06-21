package ru.reweu.game.platform;

import java.util.Locale;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import ru.reweu.game.GameConfig;
import ru.reweu.game.render.SkyRenderer;

import static org.lwjgl.glfw.GLFW.*;
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

/**
 * Инициализация GLFW/окна и базового GL-состояния.
 */
public final class GlfwAppContext implements AutoCloseable {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private final long window;

    public GlfwAppContext() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (System.getenv("GLFW_FORCE_WAYLAND") == null
            && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        }
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        int windowWidth = DEFAULT_WIDTH;
        int windowHeight = DEFAULT_HEIGHT;
        long primaryMonitor = glfwGetPrimaryMonitor();
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
            glfwTerminate();
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

        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public long window() {
        return window;
    }

    public void getFramebufferSize(int[] widthOut, int[] heightOut) {
        glfwGetFramebufferSize(window, widthOut, heightOut);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void swapBuffers() {
        glfwSwapBuffers(window);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    @Override
    public void close() {
        SkyRenderer.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}
