package ru.reweu.game.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Utils {

    private static ConcurrentHashMap<String, Mesh> wheel = new ConcurrentHashMap<>();
    private static List<Mesh> wheels = new ArrayList<>();

    public static Mesh getWheel(String name) {
        return wheel.get(name);
    }

    public static ConcurrentHashMap<String, Mesh> getWheel() {
        return wheel;
    }

    public static List<Mesh> getWheels() {
        return wheels;
    }

    public static void addWheels(Mesh mesh) {
        Utils.wheels.add(mesh);
    }

    public static void addWheel(String name, Mesh mesh) {
        Utils.wheel.put(name, mesh);
    }
}
