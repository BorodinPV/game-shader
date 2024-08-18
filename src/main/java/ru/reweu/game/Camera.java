package ru.reweu.game;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import org.joml.Matrix4f;
import org.joml.Vector3f;
//
//public class Camera {
//    private Vector3f position;
//    private Vector3f front;
//    private Vector3f up;
//    private Vector3f right;
//    private Vector3f worldUp;
//
//    private float yaw;
//    private float pitch;
//
//    private float movementSpeed;
//    private float mouseSensitivity = 0.1f; // Добавляем переменную для чувствительности мыши
//
//    public Camera(Vector3f position, Vector3f up, float yaw, float pitch) {
//        this.position = position;
//        this.worldUp = up;
//        this.yaw = yaw;
//        this.pitch = pitch;
//        this.front = new Vector3f(0.0f, 0.0f, -1.0f);
//        this.movementSpeed = 2.5f;
//
//        updateCameraVectors();
//    }
//
//    public Matrix4f getViewMatrix() {
//        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), up);
//    }
//
//    public void processKeyboard(int key, float deltaTime, boolean lockY) {
//        float velocity = 6 * deltaTime;
//        Vector3f frontOnXZ = new Vector3f(front.x, 0, front.z).normalize();
//        Vector3f rightOnXZ = new Vector3f(right.x, 0, right.z).normalize();
//
//        if (key == GLFW_KEY_W)
//            position.add(frontOnXZ.mul(velocity));
//        if (key == GLFW_KEY_S)
//            position.sub(frontOnXZ.mul(velocity));
//        if (key == GLFW_KEY_A)
//            position.sub(rightOnXZ.mul(velocity));
//        if (key == GLFW_KEY_D)
//            position.add(rightOnXZ.mul(velocity));
//
//        if (lockY) {
//            position.y = 1.0f;  // Привязываем высоту камеры к уровню плоскости
//        }
//    }
//
//    public void processMouseMovement(float xOffset, float yOffset) {
//        xOffset *= mouseSensitivity;
//        yOffset *= mouseSensitivity;
//
//        yaw += xOffset;
//        pitch += yOffset;
//
//        // Ограничиваем угол поворота по вертикали
//        if (pitch > 89.0f)
//            pitch = 89.0f;
//        if (pitch < -89.0f)
//            pitch = -89.0f;
//
//        // Обновляем векторы направления
//        updateCameraVectors();
//    }
//
//    public void attachToModel(Vector3f modelPosition, float x, float y, float z) {
//        this.position.set(modelPosition).add(x, y, z);
//        this.front.set(0.0f, -1.0f, 0.0f); // Направление камеры вниз
//        this.up.set(0.0f, 0.0f, -1.0f); // Вектор "вверх" камеры
//        updateCameraVectors();
//    }
//
//    public void setThirdPersonView(Vector3f modelPosition, float distanceBehind, float heightAbove) {
//        // Позиционируем камеру за моделью и немного выше
//        this.position.set(modelPosition)
//                .sub(front.x * distanceBehind, 0, front.z * distanceBehind) // Перемещаем назад по оси Z
//                .add(0, heightAbove, 0); // Поднимаем камеру на высоту
//
//        // Перемещаем взгляд немного вниз для вида сверху
//        pitch = -15.0f; // Например, наклон вниз на 15 градусов
//        updateCameraVectors(); // Обновляем векторы направления
//    }
//
//    // Обновляем позицию камеры, чтобы следовать за машиной
//    public void updateCameraPosition(Vector3f carPosition, Vector3f carDirection, float distanceBehind, float heightAbove) {
//        // Рассчитываем позицию камеры позади машины и немного выше
//        Vector3f offset = new Vector3f(carDirection).normalize().mul(distanceBehind); // Оставляем фиксированное расстояние позади
//        this.position.set(carPosition).add(offset).add(0, heightAbove, 0); // Поднимаем на заданную высоту
//
//        // Направляем камеру в сторону машины (назад)
//        front = new Vector3f(carPosition).sub(position).normalize(); // Теперь камера смотрит на машину
//
//        // Обновляем другие векторы камеры
//        updateCameraVectors();
//    }
//
//
//    private void updateCameraVectors() {
//        Vector3f front = new Vector3f();
//        front.x = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
//        front.y = (float) Math.sin(Math.toRadians(pitch));
//        front.z = (float) Math.sin(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
//        this.front = front.normalize();
//        right = new Vector3f(front).cross(worldUp).normalize();
//        up = new Vector3f(right).cross(front).normalize();
//    }
//
////    private void updateCameraVectors() {
////        // Обновляем вектор right и up на основе текущего front
////        right = new Vector3f(front).cross(worldUp).normalize();
////        up = new Vector3f(right).cross(front).normalize();
////    }
//
//    public Vector3f getPosition() {
//        return position;
//    }
//}


import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;  // Позиция камеры в мировом пространстве
    private Vector3f front;     // Вектор направления, куда смотрит камера
    private Vector3f up;        // Вектор, который указывает вверх относительно камеры
    private Vector3f right;     // Вектор, который указывает вправо относительно камеры
    private Vector3f worldUp;   // Вектор, указывающий вверх в мировом пространстве (обычно это ось Y)

    private float yaw;          // Угол вращения камеры вокруг вертикальной оси (YAW)
    private float pitch;        // Угол наклона камеры вверх и вниз (PITCH)

    private float mouseSensitivity = 0.1f;  // Чувствительность мыши для поворота камеры

    // Конструктор камеры, задающий её начальную позицию, направление и углы поворота
    public Camera(Vector3f position, Vector3f up, float yaw, float pitch) {
        this.position = position;  // Устанавливаем начальную позицию камеры
        this.worldUp = up;         // Устанавливаем начальный вектор "вверх" в мировом пространстве
        this.yaw = yaw;            // Устанавливаем начальный угол вращения камеры
        this.pitch = pitch;        // Устанавливаем начальный угол наклона камеры
        this.front = new Vector3f(0.0f, 0.0f, -1.0f);  // Изначально камера смотрит вдоль оси Z

        updateCameraVectors();  // Обновляем векторы направления камеры
    }


    // Обрабатываем движение мыши для поворота камеры
    public void processMouseMovement(float xOffset, float yOffset) {
        xOffset *= mouseSensitivity;  // Умножаем смещение мыши по X на чувствительность
        yOffset *= mouseSensitivity;  // Умножаем смещение мыши по Y на чувствительность

        yaw += xOffset;   // Добавляем смещение по YAW (влево-вправо)
        pitch += yOffset; // Добавляем смещение по PITCH (вверх-вниз)

        // Ограничиваем угол наклона камеры, чтобы избежать переворота
        if (pitch > 89.0f)
            pitch = 89.0f;
        if (pitch < -89.0f)
            pitch = -89.0f;

        updateCameraVectors();  // Обновляем векторы направления камеры с учётом новых углов
    }

    private void updateCameraVectors() {
        Vector3f front = new Vector3f();
        front.x = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) Math.sin(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        this.front = front.normalize();
        right = new Vector3f(front).cross(worldUp).normalize();
        up = new Vector3f(right).cross(front).normalize();
    }


    // Возвращает матрицу вида, необходимую для преобразования в пространстве экрана
    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), up);
        // Создаём матрицу вида, где камера смотрит из позиции `position` в точку `position + front` и с вектором вверх `up`
    }

    // Обрабатываем ввод с клавиатуры для перемещения камеры
    public void processKeyboard(int key, float deltaTime, boolean lockY) {
        float velocity = 6 * deltaTime;  // Рассчитываем скорость перемещения с учётом времени кадра

        // Создаём нормализованные векторы направления по XZ-плоскости
        Vector3f frontOnXZ = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f rightOnXZ = new Vector3f(right.x, 0, right.z).normalize();

        // Проверяем нажатие клавиш и перемещаем камеру в соответствующем направлении
        if (key == GLFW_KEY_W)
            position.add(frontOnXZ.mul(velocity));  // Вперёд
        if (key == GLFW_KEY_S)
            position.sub(frontOnXZ.mul(velocity));  // Назад
        if (key == GLFW_KEY_A)
            position.sub(rightOnXZ.mul(velocity));  // Влево
        if (key == GLFW_KEY_D)
            position.add(rightOnXZ.mul(velocity));  // Вправо

        if (lockY) {
            position.y = 1.0f;  // Если высота фиксирована, принудительно устанавливаем Y
        }
    }

    // Устанавливаем камеру позади модели на определённом расстоянии и высоте (вид от третьего лица)
    public void setThirdPersonView(Vector3f modelPosition, float distanceBehind, float heightAbove) {
        this.position.set(modelPosition)  // Устанавливаем позицию камеры в зависимости от позиции модели
                .sub(front.x * distanceBehind, 0, front.z * distanceBehind)  // Смещаем камеру назад по оси Z
                .add(0, heightAbove, 0);  // Поднимаем камеру на заданную высоту

        pitch = -15.0f;  // Наклоняем камеру вниз на 15 градусов для вида сверху
        updateCameraVectorsModel();  // Обновляем векторы направления камеры
    }

    // Обновляем позицию камеры, чтобы она следовала за машиной
    public void updateCameraPosition(Vector3f carPosition, Vector3f carDirection, float distanceBehind, float heightAbove) {
        // Рассчитываем смещение камеры позади машины и немного выше
        Vector3f offset = new Vector3f(carDirection).normalize().mul(distanceBehind);  // Определяем расстояние позади машины
        this.position.set(carPosition).add(offset).add(0, heightAbove, 0);  // Устанавливаем позицию камеры

        front = new Vector3f(carPosition).sub(position).normalize();  // Направляем камеру на машину

        updateCameraVectorsModel();  // Обновляем векторы направления камеры
    }

    // Обновляем векторы направления камеры на основе текущих значений front, right и up
    private void updateCameraVectorsModel() {
        // Рассчитываем вектор right (вправо) как перекрёстный продукт front и worldUp
        right = new Vector3f(front).cross(worldUp).normalize();
        // Рассчитываем вектор up (вверх) как перекрёстный продукт right и front
        up = new Vector3f(right).cross(front).normalize();
    }

    // Возвращает текущую позицию камеры
    public Vector3f getPosition() {
        return position;
    }
}

