#version 330 core

out vec4 FragColor;

in vec2 TexCoord;

uniform sampler2D diffuseTexture;

void main()
{
    vec4 texColor = texture(diffuseTexture, TexCoord);

    float alpha = texColor.a; // Используем альфа-канал текстуры

    // Если альфа-канал отсутствует, можно использовать другой канал как альфа (например, красный)
    // float alpha = texColor.r;

    if (alpha < 0.1) discard; // Отбрасываем пиксели, которые практически прозрачны

    FragColor = vec4(texColor.rgb, alpha); // Используем альфа для прозрачности
}
