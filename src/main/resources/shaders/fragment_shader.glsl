#version 330 core

out vec4 FragColor;

in vec2 TexCoord;
in vec3 FragPos;
in vec3 Normal;

uniform sampler2D texture1;    // Основная текстура

uniform vec3 lightColor;       // Цвет света
uniform vec3 sunDirection;     // Направление солнечного света
uniform vec3 sunColor;         // Цвет солнечного света
uniform float sunIntensity;    // Интенсивность солнечного света
uniform vec3 ambientColor;     // Цвет амбиентного освещения

void main()
{
    // Нормализуем нормали
    vec3 norm = normalize(Normal);

    // Расчет солнечного освещения
    float sunDiff = max(dot(norm, -sunDirection), 0.0);
    vec3 sunDiffuse = sunDiff * sunColor * sunIntensity;

    // Амбиентное освещение
    vec3 ambient = ambientColor;

    // Итоговое освещение
    vec3 lighting = ambient + sunDiffuse;

    // Применение освещения к текстуре
    vec3 textureColor = texture(texture1, TexCoord).rgb;
    vec3 result = textureColor * lighting;

    FragColor = vec4(result, 1.0);
}
