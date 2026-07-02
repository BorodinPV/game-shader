package ru.reweu.game;

/**
 * Конфигурация для масштабов освещения.
 * Группирует все множители интенсивности освещения в один объект.
 */
public class LightingScalesConfig {

    private float exposureScale;
    private float iblScale;
    private float sunIntensityScale;
    private float fillStrengthScale;
    private float fillSpecularScale;
    private float emissiveBoostScale;
    private float worldIndirectScale;

    public LightingScalesConfig(float exposureScale, float iblScale, float sunIntensityScale,
                               float fillStrengthScale, float fillSpecularScale,
                               float emissiveBoostScale, float worldIndirectScale) {
        this.exposureScale = exposureScale;
        this.iblScale = iblScale;
        this.sunIntensityScale = sunIntensityScale;
        this.fillStrengthScale = fillStrengthScale;
        this.fillSpecularScale = fillSpecularScale;
        this.emissiveBoostScale = emissiveBoostScale;
        this.worldIndirectScale = worldIndirectScale;
    }

    public static LightingScalesConfig neutral() {
        return new LightingScalesConfig(1f, 1f, 1f, 1f, 1f, 1f, 1f);
    }

    public float getExposureScale() {
        return exposureScale;
    }

    public void setExposureScale(float exposureScale) {
        this.exposureScale = exposureScale;
    }

    public float getIblScale() {
        return iblScale;
    }

    public void setIblScale(float iblScale) {
        this.iblScale = iblScale;
    }

    public float getSunIntensityScale() {
        return sunIntensityScale;
    }

    public void setSunIntensityScale(float sunIntensityScale) {
        this.sunIntensityScale = sunIntensityScale;
    }

    public float getFillStrengthScale() {
        return fillStrengthScale;
    }

    public void setFillStrengthScale(float fillStrengthScale) {
        this.fillStrengthScale = fillStrengthScale;
    }

    public float getFillSpecularScale() {
        return fillSpecularScale;
    }

    public void setFillSpecularScale(float fillSpecularScale) {
        this.fillSpecularScale = fillSpecularScale;
    }

    public float getEmissiveBoostScale() {
        return emissiveBoostScale;
    }

    public void setEmissiveBoostScale(float emissiveBoostScale) {
        this.emissiveBoostScale = emissiveBoostScale;
    }

    public float getWorldIndirectScale() {
        return worldIndirectScale;
    }

    public void setWorldIndirectScale(float worldIndirectScale) {
        this.worldIndirectScale = worldIndirectScale;
    }

    @Override
    public String toString() {
        return "LightingScalesConfig{" +
            "exposureScale=" + exposureScale +
            ", iblScale=" + iblScale +
            ", sunIntensityScale=" + sunIntensityScale +
            ", fillStrengthScale=" + fillStrengthScale +
            ", fillSpecularScale=" + fillSpecularScale +
            ", emissiveBoostScale=" + emissiveBoostScale +
            ", worldIndirectScale=" + worldIndirectScale +
            '}';
    }
}
