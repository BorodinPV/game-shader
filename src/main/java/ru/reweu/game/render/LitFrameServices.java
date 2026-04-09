package ru.reweu.game.render;

import org.joml.Matrix4f;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Снимок сервисов кадра: тени, IBL, привязка униформ освещения к шейдерам.
 * Реализация по умолчанию — {@link WorldRenderer}; абстракция отвязывает пайплайн от конкретного класса (DIP).
 */
public interface LitFrameServices {

    ShaderProgram getWorldShaderProgram();

    DirectionalShadowMap getShadowMap();

    EnvironmentIbl getEnvironmentIbl();

    void prepareFrame(
        LightingFrame lit,
        Matrix4f view,
        Matrix4f projection,
        boolean shadowSamplingEnabled
    );

    void prepareFrameFor(
        LightingFrame lit,
        ShaderProgram shaderProgram,
        Matrix4f view,
        Matrix4f projection,
        boolean shadowSamplingEnabled
    );
}
