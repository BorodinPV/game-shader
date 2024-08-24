package ru.reweu.game.weather;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform3f;

import org.joml.Vector3f;
import ru.reweu.game.Camera;
import ru.reweu.game.render.ShaderProgram;

public class Fog {

    public static void startFog(Camera camera, ShaderProgram worldShaderProgram) {
        // Установка параметров тумана
        Vector3f cameraPosition = camera.getPosition();
        glUniform3f(glGetUniformLocation(worldShaderProgram.getProgramId(), "fogColor"), 0.5f, 0.6f, 0.7f); // Голубовато-серый туман
        glUniform1f(glGetUniformLocation(worldShaderProgram.getProgramId(), "fogDensity"), 0.2f); // Плотность тумана
        glUniform3f(glGetUniformLocation(worldShaderProgram.getProgramId(), "cameraPos"), cameraPosition.x, cameraPosition.y, cameraPosition.z);
    }
}
