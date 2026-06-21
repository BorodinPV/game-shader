package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Кэш location'ов per-frame униформ освещения для одной GL-программы.
 * Избегает десятков {@link ShaderProgram#uniformLocation(String)} на кадр.
 */
public final class LitFrameUniformCache {

    private static final Matrix4f IDENTITY_MODEL = new Matrix4f();

    // Instance-level cache for light matrices (avoid repeated allocations)
    private final Matrix4f[] padded_light_matrices = new Matrix4f[4];
    private final float[] cascade_split_pad = new float[3];
    private final Matrix4f view_inverse = new Matrix4f();
    private final Vector3f cam_position = new Vector3f();

    private final int programId;
    private final int worldProgramId;

    private final int sunDirection;
    private final int sunColor;
    private final int sunIntensity;
    private final int ambientColor;
    private final int fillDirection;
    private final int fillColor;
    private final int fillStrength;
    private final int fillSpecularStrength;
    private final int skyAmbientColor;
    private final int groundAmbientColor;
    private final int hemiMix;
    private final int ambientHemiScale;
    private final int ambientHemiHemi;
    private final int exposure;
    private final int textureScale;
    private final int view;
    private final int projection;
    private final int model;
    private final int cameraPosition;
    private final int shadowMapArray;
    private final int shadowsEnabled;
    private final int shadowCascadeCount;
    private final int shadowMapTexelSize;
    private final int shadowBiasScale;
    private final int shadowReceiveFloor;
    private final int diagnosticNoIblOcclusion;
    private final int shadowPcfUseShadingNormal;
    private final int brdfLut;
    private final int hasBrdfLut;
    private final int irradianceMap;
    private final int prefilterMap;
    private final int prefilterMaxMip;
    private final int iblIntensity;
    private final int emissiveBoost;
    private final int[] lightSpaceMatrix = new int[4];
    private final int cascadeSplitDistance;

    private LitFrameUniformCache(ShaderProgram shader, int worldProgramId) {
        this.programId = shader.getProgramId();
        this.worldProgramId = worldProgramId;

        for (int i = 0; i < padded_light_matrices.length; i++) {
            padded_light_matrices[i] = new Matrix4f();
        }

        sunDirection = shader.uniformLocation("sunDirection");
        sunColor = shader.uniformLocation("sunColor");
        sunIntensity = shader.uniformLocation("sunIntensity");
        ambientColor = shader.uniformLocation("ambientColor");
        fillDirection = shader.uniformLocation("fillDirection");
        fillColor = shader.uniformLocation("fillColor");
        fillStrength = shader.uniformLocation("fillStrength");
        fillSpecularStrength = shader.uniformLocation("u_fillSpecularStrength");
        skyAmbientColor = shader.uniformLocation("skyAmbientColor");
        groundAmbientColor = shader.uniformLocation("groundAmbientColor");
        hemiMix = shader.uniformLocation("hemiMix");
        ambientHemiScale = shader.uniformLocation("ambientHemiScale");
        ambientHemiHemi = shader.uniformLocation("ambientHemiHemi");
        exposure = shader.uniformLocation("exposure");
        textureScale = shader.uniformLocation("textureScale");
        view = shader.uniformLocation("view");
        projection = shader.uniformLocation("projection");
        model = shader.uniformLocation("model");
        cameraPosition = shader.uniformLocation("cameraPosition");
        shadowMapArray = shader.uniformLocation("shadowMapArray");
        shadowsEnabled = shader.uniformLocation("shadowsEnabled");
        shadowCascadeCount = shader.uniformLocation("shadowCascadeCount");
        shadowMapTexelSize = shader.uniformLocation("u_shadowMapTexelSize");
        shadowBiasScale = shader.uniformLocation("u_shadowBiasScale");
        shadowReceiveFloor = shader.uniformLocation("u_shadowReceiveFloor");
        diagnosticNoIblOcclusion = shader.uniformLocation("u_diagnosticNoIblOcclusion");
        shadowPcfUseShadingNormal = shader.uniformLocation("u_shadowPcfUseShadingNormal");
        brdfLut = shader.uniformLocation("u_brdfLut");
        hasBrdfLut = shader.uniformLocation("u_hasBrdfLut");
        irradianceMap = shader.uniformLocation("u_irradianceMap");
        prefilterMap = shader.uniformLocation("u_prefilterMap");
        prefilterMaxMip = shader.uniformLocation("u_prefilterMaxMip");
        iblIntensity = shader.uniformLocation("u_iblIntensity");
        emissiveBoost = shader.uniformLocation("u_emissiveBoost");
        for (int i = 0; i < lightSpaceMatrix.length; i++) {
            lightSpaceMatrix[i] = shader.uniformLocation("lightSpaceMatrix[" + i + "]");
        }
        cascadeSplitDistance = shader.uniformLocation("cascadeSplitDistance[0]");
        if (cascadeSplitDistance == -1) {
            // fallback not needed if [0] exists; array upload uses [0] loc
        }
    }

    public static LitFrameUniformCache forWorld(ShaderProgram shader) {
        int pid = shader.getProgramId();
        return new LitFrameUniformCache(shader, pid);
    }

    public static LitFrameUniformCache forGltf(ShaderProgram shader, int worldProgramId) {
        return new LitFrameUniformCache(shader, worldProgramId);
    }

    public int programId() {
        return programId;
    }

    public void bind(
        LightingFrame lit,
        ShaderProgram shader,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        DirectionalShadowMap shadowMap,
        BrdfLutTexture brdfLutTexture,
        EnvironmentIbl environmentIbl,
        boolean shadowSamplingEnabled
    ) {
        shader.use();

        Vector3f sunDirectionVec = lit.sunDirection();
        if (this.sunDirection != -1) {
            glUniform3f(this.sunDirection, sunDirectionVec.x, sunDirectionVec.y, sunDirectionVec.z);
        }
        Vector3f sunColorVec = lit.sunColor();
        if (sunColor != -1) {
            glUniform3f(sunColor, sunColorVec.x, sunColorVec.y, sunColorVec.z);
        }
        if (sunIntensity != -1) {
            glUniform1f(sunIntensity, lit.sunIntensity());
        }
        Vector3f amb = lit.ambientColor();
        if (ambientColor != -1) {
            glUniform3f(ambientColor, amb.x, amb.y, amb.z);
        }

        Vector3f fillDir = lit.fillDirection();
        if (fillDirection != -1) {
            glUniform3f(fillDirection, fillDir.x, fillDir.y, fillDir.z);
        }
        Vector3f fillCol = lit.fillColor();
        if (fillColor != -1) {
            glUniform3f(fillColor, fillCol.x, fillCol.y, fillCol.z);
        }
        if (fillStrength != -1) {
            float fs = programId == worldProgramId ? lit.fillStrengthWorld() : lit.fillStrengthGltf();
            glUniform1f(fillStrength, fs);
        }
        if (fillSpecularStrength != -1) {
            glUniform1f(fillSpecularStrength, lit.fillSpecularStrengthGltf());
        }

        if (skyAmbientColor != -1) {
            Vector3f sk = lit.skyAmbientColor();
            glUniform3f(skyAmbientColor, sk.x, sk.y, sk.z);
        }
        if (groundAmbientColor != -1) {
            Vector3f gr = lit.groundAmbientColor();
            glUniform3f(groundAmbientColor, gr.x, gr.y, gr.z);
        }
        if (hemiMix != -1) {
            glUniform1f(hemiMix, lit.hemiMix());
        }
        if (ambientHemiScale != -1) {
            glUniform1f(ambientHemiScale, lit.worldAmbientHemiScale());
        }
        if (ambientHemiHemi != -1) {
            glUniform1f(ambientHemiHemi, lit.worldAmbientHemiHemi());
        }
        if (exposure != -1) {
            glUniform1f(exposure, lit.exposure());
        }

        if (textureScale != -1) {
            glUniform1f(textureScale, GameConfig.LANDSCAPE_TEXTURE_SCALE);
        }
        if (view != -1) {
            shader.setUniformMat4At(view, viewMatrix);
        }
        if (projection != -1) {
            shader.setUniformMat4At(projection, projectionMatrix);
        }
        if (model != -1) {
            shader.setUniformMat4At(model, IDENTITY_MODEL);
        }

        if (cameraPosition != -1) {
            viewMatrix.invert(view_inverse).getTranslation(cam_position);
            glUniform3f(cameraPosition, cam_position.x, cam_position.y, cam_position.z);
        }

        shadowMap.bindForReadingArray(DirectionalShadowMap.SHADOW_MAP_UNIT);
        if (shadowMapArray != -1) {
            glUniform1i(shadowMapArray, DirectionalShadowMap.SHADOW_MAP_UNIT);
        }
        if (shadowsEnabled != -1) {
            glUniform1i(shadowsEnabled, shadowSamplingEnabled ? 1 : 0);
        }

        int cc = shadowMap.getCascadeCount();
        Matrix4f[] mats = shadowMap.getLightSpaceMatrices();
        Matrix4f fallback = mats[Math.max(0, cc - 1)];
        for (int i = 0; i < 4; i++) {
            if (lightSpaceMatrix[i] == -1) {
                continue;
            }
            Matrix4f src = i < cc ? mats[i] : fallback;
            padded_light_matrices[i].set(src);
            shader.setUniformMat4At(lightSpaceMatrix[i], padded_light_matrices[i]);
        }

        if (cc > 1 && cascadeSplitDistance != -1) {
            float[] src = shadowMap.getCascadeSplitDistances();
            int n = cc - 1;
            System.arraycopy(src, 0, cascade_split_pad, 0, n);
            for (int i = n; i < 3; i++) {
                cascade_split_pad[i] = GameConfig.FAR_PLANE;
            }
            shader.setUniformFloatArrayAt(cascadeSplitDistance, cascade_split_pad, 3);
        }
        if (shadowCascadeCount != -1) {
            glUniform1i(shadowCascadeCount, cc);
        }
        if (shadowMapTexelSize != -1) {
            float inv = 1f / shadowMap.getSize();
            glUniform2f(shadowMapTexelSize, inv, inv);
        }

        RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
        if (shadowBiasScale != -1) {
            float bias = programId == worldProgramId ? rs.getShadowBiasWorld() : rs.getShadowBiasGltf();
            glUniform1f(shadowBiasScale, bias);
        }
        if (shadowReceiveFloor != -1) {
            glUniform1f(shadowReceiveFloor, rs.getGltfShadowReceiveFloor());
        }
        if (diagnosticNoIblOcclusion != -1) {
            glUniform1i(diagnosticNoIblOcclusion, GameConfig.effectiveDiagnosticGltfNoIblOcclusion() ? 1 : 0);
        }
        if (shadowPcfUseShadingNormal != -1) {
            glUniform1i(shadowPcfUseShadingNormal, rs.isGltfShadowPcfUseShadingNormal() ? 1 : 0);
        }

        if (brdfLut != -1 && brdfLutTexture != null) {
            glActiveTexture(GL_TEXTURE0 + WorldRenderer.BRDF_LUT_UNIT);
            glBindTexture(GL_TEXTURE_2D, brdfLutTexture.id());
            glUniform1i(brdfLut, WorldRenderer.BRDF_LUT_UNIT);
            if (hasBrdfLut != -1) {
                glUniform1i(hasBrdfLut, 1);
            }
        }

        if (irradianceMap != -1 && environmentIbl != null) {
            glActiveTexture(GL_TEXTURE0 + EnvironmentIbl.IRRADIANCE_UNIT);
            glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getIrradianceMap());
            glUniform1i(irradianceMap, EnvironmentIbl.IRRADIANCE_UNIT);
            if (prefilterMap != -1) {
                glActiveTexture(GL_TEXTURE0 + EnvironmentIbl.PREFILTER_UNIT);
                glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getPrefilterMap());
                glUniform1i(prefilterMap, EnvironmentIbl.PREFILTER_UNIT);
            }
            if (prefilterMaxMip != -1) {
                glUniform1f(prefilterMaxMip, environmentIbl.getPrefilterMaxMip());
            }
            if (iblIntensity != -1) {
                glUniform1f(iblIntensity, lit.iblIntensity());
            }
        }

        if (emissiveBoost != -1) {
            glUniform1f(emissiveBoost, lit.emissiveBoost());
        }
    }
}
