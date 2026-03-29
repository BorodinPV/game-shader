package ru.reweu.game.tools;

import java.util.Locale;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Минимальное скрытое окно + текущий OpenGL-контекст.
 * Нужно для {@link ru.reweu.game.loader.ModelLoader#loadModel}, т.к. {@link ru.reweu.game.loader.Mesh} создаёт VAO/VBO на GPU.
 */
public final class HeadlessGlContext implements AutoCloseable {

    private final long window;

    public HeadlessGlContext() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (System.getenv("GLFW_FORCE_WAYLAND") == null
            && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        }
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(16, 16, "headless-gl", NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new RuntimeException("Failed to create GLFW window for headless GL");
        }
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.err.println("HeadlessGlContext: LWJGL " + Version.getVersion()
            + ", OpenGL " + GL11.glGetString(GL11.GL_VERSION));
    }

    @Override
    public void close() {
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
}
