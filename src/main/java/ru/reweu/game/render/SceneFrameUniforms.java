package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Общие per-frame униформы для освещённых шейдеров (ландшафт {@code fragment_shader.glsl},
 * glTF PBR {@code pbr_gltf.frag}): направленный свет, fill, гемисфера (world), exposure,
 * тени, IBL (glTF). {@code ambientColor}, гемисфера и {@code hemiMix} — также в {@code pbr_gltf.frag}
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

        Vector3f clear = lit.clearColor();
        glClearColor(clear.x, clear.y, clear.z, 1.0f);

        int pid = shaderProgram.getProgramId();
        Vector3f sunDirection = lit.sunDirection();
        glUniform3f(glGetUniformLocation(pid, "sunDirection"), sunDirection.x, sunDirection.y, sunDirection.z);
        Vector3f sunColor = lit.sunColor();
        glUniform3f(glGetUniformLocation(pid, "sunColor"), sunColor.x, sunColor.y, sunColor.z);
        glUniform1f(glGetUniformLocation(pid, "sunIntensity"), lit.sunIntensity());
        Vector3f amb = lit.ambientColor();
        glUniform3f(glGetUniformLocation(pid, "ambientColor"), amb.x, amb.y, amb.z);

        Vector3f fillDir = lit.fillDirection();
        glUniform3f(glGetUniformLocation(pid, "fillDirection"), fillDir.x, fillDir.y, fillDir.z);
        Vector3f fillCol = lit.fillColor();
        glUniform3f(glGetUniformLocation(pid, "fillColor"), fillCol.x, fillCol.y, fillCol.z);
        int fillStr = glGetUniformLocation(pid, "fillStrength");
        if (fillStr != -1) {
            float fs = pid == worldShaderProgramId
                ? lit.fillStrengthWorld()
                : lit.fillStrengthGltf();
            glUniform1f(fillStr, fs);
        }
        int fillSpec = glGetUniformLocation(pid, "u_fillSpecularStrength");
        if (fillSpec != -1) {
            glUniform1f(fillSpec, lit.fillSpecularStrengthGltf());
        }

        int sky = glGetUniformLocation(pid, "skyAmbientColor");
        if (sky != -1) {
            Vector3f sk = lit.skyAmbientColor();
            glUniform3f(sky, sk.x, sk.y, sk.z);
        }
        int ground = glGetUniformLocation(pid, "groundAmbientColor");
        if (ground != -1) {
            Vector3f gr = lit.groundAmbientColor();
            glUniform3f(ground, gr.x, gr.y, gr.z);
        }
        int hemiMix = glGetUniformLocation(pid, "hemiMix");
        if (hemiMix != -1) {
            glUniform1f(hemiMix, lit.hemiMix());
        }
        int ahs = glGetUniformLocation(pid, "ambientHemiScale");
        if (ahs != -1) {
            glUniform1f(ahs, lit.worldAmbientHemiScale());
        }
        int ahh = glGetUniformLocation(pid, "ambientHemiHemi");
        if (ahh != -1) {
            glUniform1f(ahh, lit.worldAmbientHemiHemi());
        }
        int exp = glGetUniformLocation(pid, "exposure");
        if (exp != -1) {
            glUniform1f(exp, lit.exposure());
        }

        shaderProgram.setUniform("textureScale", GameConfig.LANDSCAPE_TEXTURE_SCALE);
        shaderProgram.setUniform("view", view);
        shaderProgram.setUniform("projection", projection);
        shaderProgram.setUniform("model", new Matrix4f());

        int camLoc = glGetUniformLocation(pid, "cameraPosition");
        if (camLoc != -1) {
            Vector3f cam = new Matrix4f(view).invert(new Matrix4f()).getTranslation(new Vector3f());
            glUniform3f(camLoc, cam.x, cam.y, cam.z);
        }

        shadowMap.bindForReading(DirectionalShadowMap.SHADOW_MAP_UNIT);
        int sm = glGetUniformLocation(pid, "shadowMap");
        if (sm != -1) {
            glUniform1i(sm, DirectionalShadowMap.SHADOW_MAP_UNIT);
        }
        int se = glGetUniformLocation(pid, "shadowsEnabled");
        if (se != -1) {
            glUniform1i(se, shadowSamplingEnabled ? 1 : 0);
        }
        shaderProgram.setUniform("lightSpaceMatrix", shadowMap.getLightSpaceMatrix());

        RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
        int sbs = glGetUniformLocation(pid, "u_shadowBiasScale");
        if (sbs != -1) {
            float bias = pid == worldShaderProgramId ? rs.getShadowBiasWorld() : rs.getShadowBiasGltf();
            glUniform1f(sbs, bias);
        }
        int srf = glGetUniformLocation(pid, "u_shadowReceiveFloor");
        if (srf != -1) {
            glUniform1f(srf, rs.getGltfShadowReceiveFloor());
        }

        int diagOcc = glGetUniformLocation(pid, "u_diagnosticNoIblOcclusion");
        if (diagOcc != -1) {
            glUniform1i(diagOcc, GameConfig.effectiveDiagnosticGltfNoIblOcclusion() ? 1 : 0);
        }
        int pcfShadeN = glGetUniformLocation(pid, "u_shadowPcfUseShadingNormal");
        if (pcfShadeN != -1) {
            glUniform1i(pcfShadeN, rs.isGltfShadowPcfUseShadingNormal() ? 1 : 0);
        }

        int brdfLoc = glGetUniformLocation(pid, "u_brdfLut");
        if (brdfLoc != -1 && brdfLut != null) {
            glActiveTexture(GL_TEXTURE0 + WorldRenderer.BRDF_LUT_UNIT);
            glBindTexture(GL_TEXTURE_2D, brdfLut.id());
            glUniform1i(brdfLoc, WorldRenderer.BRDF_LUT_UNIT);
            int hasBrdf = glGetUniformLocation(pid, "u_hasBrdfLut");
            if (hasBrdf != -1) {
                glUniform1i(hasBrdf, 1);
            }
        }

        int irLoc = glGetUniformLocation(pid, "u_irradianceMap");
        if (irLoc != -1 && environmentIbl != null) {
            glActiveTexture(GL_TEXTURE0 + EnvironmentIbl.IRRADIANCE_UNIT);
            glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getIrradianceMap());
            glUniform1i(irLoc, EnvironmentIbl.IRRADIANCE_UNIT);
            int prLoc = glGetUniformLocation(pid, "u_prefilterMap");
            if (prLoc != -1) {
                glActiveTexture(GL_TEXTURE0 + EnvironmentIbl.PREFILTER_UNIT);
                glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getPrefilterMap());
                glUniform1i(prLoc, EnvironmentIbl.PREFILTER_UNIT);
            }
            int pm = glGetUniformLocation(pid, "u_prefilterMaxMip");
            if (pm != -1) {
                glUniform1f(pm, environmentIbl.getPrefilterMaxMip());
            }
            int iblI = glGetUniformLocation(pid, "u_iblIntensity");
            if (iblI != -1) {
                glUniform1f(iblI, lit.iblIntensity());
            }
        }

        int emissiveBoost = glGetUniformLocation(pid, "u_emissiveBoost");
        if (emissiveBoost != -1) {
            glUniform1f(emissiveBoost, lit.emissiveBoost());
        }
    }
}
