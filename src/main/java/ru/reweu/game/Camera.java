package ru.reweu.game;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private Vector3f front;
    private Vector3f up;
    private Vector3f right;
    private Vector3f worldUp;

    private float yaw;
    private float pitch;

    private float mouseSensitivity = 0.1f;

    public Camera(Vector3f position, Vector3f up, float yaw, float pitch) {
        this.position = position;
        this.worldUp = up;
        this.yaw = yaw;
        this.pitch = pitch;
        this.front = new Vector3f(0.0f, 0.0f, -1.0f);

        updateCameraVectors();
    }

    public void processMouseMovement(float xOffset, float yOffset) {
        xOffset *= mouseSensitivity;
        yOffset *= mouseSensitivity;

        yaw += xOffset;
        pitch += yOffset;

        if (pitch > 89.0f) {
            pitch = 89.0f;
        }
        if (pitch < -89.0f) {
            pitch = -89.0f;
        }

        updateCameraVectors();
    }

    private void updateCameraVectors() {
        Vector3f f = new Vector3f();
        f.x = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        f.y = (float) Math.sin(Math.toRadians(pitch));
        f.z = (float) Math.sin(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        this.front = f.normalize();
        right = new Vector3f(front).cross(worldUp).normalize();
        up = new Vector3f(right).cross(front).normalize();
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), up);
    }

    public void processKeyboard(int key, float deltaTime) {
        float velocity = 6 * deltaTime;

        Vector3f frontOnXZ = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f rightOnXZ = new Vector3f(right.x, 0, right.z).normalize();

        if (key == GLFW_KEY_W) {
            position.add(frontOnXZ.mul(velocity));
        }
        if (key == GLFW_KEY_S) {
            position.sub(frontOnXZ.mul(velocity));
        }
        if (key == GLFW_KEY_A) {
            position.sub(rightOnXZ.mul(velocity));
        }
        if (key == GLFW_KEY_D) {
            position.add(rightOnXZ.mul(velocity));
        }
    }

    public void setThirdPersonView(Vector3f modelPosition, float distanceBehind, float heightAbove) {
        this.position.set(modelPosition)
            .sub(front.x * distanceBehind, 0, front.z * distanceBehind)
            .add(0, heightAbove, 0);

        pitch = -15.0f;
        updateCameraVectorsModel();
    }

    private void updateCameraVectorsModel() {
        right = new Vector3f(front).cross(worldUp).normalize();
        up = new Vector3f(right).cross(front).normalize();
    }

    public Vector3f getPosition() {
        return position;
    }
}
