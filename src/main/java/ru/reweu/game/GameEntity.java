package ru.reweu.game;

import org.joml.Vector3f;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.gltf.GltfScene;

/**
 * Представляет игровой объект с позицией, масштабом и представлением (меш или glTF).
 * Объединяет данные, которые были разбросаны по нескольким спискам в Game3d.
 */
public class GameEntity {

    private final Vector3f worldPosition;
    private float scale;
    
    // Одно из этих полей не null - либо это меш-объект (Assimp), либо glTF
    private final Mesh[] meshes;
    private final GltfScene gltfScene;
    
    private final String name;

    /**
     * Создаёт сущность с мешами (Assimp-модель).
     */
    public GameEntity(String name, Vector3f worldPosition, float scale, Mesh[] meshes) {
        this.name = name;
        this.worldPosition = new Vector3f(worldPosition);
        this.scale = scale;
        this.meshes = meshes;
        this.gltfScene = null;
    }

    /**
     * Создаёт сущность с glTF сценой.
     */
    public GameEntity(String name, Vector3f worldPosition, float scale, GltfScene gltfScene) {
        this.name = name;
        this.worldPosition = new Vector3f(worldPosition);
        this.scale = scale;
        this.meshes = null;
        this.gltfScene = gltfScene;
    }

    public String getName() {
        return name;
    }

    public Vector3f getWorldPosition() {
        return worldPosition;
    }

    public void setWorldPosition(Vector3f position) {
        this.worldPosition.set(position);
    }

    public void setWorldPosition(float x, float y, float z) {
        this.worldPosition.set(x, y, z);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public Mesh[] getMeshes() {
        return meshes;
    }

    public GltfScene getGltfScene() {
        return gltfScene;
    }

    public boolean isGltf() {
        return gltfScene != null;
    }

    public boolean isMesh() {
        return meshes != null;
    }

    @Override
    public String toString() {
        return "GameEntity{" +
            "name='" + name + '\'' +
            ", position=" + worldPosition +
            ", scale=" + scale +
            ", type=" + (isGltf() ? "glTF" : "Mesh") +
            '}';
    }
}
