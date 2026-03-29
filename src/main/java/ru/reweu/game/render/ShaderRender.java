package ru.reweu.game.render;

import java.util.List;
import org.joml.Matrix4f;
import ru.reweu.game.loader.Mesh;

public class ShaderRender {

    /** Карта теней: только depth. */
    public static void renderWorldMeshesDepth(
        ShaderProgram depthShader,
        Mesh[] meshes,
        Matrix4f model,
        Matrix4f lightSpaceMatrix
    ) {
        depthShader.use();
        depthShader.setUniform("lightSpaceMatrix", lightSpaceMatrix);
        depthShader.setUniform("model", model);
        for (Mesh meshRender : meshes) {
            meshRender.renderDepthOnly();
        }
    }

    /** Шейдер мира (fragment_shader.glsl); свет задаётся в {@link WorldRenderer}. */
    public static void renderWorldMeshes(
        ShaderProgram shaderProgram,
        Mesh[] meshes,
        Matrix4f model,
        Matrix4f view,
        Matrix4f projection
    ) {
        renderWorldMeshes(shaderProgram, meshes, model, view, projection, false);
    }

    /**
     * @param opaqueGeometryPass см. {@link Mesh#render(boolean)}
     */
    public static void renderWorldMeshes(
        ShaderProgram shaderProgram,
        Mesh[] meshes,
        Matrix4f model,
        Matrix4f view,
        Matrix4f projection,
        boolean opaqueGeometryPass
    ) {
        shaderProgram.use();
        if (view != null) {
            shaderProgram.setUniform("view", view);
        }
        if (projection != null) {
            shaderProgram.setUniform("projection", projection);
        }
        shaderProgram.setUniform("model", model);
        for (Mesh meshRender : meshes) {
            meshRender.render(opaqueGeometryPass);
        }
    }

    /** Прозрачный оверлей (стекло): дальше от камеры раньше, чем ближнее. */
    public static void renderWorldMeshesTransparentSorted(
        ShaderProgram shaderProgram,
        List<Mesh> meshes,
        Matrix4f model,
        Matrix4f view,
        Matrix4f projection,
        org.joml.Vector3f cameraWorldPos
    ) {
        if (meshes.isEmpty()) {
            return;
        }
        meshes.sort((a, b) -> Float.compare(
            distSqWorld(b, model, cameraWorldPos),
            distSqWorld(a, model, cameraWorldPos)
        ));
        shaderProgram.use();
        if (view != null) {
            shaderProgram.setUniform("view", view);
        }
        if (projection != null) {
            shaderProgram.setUniform("projection", projection);
        }
        shaderProgram.setUniform("model", model);
        for (Mesh m : meshes) {
            m.render(false);
        }
    }

    private static float distSqWorld(Mesh m, Matrix4f model, org.joml.Vector3f cam) {
        org.joml.Vector3f p = m.getLocalCenterApprox();
        model.transformPosition(p);
        return p.distanceSquared(cam);
    }
}
