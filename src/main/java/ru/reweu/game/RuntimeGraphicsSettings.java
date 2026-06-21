package ru.reweu.game;

import org.joml.Vector3f;
import ru.reweu.game.render.LightingFrame;

/**
 * Настройки освещения и рендера во время выполнения (меню ESC). Стартовые значения — из {@link GameConfig}.
 *
 * <p><b>Растр</b> ({@link ru.reweu.game.render.SceneRenderer} без ray trace): все тогглы и слайдеры
 * участвуют в {@link #applyLightingToggles}, {@link ru.reweu.game.render.SceneFrameUniforms},
 * отрисовке неба/ландшафта/glTF.
 *
 * <p><b>Ray trace</b> ({@link GameConfig#RAY_TRACE_ENABLED}): кадр идёт только через
 * {@link ru.reweu.game.render.rt.RayTraceRenderer} — туда попадает уже {@link LightingFrame}
 * (солнце, exposure, sky/ground, тогглы IBL/солнца/гемисферы/ambient и слайдеры ×), но
 * <em>нет</em> fill, карт теней PCF, отдельных bias/floor glTF и тогглов «рисовать ландшафт/glTF/…».
 */
public final class RuntimeGraphicsSettings {

    private static RuntimeGraphicsSettings instance;

    public static RuntimeGraphicsSettings get() {
        RuntimeGraphicsSettings local = instance;
        if (local != null) {
            return local;
        }
        synchronized (RuntimeGraphicsSettings.class) {
            local = instance;
            if (local != null) {
                return local;
            }
            local = new RuntimeGraphicsSettings();
            local.resetDefaultsFromGameConfig();
            RuntimeGraphicsSettingsPersistence.loadInto(local);
            instance = local;
            return local;
        }
    }

    /** Записать текущие настройки в {@link RuntimeGraphicsSettingsPersistence#storagePath()}. */
    public static void persistCurrent() {
        RuntimeGraphicsSettings inst = instance;
        if (inst != null) {
            RuntimeGraphicsSettingsPersistence.save(inst);
        }
    }

    /** Пресет «студия» (мягче ключ, сильнее fill/IBL). */
    private boolean studioPreset;

    private boolean sunLightEnabled;
    private boolean shadowsEnabled;
    private boolean fillLightEnabled;
    private boolean iblEnabled;
    /** Константный ambient в world-шейдере ({@code ambientColor}). */
    private boolean ambientConstantEnabled;
    /** Гемисфера небо/земля ({@code skyAmbientColor} / {@code groundAmbientColor}). */
    private boolean hemisphereAmbientEnabled;

    private boolean drawLandscape;
    private boolean drawGltfCars;
    private boolean drawProps;
    private boolean drawSky;
    private boolean showFpsOverlay;
    private boolean rainEnabled;
    private boolean fogEnabled;

    /** Множитель slope-bias PCF для ландшафта (меню / рантайм; старт из env и дефолтов {@link GameConfig}). */
    private float shadowBiasWorld;
    /** То же для glTF PBR. */
    private float shadowBiasGltf;
    /** Минимум {@code sh} прямого солнца на glTF ({@code u_shadowReceiveFloor}). */
    private float gltfShadowReceiveFloor;
    /** PCF slope-bias от шейдинговой нормали на glTF ({@code GAME_SHADOW_PCF_USE_SHADING_NORMAL}). */
    private boolean gltfShadowPcfUseShadingNormal;

    /**
     * Дополнительные множители к базовому кадру из {@link ru.reweu.game.render.SceneLighting#frame(RuntimeGraphicsSettings)}
     * (поверх env {@link GameConfig#effectiveExposureScale()} и т.д.). Все стартуют с 1.
     */
    private float lightingExposureScale;
    private float lightingIblScale;
    private float lightingSunIntensityScale;
    private float lightingFillStrengthScale;
    private float lightingFillSpecularScale;
    private float lightingEmissiveBoostScale;
    /** Ambient константа + цвета гемисферы (небо/земля) в world-шейдере. */
    private float lightingWorldIndirectScale;

    private final Vector3f toggleScratchAmb = new Vector3f();
    private final Vector3f toggleScratchSky = new Vector3f();
    private final Vector3f toggleScratchGround = new Vector3f();
    private final Vector3f toggleZero = new Vector3f();

    private RuntimeGraphicsSettings() {
    }

    public void resetDefaultsFromGameConfig() {
        studioPreset = GameConfig.effectiveStudioLightingPreset();
        sunLightEnabled = true;
        shadowsEnabled = true;
        fillLightEnabled = true;
        iblEnabled = true;
        ambientConstantEnabled = true;
        hemisphereAmbientEnabled = true;
        drawLandscape = GameConfig.DRAW_LANDSCAPE;
        drawGltfCars = GameConfig.USE_GLTF_NATIVE_LOADER;
        drawProps = true;
        drawSky = true;
        showFpsOverlay = true;
        rainEnabled = false;
        fogEnabled = false;
        shadowBiasWorld = GameConfig.effectiveShadowBiasScaleForProgram(true);
        shadowBiasGltf = GameConfig.effectiveShadowBiasScaleForProgram(false);
        gltfShadowReceiveFloor = GameConfig.effectiveGltfShadowReceiveFloor();
        gltfShadowPcfUseShadingNormal = GameConfig.effectiveShadowPcfUseShadingNormal();
        lightingExposureScale = 1f;
        lightingIblScale = 1f;
        lightingSunIntensityScale = 1f;
        lightingFillStrengthScale = 1f;
        lightingFillSpecularScale = 1f;
        lightingEmissiveBoostScale = 1f;
        lightingWorldIndirectScale = 1f;
    }

    public LightingFrame applyLightingToggles(LightingFrame f) {
        float sunInt = sunLightEnabled ? f.sunIntensity() * lightingSunIntensityScale : 0f;
        float fillW = fillLightEnabled ? f.fillStrengthWorld() * lightingFillStrengthScale : 0f;
        float fillG = fillLightEnabled ? f.fillStrengthGltf() * lightingFillStrengthScale : 0f;
        float fillSpec = fillLightEnabled ? f.fillSpecularStrengthGltf() * lightingFillSpecularScale : 0f;
        float ibl = iblEnabled ? f.iblIntensity() * lightingIblScale : 0f;
        Vector3f amb = ambientConstantEnabled
            ? toggleScratchAmb.set(f.ambientColor()).mul(lightingWorldIndirectScale)
            : toggleZero;
        Vector3f sky = hemisphereAmbientEnabled
            ? toggleScratchSky.set(f.skyAmbientColor()).mul(lightingWorldIndirectScale)
            : toggleZero;
        Vector3f ground = hemisphereAmbientEnabled
            ? toggleScratchGround.set(f.groundAmbientColor()).mul(lightingWorldIndirectScale)
            : toggleZero;
        float hemiMix = hemisphereAmbientEnabled ? f.hemiMix() : 0f;
        float hemiScale = hemisphereAmbientEnabled ? f.worldAmbientHemiScale() : 0f;
        float hemiHemi = hemisphereAmbientEnabled ? f.worldAmbientHemiHemi() : 0f;
        float exposure = f.exposure() * lightingExposureScale;
        float emissive = f.emissiveBoost() * lightingEmissiveBoostScale;
        return new LightingFrame(
            f.sunDirection(),
            f.sunColor(),
            sunInt,
            amb,
            f.fillDirection(),
            f.fillColor(),
            fillW,
            fillG,
            fillSpec,
            emissive,
            sky,
            ground,
            f.clearColor(),
            exposure,
            ibl,
            hemiMix,
            hemiScale,
            hemiHemi,
            f.sunDiscScale()
        );
    }

    public boolean isStudioPreset() {
        return studioPreset;
    }

    public void setStudioPreset(boolean studioPreset) {
        this.studioPreset = studioPreset;
    }

    public boolean isSunLightEnabled() {
        return sunLightEnabled;
    }

    public void setSunLightEnabled(boolean sunLightEnabled) {
        this.sunLightEnabled = sunLightEnabled;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    public void setShadowsEnabled(boolean shadowsEnabled) {
        this.shadowsEnabled = shadowsEnabled;
    }

    public boolean isFillLightEnabled() {
        return fillLightEnabled;
    }

    public void setFillLightEnabled(boolean fillLightEnabled) {
        this.fillLightEnabled = fillLightEnabled;
    }

    public boolean isIblEnabled() {
        return iblEnabled;
    }

    public void setIblEnabled(boolean iblEnabled) {
        this.iblEnabled = iblEnabled;
    }

    public boolean isAmbientConstantEnabled() {
        return ambientConstantEnabled;
    }

    public void setAmbientConstantEnabled(boolean ambientConstantEnabled) {
        this.ambientConstantEnabled = ambientConstantEnabled;
    }

    public boolean isHemisphereAmbientEnabled() {
        return hemisphereAmbientEnabled;
    }

    public void setHemisphereAmbientEnabled(boolean hemisphereAmbientEnabled) {
        this.hemisphereAmbientEnabled = hemisphereAmbientEnabled;
    }

    public boolean isDrawLandscape() {
        return drawLandscape;
    }

    public void setDrawLandscape(boolean drawLandscape) {
        this.drawLandscape = drawLandscape;
    }

    public boolean isDrawGltfCars() {
        return drawGltfCars;
    }

    public void setDrawGltfCars(boolean drawGltfCars) {
        this.drawGltfCars = drawGltfCars;
    }

    public boolean isDrawProps() {
        return drawProps;
    }

    public void setDrawProps(boolean drawProps) {
        this.drawProps = drawProps;
    }

    public boolean isDrawSky() {
        return drawSky;
    }

    public void setDrawSky(boolean drawSky) {
        this.drawSky = drawSky;
    }

    public boolean isShowFpsOverlay() {
        return showFpsOverlay;
    }

    public void setShowFpsOverlay(boolean showFpsOverlay) {
        this.showFpsOverlay = showFpsOverlay;
    }

    public boolean isRainEnabled() {
        return rainEnabled;
    }

    public void setRainEnabled(boolean rainEnabled) {
        this.rainEnabled = rainEnabled;
    }

    public boolean isFogEnabled() {
        return fogEnabled;
    }

    public void setFogEnabled(boolean fogEnabled) {
        this.fogEnabled = fogEnabled;
    }

    public float getShadowBiasWorld() {
        return shadowBiasWorld;
    }

    public void setShadowBiasWorld(float shadowBiasWorld) {
        this.shadowBiasWorld = Math.max(0.25f, Math.min(4f, shadowBiasWorld));
    }

    public float getShadowBiasGltf() {
        return shadowBiasGltf;
    }

    public void setShadowBiasGltf(float shadowBiasGltf) {
        this.shadowBiasGltf = Math.max(0.25f, Math.min(4f, shadowBiasGltf));
    }

    public float getGltfShadowReceiveFloor() {
        return gltfShadowReceiveFloor;
    }

    public void setGltfShadowReceiveFloor(float gltfShadowReceiveFloor) {
        this.gltfShadowReceiveFloor = Math.max(0f, Math.min(0.95f, gltfShadowReceiveFloor));
    }

    public boolean isGltfShadowPcfUseShadingNormal() {
        return gltfShadowPcfUseShadingNormal;
    }

    public void setGltfShadowPcfUseShadingNormal(boolean gltfShadowPcfUseShadingNormal) {
        this.gltfShadowPcfUseShadingNormal = gltfShadowPcfUseShadingNormal;
    }

    public float getLightingExposureScale() {
        return lightingExposureScale;
    }

    public void setLightingExposureScale(float lightingExposureScale) {
        this.lightingExposureScale = Math.max(0.2f, Math.min(3f, lightingExposureScale));
    }

    public float getLightingIblScale() {
        return lightingIblScale;
    }

    public void setLightingIblScale(float lightingIblScale) {
        this.lightingIblScale = Math.max(0f, Math.min(3f, lightingIblScale));
    }

    public float getLightingSunIntensityScale() {
        return lightingSunIntensityScale;
    }

    public void setLightingSunIntensityScale(float lightingSunIntensityScale) {
        this.lightingSunIntensityScale = Math.max(0f, Math.min(3f, lightingSunIntensityScale));
    }

    public float getLightingFillStrengthScale() {
        return lightingFillStrengthScale;
    }

    public void setLightingFillStrengthScale(float lightingFillStrengthScale) {
        this.lightingFillStrengthScale = Math.max(0f, Math.min(2.5f, lightingFillStrengthScale));
    }

    public float getLightingFillSpecularScale() {
        return lightingFillSpecularScale;
    }

    public void setLightingFillSpecularScale(float lightingFillSpecularScale) {
        this.lightingFillSpecularScale = Math.max(0f, Math.min(2.5f, lightingFillSpecularScale));
    }

    public float getLightingEmissiveBoostScale() {
        return lightingEmissiveBoostScale;
    }

    public void setLightingEmissiveBoostScale(float lightingEmissiveBoostScale) {
        this.lightingEmissiveBoostScale = Math.max(0.25f, Math.min(4f, lightingEmissiveBoostScale));
    }

    public float getLightingWorldIndirectScale() {
        return lightingWorldIndirectScale;
    }

    public void setLightingWorldIndirectScale(float lightingWorldIndirectScale) {
        this.lightingWorldIndirectScale = Math.max(0f, Math.min(2.5f, lightingWorldIndirectScale));
    }
}
