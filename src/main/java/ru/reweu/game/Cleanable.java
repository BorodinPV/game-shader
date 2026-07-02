package ru.reweu.game;

/**
 * Интерфейс для объектов, требующих явного освобождения ресурсов.
 */
@FunctionalInterface
public interface Cleanable {
    void cleanup();
}
