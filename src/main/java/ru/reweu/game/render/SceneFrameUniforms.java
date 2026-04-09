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
 * Общие per-frame униформы для освещённых шейдеров (ландшафт {@code fragment_shader.glsl},
 * glTF PBR {@code pbr_gltf.frag}): направленный свет, fill, гемисфера (world), exposure,
 * тени, IBL (glTF). Цвет очистки буфера задаётся в {@link ru.reweu.game.Game3d} до {@code glClear}, не здесь.
 * {@code ambientColor}, гемисфера и {@code hemiMix} — также в {@code pbr_gltf.frag}
 * (диффуз «сцены» рядом с IBL). Вызывается один раз на кадр на активной программе до отрисовки мешей.
 *
 * <p>Согласовано с {@code docs/RENDERING.md} и glTF 2.0: материал (baseColor, MR, …) задаётся
 * отдельно в {@link ru.reweu.game.gltf.GltfPbrRenderer}; {@code u_emissiveBoost} — из
 * {@link LightingFrame#emissiveBoost()} (меню «Emissive»); occlusion в шейдере только на
 * косвенный свет (§3.9.3).
 *
 * <p>Конвенция вектора солнца: {@code sunDirection} — направление <em>от точки сцены к солнцу</em>
 * ({@link LightingFrame#sunDirection()}). В шейдерах для диффуза/спекуляра/теней используется
 * тот же вектор {@code normalize(sunDirection)} (без смены знака).
 */
public final class SceneFrameUniforms {

    private static final Matrix4f IDENTITY_MODEL = new Matrix4f();
    private static final Matrix4f VIEW_INVERSE = new Matrix4f();
    /** Ссылки на матрицы каскадов для {@code lightSpaceMatrix[0..3]} без аллокаций на кадр. */
    private static final Matrix4f[] PADDED_LIGHT_MATRICES = new Matrix4f[4];
    private static final float[] CASCADE_SPLIT_PAD = new float[3];

    private SceneFrameUniforms() {
    }

    /**
     * @param lit снимок освещения на кадр ({@link SceneLighting#frame()})
     * @param worldShaderProgramId id программы «мир» (vertex/fragment ландшафта); влияет на
     *                             {@link LightingFrame#fillStrengthWorld()} vs {@link LightingFrame#fillStrengthGltf()}.
     * @param shadowSamplingEnabled {@code false}: выборка теней отключена ({@link GameConfig#GLTF_DEBUG_DISABLE_SHADOWS} для glTF).
     */
    public static void bindLitFrame(
        LightingFrame lit,
        ShaderProgram shaderProgram,
        Matrix4f view,
        Matrix4f projection,
        int worldShaderProgramId,
        DirectionalShadowMap shadowMap,
        BrdfLutTexture brdfLut,
        EnvironmentIbl environmentIbl,
        boolean shadowSamplingEnabled
    ) {
        shaderProgram.use();

        int pid = shaderProgram.getProgramId();
        Vector3f sunDirection = lit.sunDirection();
        glUniform3f(ShaderProgram.uniformLocation(pid, "sunDirection"), sunDirection.x, sunDirection.y, sunDirection.z);
        Vector3f sunColor = lit.sunColor();
        glUniform3f(ShaderProgram.uniformLocation(pid, "sunColor"), sunColor.x, sunColor.y, sunColor.z);
        glUniform1f(ShaderProgram.uniformLocation(pid, "sunIntensity"), lit.sunIntensity());
        Vector3f amb = lit.ambientColor();
        glUniform3f(ShaderProgram.uniformLocation(pid, "ambientColor"), amb.x, amb.y, amb.z);

        Vector3f fillDir = lit.fillDirection();
        glUniform3f(ShaderProgram.uniformLocation(pid, "fillDirection"), fillDir.x, fillDir.y, fillDir.z);
        Vector3f fillCol = lit.fillColor();
        glUniform3f(ShaderProgram.uniformLocation(pid, "fillColor"), fillCol.x, fillCol.y, fillCol.z);
        int fillStr = ShaderProgram.uniformLocation(pid, "fillStrength");
        if (fillStr != -1) {
            float fs = pid == worldShaderProgramId
                ? lit.fillStrengthWorld()
                : lit.fillStrengthGltf();
            glUniform1f(fillStr, fs);
        }
        int fillSpec = ShaderProgram.uniformLocation(pid, "u_fillSpecularStrength");
        if (fillSpec != -1) {
            glUniform1f(fillSpec, lit.fillSpecularStrengthGltf());
        }

        int sky = ShaderProgram.uniformLocation(pid, "skyAmbientColor");
        if (sky != -1) {
            Vector3f sk = lit.skyAmbientColor();
            glUniform3f(sky, sk.x, sk.y, sk.z);
        }
        int ground = ShaderProgram.uniformLocation(pid, "groundAmbientColor");
        if (ground != -1) {
            Vector3f gr = lit.groundAmbientColor();
            glUniform3f(ground, gr.x, gr.y, gr.z);
        }
        int hemiMix = ShaderProgram.uniformLocation(pid, "hemiMix");
        if (hemiMix != -1) {
            glUniform1f(hemiMix, lit.hemiMix());
        }
        int ahs = ShaderProgram.uniformLocation(pid, "ambientHemiScale");
        if (ahs != -1) {
            glUniform1f(ahs, lit.worldAmbientHemiScale());
        }
        int ahh = ShaderProgram.uniformLocation(pid, "ambientHemiHemi");
        if (ahh != -1) {
            glUniform1f(ahh, lit.worldAmbientHemiHemi());
        }
        int exp = ShaderProgram.uniformLocation(pid, "exposure");
        if (exp != -1) {
            glUniform1f(exp, lit.exposure());
        }

        shaderProgram.setUniform("textureScale", GameConfig.LANDSCAPE_TEXTURE_SCALE);
        shaderProgram.setUniform("view", view);
        shaderProgram.setUniform("projection", projection);
        shaderProgram.setUniform("model", IDENTITY_MODEL);

        int camLoc = ShaderProgram.uniformLocation(pid, "cameraPosition");
        if (camLoc != -1) {
            Vector3f cam = view.invert(VIEW_INVERSE).getTranslation(new Vector3f());
            glUniform3f(camLoc, cam.x, cam.y, cam.z);
        }

        shadowMap.bindForReadingArray(DirectionalShadowMap.SHADOW_MAP_UNIT);
        int sma = ShaderProgram.uniformLocation(pid, "shadowMapArray");
        if (sma != -1) {
            glUniform1i(sma, DirectionalShadowMap.SHADOW_MAP_UNIT);
        }
        int se = ShaderProgram.uniformLocation(pid, "shadowsEnabled");
        if (se != -1) {
            glUniform1i(se, shadowSamplingEnabled ? 1 : 0);
        }
        int cc = shadowMap.getCascadeCount();
        Matrix4f[] mats = shadowMap.getLightSpaceMatrices();
        for (int i = 0; i < 4; i++) {
            PADDED_LIGHT_MATRICES[i] = i < cc ? mats[i] : mats[Math.max(0, cc - 1)];
        }
        shaderProgram.setUniformMat4Array("lightSpaceMatrix", PADDED_LIGHT_MATRICES, 4);
        if (cc > 1) {
            float[] src = shadowMap.getCascadeSplitDistances();
            int n = cc - 1;
            System.arraycopy(src, 0, CASCADE_SPLIT_PAD, 0, n);
            for (int i = n; i < 3; i++) {
                CASCADE_SPLIT_PAD[i] = GameConfig.FAR_PLANE;
            }
            shaderProgram.setUniformFloatArray("cascadeSplitDistance", CASCADE_SPLIT_PAD, 3);
        }
        shaderProgram.setUniform("shadowCascadeCount", cc);
        int uts = ShaderProgram.uniformLocation(pid, "u_shadowMapTexelSize");
        if (uts != -1) {
            float inv = 1f / shadowMap.getSize();
            glUniform2f(uts, inv, inv);
        }

        RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
        int sbs = ShaderProgram.uniformLocation(pid, "u_shadowBiasScale");
        if (sbs != -1) {
            float bias = pid == worldShaderProgramId ? rs.getShadowBiasWorld() : rs.getShadowBiasGltf();
            glUniform1f(sbs, bias);
        }
        int srf = ShaderProgram.uniformLocation(pid, "u_shadowReceiveFloor");
        if (srf != -1) {
            glUniform1f(srf, rs.getGltfShadowReceiveFloor());
        }

        int diagOcc = ShaderProgram.uniformLocation(pid, "u_diagnosticNoIblOcclusion");
        if (diagOcc != -1) {
            glUniform1i(diagOcc, GameConfig.effectiveDiagnosticGltfNoIblOcclusion() ? 1 : 0);
        }
        int pcfShadeN = ShaderProgram.uniformLocation(pid, "u_shadowPcfUseShadingNormal");
        if (pcfShadeN != -1) {
            glUniform1i(pcfShadeN, rs.isGltfShadowPcfUseShadingNormal() ? 1 : 0);
        }

        int brdfLoc = ShaderProgram.uniformLocation(pid, "u_brdfLut");
        if (brdfLoc != -1 && brdfLut != null) {
            glActiveTexture(GL_TEXTURE0 + WorldRenderer.BRDF_LUT_UNIT);
            glBindTexture(GL_TEXTURE_2D, brdfLut.id());
            glUniform1i(brdfLoc, WorldRenderer.BRDF_LUT_UNIT);
            int hasBrdf = ShaderProgram.uniformLocation(pid, "u_hasBrdfLut");
            if (hasBrdf != -1) {
                glUniform1i(hasBrdf, 1);
            }
        }

        int irLoc = ShaderProgram.uniformLocation(pid, "u_irradianceMap");
        if (irLoc != -1 && environmentIbl != null) {
            glActiveTexture(GL_TEXTURE0 + EnvironmentIbl.IRRADIANCE_UNIT);
            glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getIrradianceMap());
            glUniform1i(irLoc, EnvironmentIbl.IRRADIANCE_UNIT);
            int prLoc = ShaderProgram.uniformLocation(pid, "u_prefilterMap");
            if (prLoc != -1) {
                glActiveTexture(GL_TEXTURE0 + EnvironmentIbl.PREFILTER_UNIT);
                glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getPrefilterMap());
                glUniform1i(prLoc, EnvironmentIbl.PREFILTER_UNIT);
            }
            int pm = ShaderProgram.uniformLocation(pid, "u_prefilterMaxMip");
            if (pm != -1) {
                glUniform1f(pm, environmentIbl.getPrefilterMaxMip());
            }
            int iblI = ShaderProgram.uniformLocation(pid, "u_iblIntensity");
            if (iblI != -1) {
                glUniform1f(iblI, lit.iblIntensity());
            }
        }

        int emissiveBoost = ShaderProgram.uniformLocation(pid, "u_emissiveBoost");
        if (emissiveBoost != -1) {
            glUniform1f(emissiveBoost, lit.emissiveBoost());
        }
    }
}
