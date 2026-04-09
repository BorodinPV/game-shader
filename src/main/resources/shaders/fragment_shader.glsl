#version 330 core

#include "/shaders/include/tonemap_exposure.glsl"
#include "/shaders/include/shadow_pcf_csm_world.glsl"

out vec4 FragColor;

in vec2 TexCoord;
in vec3 Normal;
in vec3 FragPos;
in vec4 VertexColor;

uniform sampler2D texture1;
uniform int useSpecularTexture;
uniform sampler2D textureMetallicRoughness;

uniform vec3 sunDirection;
uniform vec3 sunColor;
uniform float sunIntensity;
uniform vec3 ambientColor;
uniform vec3 cameraPosition;
uniform float textureScale;

uniform int useDiffuseTexture;
uniform vec3 diffuseColor;
uniform float materialAlpha;
uniform int opaqueGeometryPass;

uniform int shadowsEnabled;

uniform float exposure;
uniform vec3 fillDirection;
uniform vec3 fillColor;
uniform float fillStrength;
uniform vec3 skyAmbientColor;
uniform vec3 groundAmbientColor;
uniform float hemiMix;
uniform float ambientHemiScale;
uniform float ambientHemiHemi;

void main()
{
    vec3 norm = normalize(Normal);
    vec3 V = normalize(cameraPosition - FragPos);
    float ts = max(textureScale, 1e-4);

    float roughness = 0.94;
    float metallic = 0.0;
    if (useSpecularTexture != 0) {
        vec3 mr = texture(textureMetallicRoughness, TexCoord * ts).rgb;
        roughness = clamp(mr.g, 0.04, 1.0);
        metallic = clamp(mr.b, 0.0, 1.0);
    }

    /* sunDirection — от точки к солнцу (как в SceneLighting); согласовано с ray_trace.comp и sky_pass */
    vec3 Lsun = normalize(sunDirection);
    float sunDiff = max(dot(norm, Lsun), 0.0);
    float sh = shadowPcfCsmWorld(norm, Lsun, FragPos, cameraPosition, shadowsEnabled);
    vec3 sunDiffuse = sunDiff * sunColor * sunIntensity * sh;

    vec3 fillDir = normalize(fillDirection);
    float fillDiff = max(dot(norm, fillDir), 0.0);
    vec3 fillLight = fillDiff * fillColor * fillStrength;

    float hemi = norm.y * 0.5 + 0.5;
    vec3 hemiAmb = mix(groundAmbientColor, skyAmbientColor, hemi);
    vec3 ambientMix = mix(ambientColor, hemiAmb, hemiMix);

    vec3 lighting = ambientMix * (ambientHemiScale + ambientHemiHemi * hemi) + sunDiffuse + fillLight;

    vec3 albedo;
    float alpha = 1.0;
    if (useDiffuseTexture != 0) {
        vec4 texSample = texture(texture1, TexCoord * ts);
        vec3 tc = texSample.rgb;
        alpha = texSample.a * materialAlpha;
        vec3 tint = diffuseColor;
        if (dot(tint, tint) < 1e-8) {
            tint = vec3(1.0);
        }
        albedo = tc * tint;
    } else {
        vec3 dc = diffuseColor;
        if (dot(dc, dc) < 1e-8) {
            dc = vec3(0.75);
        }
        albedo = dc;
        alpha = materialAlpha;
    }

    albedo *= VertexColor.rgb;
    alpha *= VertexColor.a;

    vec3 H = normalize(Lsun + V);
    float gloss = 1.0 - roughness;
    float shininess = mix(4.0, 96.0, gloss * gloss);
    float specPow = pow(max(dot(norm, H), 0.0), shininess);
    float specStr = mix(0.02, 0.28, gloss) * mix(0.2, 1.0, metallic + 0.35);
    vec3 specSun = sunColor * sunIntensity * specPow * specStr * sh;

    vec3 R = reflect(-V, norm);
    float envElev = R.y * 0.5 + 0.5;
    vec3 envCol = mix(vec3(0.22, 0.24, 0.28), vec3(0.72, 0.78, 0.92), envElev);
    float fresnel = pow(1.0 - max(dot(norm, V), 0.0), 3.2);
    float envMix = fresnel * mix(0.04, 0.42, metallic + 0.25) * (0.25 + 0.35 * gloss);
    vec3 envSpec = envCol * envMix;

    vec3 diffuseContrib = albedo * lighting;
    vec3 linearColor = diffuseContrib + specSun + envSpec;
    vec3 mapped = tonemapDisplay(linearColor, exposure);
    if (opaqueGeometryPass != 0) {
        alpha = 1.0;
    }
    FragColor = vec4(mapped, alpha);
}
