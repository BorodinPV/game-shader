#version 330 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aTangent;
layout(location = 3) in vec2 aUv0;
layout(location = 4) in vec2 aUv1;
layout(location = 5) in vec4 aColor;
layout(location = 6) in vec4 aJoints;
layout(location = 7) in vec4 aWeights;
layout(location = 8) in vec3 m0;
layout(location = 9) in vec3 m1;
layout(location = 10) in vec3 m2;
layout(location = 11) in vec3 m3;

layout(std140) uniform JointBlock {
    mat4 u_jointMatrix[64];
};

uniform mat4 model;
uniform mat4 lightSpaceMatrix;
uniform int u_useSkinning;
uniform int u_morphCount;
uniform vec4 u_morphWeightsA;

#include "/shaders/include/pbr_gltf_world4.glsl"

void main()
{
    vec4 world4 = pbrGltfWorld4FromAttributes();
    gl_Position = lightSpaceMatrix * world4;
}
