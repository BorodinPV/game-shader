#version 330 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
layout(location = 3) in vec4 aVertexColor;

uniform mat4 model;
uniform mat4 lightSpaceMatrix;
uniform int usePlanarUv;
uniform vec4 uvBounds;

void main()
{
    vec3 worldPos = vec3(model * vec4(aPos, 1.0));
    gl_Position = lightSpaceMatrix * vec4(worldPos, 1.0);
}
