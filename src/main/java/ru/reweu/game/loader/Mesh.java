package ru.reweu.game.loader;

import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform4f;
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
import static org.lwjgl.opengl.GL30.glUniform1i;
import static org.lwjgl.opengl.GL30.glUniform3f;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.GL_TEXTURE3;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glVertexAttribPointer;

import ru.reweu.game.render.ShaderProgram;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ru.reweu.game.GameConfig;

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
    /** World-space terrain without UVs in OBJ: planar XZ mapping in vertex_shader.glsl */
    private final boolean usePlanarUv;
    private final float planarMinX;
    private final float planarMinZ;
    private final float planarRangeX;
    private final float planarRangeZ;
    /** Копии для коллизий с рельефом (локальные координаты, как в VBO) */
    private final List<Vector3f> collisionVertices;
    private final List<Integer> collisionIndices;
    private final Vector3f localCenterApprox;
    /** Стекло: отдельный проход с альфой и без записи в depth — иначе «просвечивает» ландшафт. */
    private final boolean transparentOverlayPass;
    /** glTF COLOR_0: есть ли отличие от (1,1,1,1) — для отчётов. */
    private final boolean nonDefaultVertexColor;

    public Mesh(List<Vector3f> vertices,
                List<Vector3f> normals,
                List<Vector3f> texCoords,
                List<Vector4f> vertexColors,
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

        boolean nonDefaultVc = false;
        for (Vector4f vc : vertexColors) {
            if (Math.abs(vc.x - 1f) > 0.02f || Math.abs(vc.y - 1f) > 0.02f || Math.abs(vc.z - 1f) > 0.02f
                || Math.abs(vc.w - 1f) > 0.02f) {
                nonDefaultVc = true;
                break;
            }
        }
        this.nonDefaultVertexColor = nonDefaultVc;

        this.isTransparent = materialAlpha < 1.0f;
        this.localCenterApprox = centroid(vertices);
        this.transparentOverlayPass = isTransparent || nameSuggestsGlass(name);

        boolean degenerateUv = true;
        for (Vector3f tc : texCoords) {
            if (Math.abs(tc.x) > 1e-5f || Math.abs(tc.y) > 1e-5f) {
                degenerateUv = false;
                break;
            }
        }
        if (degenerateUv) {
            this.usePlanarUv = true;
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            for (Vector3f v : vertices) {
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minZ = Math.min(minZ, v.z);
                maxZ = Math.max(maxZ, v.z);
            }
            this.planarMinX = minX;
            this.planarMinZ = minZ;
            this.planarRangeX = maxX - minX + 1e-4f;
            this.planarRangeZ = maxZ - minZ + 1e-4f;
        } else {
            this.usePlanarUv = false;
            this.planarMinX = 0f;
            this.planarMinZ = 0f;
            this.planarRangeX = 1f;
            this.planarRangeZ = 1f;
        }

        this.collisionVertices = List.copyOf(vertices);
        this.collisionIndices = List.copyOf(indices);

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        int stride = 12 * Float.BYTES;
        FloatBuffer verticesBuffer = memAllocFloat(vertices.size() * 12);
        try {
            for (int i = 0; i < vertices.size(); i++) {
                verticesBuffer.put(vertices.get(i).x).put(vertices.get(i).y).put(vertices.get(i).z);
                verticesBuffer.put(normals.get(i).x).put(normals.get(i).y).put(normals.get(i).z);
                verticesBuffer.put(texCoords.get(i).x).put(texCoords.get(i).y);
                Vector4f vc = vertexColors.get(i);
                verticesBuffer.put(vc.x).put(vc.y).put(vc.z).put(vc.w);
            }
            verticesBuffer.flip();

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 8 * Float.BYTES);
            glEnableVertexAttribArray(3);
        } finally {
            memFree(verticesBuffer);
        }

        IntBuffer indicesBuffer = memAllocInt(indices.size());
        try {
            indices.forEach(indicesBuffer::put);
            indicesBuffer.flip();

            eboId = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        } finally {
            memFree(indicesBuffer);
        }

        glBindVertexArray(0);
    }

    private static Vector3f centroid(List<Vector3f> verts) {
        if (verts.isEmpty()) {
            return new Vector3f();
        }
        Vector3f s = new Vector3f();
        for (Vector3f v : verts) {
            s.add(v);
        }
        return s.div(verts.size());
    }

    private static boolean nameSuggestsGlass(String meshName) {
        if (meshName == null || meshName.isBlank()) {
            return false;
        }
        String n = meshName.toLowerCase(Locale.ROOT);
        return n.contains("glass") || n.contains("windshield") || n.contains("windscreen")
            || n.contains("glas");
    }

    /** Для отчётов: имя меша попадает под эвристику «стекло» (см. {@link #nameSuggestsGlass}). */
    public boolean isGlassNameMatch() {
        return nameSuggestsGlass(name);
    }

    public boolean hasAmbientTexture() {
        return ambientTexture != null;
    }

    public Vector3f getLocalCenterApprox() {
        return new Vector3f(localCenterApprox);
    }

    public boolean isTransparentOverlayPass() {
        return transparentOverlayPass;
    }

    /** @param opaqueGeometryPass при {@code true} в шейдере альфа принудительно 1 — непрозрачная геометрия и буфер глубины. */
    public void render(boolean opaqueGeometryPass) {
        int shaderProgram = ShaderProgram.getActiveProgramId();
        int opaqueGeomLoc = ShaderProgram.uniformLocation(shaderProgram, "opaqueGeometryPass");
        if (opaqueGeomLoc != -1) {
            glUniform1i(opaqueGeomLoc, opaqueGeometryPass ? 1 : 0);
        }
        int textureScaleLocation = ShaderProgram.uniformLocation(shaderProgram, "textureScale");
        if (textureScaleLocation != -1) {
            float ts = usePlanarUv ? GameConfig.LANDSCAPE_TEXTURE_SCALE : 1.0f;
            glUniform1f(textureScaleLocation, ts);
        }
        int useDiffuseTextureLocation = ShaderProgram.uniformLocation(shaderProgram, "useDiffuseTexture");
        int useSpecularTextureLocation = ShaderProgram.uniformLocation(shaderProgram, "useSpecularTexture");
        int useNormalTextureLocation = ShaderProgram.uniformLocation(shaderProgram, "useNormalTexture");
        int useAlphaTextureLocation = ShaderProgram.uniformLocation(shaderProgram, "useAlphaTexture");
        int materialColorLocation = ShaderProgram.uniformLocation(shaderProgram, "diffuseColor");
        int materialAlphaLocation = ShaderProgram.uniformLocation(shaderProgram, "materialAlpha");

        applyMaterialAlpha(materialAlphaLocation);
        bindDiffuseForActiveShader(shaderProgram, useDiffuseTextureLocation, materialColorLocation);
        bindOptionalTextures(
            shaderProgram, useSpecularTextureLocation, useNormalTextureLocation, useAlphaTextureLocation);
        setPlanarUvUniforms(shaderProgram);

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void render() {
        render(false);
    }

    /** Проход глубины (карта теней): без текстур, только геометрия. */
    public void renderDepthOnly() {
        setPlanarUvUniforms(ShaderProgram.getActiveProgramId());
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void applyMaterialAlpha(int materialAlphaLocation) {
        if (materialAlphaLocation != -1) {
            glUniform1f(materialAlphaLocation, materialAlpha);
        }
    }

    private void bindDiffuseForActiveShader(int shaderProgram, int useDiffuseTextureLocation, int materialColorLocation) {
        if (diffuseTexture != null) {
            glActiveTexture(GL_TEXTURE0);
            diffuseTexture.bind();
            int texture1Loc = ShaderProgram.uniformLocation(shaderProgram, "texture1");
            if (texture1Loc != -1) {
                glUniform1i(texture1Loc, 0);
            }
            int diffuseSamplerLoc = ShaderProgram.uniformLocation(shaderProgram, "diffuseTexture");
            if (diffuseSamplerLoc != -1) {
                glUniform1i(diffuseSamplerLoc, 0);
            }
            if (useDiffuseTextureLocation != -1) {
                glUniform1i(useDiffuseTextureLocation, 1);
            }
            if (materialColorLocation != -1) {
                float len2 = color.x * color.x + color.y * color.y + color.z * color.z;
                if (len2 < 1e-8f) {
                    glUniform3f(materialColorLocation, 1f, 1f, 1f);
                } else {
                    glUniform3f(materialColorLocation, color.x, color.y, color.z);
                }
            }
        } else {
            if (useDiffuseTextureLocation != -1) {
                glUniform1i(useDiffuseTextureLocation, 0);
            }
            if (materialColorLocation != -1) {
                float len2 = color.x * color.x + color.y * color.y + color.z * color.z;
                if (len2 < 1e-8f) {
                    glUniform3f(materialColorLocation, 0.75f, 0.75f, 0.75f);
                } else {
                    glUniform3f(materialColorLocation, color.x, color.y, color.z);
                }
            }
        }
    }

    private void bindOptionalTextures(
        int shaderProgram,
        int useSpecularTextureLocation,
        int useNormalTextureLocation,
        int useAlphaTextureLocation
    ) {
        int mrLoc = ShaderProgram.uniformLocation(shaderProgram, "textureMetallicRoughness");
        if (useSpecularTextureLocation != -1) {
            if (specularTexture != null) {
                glActiveTexture(GL_TEXTURE1);
                specularTexture.bind();
                glUniform1i(useSpecularTextureLocation, 1);
                if (mrLoc != -1) {
                    glUniform1i(mrLoc, 1);
                }
            } else {
                glUniform1i(useSpecularTextureLocation, 0);
            }
        }
        int nLoc = ShaderProgram.uniformLocation(shaderProgram, "textureNormal");
        if (useNormalTextureLocation != -1) {
            if (normalTexture != null) {
                glActiveTexture(GL_TEXTURE2);
                normalTexture.bind();
                glUniform1i(useNormalTextureLocation, 1);
                if (nLoc != -1) {
                    glUniform1i(nLoc, 2);
                }
            } else {
                glUniform1i(useNormalTextureLocation, 0);
            }
        }
        int aLoc = ShaderProgram.uniformLocation(shaderProgram, "textureOpacity");
        if (useAlphaTextureLocation != -1) {
            if (alphaTexture != null) {
                glActiveTexture(GL_TEXTURE3);
                alphaTexture.bind();
                glUniform1i(useAlphaTextureLocation, 1);
                if (aLoc != -1) {
                    glUniform1i(aLoc, 3);
                }
            } else {
                glUniform1i(useAlphaTextureLocation, 0);
            }
        }
        glActiveTexture(GL_TEXTURE0);
    }

    private void setPlanarUvUniforms(int shaderProgram) {
        int planarLoc = ShaderProgram.uniformLocation(shaderProgram, "usePlanarUv");
        if (planarLoc != -1) {
            glUniform1i(planarLoc, usePlanarUv ? 1 : 0);
        }
        int uvBoundsLoc = ShaderProgram.uniformLocation(shaderProgram, "uvBounds");
        if (uvBoundsLoc != -1) {
            glUniform4f(uvBoundsLoc, planarMinX, planarMinZ, planarRangeX, planarRangeZ);
        }
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

    public List<Vector3f> getCollisionVertices() {
        return collisionVertices;
    }

    public List<Integer> getCollisionIndices() {
        return collisionIndices;
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

    public boolean hasNonDefaultVertexColor() {
        return nonDefaultVertexColor;
    }

    /**
     * Ключ для сортировки draw по материалу (текстуры и флаги), чтобы реже переключать биндинги.
     */
    public long materialStateKey() {
        long h = 17;
        h = 31 * h + ptr(diffuseTexture);
        h = 31 * h + ptr(ambientTexture);
        h = 31 * h + ptr(specularTexture);
        h = 31 * h + ptr(normalTexture);
        h = 31 * h + ptr(alphaTexture);
        h = 31 * h + ptr(specularHighlightTexture);
        h = 31 * h + Float.floatToIntBits(materialAlpha);
        h = 31 * h + (usePlanarUv ? 1 : 0);
        h = 31 * h + Float.floatToIntBits(color.x);
        h = 31 * h + Float.floatToIntBits(color.y);
        h = 31 * h + Float.floatToIntBits(color.z);
        h = 31 * h + (transparentOverlayPass ? 1 : 0);
        return h;
    }

    private static int ptr(Texture t) {
        return t == null ? 0 : System.identityHashCode(t);
    }
}