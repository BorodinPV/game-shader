package ru.reweu.game.main;

public class Variables {

    private static float deltaTime;
    private static float lastFrame;

    public static void setDeltaTime(float deltaTime) {
        Variables.deltaTime = deltaTime;
    }

    public static void setLastFrame(float lastFrame) {
        Variables.lastFrame = lastFrame;
    }

    public static float getDeltaTime() {
        return deltaTime;
    }

    public static float getLastFrame() {
        return lastFrame;
    }
}
