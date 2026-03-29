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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
            String log = glGetProgramInfoLog(programId);
            RenderErrorLog.warn("Shader link failed: " + log);
            throw new RuntimeException("Failed to link shader program: " + log);
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
            String raw = new String(Files.readAllBytes(Paths.get(ResourceLoader.loadResourceAsFile(resourcePath).getPath())));
            shaderSource = preprocessIncludes(raw, resourcePath, new HashSet<>(), new HashSet<>());
        } catch (Exception e) {
            RenderErrorLog.warn("Shader file load failed: " + resourcePath, e);
            throw new RuntimeException("Failed to load shader from file: " + resourcePath, e);
        }

        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, shaderSource);
        glCompileShader(shaderId);
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId);
            RenderErrorLog.warn("Shader compile failed (" + resourcePath + "): " + log);
            throw new RuntimeException("Failed to compile shader: " + log);
        }

        return shaderId;
    }

    /**
     * Поддержка {@code #include "/shaders/include/foo.glsl"} для общих кусков (тонмап, и т.д.).
     */
    /**
     * Загрузка и {@link #preprocessIncludes} для произвольного этапа (в т.ч. compute).
     */
    public static String loadPreprocessedShaderSource(String resourcePath) throws java.io.IOException {
        String raw = new String(Files.readAllBytes(Paths.get(ResourceLoader.loadResourceAsFile(resourcePath).getPath())));
        return preprocessIncludes(raw, resourcePath, new HashSet<>(), new HashSet<>());
    }

    /**
     * @param active          стек для обнаружения циклических #include
     * @param globalIncluded  уже подставленные пути (дедупликация при вложенных include)
     */
    private static String preprocessIncludes(
        String source,
        String currentPath,
        Set<String> active,
        Set<String> globalIncluded
    ) {
        if (!active.add(currentPath)) {
            RenderErrorLog.warn("Circular #include: " + currentPath);
            throw new RuntimeException("Circular #include: " + currentPath);
        }
        try {
            String[] lines = source.split("\r?\n", -1);
            StringBuilder out = new StringBuilder(source.length() + 256);
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("#include ") && t.contains("\"")) {
                    int q1 = t.indexOf('"');
                    int q2 = t.indexOf('"', q1 + 1);
                    if (q2 <= q1) {
                        RenderErrorLog.warn("Malformed #include in " + currentPath + ": " + line);
                        throw new RuntimeException("Malformed #include in " + currentPath + ": " + line);
                    }
                    String incPath = t.substring(q1 + 1, q2);
                    if (globalIncluded.contains(incPath)) {
                        continue;
                    }
                    try {
                        String inner = new String(Files.readAllBytes(Paths.get(ResourceLoader.loadResourceAsFile(incPath).getPath())));
                        String expanded = preprocessIncludes(inner, incPath, active, globalIncluded);
                        globalIncluded.add(incPath);
                        out.append(expanded);
                    } catch (Exception e) {
                        RenderErrorLog.warn("Failed to resolve #include \"" + incPath + "\" from " + currentPath, e);
                        throw new RuntimeException("Failed to resolve #include \"" + incPath + "\" from " + currentPath, e);
                    }
                } else {
                    out.append(line).append('\n');
                }
            }
            return out.toString();
        } finally {
            active.remove(currentPath);
        }
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
        Integer loc = uniforms.get(name);
        if (loc == null || loc == -1) {
            return;
        }
        try (var stack = stackPush()) {
            glUniformMatrix4fv(loc, false, value.get(stack.mallocFloat(16)));
        }
    }

    public void setUniform(String name, Vector3f value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        Integer loc = uniforms.get(name);
        if (loc == null || loc == -1) {
            return;
        }
        glUniform3f(loc, value.x, value.y, value.z);
    }

    // Метод для установки булевого значения
    public void setUniform(String name, boolean value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        Integer loc = uniforms.get(name);
        if (loc == null || loc == -1) {
            return;
        }
        glUniform1i(loc, value ? 1 : 0);
    }

    // Метод для установки целого значения (например, для текстурных сэмплеров)
    public void setUniform(String name, int value) {
        if (!uniforms.containsKey(name)) {
            uniforms.put(name, glGetUniformLocation(programId, name));
        }
        Integer loc = uniforms.get(name);
        if (loc == null || loc == -1) {
            return;
        }
        glUniform1i(loc, value);
    }

    public void cleanup() {
        glUseProgram(0);
        glDeleteProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }
}
