#version 330 core
layout(location = 0) in vec3 aPos;

uniform mat4 view;
uniform mat4 projection;
uniform mat4 u_instanceModel[64];

void main()
{
    mat4 M = u_instanceModel[gl_InstanceID];
    gl_Position = projection * view * M * vec4(aPos, 1.0);
}
