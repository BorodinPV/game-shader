#version 330 core

// Должно совпадать с Mesh: interleaved pos(3), normal(3), uv(2), color(4) → locations 0–3
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
layout(location = 3) in vec4 aVertexColor;

out vec2 TexCoord;
out vec3 Normal;
out vec3 FragPos;
out vec4 VertexColor;
out vec4 FragPosLightSpace;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform mat4 lightSpaceMatrix;

// Если в OBJ не было UV (Blender экспорт без развёртки), Mesh задаёт планарную проекцию по XZ
uniform int usePlanarUv;
uniform vec4 uvBounds; // minX, minZ, rangeX, rangeZ в локальных координатах меша

void main()
{
    vec3 worldPos = vec3(model * vec4(aPos, 1.0));
    FragPos = worldPos;
    FragPosLightSpace = lightSpaceMatrix * vec4(worldPos, 1.0);
    Normal = mat3(transpose(inverse(model))) * aNormal;
    gl_Position = projection * view * vec4(worldPos, 1.0);
    VertexColor = aVertexColor;

    if (usePlanarUv != 0) {
        float rx = max(uvBounds.z, 1e-6);
        float rz = max(uvBounds.w, 1e-6);
        TexCoord = vec2((aPos.x - uvBounds.x) / rx, (aPos.z - uvBounds.y) / rz);
    } else {
        TexCoord = aTexCoord;
    }
}
