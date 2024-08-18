#version 330 core

out vec4 FragColor;

in vec2 TexCoord;
in vec3 FragPos;
in vec3 Normal;

uniform sampler2D texture1;

uniform vec3 lightColor;
uniform vec3 sunDirection;
uniform vec3 sunColor;
uniform float sunIntensity;
uniform vec3 ambientColor;

// Параметры тумана
uniform vec3 fogColor;
uniform float fogDensity;
uniform vec3 cameraPos;

// Параметры атмосферы
uniform vec3 skyColor;
uniform float atmosphereStart;   // Начальная высота атмосферы
uniform float atmosphereEnd;     // Высота, где атмосфера становится полной

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

    // Расчет расстояния от фрагмента до камеры
    float distance = length(FragPos - cameraPos);

    // Уменьшение плотности тумана на высоте
    float heightFactor = clamp((FragPos.y - atmosphereStart) / (atmosphereEnd - atmosphereStart), 0.0, 1.0);

    // Экспоненциальный туман с еще большим сглаживанием
    float fogFactor = 1.0 - exp(-pow(distance * fogDensity, 2.0));  // Увеличение степени до 2.0 для сглаживания

    // Расчет цвета с учетом атмосферы
    vec3 atmosphereColor = mix(result, skyColor, heightFactor);

    // Смешивание тумана и сцены для плавного перехода
    vec3 finalColor = mix(atmosphereColor, fogColor, fogFactor);

    FragColor = vec4(finalColor, 1.0);
}
