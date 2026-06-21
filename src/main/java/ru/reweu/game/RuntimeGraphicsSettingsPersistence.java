package ru.reweu.game;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;

import ru.reweu.game.render.RenderErrorLog;

/**
 * Сохранение {@link RuntimeGraphicsSettings} в JSON (персистентность между запусками).
 *
 * <p>Файл: {@code $XDG_CONFIG_HOME/game-shader/graphics-settings.json} (Linux),
 * {@code %APPDATA%/game-shader/graphics-settings.json} (Windows), иначе {@code ~/.config/game-shader/…}.
 */
public final class RuntimeGraphicsSettingsPersistence {

    static final int FORMAT_VERSION = 1;

    private RuntimeGraphicsSettingsPersistence() {
    }

    public static Path storagePath() {
        Path dir;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String app = System.getenv("APPDATA");
            dir = (app != null && !app.isBlank())
                ? Paths.get(app, "game-shader")
                : Paths.get(System.getProperty("user.home"), "game-shader");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            if (xdg != null && !xdg.isBlank()) {
                dir = Paths.get(xdg, "game-shader");
            } else {
                dir = Paths.get(System.getProperty("user.home"), ".config", "game-shader");
            }
        }
        return dir.resolve("graphics-settings.json");
    }

    public static void loadInto(RuntimeGraphicsSettings s) {
        Path p = storagePath();
        if (!Files.isRegularFile(p)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8);
             JsonReader jr = Json.createReader(reader)) {
            JsonObject o = jr.readObject();
            int ver = o.getInt("formatVersion", 0);
            if (ver != FORMAT_VERSION) {
                RenderErrorLog.warn("graphics-settings.json: formatVersion=" + ver + " expected " + FORMAT_VERSION + ", ignored");
                return;
            }
            optBool(o, "studioPreset", s::setStudioPreset);
            optBool(o, "sunLightEnabled", s::setSunLightEnabled);
            optBool(o, "shadowsEnabled", s::setShadowsEnabled);
            optBool(o, "fillLightEnabled", s::setFillLightEnabled);
            optBool(o, "iblEnabled", s::setIblEnabled);
            optBool(o, "ambientConstantEnabled", s::setAmbientConstantEnabled);
            optBool(o, "hemisphereAmbientEnabled", s::setHemisphereAmbientEnabled);
            optBool(o, "drawLandscape", s::setDrawLandscape);
            optBool(o, "drawGltfCars", s::setDrawGltfCars);
            optBool(o, "drawProps", s::setDrawProps);
            optBool(o, "drawSky", s::setDrawSky);
            optBool(o, "showFpsOverlay", s::setShowFpsOverlay);
            optBool(o, "rainEnabled", s::setRainEnabled);
            optBool(o, "fogEnabled", s::setFogEnabled);
            optBool(o, "gltfShadowPcfUseShadingNormal", s::setGltfShadowPcfUseShadingNormal);

            optFloat(o, "shadowBiasWorld", s::setShadowBiasWorld);
            optFloat(o, "shadowBiasGltf", s::setShadowBiasGltf);
            optFloat(o, "gltfShadowReceiveFloor", s::setGltfShadowReceiveFloor);
            optFloat(o, "lightingExposureScale", s::setLightingExposureScale);
            optFloat(o, "lightingIblScale", s::setLightingIblScale);
            optFloat(o, "lightingSunIntensityScale", s::setLightingSunIntensityScale);
            optFloat(o, "lightingFillStrengthScale", s::setLightingFillStrengthScale);
            optFloat(o, "lightingFillSpecularScale", s::setLightingFillSpecularScale);
            optFloat(o, "lightingEmissiveBoostScale", s::setLightingEmissiveBoostScale);
            optFloat(o, "lightingWorldIndirectScale", s::setLightingWorldIndirectScale);
        } catch (Exception e) {
            RenderErrorLog.warn("graphics-settings.json: load failed", e);
        }
    }

    public static void save(RuntimeGraphicsSettings s) {
        Path p = storagePath();
        try {
            Files.createDirectories(p.getParent());
        } catch (IOException e) {
            RenderErrorLog.warn("graphics-settings.json: cannot create directory " + p.getParent(), e);
            return;
        }
        JsonObjectBuilder b = Json.createObjectBuilder();
        b.add("formatVersion", FORMAT_VERSION);
        b.add("studioPreset", s.isStudioPreset());
        b.add("sunLightEnabled", s.isSunLightEnabled());
        b.add("shadowsEnabled", s.isShadowsEnabled());
        b.add("fillLightEnabled", s.isFillLightEnabled());
        b.add("iblEnabled", s.isIblEnabled());
        b.add("ambientConstantEnabled", s.isAmbientConstantEnabled());
        b.add("hemisphereAmbientEnabled", s.isHemisphereAmbientEnabled());
        b.add("drawLandscape", s.isDrawLandscape());
        b.add("drawGltfCars", s.isDrawGltfCars());
        b.add("drawProps", s.isDrawProps());
        b.add("drawSky", s.isDrawSky());
        b.add("showFpsOverlay", s.isShowFpsOverlay());
        b.add("rainEnabled", s.isRainEnabled());
        b.add("fogEnabled", s.isFogEnabled());
        b.add("gltfShadowPcfUseShadingNormal", s.isGltfShadowPcfUseShadingNormal());
        b.add("shadowBiasWorld", s.getShadowBiasWorld());
        b.add("shadowBiasGltf", s.getShadowBiasGltf());
        b.add("gltfShadowReceiveFloor", s.getGltfShadowReceiveFloor());
        b.add("lightingExposureScale", s.getLightingExposureScale());
        b.add("lightingIblScale", s.getLightingIblScale());
        b.add("lightingSunIntensityScale", s.getLightingSunIntensityScale());
        b.add("lightingFillStrengthScale", s.getLightingFillStrengthScale());
        b.add("lightingFillSpecularScale", s.getLightingFillSpecularScale());
        b.add("lightingEmissiveBoostScale", s.getLightingEmissiveBoostScale());
        b.add("lightingWorldIndirectScale", s.getLightingWorldIndirectScale());
        JsonObject obj = b.build();
        Map<String, ?> pretty = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true);
        try (var writer = Json.createWriterFactory(pretty).createWriter(
            Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            writer.writeObject(obj);
        } catch (IOException e) {
            RenderErrorLog.warn("graphics-settings.json: save failed", e);
        }
    }

    private static void optBool(JsonObject o, String key, Consumer<Boolean> apply) {
        if (!o.containsKey(key)) {
            return;
        }
        apply.accept(o.getBoolean(key));
    }

    private static void optFloat(JsonObject o, String key, Consumer<Float> apply) {
        if (!o.containsKey(key)) {
            return;
        }
        JsonNumber n = o.getJsonNumber(key);
        apply.accept((float) n.doubleValue());
    }
}
