package ru.reweu.game;

import org.joml.Vector3f;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.gltf.GltfScene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Менеджер для управления всеми игровыми сущностями (машины, ландшафт, пропсы).
 * Заменяет множество разрозненных списков на единую структуру.
 */
public class EntityManager {

    private final List<GameEntity> entities = new ArrayList<>();

    /**
     * Создаёт и регистрирует новую сущность с мешами.
     */
    public GameEntity createMeshEntity(String name, Vector3f position, float scale, Mesh[] meshes) {
        GameEntity entity = new GameEntity(name, position, scale, meshes);
        entities.add(entity);
        return entity;
    }

    /**
     * Создаёт и регистрирует новую сущность с glTF.
     */
    public GameEntity createGltfEntity(String name, Vector3f position, float scale, GltfScene gltfScene) {
        GameEntity entity = new GameEntity(name, position, scale, gltfScene);
        entities.add(entity);
        return entity;
    }

    /**
     * Регистрирует существующую сущность.
     */
    public void registerEntity(GameEntity entity) {
        entities.add(entity);
    }

    /**
     * Получает сущность по имени (первое совпадение).
     */
    public GameEntity getEntityByName(String name) {
        return entities.stream()
            .filter(e -> e.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Получает все сущности (неизменяемый список).
     */
    public List<GameEntity> getAllEntities() {
        return Collections.unmodifiableList(entities);
    }

    /**
     * Получает все сущности типа glTF.
     */
    public List<GameEntity> getGltfEntities() {
        return entities.stream()
            .filter(GameEntity::isGltf)
            .toList();
    }

    /**
     * Получает все сущности типа Mesh.
     */
    public List<GameEntity> getMeshEntities() {
        return entities.stream()
            .filter(GameEntity::isMesh)
            .toList();
    }

    /**
     * Удаляет сущность по имени.
     */
    public boolean removeEntityByName(String name) {
        return entities.removeIf(e -> e.getName().equals(name));
    }

    /**
     * Удаляет сущность.
     */
    public boolean removeEntity(GameEntity entity) {
        return entities.remove(entity);
    }

    /**
     * Получает количество зарегистрированных сущностей.
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Очищает все сущности.
     */
    public void clear() {
        entities.clear();
    }

    @Override
    public String toString() {
        return "EntityManager{" +
            "entityCount=" + entities.size() +
            ", entities=" + entities +
            '}';
    }
}
