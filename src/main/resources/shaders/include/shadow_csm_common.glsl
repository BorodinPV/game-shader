uniform mat4 lightSpaceMatrix[4];
uniform float cascadeSplitDistance[3];
uniform int shadowCascadeCount;
uniform vec2 u_shadowMapTexelSize;

uniform sampler2DArray shadowMapArray;

int shadowSelectCascade(float dist) {
    int c = 0;
    if (shadowCascadeCount > 1 && dist >= cascadeSplitDistance[0]) {
        c = 1;
    }
    if (shadowCascadeCount > 2 && dist >= cascadeSplitDistance[1]) {
        c = 2;
    }
    if (shadowCascadeCount > 3 && dist >= cascadeSplitDistance[2]) {
        c = 3;
    }
    return min(c, shadowCascadeCount - 1);
}
