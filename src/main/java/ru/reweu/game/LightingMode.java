package ru.reweu.game;

/**
 * Пресеты режимов освещения.
 * Определяет какие источники света активны для каждого сценария.
 */
public enum LightingMode {
    /**
     * Студийное освещение (мягче ключ, сильнее заполняющий свет и IBL).
     */
    STUDIO(true, true, true, true, true, true),

    /**
     * Наружное естественное освещение (сильное солнце, тени).
     */
    OUTDOOR(true, true, true, true, false, false),

    /**
     * Ночное освещение (слабое солнце, только IBL).
     */
    NIGHT(false, true, true, true, false, false),

    /**
     * Полная тьма (только IBL и ambient).
     */
    DARK(false, false, false, true, false, false),

    /**
     * Только солнце (без заполняющего и IBL).
     */
    SUN_ONLY(true, true, false, false, false, false);

    private final boolean sunLightEnabled;
    private final boolean shadowsEnabled;
    private final boolean fillLightEnabled;
    private final boolean iblEnabled;
    private final boolean ambientConstantEnabled;
    private final boolean hemisphereAmbientEnabled;

    LightingMode(boolean sunLight, boolean shadows, boolean fillLight, boolean ibl,
                 boolean ambientConstant, boolean hemisphereAmbient) {
        this.sunLightEnabled = sunLight;
        this.shadowsEnabled = shadows;
        this.fillLightEnabled = fillLight;
        this.iblEnabled = ibl;
        this.ambientConstantEnabled = ambientConstant;
        this.hemisphereAmbientEnabled = hemisphereAmbient;
    }

    public boolean isSunLightEnabled() {
        return sunLightEnabled;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    public boolean isFillLightEnabled() {
        return fillLightEnabled;
    }

    public boolean isIblEnabled() {
        return iblEnabled;
    }

    public boolean isAmbientConstantEnabled() {
        return ambientConstantEnabled;
    }

    public boolean isHemisphereAmbientEnabled() {
        return hemisphereAmbientEnabled;
    }

    public static LightingMode fromString(String name) {
        try {
            return LightingMode.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STUDIO;
        }
    }
}
