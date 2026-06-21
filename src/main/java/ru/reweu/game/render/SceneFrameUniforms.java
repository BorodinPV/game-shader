package ru.reweu.game.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Общие per-frame униформы для освещённых шейдеров (ландшафт {@code fragment_shader.glsl},
 * glTF PBR {@code pbr_gltf.frag}).
 */
public final class SceneFrameUniforms {

    private static final Map<Long, LitFrameUniformCache> AD_HOC_CACHE = new ConcurrentHashMap<>();

    private SceneFrameUniforms() {
    }

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
        long key = ((long) shaderProgram.getProgramId() << 32) | (worldShaderProgramId & 0xffffffffL);
        LitFrameUniformCache cache = AD_HOC_CACHE.computeIfAbsent(key, k ->
            shaderProgram.getProgramId() == worldShaderProgramId
                ? LitFrameUniformCache.forWorld(shaderProgram)
                : LitFrameUniformCache.forGltf(shaderProgram, worldShaderProgramId)
        );
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
}
