package ru.reweu.game.render;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FALSE;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glDetachShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.loader.ResourceLoader;

public class ShaderProgram {
    private final int programId;
    private static int activeProgramId;

    private final Map<String, Integer> uniforms;

    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        programId = glCreateProgram();

        int vertexShaderId = loadShader(vertexShaderPath, GL_VERTEX_SHADER);
        int fragmentShaderId = loadShader(fragmentShaderPath, GL_FRAGMENT_SHADER);

        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Failed to link shader program: " + glGetProgramInfoLog(programId));
        }

        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);

        uniforms = new HashMap<>();
    }

    private int loadShader(String resourcePath, int type) {
        String shaderSource;
        try {
            shaderSource = new String(Files.readAllBytes(Paths.get(ResourceLoader.loadResourceAsFile(resourcePath).getPath())));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load shader from file: " + resourcePath, e);
        }

        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, shaderSource);
        glCompileShader(shaderId);
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Failed to compile shader: " + glGetShaderInfoLog(shaderId));
        }

        return shaderId;
    }

    public void use() {
        glUseProgram(programId);
        activeProgramId = programId;
    }

    public static int getActiveProgramId() {
        return activeProgramId;
    }

    public void setUniform(String name, float value) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }

    public void setUniform(String name, Matrix4f value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        try (var stack = stackPush()) {
            glUniformMatrix4fv(uniforms.get(name), false, value.get(stack.mallocFloat(16)));
        }
    }

    public void setUniform(String name, Vector3f value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        glUniform3f(uniforms.get(name), value.x, value.y, value.z);
    }

    // Метод для установки булевого значения
    public void setUniform(String name, boolean value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        glUniform1i(uniforms.get(name), value ? 1 : 0); // OpenGL не поддерживает прямую передачу bool, используем int (1 или 0)
    }

    // Метод для установки целого значения (например, для текстурных сэмплеров)
    public void setUniform(String name, int value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        glUniform1i(uniforms.get(name), value);
    }

    public void cleanup() {
        glUseProgram(0);
        glDeleteProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }
}
