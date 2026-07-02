package ru.reweu.game.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Интерфейс для рендер-пасса (этап рендеринга).
 * Позволяет композировать разные этапы рендеринга единообразно.
 */
public interface RenderPass {
    
    /**
     * Выполнить рендер-пасс.
     * 
     * @param lightingFrame информация об освещении сцены
     * @param viewMatrix матрица вида (камеры)
     * @param projectionMatrix матрица проекции
     * @param cameraPosition позиция камеры в мире
     * @param deltaTime время между кадрами
     */
    void render(LightingFrame lightingFrame, Matrix4f viewMatrix, Matrix4f projectionMatrix,
                Vector3f cameraPosition, float deltaTime);
}
