// Общий путь morph + skin для pbr_gltf.vert и pbr_gltf_shadow.vert (диагностика H3).
uniform int u_instancedBatchCount;
uniform mat4 u_modelBatch[64];

mat4 pbrGltfModelOrBatch()
{
    if (u_instancedBatchCount > 0) {
        return u_modelBatch[gl_InstanceID];
    }
    return model;
}

void pbrGltfSkinnedLocal(out vec4 skinnedLocal, out mat4 skinMat)
{
    vec3 pos = aPos;
    if (u_morphCount > 0) {
        pos += u_morphWeightsA.x * m0;
        if (u_morphCount > 1) pos += u_morphWeightsA.y * m1;
        if (u_morphCount > 2) pos += u_morphWeightsA.z * m2;
        if (u_morphCount > 3) pos += u_morphWeightsA.w * m3;
    }

    skinMat = mat4(1.0);
    if (u_useSkinning != 0) {
        skinMat = mat4(0.0);
        for (int i = 0; i < 4; i++) {
            int ji = int(aJoints[i] + 0.5);
            skinMat += aWeights[i] * u_jointMatrix[ji];
        }
    }

    skinnedLocal = skinMat * vec4(pos, 1.0);
}

vec4 pbrGltfWorld4FromAttributes()
{
    vec4 skinnedLocal;
    mat4 skinMat;
    pbrGltfSkinnedLocal(skinnedLocal, skinMat);
    return pbrGltfModelOrBatch() * skinnedLocal;
}
