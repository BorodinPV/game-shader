package ru.reweu.game.render.rt;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FALSE;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
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
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;
import static org.lwjgl.system.MemoryStack.stackPush;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.render.RenderErrorLog;
import ru.reweu.game.render.ShaderProgram;

/**
 * Отдельная программа только с compute shader (OpenGL 4.3+).
 */
public final class ComputeShaderProgram {

    private final int programId;

    public ComputeShaderProgram(String computeShaderPath) {
        try {
            String src = ShaderProgram.loadPreprocessedShaderSource(computeShaderPath);
            int cs = glCreateShader(GL_COMPUTE_SHADER);
            glShaderSource(cs, src);
            glCompileShader(cs);
            if (glGetShaderi(cs, GL_COMPILE_STATUS) == GL_FALSE) {
                String log = glGetShaderInfoLog(cs);
                RenderErrorLog.warn("Compute shader compile failed (" + computeShaderPath + "): " + log);
                throw new RuntimeException(log);
            }
            programId = glCreateProgram();
            glAttachShader(programId, cs);
            glLinkProgram(programId);
            if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(programId);
                RenderErrorLog.warn("Compute shader link failed (" + computeShaderPath + "): " + log);
                throw new RuntimeException(log);
            }
            glDetachShader(programId, cs);
            glDeleteShader(cs);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            RenderErrorLog.warn("Compute shader source load failed: " + computeShaderPath, e);
            throw new RuntimeException("Compute shader: " + computeShaderPath, e);
        }
    }

    public void use() {
        glUseProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }

    public void setUniform(String name, Matrix4f value) {
        int loc = glGetUniformLocation(programId, name);
        if (loc == -1) {
            return;
        }
        try (var stack = stackPush()) {
            glUniformMatrix4fv(loc, false, value.get(stack.mallocFloat(16)));
        }
    }

    public void setUniform(String name, Vector3f value) {
        int loc = glGetUniformLocation(programId, name);
        if (loc != -1) {
            glUniform3f(loc, value.x, value.y, value.z);
        }
    }

    public void setUniform(String name, float value) {
        int loc = glGetUniformLocation(programId, name);
        if (loc != -1) {
            glUniform1f(loc, value);
        }
    }

    public void setUniform(String name, int value) {
        int loc = glGetUniformLocation(programId, name);
        if (loc != -1) {
            glUniform1i(loc, value);
        }
    }

    public void cleanup() {
        glDeleteProgram(programId);
    }
}
