package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RG;
import static org.lwjgl.opengl.GL30.GL_RG16F;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.joml.Vector3f;

/**
 * 2D BRDF LUT (split-sum) для спекулярного IBL; генерация на CPU (GGX, tangent space N=(0,0,1)).
 */
public final class BrdfLutTexture {

    private static final float PI = (float) Math.PI;

    private final int textureId;

    public BrdfLutTexture() {
        this(256);
    }

    public BrdfLutTexture(int resolution) {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        FloatBuffer data = generateBrdfLut(resolution);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, resolution, resolution, 0, GL_RG, GL_FLOAT, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int id() {
        return textureId;
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }

    private static FloatBuffer generateBrdfLut(int size) {
        int floats = size * size * 2;
        FloatBuffer buffer = ByteBuffer.allocateDirect(floats * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        for (int y = 0; y < size; y++) {
            float roughness = (y + 0.5f) / size;
            float a = Math.max(roughness * roughness, 1e-4f);
            for (int x = 0; x < size; x++) {
                float nDotV = (x + 0.5f) / size;
                float[] ab = integrateBrdf(nDotV, a);
                buffer.put(ab[0]).put(ab[1]);
            }
        }
        buffer.flip();
        return buffer;
    }

    private static float[] integrateBrdf(float nDotV, float roughness) {
        Vector3f v = new Vector3f((float) Math.sqrt(Math.max(0.0, 1.0 - nDotV * nDotV)), 0f, nDotV);
        float aScale = 0f;
        float bScale = 0f;
        Vector3f n = new Vector3f(0f, 0f, 1f);
        int sampleCount = 128;
        for (int i = 0; i < sampleCount; i++) {
            float[] xi = hammersley(i, sampleCount);
            Vector3f h = importanceSampleGgx(xi[0], xi[1], n, roughness);
            Vector3f l = new Vector3f(v).mul(2f * v.dot(h)).sub(h).normalize();
            float nDotL = Math.max(l.z, 0f);
            float nDotH = Math.max(h.z, 0f);
            float vDotH = Math.max(v.dot(h), 0f);
            if (nDotL > 0f) {
                float g = geometrySmith(n, v, l, roughness);
                float gvis = (g * vDotH) / Math.max(nDotH * nDotV, 1e-8f);
                float fc = (float) Math.pow(1f - vDotH, 5f);
                aScale += (1f - fc) * gvis;
                bScale += fc * gvis;
            }
        }
        return new float[] {aScale / sampleCount, bScale / sampleCount};
    }

    private static float[] hammersley(int i, int n) {
        return new float[] {(float) i / n, radicalInverseVdC(i)};
    }

    private static float radicalInverseVdC(int bits) {
        int b = bits & 0xFFFF;
        b = (b << 16) | (b >>> 16);
        b = ((b & 0x55555555) << 1) | ((b & 0xAAAAAAAA) >>> 1);
        b = ((b & 0x33333333) << 2) | ((b & 0xCCCCCCCC) >>> 2);
        b = ((b & 0x0F0F0F0F) << 4) | ((b & 0xF0F0F0F0) >>> 4);
        b = ((b & 0x00FF00FF) << 8) | ((b & 0xFF00FF00) >>> 8);
        return (b & 0xFFFF) * 1.52587890625e-5f;
    }

    private static Vector3f importanceSampleGgx(float xi0, float xi1, Vector3f n, float roughness) {
        float a = roughness * roughness;
        float phi = 2f * PI * xi0;
        float cosTheta = (float) Math.sqrt((1f - xi1) / (1f + (a * a - 1f) * xi1));
        float sinTheta = (float) Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));
        Vector3f h = new Vector3f(
            (float) Math.cos(phi) * sinTheta,
            (float) Math.sin(phi) * sinTheta,
            cosTheta
        );
        Vector3f up = Math.abs(n.z) < 0.999f ? new Vector3f(0f, 0f, 1f) : new Vector3f(1f, 0f, 0f);
        Vector3f tangent = new Vector3f(up).cross(n).normalize();
        Vector3f bitangent = new Vector3f(n).cross(tangent);
        return new Vector3f(tangent).mul(h.x).add(new Vector3f(bitangent).mul(h.y)).add(new Vector3f(n).mul(h.z)).normalize();
    }

    private static float geometrySchlickGgx(float nDotW, float roughness) {
        float r = roughness + 1f;
        float k = (r * r) / 8f;
        return nDotW / Math.max(nDotW * (1f - k) + k, 1e-8f);
    }

    private static float geometrySmith(Vector3f n, Vector3f v, Vector3f l, float roughness) {
        float nDotV = Math.max(n.dot(v), 0f);
        float nDotL = Math.max(n.dot(l), 0f);
        return geometrySchlickGgx(nDotV, roughness) * geometrySchlickGgx(nDotL, roughness);
    }
}
