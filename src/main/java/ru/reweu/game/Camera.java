package ru.reweu.game;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    private static final float MIN_PITCH = -89.0f;
    private static final float MAX_PITCH = 89.0f;
    private static final float MOVE_SPEED = 6f;
    private static final float SPRINT_MULTIPLIER = 2.0f;
    private static final float JUMP_VELOCITY = 6f;
    private static final float GRAVITY = -15f;

    private final Vector3f position;
    private final Vector3f front;
    private final Vector3f up;
    private final Vector3f right;
    private final Vector3f worldUp;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Vector3f lookAtTarget = new Vector3f();
    private final Vector3f frontOnXZ = new Vector3f();
    private final Vector3f rightOnXZ = new Vector3f();
    private final Vector3f moveDelta = new Vector3f();

    private float yaw;
    private float pitch;
    private float mouseSensitivity = 0.1f;
    private boolean viewDirty = true;

    private float verticalVelocity = 0f;
    private boolean onGround = true;

    public Camera(Vector3f position, Vector3f up, float yaw, float pitch) {
        this.position = new Vector3f(position);
        this.worldUp = new Vector3f(up);
        this.yaw = yaw;
        this.pitch = pitch;
        this.front = new Vector3f(0.0f, 0.0f, -1.0f);
        this.right = new Vector3f();
        this.up = new Vector3f();
        updateCameraVectors();
    }

    public void processMouseMovement(float xOffset, float yOffset) {
        xOffset *= mouseSensitivity;
        yOffset *= mouseSensitivity;

        yaw += xOffset;
        pitch += yOffset;

        if (pitch > MAX_PITCH) {
            pitch = MAX_PITCH;
        }
        if (pitch < MIN_PITCH) {
            pitch = MIN_PITCH;
        }

        updateCameraVectors();
    }

    private void updateCameraVectors() {
        front.x = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) Math.sin(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        front.normalize();
        right.set(front).cross(worldUp).normalize();
        up.set(right).cross(front).normalize();
        viewDirty = true;
    }

    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            lookAtTarget.set(position).add(front);
            viewMatrix.setLookAt(position, lookAtTarget, up);
            viewDirty = false;
        }
        return viewMatrix;
    }

    public void processKeyboard(int key, float deltaTime, boolean sprinting) {
        float speed = MOVE_SPEED * (sprinting ? SPRINT_MULTIPLIER : 1f);
        float velocity = speed * deltaTime;

        frontOnXZ.set(front.x, 0f, front.z).normalize();
        rightOnXZ.set(right.x, 0f, right.z).normalize();

        if (key == GLFW_KEY_W) {
            moveDelta.set(frontOnXZ).mul(velocity);
            position.add(moveDelta);
            viewDirty = true;
        }
        if (key == GLFW_KEY_S) {
            moveDelta.set(frontOnXZ).mul(velocity);
            position.sub(moveDelta);
            viewDirty = true;
        }
        if (key == GLFW_KEY_A) {
            moveDelta.set(rightOnXZ).mul(velocity);
            position.sub(moveDelta);
            viewDirty = true;
        }
        if (key == GLFW_KEY_D) {
            moveDelta.set(rightOnXZ).mul(velocity);
            position.add(moveDelta);
            viewDirty = true;
        }
    }

    /** Прыжок — задаёт начальную вертикальную скорость (только если персонаж на земле). */
    public void jump() {
        if (onGround) {
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        }
    }

    /**
     * Обновляет вертикальную физику (гравитация + столкновение с рельефом).
     *
     * @param terrainY высота рельефа под камерой (или {@link Float#NaN} если нет)
     * @param eyeHeight высота глаз над рельефом
     * @param deltaTime время кадра
     */
    public void updatePhysics(float terrainY, float eyeHeight, float deltaTime) {
        if (Float.isNaN(terrainY)) {
            return;
        }
        float groundY = terrainY + eyeHeight;

        if (!onGround) {
            verticalVelocity += GRAVITY * deltaTime;
            position.y += verticalVelocity * deltaTime;

            if (position.y <= groundY) {
                position.y = groundY;
                verticalVelocity = 0f;
                onGround = true;
            }
            viewDirty = true;
        } else {
            // Snap to ground when standing
            position.y = groundY;
            viewDirty = true;
        }
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setThirdPersonView(Vector3f modelPosition, float distanceBehind, float heightAbove) {
        position.set(modelPosition)
            .sub(front.x * distanceBehind, 0, front.z * distanceBehind)
            .add(0, heightAbove, 0);

        pitch = -15.0f;
        right.set(front).cross(worldUp).normalize();
        up.set(right).cross(front).normalize();
        viewDirty = true;
    }

    public Vector3f getPosition() {
        return position;
    }

    public void invalidateView() {
        viewDirty = true;
    }
}
