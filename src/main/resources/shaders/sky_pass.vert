#version 330 core

/**
 * Полноэкранный треугольник без VBO (gl_VertexID): корректное направление луча в фрагменте,
 * без швов между гранями куба и без привязки куба к началу координат.
 */
const vec2 verts[3] = vec2[](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));

out vec2 vNdc;

void main()
{
    vNdc = verts[gl_VertexID];
    gl_Position = vec4(vNdc, 0.0, 1.0);
}
