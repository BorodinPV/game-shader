// Единый ACES + exposure: линейный HDR → display (~sRGB). Подключается первым в корневых .frag / .comp.
// См. также: math_const.glsl, pbr_ggx.glsl, shadow_csm_common.glsl, spherical_equirect_uv.glsl

vec3 acesTonemap(vec3 x)
{
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 tonemapDisplay(vec3 linearHdr, float exposure)
{
    return acesTonemap(linearHdr * exposure);
}
