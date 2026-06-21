package ru.reweu.game.render.rt;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteTextures;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenTextures;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glTexImage2D;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.glDispatchCompute;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import ru.reweu.game.GameConfig;
import ru.reweu.game.gltf.GltfScene;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.render.RenderErrorLog;
import ru.reweu.game.render.LightingFrame;
import ru.reweu.game.render.ShaderProgram;

/**
 * Полноэкранная compute-трассировка (первичный луч + тень от солнца), вывод через тонмап.
 * Требует OpenGL 4.3 и {@link GameConfig#RAY_TRACE_ENABLED}.
 * Позиции glTF на момент конструктора попадают в SSBO; при движении машин нужна пересборка буфера.
 */
public final class RayTraceRenderer {

    private final ComputeShaderProgram compute;
    private final ShaderProgram present;
    private final int ssbo;
    private final int triCount;
    private final int rtTexture;
    private final int emptyVao;
    private int rtW;
    private int rtH;
    private static final Matrix4f tmpInvP = new Matrix4f();
    private static final Matrix4f tmpInvV = new Matrix4f();

    public RayTraceRenderer(
        List<Mesh[]> landscape,
        List<GltfScene> gltfScenes,
        List<Vector3f> gltfWorldPositions,
        List<Float> gltfScales,
        List<Mesh[]> propMeshes,
        List<Vector3f> propPositions
    ) {
        if (!GL.getCapabilities().OpenGL43) {
            RenderErrorLog.warn("Ray tracing requires OpenGL 4.3 (compute shaders)");
            throw new IllegalStateException("Ray tracing requires OpenGL 4.3");
        }
        compute = new ComputeShaderProgram("/shaders/rt/ray_trace.comp");
        present = new ShaderProgram("/shaders/rt/present.vert", "/shaders/rt/present.frag");

        int maxT = GameConfig.RAY_TRACE_MAX_TRIANGLES;
        FloatBuffer buf = memAllocFloat(maxT * 16);
        Matrix4f landM = RayTraceGeometry.landscapeModel(landscape);
        List<GltfScene> scenes = gltfScenes != null ? gltfScenes : new ArrayList<>();
        List<Matrix4f> gltfRoots = RayTraceGeometry.gltfRootModels(
            gltfWorldPositions != null ? gltfWorldPositions : new ArrayList<>(),
            gltfScales != null ? gltfScales : new ArrayList<>()
        );
        List<Vector3f> propPos = propPositions != null ? propPositions : new ArrayList<>();
        int built = RayTraceGeometry.build(
            landscape,
            landM,
            scenes,
            gltfRoots,
            propMeshes,
            propPos,
            maxT,
            buf
        );
        buf.flip();
        if (built == 0) {
            memFree(buf);
            buf = memAllocFloat(16);
            for (int i = 0; i < 16; i++) {
                buf.put(0f);
            }
            buf.flip();
        } else {
            buf.limit(built * 16);
        }

        triCount = built;
        ssbo = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, buf, GL_STATIC_DRAW);
        memFree(buf);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        rtTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, rtTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        emptyVao = glGenVertexArrays();
        rtW = 0;
        rtH = 0;
    }

    private void ensureRtTextureSize(int w, int h) {
        if (w == rtW && h == rtH) {
            return;
        }
        rtW = w;
        rtH = h;
        glBindTexture(GL_TEXTURE_2D, rtTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, w, h, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void render(LightingFrame lit, Matrix4f view, Matrix4f projection, Vector3f cameraPos, int fbW, int fbH) {
        int iw = Math.max(1, fbW / Math.max(1, GameConfig.RAY_TRACE_INTERNAL_SCALE));
        int ih = Math.max(1, fbH / Math.max(1, GameConfig.RAY_TRACE_INTERNAL_SCALE));
        ensureRtTextureSize(iw, ih);

        tmpInvP.set(projection).invert();
        tmpInvV.set(view).invert();

        compute.use();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        compute.setUniform("invProjection", tmpInvP);
        compute.setUniform("invView", tmpInvV);
        compute.setUniform("cameraPosition", cameraPos);
        compute.setUniform("sunDirection", lit.sunDirection());
        compute.setUniform("sunColor", lit.sunColor());
        compute.setUniform("sunIntensity", lit.sunIntensity());
        compute.setUniform("skyAmbientColor", lit.skyAmbientColor());
        compute.setUniform("groundAmbientColor", lit.groundAmbientColor());
        compute.setUniform("triCount", triCount);

        glBindImageTexture(0, rtTexture, 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
        int gx = (iw + 7) / 8;
        int gy = (ih + 7) / 8;
        glDispatchCompute(gx, gy, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        glViewport(0, 0, fbW, fbH);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        present.use();
        int pid = present.getProgramId();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, rtTexture);
        int uScene = glGetUniformLocation(pid, "uScene");
        if (uScene != -1) {
            glUniform1i(uScene, 0);
        }
        int uExp = glGetUniformLocation(pid, "exposure");
        if (uExp != -1) {
            glUniform1f(uExp, lit.exposure());
        }
        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
    }

    public void cleanup() {
        compute.cleanup();
        present.cleanup();
        glDeleteBuffers(ssbo);
        glDeleteTextures(rtTexture);
        glDeleteVertexArrays(emptyVao);
    }
}
