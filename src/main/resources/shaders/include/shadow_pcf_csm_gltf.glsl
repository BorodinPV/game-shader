#include "/shaders/include/shadow_csm_common.glsl"

uniform float u_shadowBiasScale;

float shadowPcfCsmGltf(
    vec3 N,
    vec3 L,
    vec3 worldPos,
    vec3 cameraPos,
    int shadowsEnabled)
{
    if (shadowsEnabled == 0) {
        return 1.0;
    }
    float dist = length(cameraPos - worldPos);
    int cascade = shadowSelectCascade(dist);
    vec4 ls = lightSpaceMatrix[cascade] * vec4(worldPos, 1.0);
    vec3 proj = ls.xyz / ls.w;
    proj = proj * 0.5 + 0.5;
    if (proj.z > 1.0 || proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0) {
        return 1.0;
    }
    float ndotl = max(dot(N, L), 0.0);
    float bias = max(0.004 * (1.0 - ndotl), 0.0015) * u_shadowBiasScale;
    float current = proj.z;
    vec2 texel = u_shadowMapTexelSize;
    float s = 0.0;
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            float d = texture(shadowMapArray, vec3(proj.xy + vec2(float(x), float(y)) * texel, float(cascade))).r;
            s += current - bias > d ? 0.0 : 1.0;
        }
    }
    return s / 25.0;
}
