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
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1fv;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.loader.ResourceLoader;

public class ShaderProgram {
    private final int programId;
    private static int activeProgramId;

    /** programId → (name → location), включая -1 для отсутствующих uniform. */
    private static final Map<Integer, Map<String, Integer>> UNIFORM_LOCATIONS_BY_PROGRAM = new ConcurrentHashMap<>();

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
    }

    /**
     * Кэшированный результат glGetUniformLocation для произвольной программы (в т.ч. только по id активной программы).
     */
    public static int uniformLocation(int programId, String name) {
        return UNIFORM_LOCATIONS_BY_PROGRAM
            .computeIfAbsent(programId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public int uniformLocation(String name) {
        return uniformLocation(programId, name);
    }

    /** Удалить кэш uniform после удаления программы (в т.ч. compute-программы не из этого класса). */
    public static void removeUniformCacheForProgram(int programId) {
        UNIFORM_LOCATIONS_BY_PROGRAM.remove(programId);
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
        int location = uniformLocation(name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }

    public void setUniform(String name, Matrix4f value) {
        int loc = uniformLocation(name);
        if (loc == -1) {
            return;
        }
        try (var stack = stackPush()) {
            glUniformMatrix4fv(loc, false, value.get(stack.mallocFloat(16)));
        }
    }

    public void setUniform(String name, Vector3f value) {
        int loc = uniformLocation(name);
        if (loc == -1) {
            return;
        }
        glUniform3f(loc, value.x, value.y, value.z);
    }

    // Метод для установки булевого значения
    public void setUniform(String name, boolean value) {
        int loc = uniformLocation(name);
        if (loc == -1) {
            return;
        }
        glUniform1i(loc, value ? 1 : 0);
    }

    // Метод для установки целого значения (например, для текстурных сэмплеров)
    public void setUniform(String name, int value) {
        int loc = uniformLocation(name);
        if (loc == -1) {
            return;
        }
        glUniform1i(loc, value);
    }

    /** Первые {@code count} матриц в uniform-массив {@code mat4 name[]}. */
    public void setUniformMat4Array(String name, Matrix4f[] matrices, int count) {
        if (count <= 0) {
            return;
        }
        try (var stack = stackPush()) {
            for (int i = 0; i < count; i++) {
                int loc = uniformLocation(programId, name + "[" + i + "]");
                if (loc == -1) {
                    continue;
                }
                glUniformMatrix4fv(loc, false, matrices[i].get(stack.mallocFloat(16)));
            }
        }
    }

    /** Первые {@code count} элементов {@code float name[]}. */
    public void setUniformFloatArray(String name, float[] values, int count) {
        if (count <= 0 || values == null) {
            return;
        }
        int loc = glGetUniformLocation(programId, name + "[0]");
        if (loc == -1) {
            loc = glGetUniformLocation(programId, name);
        }
        if (loc == -1) {
            return;
        }
        try (var stack = stackPush()) {
            FloatBuffer fb = stack.mallocFloat(count);
            fb.put(values, 0, count);
            fb.flip();
            glUniform1fv(loc, fb);
        }
    }

    public void cleanup() {
        glUseProgram(0);
        removeUniformCacheForProgram(programId);
        glDeleteProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }
}
