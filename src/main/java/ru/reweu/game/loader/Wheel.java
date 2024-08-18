package ru.reweu.game.loader;

import org.joml.Vector3f;

public class Wheel {
    private ru.reweu.game.loader.Mesh mesh;
    private Vector3f position;
    private float rotationAngle; // Угол поворота колеса

    public Wheel(Vector3f position) {
        this.rotationAngle = 0.0f; // Начальный угол поворота
    }

    public void setRotationAngle(float rotationAngle) {
        this.rotationAngle = rotationAngle;
    }

    @Override
    public String toString() {
        return "Wheel{" +
            "mesh=" + mesh +
            ", position=" + position +
            ", rotationAngle=" + rotationAngle +
            '}';
    }
}
