// PCF 5×5 для glTF: больший базовый bias; u_shadowBiasScale — GAME_SHADOW_BIAS_SCALE.
uniform float u_shadowBiasScale;

float shadowPcf3x3(
    vec3 N,
    vec3 L,
    vec4 fragPosLightSpace,
    sampler2D shadowMap,
    int shadowsEnabled)
{
    if (shadowsEnabled == 0) {
        return 1.0;
    }
    vec3 proj = fragPosLightSpace.xyz / fragPosLightSpace.w;
    proj = proj * 0.5 + 0.5;
    if (proj.z > 1.0 || proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0) {
        return 1.0;
    }
    float ndotl = max(dot(N, L), 0.0);
    float bias = max(0.004 * (1.0 - ndotl), 0.0015) * u_shadowBiasScale;
    float current = proj.z;
    float s = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            float d = texture(shadowMap, proj.xy + vec2(float(x), float(y)) * texel).r;
            s += current - bias > d ? 0.0 : 1.0;
        }
    }
    return s / 25.0;
}
