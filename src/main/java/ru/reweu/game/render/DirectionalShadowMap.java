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
import static org.lwjgl.opengl.GL11C.GL_FRONT;
import static org.lwjgl.opengl.GL11C.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL12.glTexImage3D;
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
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTextureLayer;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexParameterfv;

import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import ru.reweu.game.GameConfig;

/**
 * Направленный свет: каскадные карты глубины (CSM) в {@link GL_TEXTURE_2D_ARRAY}.
 */
public final class DirectionalShadowMap {

    public static final int SHADOW_MAP_UNIT = 5;

    private final int size;
    private final int cascadeCount;
    private final int depthTextureArray;
    private final int depthFbo;
    private final Matrix4f[] lightSpaceMatrices;
    private final float[] cascadeSplitDistances;

    private final Vector3f tmpLightDir = new Vector3f();
    private final Vector3f tmpEye = new Vector3f();
    private final Vector3f tmpUp = new Vector3f(0f, 1f, 0f);
    private final Matrix4f invViewScratch = new Matrix4f();
    private final Vector3f camPosScratch = new Vector3f();
    private final Vector3f camForward = new Vector3f();
    private final Vector3f camRight = new Vector3f();
    private final Vector3f camUp = new Vector3f();
    private final Vector3f sliceCenter = new Vector3f();
    private final Vector3f sliceNearCenter = new Vector3f();
    private final Vector3f sliceFarCenter = new Vector3f();
    private final Matrix4f tmpLightView = new Matrix4f();
    private final Matrix4f tmpLightProj = new Matrix4f();
    private final Vector4f stabilizeOrigin = new Vector4f();
    private final Matrix4f stabilizeSnap = new Matrix4f();
    private final Vector3f[] frustumCorners = new Vector3f[8];
    private final Vector4f tmpCorner = new Vector4f();
    private final Vector3f tmpMin = new Vector3f();
    private final Vector3f tmpMax = new Vector3f();

    public DirectionalShadowMap(int size) {
        this.size = size;
        this.cascadeCount = GameConfig.effectiveShadowCascadeCount();
        this.lightSpaceMatrices = new Matrix4f[cascadeCount];
        for (int i = 0; i < cascadeCount; i++) {
            lightSpaceMatrices[i] = new Matrix4f();
        }
        int splitLen = Math.max(0, cascadeCount - 1);
        this.cascadeSplitDistances = new float[splitLen];

        depthTextureArray = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTextureArray);
        glTexImage3D(
            GL_TEXTURE_2D_ARRAY,
            0,
            GL_DEPTH_COMPONENT,
            size,
            size,
            cascadeCount,
            0,
            GL_DEPTH_COMPONENT,
            GL_FLOAT,
            (java.nio.ByteBuffer) null
        );
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer border = stack.mallocFloat(4);
            border.put(1f).put(1f).put(1f).put(1f);
            border.flip();
            glTexParameterfv(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BORDER_COLOR, border);
        }

        depthFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTextureArray, 0, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        for (int i = 0; i < frustumCorners.length; i++) {
            frustumCorners[i] = new Vector3f();
        }

        updateLightMatrices(
            new Matrix4f(),
            new Matrix4f().perspective(
                (float) Math.toRadians(GameConfig.FOV_DEGREES),
                16f / 9f,
                GameConfig.NEAR_PLANE,
                GameConfig.FAR_PLANE
            ),
            SceneLighting.frame().sunDirection(),
            GameConfig.NEAR_PLANE,
            GameConfig.FAR_PLANE
        );
    }

    public int getCascadeCount() {
        return cascadeCount;
    }

    /**
     * Границы по расстоянию от камеры: каскад {@code i} покрывает
     * {@code [i==0 ? near : splits[i-1], i==last ? far : splits[i]]}.
     */
    public float[] getCascadeSplitDistances() {
        return cascadeSplitDistances;
    }

    public Matrix4f getLightSpaceMatrix(int cascade) {
        return lightSpaceMatrices[cascade];
    }

    /** Матрицы каскадов (длина {@link #getCascadeCount()}), только для uniform-массива в шейдере. */
    public Matrix4f[] getLightSpaceMatrices() {
        return lightSpaceMatrices;
    }

    /**
     * Обновляет матрицы каскадов по текущей камере и солнцу.
     *
     * @param view              матрица вида камеры
     * @param projection        матрица проекции камеры
     * @param sunDirection      направление к солнцу (нормализованное)
     * @param nearPlane         ближняя плоскость камеры
     * @param farPlane          дальняя плоскость камеры
     */
    public void updateLightMatrices(
        Matrix4f view,
        Matrix4f projection,
        Vector3f sunDirection,
        float nearPlane,
        float farPlane
    ) {
        tmpLightDir.set(sunDirection).normalize();
        ShadowCascadeSplits.computeSplitDistances(
            cascadeCount,
            nearPlane,
            farPlane,
            GameConfig.SHADOW_CASCADE_LAMBDA,
            cascadeSplitDistances
        );

        view.invert(invViewScratch);
        invViewScratch.getTranslation(camPosScratch);
        camForward.set(0f, 0f, -1f);
        invViewScratch.transformDirection(camForward).normalize();
        camRight.set(1f, 0f, 0f);
        invViewScratch.transformDirection(camRight).normalize();
        camUp.set(0f, 1f, 0f);
        invViewScratch.transformDirection(camUp).normalize();

        float tanHalfFov = 1f / projection.m11();
        float aspect = projection.m11() / projection.m00();

        for (int i = 0; i < cascadeCount; i++) {
            float zNearSlice = (i == 0) ? nearPlane : cascadeSplitDistances[i - 1];
            float zFarSlice = (i == cascadeCount - 1) ? farPlane : cascadeSplitDistances[i];

            buildSliceCorners(
                camPosScratch,
                camForward,
                camRight,
                camUp,
                aspect,
                tanHalfFov,
                zNearSlice,
                zFarSlice,
                frustumCorners
            );

            sliceCenter.set(0f, 0f, 0f);
            for (Vector3f c : frustumCorners) {
                sliceCenter.add(c);
            }
            sliceCenter.mul(1f / 8f);

            float lightDist = 95f;
            tmpEye.set(tmpLightDir).mul(-lightDist).add(sliceCenter);
            tmpLightView.identity().lookAt(tmpEye, sliceCenter, tmpUp);

            tmpMin.set(Float.POSITIVE_INFINITY);
            tmpMax.set(Float.NEGATIVE_INFINITY);
            for (Vector3f c : frustumCorners) {
                tmpCorner.set(c.x, c.y, c.z, 1f);
                tmpLightView.transform(tmpCorner);
                tmpMin.x = Math.min(tmpMin.x, tmpCorner.x);
                tmpMin.y = Math.min(tmpMin.y, tmpCorner.y);
                tmpMin.z = Math.min(tmpMin.z, tmpCorner.z);
                tmpMax.x = Math.max(tmpMax.x, tmpCorner.x);
                tmpMax.y = Math.max(tmpMax.y, tmpCorner.y);
                tmpMax.z = Math.max(tmpMax.z, tmpCorner.z);
            }
            float zMult = 10f;
            tmpLightProj.identity().ortho(
                tmpMin.x, tmpMax.x,
                tmpMin.y, tmpMax.y,
                tmpMin.z - zMult, tmpMax.z + zMult
            );
            tmpLightProj.mul(tmpLightView, lightSpaceMatrices[i]);
            stabilizeShadowMatrixTexels(lightSpaceMatrices[i], size);
        }
    }

    /**
     * Заполняет {@code out8[0..7]} без аллокаций (буферы углов переиспользуются каждый кадр).
     */
    private void buildSliceCorners(
        Vector3f camPos,
        Vector3f forward,
        Vector3f right,
        Vector3f up,
        float aspect,
        float tanHalfFov,
        float dNear,
        float dFar,
        Vector3f[] out8
    ) {
        float hn = dNear * tanHalfFov;
        float wn = hn * aspect;
        float hf = dFar * tanHalfFov;
        float wf = hf * aspect;
        sliceNearCenter.set(camPos).fma(dNear, forward);
        sliceFarCenter.set(camPos).fma(dFar, forward);
        int k = 0;
        for (int pass = 0; pass < 2; pass++) {
            Vector3f base = (pass == 0) ? sliceNearCenter : sliceFarCenter;
            float hw = (pass == 0) ? wn : wf;
            float hh = (pass == 0) ? hn : hf;
            out8[k++].set(base).fma(-hw, right).fma(-hh, up);
            out8[k++].set(base).fma(hw, right).fma(-hh, up);
            out8[k++].set(base).fma(-hw, right).fma(hh, up);
            out8[k++].set(base).fma(hw, right).fma(hh, up);
        }
    }

    private void stabilizeShadowMatrixTexels(Matrix4f lightSpace, int shadowMapSize) {
        if (shadowMapSize <= 0) {
            return;
        }
        stabilizeOrigin.set(0f, 0f, 0f, 1f).mul(lightSpace);
        float halfMap = shadowMapSize * 0.5f;
        stabilizeOrigin.x *= halfMap;
        stabilizeOrigin.y *= halfMap;
        stabilizeOrigin.z *= halfMap;
        float rdx = Math.round(stabilizeOrigin.x) - stabilizeOrigin.x;
        float rdy = Math.round(stabilizeOrigin.y) - stabilizeOrigin.y;
        float s = 2f / shadowMapSize;
        stabilizeSnap.translation(rdx * s, rdy * s, 0f).mul(lightSpace, lightSpace);
    }

    public int getDepthTextureArrayId() {
        return depthTextureArray;
    }

    public int getSize() {
        return size;
    }

    public void bindCascadeForWriting(int cascadeIndex) {
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTextureArray, 0, cascadeIndex);
        glViewport(0, 0, size, size);
        glClear(GL_DEPTH_BUFFER_BIT);
        glCullFace(GL_FRONT);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.25f, 0.75f);
    }

    /** После последнего каскада вызывать один раз. */
    public void endWrite() {
        glDisable(GL_POLYGON_OFFSET_FILL);
        glCullFace(org.lwjgl.opengl.GL11.GL_BACK);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindForReadingArray(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTextureArray);
    }

    public void cleanup() {
        glDeleteFramebuffers(depthFbo);
        glDeleteTextures(depthTextureArray);
    }
}
