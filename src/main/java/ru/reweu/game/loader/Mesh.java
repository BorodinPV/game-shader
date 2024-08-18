package ru.reweu.game.loader;

import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_FLOAT;
import static org.lwjgl.opengl.GL30.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL30.GL_TRIANGLES;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glBufferData;
import static org.lwjgl.opengl.GL30.glDeleteBuffers;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glDrawElements;
import static org.lwjgl.opengl.GL30.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glGenBuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glGetUniformLocation;
import static org.lwjgl.opengl.GL30.glUniform1i;
import static org.lwjgl.opengl.GL30.glUniform3f;
import static org.lwjgl.opengl.GL30.glVertexAttribPointer;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memAllocInt;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import org.joml.Vector3f;
import ru.reweu.game.render.ShaderProgram;

import static org.lwjgl.opengl.GL30.*;

public class Mesh {
    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int vertexCount;
    private final Texture diffuseTexture;
    private final Texture ambientTexture;
    private final Texture specularTexture;
    private final Texture normalTexture;
    private final Texture alphaTexture;
    private final Texture specularHighlightTexture;
    private final Vector3f color;
    private final float materialAlpha;
    private final boolean isTransparent; // Новый флаг для отслеживания прозрачности
    private final float scale;
    private final String name;

    public Mesh(List<Vector3f> vertices,
                List<Vector3f> normals,
                List<Vector3f> texCoords,
                List<Integer> indices,
                Texture diffuseTexture,
                Texture ambientTexture,
                Texture specularTexture,
                Texture normalTexture,
                Texture alphaTexture,
                Texture specularHighlightTexture,
                Vector3f color,
                float materialAlpha,
                float scale,
                String name
    ) {
        this.vertexCount = indices.size();
        this.diffuseTexture = diffuseTexture;
        this.ambientTexture = ambientTexture;
        this.specularTexture = specularTexture;
        this.normalTexture = normalTexture;
        this.alphaTexture = alphaTexture;
        this.color = color;
        this.materialAlpha = materialAlpha;
        this.specularHighlightTexture = specularHighlightTexture;
        this.scale = scale;
        this.name = name;

        // Определяем, является ли объект прозрачным
        this.isTransparent = materialAlpha < 1.0f;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        FloatBuffer verticesBuffer = memAllocFloat(vertices.size() * 8);
        for (int i = 0; i < vertices.size(); i++) {
            verticesBuffer.put(vertices.get(i).x).put(vertices.get(i).y).put(vertices.get(i).z);
            verticesBuffer.put(normals.get(i).x).put(normals.get(i).y).put(normals.get(i).z);
            verticesBuffer.put(texCoords.get(i).x).put(texCoords.get(i).y);
        }
        verticesBuffer.flip();

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);

        IntBuffer indicesBuffer = memAllocInt(indices.size());
        indices.forEach(indicesBuffer::put);
        indicesBuffer.flip();

        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    public void render() {
        int shaderProgram = ShaderProgram.getActiveProgramId();
        int useDiffuseTextureLocation = glGetUniformLocation(shaderProgram, "useDiffuseTexture");
        int useSpecularTextureLocation = glGetUniformLocation(shaderProgram, "useSpecularTexture");
        int useNormalTextureLocation = glGetUniformLocation(shaderProgram, "useNormalTexture");
        int useAlphaTextureLocation = glGetUniformLocation(shaderProgram, "useAlphaTexture");

        int materialColorLocation = glGetUniformLocation(shaderProgram, "diffuseColor");
        int materialAlphaLocation = glGetUniformLocation(shaderProgram, "materialAlpha");

        // Установка режима смешивания для прозрачных объектов
        if (isTransparent) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            glDisable(GL_BLEND);
        }

        // Установка альфа-значения
        glUniform1f(materialAlphaLocation, materialAlpha);

        if (diffuseTexture != null) {
            glUniform1i(useDiffuseTextureLocation, 1);
            diffuseTexture.bind();
        } else {
            glUniform1i(useDiffuseTextureLocation, 0);
            glUniform3f(materialColorLocation, color.x, color.y, color.z);
        }

        float sunIntensity = 0.3f; // Уменьшаем интенсивность до 50%

//        int sunIntensityLocation = glGetUniformLocation(ShaderProgram.getActiveProgramId(), "sunIntensity");
//        glUniform1f(sunIntensityLocation, sunIntensity);

//        if (diffuseTexture == null) {
//            glUniform3f(glGetUniformLocation(ShaderProgram.getActiveProgramId(), "diffuseColor"), color.x, color.y, color.z);
//        } else {
//            diffuseTexture.bind();
//        }

        // Привязка спекулярной текстуры
        if (specularTexture != null) {
            glUniform1i(useSpecularTextureLocation, 1);
            specularTexture.bind();
        } else {
            glUniform1i(useSpecularTextureLocation, 0);
        }

        // Привязка текстуры нормалей
        if (normalTexture != null) {
            glUniform1i(useNormalTextureLocation, 1);
            normalTexture.bind();
        } else {
            glUniform1i(useNormalTextureLocation, 0);
        }

        // Обработка альфа-текстуры и прозрачности
        if (alphaTexture != null) {
            glUniform1i(useAlphaTextureLocation, 1);
            alphaTexture.bind();
        } else {
            glUniform1i(useAlphaTextureLocation, 0);
        }

        // Определяем направление солнечного света
        Vector3f sunDirection = new Vector3f(-1.0f, -1000.0f, -1.0f).normalize(); // Светит сверху-слева
        Vector3f sunColor = new Vector3f(1.0f, 0.9f, 0.8f); // Теплый желтоватый цвет

        int sunDirectionLocation = glGetUniformLocation(ShaderProgram.getActiveProgramId(), "sunDirection");
        int sunColorLocation = glGetUniformLocation(ShaderProgram.getActiveProgramId(), "sunColor");

        // Передаем данные в шейдер
        glUniform3f(sunDirectionLocation, sunDirection.x, sunDirection.y, sunDirection.z);
        glUniform3f(sunColorLocation, sunColor.x, sunColor.y, sunColor.z);

        // Рендер меша
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        if (diffuseTexture != null) {
            diffuseTexture.cleanup();
        }
        if (specularTexture != null) {
            specularTexture.cleanup();
        }
        if (normalTexture != null) {
            normalTexture.cleanup();
        }
        if (alphaTexture != null) {
            alphaTexture.cleanup();
        }
        if (specularHighlightTexture != null) {
            specularHighlightTexture.cleanup();
        }
    }

    public String getName() {
        return name;
    }

    public float getScale() {
        return scale;
    }

    public Vector3f getMaterialColor() {
        return this.color;
    }

    public float getMaterialAlpha() {
        return this.materialAlpha;
    }

    public Texture getDiffuseTexture() {
        return this.diffuseTexture;
    }

    public Texture getSpecularTexture() {
        return this.specularTexture;
    }

    public Texture getNormalTexture() {
        return this.normalTexture;
    }

    public Texture getAlphaTexture() {
        return this.alphaTexture;
    }

    public boolean hasDiffuseTexture() {
        return this.diffuseTexture != null;
    }

    public boolean hasSpecularTexture() {
        return this.specularTexture != null;
    }

    public boolean hasNormalTexture() {
        return this.normalTexture != null;
    }

    public boolean hasAlphaTexture() {
        return this.alphaTexture != null;
    }

    public boolean isTransparent() {
        return this.isTransparent;
    }

    public boolean hasSpecularHighlightTexture() {
        return this.specularHighlightTexture != null;
    }

    public Texture getSpecularHighlightTexture() {
        return specularHighlightTexture;
    }
}