package ru.reweu.game.loader;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import ru.reweu.game.render.ShaderProgram;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {
    private long window;
    private int width = 800;
    private int height = 600;
    private ShaderProgram shaderProgram;
    private Mesh[] meshes;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        window = glfwCreateWindow(width, height, "3D Model Viewer", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);

        shaderProgram = new ShaderProgram("/shaders/vertex.glsl", "/shaders/fragment.glsl");

        meshes = ModelLoader.loadModel("/all/chevy/chevy suburban.obj", 0.03f);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shaderProgram.use();

            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), (float) width / height, 0.1f, 100.0f);
            Matrix4f view = new Matrix4f().lookAt(new Vector3f(0.0f, 0.0f, 3.0f), new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));

            shaderProgram.use();

            for (ru.reweu.game.loader.Mesh mesh : meshes) {
                shaderProgram.setUniform("model", new Matrix4f().translate(-3.0f, -0.3f, 7).scale(0.2f));
                shaderProgram.setUniform("view", view);
                shaderProgram.setUniform("projection", projection);

                shaderProgram.setUniform("diffuseColor", mesh.getMaterialColor());
                shaderProgram.setUniform("lightPos", new Vector3f(10.0f, 100.0f, 10.0f));
                shaderProgram.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
                shaderProgram.setUniform("ambientColor", new Vector3f(0.7f, 0.7f, 0.7f));

//                shaderProgram.setUniform("useDiffuseTexture", mesh.hasDiffuseTexture());
//                shaderProgram.setUniform("useSpecularTexture", mesh.hasSpecularTexture());
//                shaderProgram.setUniform("useNormalTexture", mesh.hasNormalTexture());
//                shaderProgram.setUniform("useAlphaTexture", mesh.hasAlphaTexture());

                mesh.render();
            }
            glDisable(GL_BLEND);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }


    private void cleanup() {
        for (Mesh mesh : meshes) {
            mesh.cleanup();
        }
        shaderProgram.cleanup();

        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
