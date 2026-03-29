#version 330 core

#include "/shaders/include/tonemap_exposure.glsl"

in vec2 vUv;

out vec4 FragColor;

uniform sampler2D uScene;
uniform float exposure;

void main() {
    vec3 linear = texture(uScene, vUv).rgb;
    FragColor = vec4(tonemapDisplay(linear, exposure), 1.0);
}
