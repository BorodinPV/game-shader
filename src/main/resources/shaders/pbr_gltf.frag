#version 330 core

#include "/shaders/include/tonemap_exposure.glsl"
#include "/shaders/include/pbr_ggx.glsl"
#include "/shaders/include/shadow_pcf3x3_gltf.glsl"
#include "/shaders/include/gltf_texture_transform.glsl"

in vec3 vWorldPos;
in vec3 vNormal;
in vec2 vUv0;
in vec2 vUv1;
in vec4 vColor;
in mat3 vTbn;
in vec4 vFragPosLightSpace;

out vec4 FragColor;

uniform vec3 cameraPosition;
uniform vec3 sunDirection;
uniform vec3 sunColor;
uniform float sunIntensity;

/* Как у ландшафта (SceneFrameUniforms; меню «Константный / гемисферный ambient»). */
uniform vec3 ambientColor;
uniform vec3 skyAmbientColor;
uniform vec3 groundAmbientColor;
uniform float hemiMix;
uniform float ambientHemiScale;
uniform float ambientHemiHemi;

uniform float exposure;
uniform vec3 fillDirection;
uniform vec3 fillColor;
uniform float fillStrength;
uniform float u_fillSpecularStrength;

uniform sampler2D shadowMap;
uniform int shadowsEnabled;

uniform sampler2D u_brdfLut;
uniform int u_hasBrdfLut;

uniform samplerCube u_irradianceMap;
uniform samplerCube u_prefilterMap;
uniform float u_prefilterMaxMip;
uniform float u_iblIntensity;

uniform sampler2D u_baseColor;
uniform int u_hasBaseColor;
uniform vec4 u_baseColorFactor;
uniform int u_baseColorTexCoord;

uniform sampler2D u_metallicRoughness;
uniform int u_hasMetallicRoughness;
uniform float u_metallicFactor;
uniform float u_roughnessFactor;
uniform int u_metallicRoughnessTexCoord;

uniform sampler2D u_normal;
uniform int u_hasNormal;
uniform float u_normalScale;
uniform int u_normalTexCoord;

uniform sampler2D u_occlusion;
uniform int u_hasOcclusion;
uniform float u_occlusionStrength;
uniform int u_occlusionTexCoord;

uniform sampler2D u_emissive;
uniform int u_hasEmissive;
uniform vec3 u_emissiveFactor;
uniform int u_emissiveTexCoord;
/* Множитель экранного HDR для emissive; из LightingFrame (меню). Не трогает baseColor. */
uniform float u_emissiveBoost;

uniform int u_alphaMode;
uniform float u_alphaCutoff;

uniform int u_doubleSided;

/**
 * 1: эвристика «тонкое стекло» для BLEND без альфы в baseColor (нет KHR_transmission): ослабляем alpha по N·V.
 * Включается в Java для материалов с именем, содержащим «glass», без emissive-текстуры (см. Land Explorer Glass_2).
 */
uniform int u_thinGlass;

/** 0: обычный кадр; 1: мир N; 2: фактор тени. GameConfig.GLTF_DEBUG_VISUALIZE_MODE */
uniform int u_debugVisualizeMode;

/** 1: для A/B отключить occlusion только в IBL (H1). GAME_DEBUG_GLTF_NO_IBL_OCCLUSION */
uniform int u_diagnosticNoIblOcclusion;
/** 1: PCF slope-bias от N вместо Ng (H4). GAME_SHADOW_PCF_USE_SHADING_NORMAL */
uniform int u_shadowPcfUseShadingNormal;

/** Минимум sh для прямого солнца после PCF (подсветка тени). GAME_SHADOW_RECEIVE_FLOOR */
uniform float u_shadowReceiveFloor;

/** KHR_texture_transform для normalTexture (см. GltfPbrRenderer). */
uniform int u_enableNormalTexTransform;
uniform vec2 u_normalTexTransformOffset;
uniform vec2 u_normalTexTransformScale;
uniform float u_normalTexTransformRotation;

const int ALPHA_OPAQUE = 0;
const int ALPHA_MASK = 1;
const int ALPHA_BLEND = 2;

void main()
{
    vec2 uvBase = u_baseColorTexCoord != 0 ? vUv1 : vUv0;
    vec2 uvMr = u_metallicRoughnessTexCoord != 0 ? vUv1 : vUv0;
    vec2 uvN = u_normalTexCoord != 0 ? vUv1 : vUv0;
    if (u_enableNormalTexTransform != 0) {
        uvN = applyGltfTextureTransform(
            uvN,
            u_normalTexTransformOffset,
            u_normalTexTransformScale,
            u_normalTexTransformRotation
        );
    }
    vec2 uvOcc = u_occlusionTexCoord != 0 ? vUv1 : vUv0;
    vec2 uvEm = u_emissiveTexCoord != 0 ? vUv1 : vUv0;

    vec3 albedo = u_baseColorFactor.rgb;
    float alpha = u_baseColorFactor.a;
    if (u_hasBaseColor != 0) {
        vec4 t = texture(u_baseColor, uvBase);
        albedo *= t.rgb;
        alpha *= t.a;
    }
    albedo *= vColor.rgb;
    alpha *= vColor.a;

    if (u_alphaMode == ALPHA_MASK) {
        if (alpha < u_alphaCutoff) {
            discard;
        }
        alpha = 1.0;
    } else if (u_alphaMode == ALPHA_OPAQUE) {
        alpha = 1.0;
    }

    float metallic = u_metallicFactor;
    float roughness = u_roughnessFactor;
    if (u_hasMetallicRoughness != 0) {
        vec4 mr = texture(u_metallicRoughness, uvMr);
        roughness *= mr.g;
        metallic *= mr.b;
    }
    roughness = clamp(roughness, 0.04, 1.0);
    metallic = clamp(metallic, 0.0, 1.0);

    /* Интерполяция mat3 vTbn по компонентам ломает ортогональность — без Gram–Schmidt
       на зеркальных UV (типично у кузова) даёт резкий шов и «полусвет/полутьма». */
    vec3 Ng = normalize(vNormal);
    vec3 T = normalize(vTbn[0] - Ng * dot(Ng, vTbn[0]));
    vec3 B = cross(Ng, T);
    if (dot(vTbn[1], B) < 0.0) {
        B = -B;
    }
    vec3 N;
    if (u_hasNormal != 0) {
        vec3 nm = texture(u_normal, uvN).xyz * 2.0 - 1.0;
        nm.xy *= u_normalScale;
        N = normalize(T * nm.x + B * nm.y + Ng * nm.z);
    } else {
        N = Ng;
    }
    if (u_doubleSided != 0 && !gl_FrontFacing) {
        N = -N;
    }
    vec3 Nshadow = Ng;
    if (u_doubleSided != 0 && !gl_FrontFacing) {
        Nshadow = -Ng;
    }

    float occlusion = 1.0;
    if (u_hasOcclusion != 0) {
        float occ = texture(u_occlusion, uvOcc).r;
        occlusion = mix(1.0, occ, u_occlusionStrength);
    }

    vec3 emissive = u_emissiveFactor;
    if (u_hasEmissive != 0) {
        emissive *= texture(u_emissive, uvEm).rgb;
    }
    emissive *= u_emissiveBoost;

    vec3 V = normalize(cameraPosition - vWorldPos);

    /* BLEND + RGB baseColor без альфы в файле даёт alpha≈1 — смешивания с салоном нет.
       N·V по геометрической нормали: normal map на стекле иначе даёт «пятна» с alpha≈1. */
    if (u_alphaMode == ALPHA_BLEND && u_thinGlass != 0) {
        float ndv = clamp(dot(Ng, V), 0.0, 1.0);
        float edge = pow(1.0 - ndv, 1.35);
        alpha = min(alpha, mix(0.08, 0.78, edge));
    }

    /* sunDirection — от точки к солнцу; согласовано с ray_trace.comp */
    vec3 L = normalize(sunDirection);
    vec3 H = normalize(V + L);
    vec3 F0 = mix(vec3(0.04), albedo, metallic);
    float albedoLum = dot(albedo, vec3(0.299, 0.587, 0.114));
    if (metallic > 0.92 && albedoLum < 0.07) {
        F0 = vec3(0.04);
    }

    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.0);

    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    /* Спекуляр — Fresnel по полувектору к солнцу. Диффуз (прямой + IBL) — по N·V:
       один kD(H,V,L) давал лишнюю «просадку» яркости при смене ракурса (отъезд камеры). */
    vec3 F_spec = fresnelSchlick(max(dot(H, V), 0.0), F0);
    /* IBL-диффуз: Schlick по N·V (split-sum). Прямое солнце — Ламберт без kD(N·V): иначе на кривых
       кузовах яркость сильно зависит от ракурса («фонарик»), тогда как ландшафт в fragment_shader.glsl
       без этого множителя. */
    float NdotV_diff = max(NdotV, 0.22);
    vec3 kD_ibl = (vec3(1.0) - fresnelSchlick(NdotV_diff, F0)) * (1.0 - metallic);

    vec3 numerator = NDF * G * F_spec;
    float denom = 4.0 * max(NdotV, 0.001) * max(NdotL, 0.001);
    vec3 specular = numerator / max(denom, 1e-8);

    vec3 diffuseSun = (1.0 - metallic) * albedo / PI;
    vec3 nPcf = (u_shadowPcfUseShadingNormal != 0) ? N : Nshadow;
    float sh = shadowPcf3x3(nPcf, L, vFragPosLightSpace, shadowMap, shadowsEnabled);
    /* Эмиссив (фары и т.п.) не должны «гаснуть» из‑за ложной тени на тонкой геометрии. */
    float emissiveLum = dot(emissive, vec3(0.299, 0.587, 0.114));
    sh = mix(sh, 1.0, smoothstep(0.0, 0.2, emissiveLum) * 0.92);
    float shSun = max(sh, u_shadowReceiveFloor);

    vec3 Lo = (diffuseSun + specular) * sunColor * sunIntensity * NdotL * shSun;

    if (u_debugVisualizeMode == 1) {
        FragColor = vec4(N * 0.5 + 0.5, alpha);
        return;
    }
    if (u_debugVisualizeMode == 2) {
        FragColor = vec4(vec3(sh), 1.0);
        return;
    }

    vec3 irradiance = texture(u_irradianceMap, N).rgb;
    vec3 diffuseIbl = irradiance * albedo * kD_ibl * occlusion * u_iblIntensity;

    float occlusionSpec = mix(0.38, 1.0, occlusion);

    vec3 R = reflect(-V, N);
    float lod = roughness * u_prefilterMaxMip;
    vec3 prefiltered = textureLod(u_prefilterMap, R, lod).rgb;
    vec2 envBrdf = vec2(1.0 - roughness * 0.5, roughness * 0.35);
    if (u_hasBrdfLut != 0) {
        envBrdf = texture(u_brdfLut, vec2(NdotV, roughness)).rg;
    }
    vec3 specIbl = prefiltered * (F0 * envBrdf.x + envBrdf.y) * u_iblIntensity * occlusionSpec;

    vec3 fillDir = normalize(fillDirection);
    float fillDiff = max(dot(N, fillDir), 0.0);
    vec3 fillLight = fillDiff * fillColor * fillStrength * albedo;

    float fillRough = 0.42;
    vec3 Hfill = normalize(V + fillDir);
    float NdotLfill = max(dot(N, fillDir), 0.0);
    float NDFfill = distributionGGX(N, Hfill, fillRough);
    float Gfill = geometrySmith(N, V, fillDir, fillRough);
    vec3 Ffill = fresnelSchlick(max(dot(Hfill, V), 0.0), F0);
    float denomFill = 4.0 * max(NdotV, 0.001) * max(NdotLfill, 0.001);
    vec3 specFill = (NDFfill * Gfill * Ffill / max(denomFill, 1e-8))
        * fillColor * fillStrength * u_fillSpecularStrength;

    /* Та же логика, что fragment_shader.glsl (hemi + константа); иначе меню гемисферы не влияет на glTF. */
    float hemi = N.y * 0.5 + 0.5;
    vec3 hemiAmb = mix(groundAmbientColor, skyAmbientColor, hemi);
    vec3 ambientMix = mix(ambientColor, hemiAmb, hemiMix);
    float ambScale = ambientHemiScale + ambientHemiHemi * hemi;
    vec3 sceneDiffuseAmbient = albedo * ambientMix * ambScale * 0.45;
    vec3 color = diffuseIbl + Lo + fillLight + specFill + emissive + specIbl + sceneDiffuseAmbient;
    vec3 mapped = tonemapDisplay(color, exposure);
    FragColor = vec4(mapped, alpha);
}
