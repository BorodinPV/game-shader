#version 330 core

#include "/shaders/include/pbr_ggx.glsl"

in vec3 WorldPos;

out vec4 FragColor;

uniform samplerCube environmentMap;
uniform float roughness;
uniform float resolution;

vec2 hammersley(int i, int N)
{
    return vec2(float(i) / float(N), fract(float(i) * 0.61803398875));
}

vec3 importanceSampleGGX(vec2 Xi, vec3 N, float r)
{
    float a = r * r;
    float phi = 2.0 * PI * Xi.x;
    float cosTheta = sqrt((1.0 - Xi.y) / (1.0 + (a * a - 1.0) * Xi.y));
    float sinTheta = sqrt(max(0.0, 1.0 - cosTheta * cosTheta));
    vec3 H = vec3(cos(phi) * sinTheta, sin(phi) * sinTheta, cosTheta);
    vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, N));
    vec3 bitangent = cross(N, tangent);
    return normalize(tangent * H.x + bitangent * H.y + N * H.z);
}

void main()
{
    vec3 N = normalize(WorldPos);
    vec3 prefiltered = vec3(0.0);
    float totalWeight = 0.0;
    const int SAMPLE_COUNT = 64;
    for (int i = 0; i < SAMPLE_COUNT; i++) {
        vec2 Xi = hammersley(i, SAMPLE_COUNT);
        vec3 H = importanceSampleGGX(Xi, N, roughness);
        vec3 L = normalize(2.0 * dot(N, H) * H - N);
        float NdotL = max(dot(N, L), 0.0);
        if (NdotL > 0.0) {
            float NdotH = max(dot(N, H), 0.0);
            float pdf = distributionGGX(N, H, roughness) * NdotH / max(4.0 * max(NdotH, 0.001), 1e-4);
            float saTexel = 4.0 * PI / (6.0 * resolution * resolution);
            float saSample = 1.0 / (float(SAMPLE_COUNT) * pdf + 1e-4);
            float mipLevel = roughness <= 0.001 ? 0.0 : 0.5 * log2(max(saSample / saTexel, 1e-6));
            prefiltered += textureLod(environmentMap, L, mipLevel).rgb * NdotL;
            totalWeight += NdotL;
        }
    }
    FragColor = vec4(prefiltered / max(totalWeight, 0.001), 1.0);
}
