#version 330 core

#include "/shaders/include/spherical_equirect_uv.glsl"

in vec3 WorldPos;

out vec4 FragColor;

uniform sampler2D equirectMap;

void main()
{
    vec3 dir = normalize(WorldPos);
    vec2 uv = sampleSphericalMap(dir);
    vec3 color = texture(equirectMap, uv).rgb;
    FragColor = vec4(color, 1.0);
}
