package ru.reweu.game.gui;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFontMem;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glViewport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import ru.reweu.game.RuntimeGraphicsSettings;
import ru.reweu.game.render.RenderErrorLog;

/**
 * Меню по ESC: слева — чекбоксы и выход; справа — сайдбар со слайдерами (NanoVG).
 */
public final class PauseMenu {

    private final long window;
    private long vg;
    private ByteBuffer fontMemBuffer;

    private float panelX;
    private float panelY;
    private float panelW;
    private float rowH = 24f;
    private float box = 16f;
    private float pad = 12f;

    private float sidebarX;
    private float sidebarY;
    private float sidebarW;
    private float sidebarH;

    private float exitBtnX;
    private float exitBtnY;
    private float exitBtnW;
    private float exitBtnH;

    private SliderRow dragSlider;

    private static final class Row {
        final String label;
        final Consumer<Boolean> set;
        final Supplier<Boolean> get;
        float cx;
        float cy;
        float cw;
        float ch;

        Row(String label, Consumer<Boolean> set, Supplier<Boolean> get) {
            this.label = label;
            this.set = set;
            this.get = get;
        }
    }

    /** Горизонтальный слайдер в сайдбаре: {@code min}…{@code max} по ширине трека. */
    private static final class SliderRow {
        static final float TRACK_H = 10f;
        static final float ROW_H = 44f;
        static final float ROW_GAP = 8f;

        final String label;
        final Supplier<Float> get;
        final Consumer<Float> set;
        final float min;
        final float max;

        float trackX;
        float trackY;
        float trackW;

        SliderRow(String label, Supplier<Float> get, Consumer<Float> set, float min, float max) {
            this.label = label;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
        }

        float normalized() {
            float v = get.get();
            float d = max - min;
            if (d <= 1e-6f) {
                return 0f;
            }
            return Math.max(0f, Math.min(1f, (v - min) / d));
        }

        void setFromPointerX(float px) {
            float t = (px - trackX) / trackW;
            if (t < 0f) {
                t = 0f;
            } else if (t > 1f) {
                t = 1f;
            }
            set.accept(min + t * (max - min));
        }

        boolean hitTrack(float x, float y) {
            float padY = 8f;
            return x >= trackX && x <= trackX + trackW
                && y >= trackY - padY && y <= trackY + TRACK_H + padY;
        }
    }

    private final Row[] rows;
    private final SliderRow[] lightingSliders;
    private final SliderRow[] shadowSliders;

    public PauseMenu(long window) {
        this.window = window;
        RuntimeGraphicsSettings rs = RuntimeGraphicsSettings.get();
        rows = new Row[] {
            new Row("Пресет «студия»", rs::setStudioPreset, rs::isStudioPreset),
            new Row("Солнце (направленный свет)", rs::setSunLightEnabled, rs::isSunLightEnabled),
            new Row("Тени", rs::setShadowsEnabled, rs::isShadowsEnabled),
            new Row("PCF: нормаль из normal map (glTF)", rs::setGltfShadowPcfUseShadingNormal, rs::isGltfShadowPcfUseShadingNormal),
            new Row("Fill-свет", rs::setFillLightEnabled, rs::isFillLightEnabled),
            new Row("IBL (косвенный свет)", rs::setIblEnabled, rs::isIblEnabled),
            new Row("Константный ambient", rs::setAmbientConstantEnabled, rs::isAmbientConstantEnabled),
            new Row("Гемисферный ambient", rs::setHemisphereAmbientEnabled, rs::isHemisphereAmbientEnabled),
            new Row("Ландшафт", rs::setDrawLandscape, rs::isDrawLandscape),
            new Row("glTF-машины", rs::setDrawGltfCars, rs::isDrawGltfCars),
            new Row("Пропы (Assimp)", rs::setDrawProps, rs::isDrawProps),
            new Row("Небо", rs::setDrawSky, rs::isDrawSky),
            new Row("FPS-счётчик", rs::setShowFpsOverlay, rs::isShowFpsOverlay),
            new Row("Дождь", rs::setRainEnabled, rs::isRainEnabled),
        };
        lightingSliders = new SliderRow[] {
            new SliderRow("Exposure ×", rs::getLightingExposureScale, rs::setLightingExposureScale, 0.2f, 3f),
            new SliderRow("IBL ×", rs::getLightingIblScale, rs::setLightingIblScale, 0f, 3f),
            new SliderRow("Солнце ×", rs::getLightingSunIntensityScale, rs::setLightingSunIntensityScale, 0f, 3f),
            new SliderRow("Fill ×", rs::getLightingFillStrengthScale, rs::setLightingFillStrengthScale, 0f, 2.5f),
            new SliderRow("Fill блики glTF ×", rs::getLightingFillSpecularScale, rs::setLightingFillSpecularScale, 0f, 2.5f),
            new SliderRow(
                "Emissive glTF (фары) ×",
                rs::getLightingEmissiveBoostScale,
                rs::setLightingEmissiveBoostScale,
                0.25f,
                4f
            ),
            new SliderRow("Мир ambient+небо ×", rs::getLightingWorldIndirectScale, rs::setLightingWorldIndirectScale, 0f, 2.5f),
        };
        shadowSliders = new SliderRow[] {
            new SliderRow("Bias теней (ландшафт)", rs::getShadowBiasWorld, rs::setShadowBiasWorld, 0.25f, 4f),
            new SliderRow("Bias теней (glTF)", rs::getShadowBiasGltf, rs::setShadowBiasGltf, 0.25f, 4f),
            new SliderRow("Пол тени glTF (min sh)", rs::getGltfShadowReceiveFloor, rs::setGltfShadowReceiveFloor, 0f, 0.95f),
        };
    }

    public void init() {
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == 0) {
            RenderErrorLog.warn("PauseMenu: NanoVG init failed");
            throw new RuntimeException("PauseMenu: NanoVG init failed");
        }
        try (InputStream in = PauseMenu.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            if (in == null) {
                throw new RuntimeException("Font not found: /fonts/NotoSans-Regular.ttf");
            }
            byte[] data = in.readAllBytes();
            fontMemBuffer = BufferUtils.createByteBuffer(data.length);
            fontMemBuffer.put(data).flip();
            if (nvgCreateFontMem(vg, "ui", fontMemBuffer, false) == -1) {
                throw new RuntimeException("PauseMenu: font register failed");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void render() {
        int[] winW = new int[1];
        int[] winH = new int[1];
        glfwGetWindowSize(window, winW, winH);
        int[] fbW = new int[1];
        int[] fbH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);

        int ww = Math.max(1, winW[0]);
        int wh = Math.max(1, winH[0]);
        float pixelRatio = (float) fbW[0] / (float) ww;

        sidebarW = Math.min(300f, Math.max(232f, ww * 0.26f));
        sidebarX = ww - sidebarW - 14f;
        sidebarY = 14f;
        sidebarH = wh - 28f;

        panelX = 20f;
        panelY = 20f;
        panelW = Math.min(400f, Math.max(200f, sidebarX - panelX - 18f));

        float contentTop = panelY + pad + 36f;
        float y = contentTop;
        for (Row r : rows) {
            r.cx = panelX + pad;
            r.cy = y;
            r.cw = panelW - pad * 2f;
            r.ch = rowH;
            y += rowH + 4f;
        }
        exitBtnW = 160f;
        exitBtnH = 32f;
        exitBtnX = panelX + pad;
        exitBtnY = y + 12f;
        float panelH = exitBtnY + exitBtnH + pad - panelY;

        float innerX = sidebarX + pad;
        float innerW = sidebarW - 2f * pad;

        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glViewport(0, 0, fbW[0], fbH[0]);

        boolean depthWasOn = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);

        nvgBeginFrame(vg, ww, wh, pixelRatio);

        NVGColor c = NVGColor.calloc();
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 130, c);
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, ww, wh);
        nvgFillColor(vg, c);
        nvgFill(vg);

        nvgRGBA((byte) 26, (byte) 28, (byte) 36, (byte) 238, c);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, panelX, panelY, panelW, panelH, 8f);
        nvgFillColor(vg, c);
        nvgFill(vg);

        nvgRGBA((byte) 22, (byte) 24, (byte) 32, (byte) 245, c);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, sidebarX, sidebarY, sidebarW, sidebarH, 8f);
        nvgFillColor(vg, c);
        nvgFill(vg);
        c.free();

        nvgFontFace(vg, "ui");
        nvgFontSize(vg, 20f);
        NVGColor tc = NVGColor.calloc();
        nvgRGBA((byte) 240, (byte) 242, (byte) 248, (byte) 255, tc);
        nvgFillColor(vg, tc);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgText(vg, panelX + pad, panelY + pad + 10f, "Настройки (ESC — закрыть)");
        nvgFontSize(vg, 15f);
        for (Row r : rows) {
            drawCheckbox(vg, r.cx, r.cy + r.ch * 0.5f - box * 0.5f, r.get.get());
            nvgFillColor(vg, tc);
            nvgText(vg, r.cx + box + 10f, r.cy + r.ch * 0.5f, r.label);
        }

        nvgFontSize(vg, 14f);
        nvgRGBA((byte) 200, (byte) 205, (byte) 220, (byte) 255, tc);
        nvgFillColor(vg, tc);
        nvgText(vg, sidebarX + pad, sidebarY + pad + 8f, "Освещение");
        nvgFontSize(vg, 13f);
        float yAfterLight = drawSliderBlock(vg, tc, lightingSliders, innerX, innerW, sidebarY + pad + 26f);
        nvgFontSize(vg, 14f);
        nvgRGBA((byte) 200, (byte) 205, (byte) 220, (byte) 255, tc);
        nvgFillColor(vg, tc);
        nvgText(vg, sidebarX + pad, yAfterLight + 6f, "Тени");
        nvgFontSize(vg, 13f);
        drawSliderBlock(vg, tc, shadowSliders, innerX, innerW, yAfterLight + 26f);

        nvgFontSize(vg, 17f);
        NVGColor btn = NVGColor.calloc();
        nvgBeginPath(vg);
        nvgRoundedRect(vg, exitBtnX, exitBtnY, exitBtnW, exitBtnH, 6f);
        nvgRGBA((byte) 180, (byte) 60, (byte) 55, (byte) 230, btn);
        nvgFillColor(vg, btn);
        nvgFill(vg);
        nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, btn);
        nvgFillColor(vg, btn);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgText(vg, exitBtnX + exitBtnW * 0.5f, exitBtnY + exitBtnH * 0.5f, "Выход");
        tc.free();
        btn.free();

        nvgEndFrame(vg);

        if (depthWasOn) {
            glEnable(GL_DEPTH_TEST);
        }
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    /** Рисует слайдеры и обновляет координаты трека для хит-теста и перетаскивания. */
    private float drawSliderBlock(
        long vg,
        NVGColor tc,
        SliderRow[] arr,
        float innerX,
        float innerW,
        float startY
    ) {
        float y = startY;
        NVGColor trackBg = NVGColor.calloc();
        NVGColor trackFill = NVGColor.calloc();
        NVGColor thumb = NVGColor.calloc();
        NVGColor ring = NVGColor.calloc();
        for (SliderRow s : arr) {
            s.trackX = innerX;
            s.trackW = innerW;
            s.trackY = y + 24f;
            nvgFontSize(vg, 12f);
            nvgRGBA((byte) 228, (byte) 230, (byte) 238, (byte) 255, tc);
            nvgFillColor(vg, tc);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            nvgText(vg, s.trackX, y + 9f, s.label);
            String val = String.format(Locale.US, "%.2f", s.get.get());
            nvgTextAlign(vg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
            nvgText(vg, s.trackX + s.trackW, y + 9f, val);

            nvgRGBA((byte) 48, (byte) 52, (byte) 64, (byte) 255, trackBg);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, s.trackX, s.trackY, s.trackW, SliderRow.TRACK_H, 4f);
            nvgFillColor(vg, trackBg);
            nvgFill(vg);

            float t = s.normalized();
            float fillW = Math.max(0f, t * s.trackW);
            if (fillW > 0.5f) {
                nvgRGBA((byte) 72, (byte) 148, (byte) 198, (byte) 255, trackFill);
                nvgBeginPath(vg);
                nvgRoundedRect(vg, s.trackX, s.trackY, fillW, SliderRow.TRACK_H, 4f);
                nvgFillColor(vg, trackFill);
                nvgFill(vg);
            }

            float cx = s.trackX + t * s.trackW;
            float cy = s.trackY + SliderRow.TRACK_H * 0.5f;
            nvgRGBA((byte) 240, (byte) 244, (byte) 252, (byte) 255, thumb);
            nvgBeginPath(vg);
            nvgCircle(vg, cx, cy, 6.5f);
            nvgFillColor(vg, thumb);
            nvgFill(vg);
            nvgRGBA((byte) 56, (byte) 110, (byte) 160, (byte) 255, ring);
            nvgStrokeColor(vg, ring);
            nvgStrokeWidth(vg, 1.5f);
            nvgBeginPath(vg);
            nvgCircle(vg, cx, cy, 6.5f);
            nvgStroke(vg);

            y += SliderRow.ROW_H + SliderRow.ROW_GAP;
        }
        trackBg.free();
        trackFill.free();
        thumb.free();
        ring.free();
        return y;
    }

    /** Сброс захвата слайдера при закрытии меню (ESC). */
    public void resetPointerState() {
        dragSlider = null;
    }

    public void handlePointerDown(double mx, double my) {
        float x = (float) mx;
        float y = (float) my;
        dragSlider = null;
        if (x < sidebarX || x > sidebarX + sidebarW || y < sidebarY || y > sidebarY + sidebarH) {
            return;
        }
        if (tryPickSlider(lightingSliders, x, y)) {
            return;
        }
        tryPickSlider(shadowSliders, x, y);
    }

    private boolean tryPickSlider(SliderRow[] arr, float x, float y) {
        for (SliderRow s : arr) {
            if (s.hitTrack(x, y)) {
                dragSlider = s;
                s.setFromPointerX(x);
                return true;
            }
        }
        return false;
    }

    public void handlePointerMove(double mx, double my) {
        if (dragSlider != null) {
            dragSlider.setFromPointerX((float) mx);
        }
    }

    /**
     * @return {@code true}, если отпустили захват слайдера (клик обработан сайдбаром).
     */
    public boolean handlePointerUp(double mx, double my) {
        if (dragSlider != null) {
            dragSlider.setFromPointerX((float) mx);
            dragSlider = null;
            return true;
        }
        return false;
    }

    private static void drawCheckbox(long vg, float x, float y, boolean on) {
        NVGColor stroke = NVGColor.calloc();
        NVGColor fill = NVGColor.calloc();
        nvgRGBA((byte) 200, (byte) 205, (byte) 220, (byte) 255, stroke);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, 16f, 16f, 3f);
        nvgStrokeColor(vg, stroke);
        nvgStrokeWidth(vg, 1.5f);
        nvgStroke(vg);
        if (on) {
            nvgRGBA((byte) 90, (byte) 180, (byte) 120, (byte) 255, fill);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + 3f, y + 3f, 10f, 10f, 2f);
            nvgFillColor(vg, fill);
            nvgFill(vg);
        }
        stroke.free();
        fill.free();
    }

    /**
     * Клик (отпускание ЛКМ): чекбоксы и «Выход». Слайдеры — через {@link #handlePointerUp}.
     */
    public boolean handleClick(double mx, double my) {
        float x = (float) mx;
        float y = (float) my;
        for (Row r : rows) {
            if (x >= r.cx && x <= r.cx + r.cw && y >= r.cy && y <= r.cy + r.ch) {
                r.set.accept(!r.get.get());
                return true;
            }
        }
        if (x >= exitBtnX && x <= exitBtnX + exitBtnW && y >= exitBtnY && y <= exitBtnY + exitBtnH) {
            glfwSetWindowShouldClose(window, true);
            return true;
        }
        return false;
    }

    public void cleanup() {
        if (vg != 0) {
            nvgDelete(vg);
            vg = 0;
        }
        fontMemBuffer = null;
    }
}
