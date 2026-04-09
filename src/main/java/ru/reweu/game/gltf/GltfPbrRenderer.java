package ru.reweu.game.gltf;

import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDrawElements;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;
import static org.lwjgl.system.MemoryStack.stackPush;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import ru.reweu.game.GameConfig;
import ru.reweu.game.gltf.GltfPrimitiveBuilder.GltfMeshDraw;
import ru.reweu.game.loader.Texture;
import ru.reweu.game.render.ShaderProgram;

/**
 * Отрисовка примитива glTF: скиннинг (UBO), морфинг, материал PBR (§3.9).
 * Per-frame солнце/камера/IBL/тени задаются до вызова {@link #draw} через
 * {@link ru.reweu.game.render.WorldRenderer#prepareFrameFor}.
 * На кадр передаётся один {@link ru.reweu.game.render.LightingFrame} из {@link ru.reweu.game.render.SceneLighting#frame()};
 * {@code u_emissiveBoost} задаётся в {@link ru.reweu.game.render.SceneFrameUniforms#bindLitFrame}.
 */
public final class GltfPbrRenderer {

    /** Совпадает с {@code u_modelBatch[64]} в шейдерах. */
    public static final int MAX_GLTF_INSTANCED_BATCH = 64;

    private GltfPbrRenderer() {
    }

    public static void initJointBlock(ShaderProgram shader) {
        int pid = shader.getProgramId();
        int blockIndex = glGetUniformBlockIndex(pid, "JointBlock");
        if (blockIndex != -1) {
            glUniformBlockBinding(pid, blockIndex, 0);
        }
    }

    public static void draw(
        ShaderProgram shader,
        GltfTextureRegistry textures,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel node,
        MeshModel meshModel,
        Matrix4f modelMatrix,
        Matrix4f view,
        Matrix4f projection,
        float[] morphWeightsOverride,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags materialExtensionFlags
    ) {
        int pid = shader.getProgramId();
        shader.use();
        setInt(pid, "u_instancedBatchCount", 0);

        uploadJointBufferMorphAndSkin(pid, jointUbo, mesh, node, meshModel, morphWeightsOverride);

        shader.setUniform("model", modelMatrix);
        shader.setUniform("view", view);
        shader.setUniform("projection", projection);

        bindMaterial(pid, textures, mesh.material, mesh.materialModelIndex, gltfModel, materialExtensionFlags);

        drawIndexedDoubleSided(mesh);
    }

    /** Проход глубины для карты теней (без материалов). */
    public static void drawShadowDepth(
        ShaderProgram depthShader,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel node,
        MeshModel meshModel,
        Matrix4f modelMatrix,
        Matrix4f lightSpaceMatrix,
        float[] morphWeightsOverride
    ) {
        int pid = depthShader.getProgramId();
        depthShader.use();
        setInt(pid, "u_instancedBatchCount", 0);

        uploadJointBufferMorphAndSkin(pid, jointUbo, mesh, node, meshModel, morphWeightsOverride);

        depthShader.setUniform("model", modelMatrix);
        depthShader.setUniform("lightSpaceMatrix", lightSpaceMatrix);

        drawIndexedDoubleSided(mesh);
    }

    /**
     * Несколько жёстких копий одного примитива (без скина и morph GPU): одна привязка материала,
     * {@code glDrawElementsInstanced}.
     */
    public static void drawInstancedForward(
        ShaderProgram shader,
        GltfTextureRegistry textures,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel firstNode,
        MeshModel firstMeshModel,
        Matrix4f[] modelMatrices,
        int count,
        Matrix4f view,
        Matrix4f projection,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags materialExtensionFlags
    ) {
        if (count <= 0 || count > MAX_GLTF_INSTANCED_BATCH) {
            return;
        }
        int pid = shader.getProgramId();
        shader.use();
        setInt(pid, "u_instancedBatchCount", 0);
        uploadJointBufferMorphAndSkin(pid, jointUbo, mesh, firstNode, firstMeshModel, null);
        shader.setUniform("view", view);
        shader.setUniform("projection", projection);
        bindMaterial(pid, textures, mesh.material, mesh.materialModelIndex, gltfModel, materialExtensionFlags);
        setInt(pid, "u_instancedBatchCount", count);
        shader.setUniformMat4Array("u_modelBatch", modelMatrices, count);
        drawIndexedDoubleSidedInstanced(mesh, count);
        setInt(pid, "u_instancedBatchCount", 0);
    }

    public static void drawShadowDepthInstanced(
        ShaderProgram depthShader,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel firstNode,
        MeshModel firstMeshModel,
        Matrix4f[] modelMatrices,
        int count,
        Matrix4f lightSpaceMatrix
    ) {
        if (count <= 0 || count > MAX_GLTF_INSTANCED_BATCH) {
            return;
        }
        int pid = depthShader.getProgramId();
        depthShader.use();
        setInt(pid, "u_instancedBatchCount", 0);
        uploadJointBufferMorphAndSkin(pid, jointUbo, mesh, firstNode, firstMeshModel, null);
        depthShader.setUniform("lightSpaceMatrix", lightSpaceMatrix);
        setInt(pid, "u_instancedBatchCount", count);
        depthShader.setUniformMat4Array("u_modelBatch", modelMatrices, count);
        drawIndexedDoubleSidedInstanced(mesh, count);
        setInt(pid, "u_instancedBatchCount", 0);
    }

    private static void uploadJointBufferMorphAndSkin(
        int pid,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel node,
        MeshModel meshModel,
        float[] morphWeightsOverride
    ) {
        SkinModel skin = node.getSkinModel();

        int useSkin;
        try (MemoryStack stack = stackPush()) {
            var fb = stack.mallocFloat(64 * 16);
            int jointCount = GltfSkinMatrices.fillJointBuffer(skin, node, fb);
            useSkin = jointCount > 0 ? 1 : 0;
            glBindBuffer(GL_UNIFORM_BUFFER, jointUbo);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, fb);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, jointUbo);
        }

        setInt(pid, "u_useSkinning", useSkin);

        float[] mw = meshModel != null ? meshModel.getWeights() : null;
        if (mw == null) {
            mw = node.getWeights();
        }
        if (morphWeightsOverride != null) {
            mw = morphWeightsOverride;
        }
        int morphN = mesh.morphGpuCount;
        int locW = ShaderProgram.uniformLocation(pid, "u_morphWeightsA");
        if (mw != null && morphN > 0) {
            setInt(pid, "u_morphCount", morphN);
            glUniform4f(
                locW,
                mw.length > 0 ? mw[0] : 0f,
                mw.length > 1 ? mw[1] : 0f,
                mw.length > 2 ? mw[2] : 0f,
                mw.length > 3 ? mw[3] : 0f
            );
        } else {
            setInt(pid, "u_morphCount", 0);
            glUniform4f(locW, 0f, 0f, 0f, 0f);
        }
    }

    private static void drawIndexedDoubleSided(GltfMeshDraw mesh) {
        boolean doubleSided = mesh.material.isDoubleSided();
        boolean cullWasEnabled = false;
        if (doubleSided) {
            cullWasEnabled = glIsEnabled(GL_CULL_FACE);
            glDisable(GL_CULL_FACE);
        }

        glBindVertexArray(mesh.vao);
        glDrawElements(GL_TRIANGLES, mesh.indexCount, mesh.indexGlType, 0);
        glBindVertexArray(0);

        if (doubleSided) {
            if (cullWasEnabled) {
                glEnable(GL_CULL_FACE);
                glFrontFace(GL_CCW);
            } else {
                glDisable(GL_CULL_FACE);
            }
        }
    }

    private static void drawIndexedDoubleSidedInstanced(GltfMeshDraw mesh, int instanceCount) {
        boolean doubleSided = mesh.material.isDoubleSided();
        boolean cullWasEnabled = false;
        if (doubleSided) {
            cullWasEnabled = glIsEnabled(GL_CULL_FACE);
            glDisable(GL_CULL_FACE);
        }

        glBindVertexArray(mesh.vao);
        glDrawElementsInstanced(GL_TRIANGLES, mesh.indexCount, mesh.indexGlType, 0, instanceCount);
        glBindVertexArray(0);

        if (doubleSided) {
            if (cullWasEnabled) {
                glEnable(GL_CULL_FACE);
                glFrontFace(GL_CCW);
            } else {
                glDisable(GL_CULL_FACE);
            }
        }
    }

    private static void bindMaterial(
        int pid,
        GltfTextureRegistry tex,
        MaterialModelV2 m,
        int materialModelIndex,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags materialExtensionFlags
    ) {
        float[] bcf = m.getBaseColorFactor();
        if (bcf == null || bcf.length < 4) {
            glUniform4f(ShaderProgram.uniformLocation(pid, "u_baseColorFactor"), 1f, 1f, 1f, 1f);
        } else {
            glUniform4f(ShaderProgram.uniformLocation(pid, "u_baseColorFactor"), bcf[0], bcf[1], bcf[2], bcf[3]);
        }

        setInt(pid, "u_baseColorTexCoord", intOrZero(m.getBaseColorTexcoord()));
        setInt(pid, "u_metallicRoughnessTexCoord", intOrZero(m.getMetallicRoughnessTexcoord()));
        setInt(pid, "u_normalTexCoord", intOrZero(m.getNormalTexcoord()));
        setInt(pid, "u_occlusionTexCoord", intOrZero(m.getOcclusionTexcoord()));
        setInt(pid, "u_emissiveTexCoord", intOrZero(m.getEmissiveTexcoord()));

        glUniform1f(ShaderProgram.uniformLocation(pid, "u_metallicFactor"), m.getMetallicFactor());
        float roughness = m.getRoughnessFactor();
        if (gltfModel != null && materialExtensionFlags != null) {
            int matIdx = materialModelIndex >= 0
                ? materialModelIndex
                : gltfModel.getMaterialModels().indexOf(m);
            if (matIdx >= 0 && materialExtensionFlags.needsRoughnessFallback(matIdx)) {
                roughness = Math.max(roughness, GameConfig.GLTF_EXTENSION_FALLBACK_MIN_ROUGHNESS);
            }
        }
        glUniform1f(ShaderProgram.uniformLocation(pid, "u_roughnessFactor"), roughness);
        glUniform1f(ShaderProgram.uniformLocation(pid, "u_normalScale"), m.getNormalScale());
        glUniform1f(ShaderProgram.uniformLocation(pid, "u_occlusionStrength"), m.getOcclusionStrength());

        float[] ef = m.getEmissiveFactor();
        boolean hasEmissiveTex = m.getEmissiveTexture() != null;
        float e0 = 0f;
        float e1 = 0f;
        float e2 = 0f;
        if (ef != null && ef.length >= 3) {
            e0 = ef[0];
            e1 = ef[1];
            e2 = ef[2];
        }
        float eRaw0 = e0;
        float eRaw1 = e1;
        float eRaw2 = e2;
        if (hasEmissiveTex && (e0 + e1 + e2) < 1e-5f) {
            e0 = e1 = e2 = 1f;
        }
        glUniform3f(ShaderProgram.uniformLocation(pid, "u_emissiveFactor"), e0, e1, e2);

        MaterialModelV2.AlphaMode am = m.getAlphaMode();
        int alphaMode = 0;
        if (am == MaterialModelV2.AlphaMode.MASK) {
            alphaMode = 1;
        } else if (am == MaterialModelV2.AlphaMode.BLEND) {
            alphaMode = 2;
        }
        setInt(pid, "u_alphaMode", alphaMode);
        glUniform1f(ShaderProgram.uniformLocation(pid, "u_alphaCutoff"), m.getAlphaCutoff());
        setInt(pid, "u_doubleSided", m.isDoubleSided() ? 1 : 0);

        boolean thinGlass = GltfThinGlass.shouldUseThinGlass(
            m,
            hasEmissiveTex,
            eRaw0,
            eRaw1,
            eRaw2,
            gltfModel,
            materialExtensionFlags,
            materialModelIndex
        );
        setInt(pid, "u_thinGlass", thinGlass ? 1 : 0);

        bindTex(pid, tex, m.getBaseColorTexture(), "u_baseColor", GltfShaderTextureUnits.BASE_COLOR, true, "u_hasBaseColor");
        bindTex(pid, tex, m.getMetallicRoughnessTexture(), "u_metallicRoughness", GltfShaderTextureUnits.METALLIC_ROUGHNESS, false, "u_hasMetallicRoughness");
        bindTex(pid, tex, m.getNormalTexture(), "u_normal", GltfShaderTextureUnits.NORMAL, false, "u_hasNormal");
        bindTex(pid, tex, m.getOcclusionTexture(), "u_occlusion", GltfShaderTextureUnits.OCCLUSION, false, "u_hasOcclusion");
        bindTex(pid, tex, m.getEmissiveTexture(), "u_emissive", GltfShaderTextureUnits.EMISSIVE, true, "u_hasEmissive");
        if (GameConfig.GLTF_DEBUG_DISABLE_NORMAL_MAP) {
            setInt(pid, "u_hasNormal", 0);
        }
        int dbg = ShaderProgram.uniformLocation(pid, "u_debugVisualizeMode");
        if (dbg != -1) {
            glUniform1i(dbg, GameConfig.effectiveGltfDebugVisualizeMode());
        }
        int ntt = ShaderProgram.uniformLocation(pid, "u_enableNormalTexTransform");
        if (ntt != -1) {
            glUniform1i(ntt, 0);
        }
        int nto = ShaderProgram.uniformLocation(pid, "u_normalTexTransformOffset");
        if (nto != -1) {
            glUniform2f(nto, 0f, 0f);
        }
        int nts = ShaderProgram.uniformLocation(pid, "u_normalTexTransformScale");
        if (nts != -1) {
            glUniform2f(nts, 1f, 1f);
        }
        int ntr = ShaderProgram.uniformLocation(pid, "u_normalTexTransformRotation");
        if (ntr != -1) {
            glUniform1f(ntr, 0f);
        }
    }

    private static int intOrZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static void bindTex(
        int pid,
        GltfTextureRegistry reg,
        de.javagl.jgltf.model.TextureModel tm,
        String samplerName,
        int unit,
        boolean srgb,
        String hasName
    ) {
        Texture t = reg.get(tm, srgb);
        int has = t != null ? 1 : 0;
        setInt(pid, hasName, has);
        glActiveTexture(GL_TEXTURE0 + unit);
        if (t != null) {
            t.bind();
        } else {
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        int loc = ShaderProgram.uniformLocation(pid, samplerName);
        if (loc != -1) {
            glUniform1i(loc, unit);
        }
    }

    private static void setInt(int pid, String name, int v) {
        int loc = ShaderProgram.uniformLocation(pid, name);
        if (loc != -1) {
            glUniform1i(loc, v);
        }
    }
}
