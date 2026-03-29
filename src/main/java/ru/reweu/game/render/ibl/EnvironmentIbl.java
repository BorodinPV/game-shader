package ru.reweu.game.render.ibl;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.GL_RGB16F;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;
import static org.lwjgl.opengl.GL30.glVertexAttribPointer;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import ru.reweu.game.render.RenderErrorLog;
import ru.reweu.game.render.ShaderProgram;

/**
 * Один раз при старте: equirect → env cubemap → irradiance + prefiltered specular mips.
 */
public final class EnvironmentIbl {

    public static final int IRRADIANCE_UNIT = 7;
    public static final int PREFILTER_UNIT = 8;

    private static final int ENV_CUBE_SIZE = 512;
    private static final int IRRADIANCE_SIZE = 32;
    private static final int PREFILTER_SIZE = 128;

    private static final float[] CUBE_VERTICES = {
        -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f,
        1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f,
        -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f,
        -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f,
        1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f,
        1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f,
        -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f,
        1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
        -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f
    };

    private final int irradianceMap;
    private final int prefilterMap;
    private final float prefilterMaxMip;

    private final int cubeVao;
    private final int cubeVbo;

    public EnvironmentIbl(int equirectTexId) {
        cubeVao = glGenVertexArrays();
        cubeVbo = glGenBuffers();
        glBindVertexArray(cubeVao);
        glBindBuffer(GL_ARRAY_BUFFER, cubeVbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var fb = stack.mallocFloat(CUBE_VERTICES.length);
            fb.put(CUBE_VERTICES).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glBindVertexArray(0);

        ShaderProgram eq = new ShaderProgram("/shaders/ibl/cube_capture.vert", "/shaders/ibl/equirect_to_cube.frag");
        ShaderProgram ir = new ShaderProgram("/shaders/ibl/cube_capture.vert", "/shaders/ibl/irradiance_convolve.frag");
        ShaderProgram pr = new ShaderProgram("/shaders/ibl/cube_capture.vert", "/shaders/ibl/prefilter_specular.frag");

        int captureFbo = glGenFramebuffers();
        int depthRbo = glGenRenderbuffers();

        Matrix4f captureProj = new Matrix4f().perspective((float) Math.toRadians(90.0), 1.0f, 0.1f, 10.0f);
        Matrix4f[] views = captureViews();

        int envCubemap = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGB16F, ENV_CUBE_SIZE, ENV_CUBE_SIZE, 0, GL_RGB, GL_FLOAT, (java.nio.ByteBuffer) null);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        glEnable(GL_DEPTH_TEST);
        /* Камера внутри unit-куба: при GL_CULL_FACE снаружи отсекаются «внутренние» грани — часть граней cubemap остаётся чёрной. */
        boolean cullWasEnabled = glIsEnabled(GL_CULL_FACE);
        glDisable(GL_CULL_FACE);

        eq.use();
        int pidEq = eq.getProgramId();
        int uEqMap = glGetUniformLocation(pidEq, "equirectMap");

        for (int face = 0; face < 6; face++) {
            eq.use();
            glBindFramebuffer(GL_FRAMEBUFFER, captureFbo);
            glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, ENV_CUBE_SIZE, ENV_CUBE_SIZE);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, envCubemap, 0);
            if (!RenderErrorLog.logIfFramebufferIncomplete("IBL equirect→cube face " + face, GL_FRAMEBUFFER)) {
                throw new IllegalStateException("IBL FBO incomplete (equirect→cube)");
            }
            glViewport(0, 0, ENV_CUBE_SIZE, ENV_CUBE_SIZE);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            eq.setUniform("projection", captureProj);
            eq.setUniform("view", views[face]);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, equirectTexId);
            glUniform1i(uEqMap, 0);
            glBindVertexArray(cubeVao);
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap);
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP);

        irradianceMap = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGB16F, IRRADIANCE_SIZE, IRRADIANCE_SIZE, 0, GL_RGB, GL_FLOAT, (java.nio.ByteBuffer) null);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        ir.use();
        int pidIr = ir.getProgramId();
        int uEnvIr = glGetUniformLocation(pidIr, "environmentMap");

        for (int face = 0; face < 6; face++) {
            ir.use();
            glBindFramebuffer(GL_FRAMEBUFFER, captureFbo);
            glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, IRRADIANCE_SIZE, IRRADIANCE_SIZE);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, irradianceMap, 0);
            if (!RenderErrorLog.logIfFramebufferIncomplete("IBL irradiance face " + face, GL_FRAMEBUFFER)) {
                throw new IllegalStateException("IBL FBO incomplete (irradiance)");
            }
            glViewport(0, 0, IRRADIANCE_SIZE, IRRADIANCE_SIZE);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            ir.setUniform("projection", captureProj);
            ir.setUniform("view", views[face]);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap);
            glUniform1i(uEnvIr, 0);
            glBindVertexArray(cubeVao);
            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        int maxMipLevels = (int) (Math.log(PREFILTER_SIZE) / Math.log(2)) + 1;
        prefilterMaxMip = maxMipLevels - 1.0f;

        prefilterMap = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap);
        for (int mip = 0; mip < maxMipLevels; mip++) {
            int mipSize = (int) (PREFILTER_SIZE * Math.pow(0.5, mip));
            mipSize = Math.max(mipSize, 1);
            for (int i = 0; i < 6; i++) {
                glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, mip, GL_RGB16F, mipSize, mipSize, 0, GL_RGB, GL_FLOAT, (java.nio.ByteBuffer) null);
            }
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        pr.use();
        int pidPr = pr.getProgramId();
        int uEnvPr = glGetUniformLocation(pidPr, "environmentMap");
        int uRough = glGetUniformLocation(pidPr, "roughness");
        int uRes = glGetUniformLocation(pidPr, "resolution");

        for (int mip = 0; mip < maxMipLevels; mip++) {
            int mipSize = (int) (PREFILTER_SIZE * Math.pow(0.5, mip));
            mipSize = Math.max(mipSize, 1);
            float roughness = (float) mip / (float) Math.max(maxMipLevels - 1, 1);
            for (int face = 0; face < 6; face++) {
                pr.use();
                glBindFramebuffer(GL_FRAMEBUFFER, captureFbo);
                glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, mipSize, mipSize);
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, prefilterMap, mip);
                if (!RenderErrorLog.logIfFramebufferIncomplete(
                    "IBL prefilter mip " + mip + " face " + face, GL_FRAMEBUFFER)) {
                    throw new IllegalStateException("IBL FBO incomplete (prefilter)");
                }
                glViewport(0, 0, mipSize, mipSize);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                pr.setUniform("projection", captureProj);
                pr.setUniform("view", views[face]);
                glUniform1f(uRough, roughness);
                glUniform1f(uRes, mipSize);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap);
                glUniform1i(uEnvPr, 0);
                glBindVertexArray(cubeVao);
                glDrawArrays(GL_TRIANGLES, 0, 36);
            }
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        if (cullWasEnabled) {
            glEnable(GL_CULL_FACE);
        }

        glDeleteTextures(envCubemap);
        glDeleteFramebuffers(captureFbo);
        glDeleteRenderbuffers(depthRbo);
        eq.cleanup();
        ir.cleanup();
        pr.cleanup();

        IblEquirectLoader.deleteTexture(equirectTexId);
    }

    private static Matrix4f[] captureViews() {
        Matrix4f[] m = new Matrix4f[6];
        m[0] = new Matrix4f().lookAt(0, 0, 0, 1, 0, 0, 0, -1, 0);
        m[1] = new Matrix4f().lookAt(0, 0, 0, -1, 0, 0, 0, -1, 0);
        m[2] = new Matrix4f().lookAt(0, 0, 0, 0, 1, 0, 0, 0, 1);
        m[3] = new Matrix4f().lookAt(0, 0, 0, 0, -1, 0, 0, 0, -1);
        m[4] = new Matrix4f().lookAt(0, 0, 0, 0, 0, 1, 0, -1, 0);
        m[5] = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, -1, 0);
        return m;
    }

    public int getIrradianceMap() {
        return irradianceMap;
    }

    public int getPrefilterMap() {
        return prefilterMap;
    }

    public float getPrefilterMaxMip() {
        return prefilterMaxMip;
    }

    public void cleanup() {
        glDeleteTextures(irradianceMap);
        glDeleteTextures(prefilterMap);
        glDeleteVertexArrays(cubeVao);
        glDeleteBuffers(cubeVbo);
    }
}
