package ru.reweu.game.render;

import org.joml.Vector3f;

/**
 * Снимок параметров освещения на один кадр (направленный свет, fill, IBL-множители, гемисфера для world).
 * Собирается в {@link SceneLighting#frame()}; передаётся в {@link SceneFrameUniforms},
 * {@link SkyRenderer}, {@link ru.reweu.game.render.rt.RayTraceRenderer}.
 */
public record LightingFrame(
    Vector3f sunDirection,
    Vector3f sunColor,
    float sunIntensity,
    Vector3f ambientColor,
    Vector3f fillDirection,
    Vector3f fillColor,
    float fillStrengthWorld,
    float fillStrengthGltf,
    float fillSpecularStrengthGltf,
    float emissiveBoost,
    Vector3f skyAmbientColor,
    Vector3f groundAmbientColor,
    Vector3f clearColor,
    float exposure,
    float iblIntensity,
    float hemiMix,
    float worldAmbientHemiScale,
    float worldAmbientHemiHemi,
    float sunDiscScale
) {
}
