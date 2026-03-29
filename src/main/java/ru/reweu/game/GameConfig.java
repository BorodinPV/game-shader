package ru.reweu.game;

/**
 * Константы игры и рендера (раньше были в {@code Game3d}).
 *
 * <p><b>Цель визуала:</b> {@link #renderLook()} отражает выбор между наружной сценой (солнце, трава)
 * и приближением к студийному/каталожному виду ({@link #effectiveStudioLightingPreset()}).
 * Полное совпадение с DCC на белом фоне без того же HDRI невозможно — см. {@code docs/RENDERING.md}.
 */
public final class GameConfig {

    /** Что считать целевым видом при настройке освещения (для логов и отладки). */
    public enum RenderLook {
        /** Наружный день: направленное солнце, тени, IBL из HDR/процедуры. */
        OUTDOOR,
        /** Ближе к студийному ключу: мягче солнце, сильнее fill/IBL (см. пресет студии). */
        STUDIO_REFERENCE
    }

    private GameConfig() {
    }

    public static final float LANDSCAPE_TEXTURE_SCALE = 4.5f;
    public static final float LANDSCAPE_OFFSET_Y = -10f;

    /** Ландшафт (OBJ); для отладки без рельефа можно поставить {@code false}. */
    public static final boolean DRAW_LANDSCAPE = true;

    /** GLB в {@code src/main/resources/models/}. */
    public static final String FORD_MUSTANG_1965_GLB = "/models/ford_mustang_1965.glb";

    /** Вторая машина (рядом с Mustang по +X). Файл положить в {@code models/}. */
    public static final String TOYOTA_AE86_GLB = "/models/toyota_ae86_sprinter_trueno_zenki.glb";

    /** Третья и четвёртая glTF-машины в линию по +X после AE86. */
    public static final String A_LAND_EXPLORER_FREE_GLB = "/models/a_land_explorer_free.glb";
    public static final String BEETLE_FUSCA_VERSION_1_GLB = "/models/beetlefusca_version_1.glb";

    /**
     * {@code true}: Mustang через нативный glTF 2.0 runtime (jgltf + PBR); {@code false}: Assimp + упрощённый шейдер.
     */
    public static final boolean USE_GLTF_NATIVE_LOADER = true;

    /**
     * Отладка: для glTF отключить выборку карты теней ({@code shadowsEnabled=0} только в батче PBR).
     * Ландшафт по-прежнему использует тени.
     */
    public static final boolean GLTF_DEBUG_DISABLE_SHADOWS = false;

    /** Отладка: игнорировать normal map в glTF (только геометрические нормали). */
    public static final boolean GLTF_DEBUG_DISABLE_NORMAL_MAP = false;

    /**
     * Отладка визуализации в {@code pbr_gltf.frag}: 0 — обычный рендер, 1 — мир N как цвет, 2 — фактор тени {@code sh}.
     * Без пересборки: {@code GLTF_DEBUG_VISUALIZE_MODE=2} (см. {@link #effectiveGltfDebugVisualizeMode()}).
     */
    public static final int GLTF_DEBUG_VISUALIZE_MODE = 0;

    /**
     * Режим отладки glTF-шейдера: константа {@link #GLTF_DEBUG_VISUALIZE_MODE} или env {@code GLTF_DEBUG_VISUALIZE_MODE} (0–2).
     */
    public static int effectiveGltfDebugVisualizeMode() {
        int v = parseIntEnv("GLTF_DEBUG_VISUALIZE_MODE", GLTF_DEBUG_VISUALIZE_MODE);
        return Math.max(0, Math.min(2, v));
    }

    /**
     * Диагностика (H1): в glTF принудительно {@code occlusion=1} для IBL (диффуз + спек окружения).
     * Env: {@code GAME_DEBUG_GLTF_NO_IBL_OCCLUSION=1}.
     */
    public static boolean effectiveDiagnosticGltfNoIblOcclusion() {
        return envFlag("GAME_DEBUG_GLTF_NO_IBL_OCCLUSION", false);
    }

    /**
     * Диагностика (H4): в PCF для glTF использовать шейдинговую нормаль {@code N} вместо геометрической {@code Ng}.
     * Env: {@code GAME_SHADOW_PCF_USE_SHADING_NORMAL=1}.
     */
    public static boolean effectiveShadowPcfUseShadingNormal() {
        return envFlag("GAME_SHADOW_PCF_USE_SHADING_NORMAL", false);
    }

    /**
     * Проверка Toyota GLB (JSON chunk): skins=0, KHR_texture_transform в материалах не используется;
     * transmission/clearcoat в файле есть — полный шейдер не реализован, см. {@link #GLTF_EXTENSION_FALLBACK_MIN_ROUGHNESS}.
     */
    public static final String GLTF_TOYOTA_DIAGNOSTIC_NOTE =
        "Toyota GLB: skins=0, no KHR_texture_transform; clearcoat/transmission: roughness floor applied";

    /**
     * Минимальная roughness для материалов с {@code KHR_materials_clearcoat} или
     * {@code KHR_materials_transmission} в JSON, пока расширения не реализованы в шейдере
     * (снижает «зеркальные» блики от IBL/солнца на кузове/стекле).
     */
    public static final float GLTF_EXTENSION_FALLBACK_MIN_ROUGHNESS = 0.42f;

    /** Масштаб модели (половина от 9.504). */
    public static final float FORD_MUSTANG_MODEL_SCALE = 3f;

    /** Мировые XZ рядом со стартом камеры (камера ≈ (0, y, 3)). */
    public static final float FORD_MUSTANG_WORLD_X = 2.5f;
    public static final float FORD_MUSTANG_WORLD_Z = 0f;

    /** Высота над рельефом в точке постановки (+30% к прежним 0.05). */
    public static final float FORD_MUSTANG_ABOVE_TERRAIN = 0.065f;

    /** Рядом с Mustang: смещение по +X (~6.5 м в масштабе сцены). */
    public static final float TOYOTA_AE86_WORLD_X = 9f;
    public static final float TOYOTA_AE86_WORLD_Z = 0f;
    public static final float TOYOTA_AE86_ABOVE_TERRAIN = 0.065f;

    public static final float LAND_EXPLORER_WORLD_X = 15.5f;
    public static final float LAND_EXPLORER_WORLD_Z = 0f;
    public static final float LAND_EXPLORER_ABOVE_TERRAIN = 0.065f;

    public static final float BEETLE_FUSCA_WORLD_X = 22f;
    public static final float BEETLE_FUSCA_WORLD_Z = 0f;
    public static final float BEETLE_FUSCA_ABOVE_TERRAIN = 0.065f;

    /** Масштаб смещения орто-центра тени к средней позиции машин по XZ. */
    public static final float SHADOW_ORTHO_CENTER_SCALE = 0.55f;

    /** Орто-центр тени по X из средней координаты машин по X (для {@link ru.reweu.game.render.DirectionalShadowMap}). */
    public static float shadowOrthoCenterXFromAvg(float avgWorldX) {
        return avgWorldX * SHADOW_ORTHO_CENTER_SCALE;
    }

    /** Орто-центр тени по Z из средней координаты машин по Z. */
    public static float shadowOrthoCenterZFromAvg(float avgWorldZ) {
        return avgWorldZ * SHADOW_ORTHO_CENTER_SCALE;
    }

    /** Стартовый центр теней по константам постановки (два автомобиля). */
    public static float shadowOrthoCenterX() {
        float ax = (FORD_MUSTANG_WORLD_X + TOYOTA_AE86_WORLD_X) * 0.5f;
        return shadowOrthoCenterXFromAvg(ax);
    }

    public static float shadowOrthoCenterZ() {
        float az = (FORD_MUSTANG_WORLD_Z + TOYOTA_AE86_WORLD_Z) * 0.5f;
        return shadowOrthoCenterZFromAvg(az);
    }

    public static final float CAMERA_EYE_HEIGHT = 1.65f;

    public static final float FOV_DEGREES = 45f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 1000f;

    /**
     * Полноэкранная compute-трассировка (первичный луч + тень солнца) вместо растеризации.
     * Нужен OpenGL 4.3+; при {@code true} запрашивается контекст 4.3 core.
     */
    public static final boolean RAY_TRACE_ENABLED = false;

    /** Максимум треугольников в SSBO (линейный перебор на GPU). */
    public static final int RAY_TRACE_MAX_TRIANGLES = 120_000;

    /** Делитель разрешения внутреннего RT-буфера (2 = половина ширины/высоты). */
    public static final int RAY_TRACE_INTERNAL_SCALE = 2;

    /**
     * {@code true}: VSync (частота кадров ≈ герцовка монитора). {@code false}: без ограничения по FPS со стороны swap (возможен tearing).
     */
    public static final boolean VSYNC = false;

    /**
     * {@code false} (по умолчанию): наружная сцена — направленное солнце, трава, тени под машиной.<br>
     * {@code true}: пресет ближе к «студийному» виду DCC (мягче ключ, сильнее fill и IBL, светлое небо);
     * полное совпадение с редактором на белом фоне без того же HDRI недостижимо.
     */
    public static final boolean STUDIO_LIGHTING_PRESET = false;

    /**
     * Эффективный пресет «студия»: константа {@link #STUDIO_LIGHTING_PRESET} или {@code GAME_STUDIO_LIGHTING=1}
     * (A/B без пересборки).
     */
    public static boolean effectiveStudioLightingPreset() {
        return STUDIO_LIGHTING_PRESET || envFlag("GAME_STUDIO_LIGHTING", false);
    }

    /**
     * Текущая цель визуала: студийный пресет или наружная сцена.
     */
    public static RenderLook renderLook() {
        return effectiveStudioLightingPreset() ? RenderLook.STUDIO_REFERENCE : RenderLook.OUTDOOR;
    }

    /**
     * Множитель к {@link ru.reweu.game.render.LightingFrame#exposure() exposure} из {@link ru.reweu.game.render.SceneLighting#frame()} (по умолчанию 1).
     * Переменная окружения: {@code GAME_EXPOSURE_SCALE} (например {@code 1.15}).
     */
    public static float effectiveExposureScale() {
        return parseFloatEnv("GAME_EXPOSURE_SCALE", 1f);
    }

    /**
     * Множитель к {@link ru.reweu.game.render.LightingFrame#iblIntensity() IBL} из {@link ru.reweu.game.render.SceneLighting#frame()} (по умолчанию 1).
     * Переменная: {@code GAME_IBL_INTENSITY_SCALE}.
     */
    public static float effectiveIblIntensityScale() {
        return parseFloatEnv("GAME_IBL_INTENSITY_SCALE", 1f);
    }

    /** По умолчанию 2048; env {@code GAME_SHADOW_MAP_SIZE} (512–8192, к ближайшей степени двойки). */
    public static final int DEFAULT_SHADOW_MAP_SIZE = 2048;

    /**
     * Разрешение ортокарты теней. Env: {@code GAME_SHADOW_MAP_SIZE} (например 4096).
     */
    public static int effectiveShadowMapSize() {
        int v = parseIntEnv("GAME_SHADOW_MAP_SIZE", DEFAULT_SHADOW_MAP_SIZE);
        v = Math.max(512, Math.min(8192, v));
        int p = 1;
        while (p < v) {
            p <<= 1;
        }
        if (p > 8192) {
            p = 8192;
        }
        return p;
    }

    /**
     * Множитель к slope-bias в PCF ({@code u_shadowBiasScale}).
     * Env {@code GAME_SHADOW_BIAS_SCALE} задаёт одно значение и для мира, и для glTF.
     * Без env: ландшафт {@code 1}, glTF {@value #DEFAULT_SHADOW_BIAS_SCALE_GLTF} (кузов/PCF тяжелее).
     */
    public static final float DEFAULT_SHADOW_BIAS_SCALE_GLTF = 1.38f;

    private static boolean envPresent(String name) {
        String v = System.getenv(name);
        return v != null && !v.isBlank();
    }

    private static float clampShadowBiasScale(float s) {
        return Math.max(0.25f, Math.min(4f, s));
    }

    /**
     * @param isWorldShader {@code true} — программа ландшафта, иначе glTF PBR.
     */
    public static float effectiveShadowBiasScaleForProgram(boolean isWorldShader) {
        if (envPresent("GAME_SHADOW_BIAS_SCALE")) {
            float s = parseFloatEnv("GAME_SHADOW_BIAS_SCALE", 1f);
            return clampShadowBiasScale(s);
        }
        float def = isWorldShader ? 1f : DEFAULT_SHADOW_BIAS_SCALE_GLTF;
        return clampShadowBiasScale(def);
    }

    /**
     * Нижняя граница фактора тени на glTF после PCF (подсветка «затенённого» прямого солнца).
     * Env: {@code GAME_SHADOW_RECEIVE_FLOOR} (0–1, по умолчанию {@value #DEFAULT_GLTF_SHADOW_RECEIVE_FLOOR}).
     */
    public static final float DEFAULT_GLTF_SHADOW_RECEIVE_FLOOR = 0.1f;

    public static float effectiveGltfShadowReceiveFloor() {
        return Math.max(0f, Math.min(0.95f, parseFloatEnv("GAME_SHADOW_RECEIVE_FLOOR", DEFAULT_GLTF_SHADOW_RECEIVE_FLOOR)));
    }

    /**
     * Опциональный HDR equirectangular для IBL (classpath). Если файла нет — процедурная карта.
     * Сильно влияет на блики и хром; для наружной сцены лучше свой HDR, чем только процедурный fallback.
     * Цвет заливки кадра: {@link ru.reweu.game.render.LightingFrame#clearColor() clearColor} из {@link ru.reweu.game.render.SceneLighting#frame()}.
     */
    public static final String IBL_HDR_EQUIRECT = "/ibl/environment.hdr";

    /**
     * {@code true}: фон неба сэмплирует тот же prefilter cubemap, что и IBL (HDR или процедурный bake).
     * {@code false}: только градиент + диск солнца в {@code sky_pass.frag} (как раньше).
     */
    public static final boolean SKY_USE_IBL_ENVIRONMENT = true;

    /**
     * {@code true}: при старте — дамп Assimp и сводка {@link ru.reweu.game.loader.Mesh} (см. {@link #isDumpAssimpReportOnStart()}).
     */
    public static final boolean DEBUG_DUMP_ASSIMP_REPORT = false;

    /** Включить дамп без правки кода: {@code GAME_DUMP_ASSIMP_REPORT=1}. */
    public static boolean isDumpAssimpReportOnStart() {
        return DEBUG_DUMP_ASSIMP_REPORT || envFlag("GAME_DUMP_ASSIMP_REPORT", false);
    }

    private static boolean envFlag(String name, boolean defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return "1".equals(v) || "true".equalsIgnoreCase(v.trim());
    }

    private static float parseFloatEnv(String name, float defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseIntEnv(String name, int defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Одна строка в stderr: цель рендера и env-масштабы (A/B без пересборки). */
    public static void logRenderConfig() {
        String biasPart = envPresent("GAME_SHADOW_BIAS_SCALE")
            ? ("shadowBiasScale=" + effectiveShadowBiasScaleForProgram(true) + " (GAME_SHADOW_BIAS_SCALE)")
            : ("shadowBias world=" + effectiveShadowBiasScaleForProgram(true) + " gltf=" + effectiveShadowBiasScaleForProgram(false));
        System.err.println(
            "[Config] renderLook=" + renderLook()
                + " effectiveStudio=" + effectiveStudioLightingPreset()
                + " exposureScale=" + effectiveExposureScale()
                + " iblIntensityScale=" + effectiveIblIntensityScale()
                + " shadowMapSize=" + effectiveShadowMapSize()
                + " " + biasPart);
        if (USE_GLTF_NATIVE_LOADER) {
            System.err.println("[Config] gltf: " + GLTF_TOYOTA_DIAGNOSTIC_NOTE);
            System.err.println(
                "[Config] gltf shadow: shadow_pcf3x3_gltf.glsl; emissive softens sh; receiveFloor="
                    + effectiveGltfShadowReceiveFloor()
                    + " (GAME_SHADOW_RECEIVE_FLOOR; min direct sun sh)");
            int vis = effectiveGltfDebugVisualizeMode();
            if (vis != 0) {
                System.err.println("[Config] gltf debug visualize mode=" + vis + " (0=normal 1=N 2=shadowFactor; env GLTF_DEBUG_VISUALIZE_MODE)");
            }
            if (effectiveDiagnosticGltfNoIblOcclusion()) {
                System.err.println("[Config] gltf diagnostic: GAME_DEBUG_GLTF_NO_IBL_OCCLUSION=1 (IBL occlusion forced off)");
            }
            if (effectiveShadowPcfUseShadingNormal()) {
                System.err.println("[Config] gltf diagnostic: GAME_SHADOW_PCF_USE_SHADING_NORMAL=1 (PCF bias uses shading N)");
            }
        }
    }
}
