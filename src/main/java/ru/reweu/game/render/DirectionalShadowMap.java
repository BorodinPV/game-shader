package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BORDER_COLOR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPolygonOffset;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11C.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glDrawBuffer;
import static org.lwjgl.opengl.GL20.glReadBuffer;
import static org.lwjgl.opengl.GL30.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_LINEAR;
import static org.lwjgl.opengl.GL30.GL_NONE;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexParameterfv;
import static org.lwjgl.opengl.GL11C.GL_FRONT;

import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import ru.reweu.game.GameConfig;

/**
 * Ортогональная карта теней для направленного света (один каскад).
 *
 * <p>Связка с depth-pass, PCF и ray trace: {@code docs/RENDERING.md} §8.
 */
public final class DirectionalShadowMap {

    public static final int SHADOW_MAP_UNIT = 5;

    private final int size;
    private final int depthFbo;
    private final int depthTexture;
    private final Matrix4f lightSpaceMatrix = new Matrix4f();

    public DirectionalShadowMap(int size) {
        this.size = size;
        depthFbo = glGenFramebuffers();
        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, size, size, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer border = stack.mallocFloat(4);
            border.put(1f).put(1f).put(1f).put(1f);
            border.flip();
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, border);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        updateLightMatrices(
            SceneLighting.frame().sunDirection(),
            GameConfig.shadowOrthoCenterX(),
            GameConfig.shadowOrthoCenterZ()
        );
    }

    /**
     * @param centerX орто-центр тени по X (как в {@link GameConfig#shadowOrthoCenterXFromAvg}).
     * @param centerZ орто-центр тени по Z.
     */
    public void updateLightMatrices(Vector3f sunDirectionNormalized, float centerX, float centerZ) {
        Vector3f lightDir = new Vector3f(sunDirectionNormalized).normalize();
        // Смещение орто-центра к машинам: тени под кузовом и колёсами читаются на траве.
        Vector3f center = new Vector3f(centerX, 8f, centerZ);
        float dist = 95f;
        Vector3f eye = new Vector3f(lightDir).mul(-dist).add(center);
        Matrix4f lightView = new Matrix4f().lookAt(eye, center, new Vector3f(0f, 1f, 0f));
        float orthoExtent = 88f;
        Matrix4f lightProj = new Matrix4f().ortho(
            -orthoExtent, orthoExtent,
            -orthoExtent, orthoExtent,
            1f, 240f
        );
        lightProj.mul(lightView, lightSpaceMatrix);
    }

    public Matrix4f getLightSpaceMatrix() {
        return lightSpaceMatrix;
    }

    public int getDepthTexture() {
        return depthTexture;
    }

    public int getSize() {
        return size;
    }

    public void bindForWriting() {
        glViewport(0, 0, size, size);
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        glCullFace(GL_FRONT);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.25f, 0.75f);
    }

    public void endWrite() {
        glDisable(GL_POLYGON_OFFSET_FILL);
        glCullFace(org.lwjgl.opengl.GL11.GL_BACK);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindForReading(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
    }

    public void cleanup() {
        glDeleteFramebuffers(depthFbo);
        glDeleteTextures(depthTexture);
    }
}
