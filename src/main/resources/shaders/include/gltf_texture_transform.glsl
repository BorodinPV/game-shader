// KHR_texture_transform (glTF 2.0): scale, then rotate (CCW), then translate in UV space.
vec2 applyGltfTextureTransform(vec2 uv, vec2 offset, vec2 scale, float rotation)
{
    vec2 scaled = uv * scale;
    float c = cos(rotation);
    float s = sin(rotation);
    vec2 rotated = mat2(c, s, -s, c) * scaled;
    return rotated + offset;
}
