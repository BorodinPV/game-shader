package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/**
 * Демо: {@link glDrawElementsInstanced} с матрицами в uniform-массиве (до 64 копий).
 */
public final class InstancingDemoRenderer {

    public static final int MAX_INSTANCES = 64;
    private static final int INSTANCE_COUNT = 48;

    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int indexCount;
    private final ShaderProgram shader;
    private final Matrix4f[] instanceModels = new Matrix4f[MAX_INSTANCES];

    public InstancingDemoRenderer() {
        for (int i = 0; i < MAX_INSTANCES; i++) {
            instanceModels[i] = new Matrix4f();
        }
        float[] pos = buildUnitCubePositions();
        int[] idx = buildCubeIndices();
        indexCount = idx.length;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = stackPush()) {
            FloatBuffer fb = stack.mallocFloat(pos.length);
            fb.put(pos).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        try (MemoryStack stack = stackPush()) {
            IntBuffer ib = stack.mallocInt(idx.length);
            ib.put(idx).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        }
        glBindVertexArray(0);

        shader = new ShaderProgram("/shaders/instanced_demo.vert", "/shaders/instanced_demo.frag");

        int grid = (int) Math.ceil(Math.sqrt(INSTANCE_COUNT));
        int k = 0;
        for (int i = 0; i < grid && k < INSTANCE_COUNT; i++) {
            for (int j = 0; j < grid && k < INSTANCE_COUNT; j++) {
                instanceModels[k].identity()
                    .translate(-24f + i * 4f, 0.5f, -12f + j * 4f)
                    .scale(0.35f);
                k++;
            }
        }
    }

    public void render(Matrix4f view, Matrix4f projection) {
        shader.use();
        shader.setUniform("view", view);
        shader.setUniform("projection", projection);
        shader.setUniformMat4Array("u_instanceModel", instanceModels, INSTANCE_COUNT);
        glBindVertexArray(vao);
        glDrawElementsInstanced(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0, INSTANCE_COUNT);
        glBindVertexArray(0);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }

    private static float[] buildUnitCubePositions() {
        return new float[] {
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f
        };
    }

    private static int[] buildCubeIndices() {
        return new int[] {
            0, 1, 2, 2, 3, 0,
            4, 5, 6, 6, 7, 4,
            0, 4, 7, 7, 3, 0,
            1, 5, 6, 6, 2, 1,
            3, 2, 6, 6, 7, 3,
            0, 1, 5, 5, 4, 0
        };
    }
}
