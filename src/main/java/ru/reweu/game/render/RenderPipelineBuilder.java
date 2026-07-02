package ru.reweu.game.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Построитель для рендер-пайплайна (цепочка рендер-пассов).
 * Позволяет композировать разные этапы рендеринга без огромного if-else дерева.
 */
public class RenderPipelineBuilder {

    private final List<RenderPass> passes = new ArrayList<>();
    private final List<String> passNames = new ArrayList<>();

    /**
     * Добавить рендер-пасс в пайплайн.
     */
    public RenderPipelineBuilder addPass(String name, RenderPass pass) {
        passes.add(pass);
        passNames.add(name);
        return this;
    }

    /**
     * Выполнить все рендер-пассы по порядку.
     */
    public void render(LightingFrame lightingFrame, Matrix4f viewMatrix, Matrix4f projectionMatrix,
                       Vector3f cameraPosition, float deltaTime) {
        for (int i = 0; i < passes.size(); i++) {
            RenderPass pass = passes.get(i);
            try {
                pass.render(lightingFrame, viewMatrix, projectionMatrix, cameraPosition, deltaTime);
            } catch (Exception e) {
                System.err.println("Error in render pass: " + passNames.get(i));
                e.printStackTrace();
            }
        }
    }

    /**
     * Получить количество пассов.
     */
    public int getPassCount() {
        return passes.size();
    }

    /**
     * Получить имена всех пассов.
     */
    public List<String> getPassNames() {
        return new ArrayList<>(passNames);
    }

    /**
     * Очистить пайплайн.
     */
    public void clear() {
        passes.clear();
        passNames.clear();
    }

    @Override
    public String toString() {
        return "RenderPipelineBuilder{" +
            "passCount=" + passes.size() +
            ", passes=" + passNames +
            '}';
    }
}
