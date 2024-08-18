package ru.reweu.game.car;

import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.render.ShaderProgram;

import static ru.reweu.game.loader.Utils.getWheel;
import static ru.reweu.game.render.ShaderRender.renderObjects;

@Setter
public class Car {

    public static float carSpeed = 0f; // Скорость движения машины
    private float turnSpeed = 30.0f; // Скорость поворота машины
    private float wheelTurnAngle = 0.0f; // Угол поворота колес
    private float wheelRotationAngle = 0.0f; // Угол вращения колес

    public void updateWheelRotation(
            float deltaTime,
//            float carSpeed,
            float carRotationAngle,
            Vector3f carPosition,
            ShaderProgram shaderProgram
    ) {
        // Обновляем угол вращения колес на основе скорости автомобиля
        wheelRotationAngle += carSpeed * deltaTime * 5.0f;
        // Ограничиваем угол поворота колес
        wheelTurnAngle = Math.max(-30.0f, Math.min(30.0f, wheelTurnAngle));

        ru.reweu.game.loader.Mesh[] wheeelMeshes1 = getWheel().values().toArray(new ru.reweu.game.loader.Mesh[0]);
        Matrix4f model1 = new Matrix4f()
                .translate(carPosition) // Перемещение машины
                .rotateY((float) Math.toRadians(carRotationAngle)) // Поворот машины
                .translate(new Vector3f(0.64f, 0.26f, -1.09f)) // Перемещение к центру колеса
                .rotateY((float) Math.toRadians(wheelTurnAngle)) // Поворот колес
                .rotateX(wheelRotationAngle) // Вращение колес
                .scale(wheeelMeshes1[0].getScale());
        renderObjects(shaderProgram, wheeelMeshes1, model1, null, null, null);

        ru.reweu.game.loader.Mesh[] wheeelMeshes2 = getWheel().values().toArray(new ru.reweu.game.loader.Mesh[0]);
        Matrix4f model2 = new Matrix4f()
                .translate(carPosition) // Перемещение машины
                .rotateY((float) Math.toRadians(carRotationAngle)) // Поворот машины
                .translate(new Vector3f(-0.6f, 0.26f, -1.09f)) // Перемещение к центру колеса
                .rotateY((float) Math.toRadians(wheelTurnAngle)) // Поворот колес
                .rotateX(wheelRotationAngle) // Вращение колес
                .rotateY((float) Math.toRadians(180)) // Дополнительное вращение на 180 градусов
                .scale(wheeelMeshes2[0].getScale());
        renderObjects(shaderProgram, wheeelMeshes2, model2, null, null, null);

        ru.reweu.game.loader.Mesh[] wheeelMeshes3 = getWheel().values().toArray(new ru.reweu.game.loader.Mesh[0]);
        Matrix4f model3 = new Matrix4f()
                .translate(carPosition) // Перемещение машины
                .rotateY((float) Math.toRadians(carRotationAngle)) // Поворот машины
                .translate(new Vector3f(0.64f, 0.26f, 1.24f)) // Перемещение к центру колеса
                .rotateX(wheelRotationAngle) // Вращение колес
                .scale(wheeelMeshes3[0].getScale());
        renderObjects(shaderProgram, wheeelMeshes3, model3, null, null, null);

        ru.reweu.game.loader.Mesh[] wheeelMeshes4 = getWheel().values().toArray(new ru.reweu.game.loader.Mesh[0]);
        Matrix4f model4 = new Matrix4f()
                .translate(carPosition) // Перемещение машины
                .rotateY((float) Math.toRadians(carRotationAngle)) // Поворот машины
                .translate(new Vector3f(-0.6f, 0.26f, 1.24f)) // Перемещение к центру колеса
                .rotateX(wheelRotationAngle) // Вращение колес
                .rotateY((float) Math.toRadians(180)) // Дополнительное вращение на 180 градусов
                .scale(wheeelMeshes4[0].getScale());
        renderObjects(shaderProgram, wheeelMeshes4, model4, null, null, null);
    }
}
