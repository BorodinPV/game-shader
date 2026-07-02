package ru.reweu.game;

/**
 * Конфигурация для параметров теней (тень, bias, PCF).
 * Группирует связанные параметры теней в один объект.
 */
public class ShadowConfig {

    private float biasWorld;
    private float biasGltf;
    private float gltfShadowReceiveFloor;
    private boolean gltfShadowPcfUseShadingNormal;

    public ShadowConfig(float biasWorld, float biasGltf, float gltfShadowReceiveFloor, 
                       boolean gltfShadowPcfUseShadingNormal) {
        this.biasWorld = biasWorld;
        this.biasGltf = biasGltf;
        this.gltfShadowReceiveFloor = gltfShadowReceiveFloor;
        this.gltfShadowPcfUseShadingNormal = gltfShadowPcfUseShadingNormal;
    }

    public float getBiasWorld() {
        return biasWorld;
    }

    public void setBiasWorld(float biasWorld) {
        this.biasWorld = biasWorld;
    }

    public float getBiasGltf() {
        return biasGltf;
    }

    public void setBiasGltf(float biasGltf) {
        this.biasGltf = biasGltf;
    }

    public float getGltfShadowReceiveFloor() {
        return gltfShadowReceiveFloor;
    }

    public void setGltfShadowReceiveFloor(float gltfShadowReceiveFloor) {
        this.gltfShadowReceiveFloor = gltfShadowReceiveFloor;
    }

    public boolean isGltfShadowPcfUseShadingNormal() {
        return gltfShadowPcfUseShadingNormal;
    }

    public void setGltfShadowPcfUseShadingNormal(boolean gltfShadowPcfUseShadingNormal) {
        this.gltfShadowPcfUseShadingNormal = gltfShadowPcfUseShadingNormal;
    }

    @Override
    public String toString() {
        return "ShadowConfig{" +
            "biasWorld=" + biasWorld +
            ", biasGltf=" + biasGltf +
            ", gltfShadowReceiveFloor=" + gltfShadowReceiveFloor +
            ", gltfShadowPcfUseShadingNormal=" + gltfShadowPcfUseShadingNormal +
            '}';
    }
}
