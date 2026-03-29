package ru.reweu.game.render;

import org.joml.Matrix4f;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Доступ к шейдеру мира и ресурсам кадра; per-frame униформы — {@link SceneFrameUniforms}.
 */
public final class WorldRenderer {

    public static final int BRDF_LUT_UNIT = 6;

    private final ShaderProgram worldShaderProgram;
    private final DirectionalShadowMap shadowMap;
    private final BrdfLutTexture brdfLut;
    private final EnvironmentIbl environmentIbl;

    public WorldRenderer(
        ShaderProgram worldShaderProgram,
        DirectionalShadowMap shadowMap,
        BrdfLutTexture brdfLut,
        EnvironmentIbl environmentIbl
    ) {
        this.worldShaderProgram = worldShaderProgram;
        this.shadowMap = shadowMap;
        this.brdfLut = brdfLut;
        this.environmentIbl = environmentIbl;
    }

    public ShaderProgram getWorldShaderProgram() {
        return worldShaderProgram;
    }

    public DirectionalShadowMap getShadowMap() {
        return shadowMap;
    }

    public BrdfLutTexture getBrdfLut() {
        return brdfLut;
    }

    public EnvironmentIbl getEnvironmentIbl() {
        return environmentIbl;
    }

    public void prepareFrame(LightingFrame lit, Matrix4f view, Matrix4f projection) {
        prepareFrameFor(lit, worldShaderProgram, view, projection, true);
    }

    /**
     * @param shadowSamplingEnabled выборка shadow map в шейдере мира (и общие униформы кадра).
     */
    public void prepareFrame(
        LightingFrame lit,
        Matrix4f view,
        Matrix4f projection,
        boolean shadowSamplingEnabled
    ) {
        prepareFrameFor(lit, worldShaderProgram, view, projection, shadowSamplingEnabled);
    }

    /**
     * Свет, камера (из view), тени, IBL, exposure — один раз перед батчем мешей
     * (ландшафт или glTF PBR). Материалы glTF задаются в {@link ru.reweu.game.gltf.GltfPbrRenderer}.
     */
    public void prepareFrameFor(LightingFrame lit, ShaderProgram shaderProgram, Matrix4f view, Matrix4f projection) {
        prepareFrameFor(lit, shaderProgram, view, projection, true);
    }

    /**
     * @param shadowSamplingEnabled для glTF: {@code !GameConfig.GLTF_DEBUG_DISABLE_SHADOWS}; для ландшафта обычно {@code true}.
     */
    public void prepareFrameFor(
        LightingFrame lit,
        ShaderProgram shaderProgram,
        Matrix4f view,
        Matrix4f projection,
        boolean shadowSamplingEnabled
    ) {
        SceneFrameUniforms.bindLitFrame(
            lit,
            shaderProgram,
            view,
            projection,
            worldShaderProgram.getProgramId(),
            shadowMap,
            brdfLut,
            environmentIbl,
            shadowSamplingEnabled
        );
    }
}
