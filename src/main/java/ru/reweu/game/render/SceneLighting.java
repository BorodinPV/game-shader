package ru.reweu.game.render;

import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.RuntimeGraphicsSettings;

/**
 * Параметры сцены: статичный день, пресет «студия» через {@link GameConfig#effectiveStudioLightingPreset()}.
 * <p>
 * Константы геометрии солнца и тюнинг наружной сцены — {@link Outdoor}; фиксированные значения студии — {@link Studio}.
 * На кадр: {@link #frame()}; {@link #emissiveDisplayBoost()} — тот же множитель, что
 * {@link LightingFrame#emissiveBoost()} в {@link SceneFrameUniforms#bindLitFrame} для {@code pbr_gltf.frag}.
 */
public final class SceneLighting {

    /** Азимут в градусах: от +X, против часовой стрелки при виде сверху (Y вверх). */
    public static final float SUN_AZIMUTH_DEG = 48f;

    /** Угол над горизонтом в градусах (0 = горизонт, 90 = зенит). */
    public static final float SUN_ELEVATION_DEG = 55.4f;

    /** Диск солнца на небе (sky_pass): держим низко — иначе ACES даёт большое белое пятно. */
    public static final float SUN_DISC_SCALE = 11.0f;

    /**
     * Наружная сцена: базовые числа до env-масштабов {@link GameConfig#effectiveExposureScale()} /
     * {@link GameConfig#effectiveIblIntensityScale()}.
     */
    public static final class Outdoor {
        private Outdoor() {
        }

        /** Диффуз по нормали к солнцу; чуть ниже прежнего 4.6 — мягче контраст с тенью (см. fill/IBL). */
        public static final float SUN_INTENSITY_MAX = 4.15f;
        public static final float EMISSIVE_DISPLAY_BOOST = 6.0f;
        public static final float AMBIENT_R = 0.4f;
        public static final float AMBIENT_G = 0.42f;
        public static final float AMBIENT_B = 0.45f;
        public static final float FILL_STRENGTH_WORLD = 0.52f;
        public static final float FILL_STRENGTH_GLTF = 0.48f;
        public static final float HEMI_MIX = 0.55f;
        public static final float WORLD_AMBIENT_HEMI_SCALE = 0.65f;
        public static final float WORLD_AMBIENT_HEMI_HEMI = 0.35f;
        public static final float IBL_INTENSITY = 1.65f;
        public static final float EXPOSURE = 1.48f;
        public static final float FILL_SPECULAR_GLTF = 0.28f;
    }

    /**
     * Пресет «студия»: мягче ключ, сильнее fill/IBL, светлые гемисферы (см. {@link GameConfig#STUDIO_LIGHTING_PRESET}).
     */
    public static final class Studio {
        private Studio() {
        }

        public static final float SUN_INTENSITY_MAX = 3.4f;
        public static final float EMISSIVE_DISPLAY_BOOST = 6.5f;
        public static final float FILL_STRENGTH_WORLD = 0.52f;
        public static final float FILL_STRENGTH_GLTF = 0.48f;
        public static final float FILL_SPECULAR_GLTF = 0.42f;
        public static final float IBL_INTENSITY = 1.15f;
        public static final float EXPOSURE = 1.85f;
        public static final Vector3f SKY_AMBIENT = new Vector3f(0.88f, 0.90f, 0.96f);
        public static final Vector3f GROUND_AMBIENT = new Vector3f(0.48f, 0.49f, 0.52f);
        public static final Vector3f CLEAR_COLOR = new Vector3f(0.94f, 0.95f, 0.97f);
    }

    private SceneLighting() {
    }

    /**
     * Множитель emissive в {@code pbr_gltf.frag} (равен {@link LightingFrame#emissiveBoost()} после меню);
     * для вызовов без готового {@link LightingFrame}.
     */
    public static float emissiveDisplayBoost() {
        return emissiveDisplayBoost(RuntimeGraphicsSettings.get());
    }

    public static float emissiveDisplayBoost(RuntimeGraphicsSettings rs) {
        return emissiveDisplayBoostForStudio(rs.isStudioPreset()) * rs.getLightingEmissiveBoostScale();
    }

    private static float emissiveDisplayBoostForStudio(boolean studio) {
        return studio ? Studio.EMISSIVE_DISPLAY_BOOST : Outdoor.EMISSIVE_DISPLAY_BOOST;
    }

    /**
     * Полный снимок освещения на кадр — один раз на кадр в {@link ru.reweu.game.render.SceneRenderer}.
     */
    public static LightingFrame frame() {
        return frame(RuntimeGraphicsSettings.get());
    }

    /**
     * Снимок с учётом пресета студии (и далее — тогглов) из {@link RuntimeGraphicsSettings}.
     */
    public static LightingFrame frame(RuntimeGraphicsSettings rs) {
        boolean studio = rs.isStudioPreset();
        Vector3f sunDir = computeSunDirection();
        float sunH = sunHeightFactor();

        Vector3f sunCol = sunColor(sunH);
        float sunInt = sunIntensity(studio, sunH);

        Vector3f amb = new Vector3f(Outdoor.AMBIENT_R, Outdoor.AMBIENT_G, Outdoor.AMBIENT_B);
        Vector3f fillDir = computeFillDirection(sunDir);
        Vector3f fillCol = new Vector3f(0.74f, 0.80f, 0.93f);

        float fillW = studio ? Studio.FILL_STRENGTH_WORLD : Outdoor.FILL_STRENGTH_WORLD;
        float fillG = studio ? Studio.FILL_STRENGTH_GLTF : Outdoor.FILL_STRENGTH_GLTF;
        float fillSpec = studio ? Studio.FILL_SPECULAR_GLTF : Outdoor.FILL_SPECULAR_GLTF;
        float emissive = emissiveDisplayBoostForStudio(studio);

        Vector3f sky;
        Vector3f ground;
        Vector3f clear;
        if (studio) {
            sky = new Vector3f(Studio.SKY_AMBIENT);
            ground = new Vector3f(Studio.GROUND_AMBIENT);
            clear = new Vector3f(Studio.CLEAR_COLOR);
        } else {
            sky = outdoorSkyAmbient(sunH);
            ground = outdoorGroundAmbient(sunH, sunCol);
            clear = new Vector3f(sky);
        }

        float exposure = (studio ? Studio.EXPOSURE : Outdoor.EXPOSURE) * GameConfig.effectiveExposureScale();
        float ibl = (studio ? Studio.IBL_INTENSITY : Outdoor.IBL_INTENSITY) * GameConfig.effectiveIblIntensityScale();

        LightingFrame base = new LightingFrame(
            sunDir,
            sunCol,
            sunInt,
            amb,
            fillDir,
            fillCol,
            fillW,
            fillG,
            fillSpec,
            emissive,
            sky,
            ground,
            clear,
            exposure,
            ibl,
            Outdoor.HEMI_MIX,
            Outdoor.WORLD_AMBIENT_HEMI_SCALE,
            Outdoor.WORLD_AMBIENT_HEMI_HEMI,
            SUN_DISC_SCALE
        );
        return rs.applyLightingToggles(base);
    }

    private static Vector3f computeSunDirection() {
        float az = (float) Math.toRadians(SUN_AZIMUTH_DEG);
        float el = (float) Math.toRadians(SUN_ELEVATION_DEG);
        float ce = (float) Math.cos(el);
        float x = ce * (float) Math.cos(az);
        float y = (float) Math.sin(el);
        float z = ce * (float) Math.sin(az);
        return new Vector3f(x, y, z).normalize();
    }

    private static float sunHeightFactor() {
        float el = (float) Math.toRadians(SUN_ELEVATION_DEG);
        return clamp01((float) Math.sin(el));
    }

    private static Vector3f sunColor(float sunHeightFactor) {
        Vector3f warm = new Vector3f(1.0f, 0.78f, 0.52f);
        Vector3f neutral = new Vector3f(1.0f, 0.98f, 0.94f);
        return warm.lerp(neutral, sunHeightFactor, new Vector3f());
    }

    private static float sunIntensity(boolean studio, float sunHeightFactor) {
        float max = studio ? Studio.SUN_INTENSITY_MAX : Outdoor.SUN_INTENSITY_MAX;
        return max * (0.52f + 0.48f * sunHeightFactor);
    }

    private static Vector3f outdoorSkyAmbient(float sunHeightFactor) {
        Vector3f zenith = new Vector3f(0.22f, 0.50f, 0.90f);
        Vector3f horizon = new Vector3f(0.42f, 0.62f, 0.88f);
        return zenith.lerp(horizon, 1f - sunHeightFactor, new Vector3f());
    }

    private static Vector3f outdoorGroundAmbient(float sunHeightFactor, Vector3f sunCol) {
        Vector3f base = new Vector3f(0.16f, 0.32f, 0.52f);
        return base.add(sunCol.mul(0.015f * sunHeightFactor, new Vector3f()), new Vector3f());
    }

    private static Vector3f computeFillDirection(Vector3f sunDir) {
        Vector3f scatter = new Vector3f(-sunDir.x, 0.18f, -sunDir.z);
        if (scatter.lengthSquared() < 1e-8f) {
            scatter.set(0f, 1f, 0f);
        }
        return scatter.normalize();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
