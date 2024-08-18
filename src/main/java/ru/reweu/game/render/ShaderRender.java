package ru.reweu.game.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.Camera;
import ru.reweu.game.loader.Mesh;

public class ShaderRender {

    public static void renderObjects(
        ShaderProgram shaderProgram,
        Mesh[] meshes,
        Matrix4f model,
        Camera camera,
        Matrix4f view,
        Matrix4f projection
    ) {
        shaderProgram.use();
        for (Mesh meshRender : meshes) {
            if (view != null) {
                shaderProgram.setUniform("view", view);
            }
            if (projection != null) {
                shaderProgram.setUniform("projection", projection);
            }
            shaderProgram.setUniform("model", model);

            shaderProgram.setUniform("diffuseColor", meshRender.getMaterialColor());
            if (camera != null) {
                shaderProgram.setUniform("viewPos", camera.getPosition());
            }
            shaderProgram.setUniform("lightPos", new Vector3f(10.0f, 100.0f, 10.0f));
            shaderProgram.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
            shaderProgram.setUniform("ambientColor", new Vector3f(0.7f, 0.7f, 0.7f));

            shaderProgram.setUniform("useDiffuseTexture", meshRender.hasDiffuseTexture());
            shaderProgram.setUniform("useSpecularTexture", meshRender.hasSpecularTexture());
            shaderProgram.setUniform("useNormalTexture", meshRender.hasNormalTexture());
            shaderProgram.setUniform("useAlphaTexture", meshRender.hasAlphaTexture());
            shaderProgram.setUniform("useSpecularHighlightTexture", meshRender.hasSpecularHighlightTexture());
            shaderProgram.setUniform("useAlphaTexture", meshRender.hasAlphaTexture());

            meshRender.render();
        }
    }
}
