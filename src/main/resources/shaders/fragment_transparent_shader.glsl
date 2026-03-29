#version 330 core

#include "/shaders/include/tonemap_exposure.glsl"

out vec4 FragColor;

in vec2 TexCoord;

uniform sampler2D diffuseTexture;
uniform float exposure;

void main()
{
    vec4 texColor = texture(diffuseTexture, TexCoord);

    float alpha = texColor.a; // Используем альфа-канал текстуры

    // Если альфа-канал отсутствует, можно использовать другой канал как альфа (например, красный)
    // float alpha = texColor.r;

    if (alpha < 0.1) discard; // Отбрасываем пиксели, которые практически прозрачны

    FragColor = vec4(tonemapDisplay(texColor.rgb, exposure), alpha);
}
