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
uniform mat4 view;
uniform mat4 projection;
uniform mat4 lightSpaceMatrix;
uniform int u_useSkinning;
uniform int u_morphCount;
uniform vec4 u_morphWeightsA;

#include "/shaders/include/pbr_gltf_world4.glsl"

out vec3 vWorldPos;
out vec3 vNormal;
out vec2 vUv0;
out vec2 vUv1;
out vec4 vColor;
out mat3 vTbn;
out vec4 vFragPosLightSpace;

void main()
{
    vec4 skinnedLocal;
    mat4 skinMat;
    pbrGltfSkinnedLocal(skinnedLocal, skinMat);
    vec4 world4 = model * skinnedLocal;
    vWorldPos = world4.xyz;

    mat3 model3 = mat3(model);
    vec3 nLoc;
    vec3 tLoc;
    if (u_useSkinning != 0) {
        mat3 sm3 = mat3(skinMat);
        mat3 normalMat = transpose(inverse(sm3));
        nLoc = normalize(normalMat * aNormal);
        tLoc = normalize(sm3 * aTangent.xyz);
        tLoc = normalize(tLoc - nLoc * dot(nLoc, tLoc));
    } else {
        nLoc = normalize(aNormal);
        tLoc = normalize(aTangent.xyz);
    }
    vec3 nWorld = normalize(model3 * nLoc);
    vec3 tWorld = normalize(model3 * tLoc);
    tWorld = normalize(tWorld - nWorld * dot(nWorld, tWorld));
    vec3 bWorld = cross(nWorld, tWorld) * aTangent.w;
    vTbn = mat3(tWorld, bWorld, nWorld);

    vNormal = nWorld;
    vUv0 = aUv0;
    vUv1 = aUv1;
    vColor = aColor;
    vFragPosLightSpace = lightSpaceMatrix * world4;
    gl_Position = projection * view * world4;
}
