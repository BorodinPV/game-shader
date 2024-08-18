#version 330 core

out vec4 FragColor;

in vec2 TexCoord;
in vec3 Normal;
in vec3 FragPos;

uniform sampler2D diffuseTexture;
uniform sampler2D specularTexture;
uniform sampler2D normalTexture;
uniform sampler2D alphaTexture;
uniform sampler2D bumpTexture;

uniform vec3 lightPos;         // Позиция точечного источника света (можно использовать для дополнительного освещения)
uniform vec3 viewPos;          // Позиция камеры
uniform vec3 lightColor;       // Цвет света
uniform vec3 ambientColor;     // Цвет фонового освещения

uniform vec3 diffuseColor;
uniform vec3 sunDirection;     // Направление солнечного света
uniform vec3 sunColor;         // Цвет солнечного света
uniform float sunIntensity;  // Интенсивность солнечного света

uniform bool useDiffuseTexture;
uniform bool useSpecularTexture;
uniform bool useNormalTexture;
uniform bool useAlphaTexture;
uniform bool useBumpTexture;

uniform float materialAlpha;

void main()
{
    vec3 norm = normalize(Normal);

    if (useBumpTexture) {
        vec3 bumpNormal = texture(bumpTexture, TexCoord).rgb;
        bumpNormal = normalize(bumpNormal * 2.0 - 1.0);
        norm = normalize(norm + bumpNormal);
    }

    // Амбиентное освещение (фон)
    vec3 ambient = ambientColor * (useDiffuseTexture ? texture(diffuseTexture, TexCoord).rgb : diffuseColor);

    // Диффузное освещение от точечного источника
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * (useDiffuseTexture ? texture(diffuseTexture, TexCoord).rgb : diffuseColor);

    // Спекулярное освещение
    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 reflectDir = reflect(-lightDir, norm);
    float spec = 0.0;
    if (useSpecularTexture) {
        vec3 specColor = texture(specularTexture, TexCoord).rgb;
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0) * specColor.r;
    }
    vec3 specular = spec * lightColor;

    // Диффузное освещение от солнца
    float sunDiff = max(dot(norm, -sunDirection), 0.0);
    vec3 sunDiffuse = sunDiff * sunColor * diffuseColor * sunIntensity;

    vec3 result = ambient + diffuse + specular + sunDiffuse;

    // Применение альфа-канала для прозрачности
    float alpha = materialAlpha;
    if (useAlphaTexture) {
        alpha *= texture(alphaTexture, TexCoord).a;
    } else {
        alpha *= texture(diffuseTexture, TexCoord).a;
    }

    // Условие для отбрасывания пикселей с низкой прозрачностью
    if (alpha < 0.1) discard;

    FragColor = vec4(result, alpha);
}
