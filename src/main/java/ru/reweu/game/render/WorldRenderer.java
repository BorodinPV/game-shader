package ru.reweu.game.render;

import org.joml.Matrix4f;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Доступ к шейдеру мира и ресурсам кадра; per-frame униформы — {@link LitFrameUniformCache}.
 */
public final class WorldRenderer implements LitFrameServices {

    public static final int BRDF_LUT_UNIT = 6;

    private final ShaderProgram worldShaderProgram;
    private final DirectionalShadowMap shadowMap;
    private final BrdfLutTexture brdfLut;
    private final EnvironmentIbl environmentIbl;
    private final LitFrameUniformCache worldUniforms;
    private LitFrameUniformCache gltfUniforms;

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
        this.worldUniforms = LitFrameUniformCache.forWorld(worldShaderProgram);
    }

    public ShaderProgram getWorldShaderProgram() {
        return worldShaderProgram;
    }

    public DirectionalShadowMap getShadowMap() {
        return shadowMap;
    }

    public EnvironmentIbl getEnvironmentIbl() {
        return environmentIbl;
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
     * @param shadowSamplingEnabled для glTF: {@code !GameConfig.GLTF_DEBUG_DISABLE_SHADOWS}; для ландшафта обычно {@code true}.
     */
    public void prepareFrameFor(
        LightingFrame lit,
        ShaderProgram shaderProgram,
        Matrix4f view,
        Matrix4f projection,
        boolean shadowSamplingEnabled
    ) {
        LitFrameUniformCache cache = uniformCacheFor(shaderProgram);
        cache.bind(
            lit,
            shaderProgram,
            view,
            projection,
            shadowMap,
            brdfLut,
            environmentIbl,
            shadowSamplingEnabled
        );
    }

    private LitFrameUniformCache uniformCacheFor(ShaderProgram shaderProgram) {
        if (shaderProgram == worldShaderProgram) {
            return worldUniforms;
        }
        if (gltfUniforms == null || gltfUniforms.programId() != shaderProgram.getProgramId()) {
            gltfUniforms = LitFrameUniformCache.forGltf(shaderProgram, worldShaderProgram.getProgramId());
        }
        return gltfUniforms;
    }
}
