package ru.reweu.game;

import ru.reweu.game.gui.PauseMenu;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Ввод: WASD, ESC-меню, мышь для камеры и UI паузы.
 */
public final class GameInputController {

    private static final int[] MOVEMENT_KEYS = {
        GLFW_KEY_W, GLFW_KEY_S, GLFW_KEY_A, GLFW_KEY_D
    };

    private final double[] cursorX = new double[1];
    private final double[] cursorY = new double[1];
    private float lastMouseX = 400f;
    private float lastMouseY = 300f;
    private boolean firstMouse = true;
    private boolean menuOpen;
    private boolean escKeyDown;

    public boolean isMenuOpen() {
        return menuOpen;
    }

    public void update(long window, Camera camera, PauseMenu pauseMenu, float deltaTime) {
        if (!menuOpen) {
            boolean sprinting = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
            for (int key : MOVEMENT_KEYS) {
                if (glfwGetKey(window, key) == GLFW_PRESS) {
                    camera.processKeyboard(key, deltaTime, sprinting);
                }
            }
        }

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            if (!escKeyDown) {
                escKeyDown = true;
                menuOpen = !menuOpen;
                glfwSetInputMode(window, GLFW_CURSOR, menuOpen ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                if (menuOpen) {
                    firstMouse = true;
                } else {
                    pauseMenu.resetPointerState();
                    RuntimeGraphicsSettings.persistCurrent();
                }
            }
        } else {
            escKeyDown = false;
        }
    }

    public void handleMouseMove(long window, double xpos, double ypos, Camera camera, PauseMenu pauseMenu) {
        if (menuOpen) {
            pauseMenu.handlePointerMove(xpos, ypos);
            return;
        }
        if (firstMouse) {
            lastMouseX = (float) xpos;
            lastMouseY = (float) ypos;
            firstMouse = false;
        }

        float xOffset = (float) xpos - lastMouseX;
        float yOffset = lastMouseY - (float) ypos;

        lastMouseX = (float) xpos;
        lastMouseY = (float) ypos;

        camera.processMouseMovement(xOffset, yOffset);
    }

    public void handleMouseButton(long window, int button, int action, PauseMenu pauseMenu) {
        if (button != GLFW_MOUSE_BUTTON_LEFT || !menuOpen) {
            return;
        }
        glfwGetCursorPos(window, cursorX, cursorY);
        if (action == GLFW_PRESS) {
            pauseMenu.handlePointerDown(cursorX[0], cursorY[0]);
        } else if (action == GLFW_RELEASE) {
            boolean slider = pauseMenu.handlePointerUp(cursorX[0], cursorY[0]);
            if (slider || pauseMenu.handleClick(cursorX[0], cursorY[0])) {
                RuntimeGraphicsSettings.persistCurrent();
            }
        }
    }
}
