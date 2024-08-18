#version 330 core

layout(location = 0) in vec3 aPos;       // Положение вершины
layout(location = 1) in vec2 aTexCoord;  // Текстурные координаты
layout(location = 2) in vec3 aNormal;    // Нормаль вершины

out vec2 TexCoord;         // Передаем текстурные координаты во фрагментный шейдер
out vec3 FragPos;          // Позиция фрагмента (для расчета освещения)
out vec3 Normal;           // Нормаль (для расчета освещения)

uniform mat4 model;        // Матрица модели
uniform mat4 view;         // Матрица вида (камера)
uniform mat4 projection;   // Проекционная матрица

void main()
{
    FragPos = vec3(model * vec4(aPos, 1.0));  // Позиция фрагмента в мировом пространстве
    Normal = mat3(transpose(inverse(model))) * aNormal;  // Трансформация нормалей в мировое пространство

    gl_Position = projection * view * model * vec4(aPos, 1.0);  // Вычисляем положение вершины в пространстве экрана
    TexCoord = aTexCoord;  // Передаем текстурные координаты
}