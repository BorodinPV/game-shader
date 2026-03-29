package ru.reweu.game.gui;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFontMem;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
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

import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import ru.reweu.game.render.RenderErrorLog;

/**
 * FPS overlay (top-right). Uses NanoVG GL3 backend (no {@code glPushAttrib} — core-profile safe).
 */
public class FPSCounter {

    private final long window;
    private int fps;
    private double lastFpsTick;
    private int frames;
    private long vg;
    /** Must stay reachable: NanoVG keeps a pointer when nvgCreateFontMem(..., freeData=false). */
    private ByteBuffer fontMemBuffer;

    public FPSCounter(long window) {
        this.window = window;
        this.lastFpsTick = glfwGetTime();
        this.frames = 0;
    }

    public void init() {
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == 0) {
            RenderErrorLog.warn("Could not init NanoVG (FPS overlay)");
            throw new RuntimeException("Could not init NanoVG");
        }
        loadFontFromClasspath();
    }

    private void loadFontFromClasspath() {
        try (InputStream in = FPSCounter.class.getResourceAsStream("/fonts/Aero-Matics-Stencil-Bold.ttf")) {
            if (in == null) {
                RenderErrorLog.warn("Font not on classpath: /fonts/Aero-Matics-Stencil-Bold.ttf");
                throw new RuntimeException("Font not on classpath: /fonts/Aero-Matics-Stencil-Bold.ttf");
            }
            byte[] data = in.readAllBytes();
            fontMemBuffer = BufferUtils.createByteBuffer(data.length);
            fontMemBuffer.put(data).flip();
            if (nvgCreateFontMem(vg, "sans", fontMemBuffer, false) == -1) {
                RenderErrorLog.warn("Could not register font sans (NanoVG)");
                throw new RuntimeException("Could not register font sans");
            }
        } catch (IOException e) {
            RenderErrorLog.warn("FPS font IO error", e);
            throw new RuntimeException(e);
        }
    }

    public void update() {
        frames++;
        double now = glfwGetTime();
        if (now - lastFpsTick >= 1.0) {
            fps = frames;
            frames = 0;
            lastFpsTick += 1.0;
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

        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glViewport(0, 0, fbW[0], fbH[0]);

        boolean depthWasOn = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);

        nvgBeginFrame(vg, ww, wh, pixelRatio);

        NVGColor color = NVGColor.calloc();
        nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, color);

        nvgFontSize(vg, 30f);
        nvgFontFace(vg, "sans");
        nvgFillColor(vg, color);
        nvgTextAlign(vg, NVG_ALIGN_RIGHT | NVG_ALIGN_TOP);
        nvgText(vg, ww - 10, 10, "FPS: " + fps);

        nvgEndFrame(vg);
        color.free();

        if (depthWasOn) {
            glEnable(GL_DEPTH_TEST);
        }
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    public void cleanup() {
        if (vg != 0) {
            nvgDelete(vg);
            vg = 0;
        }
        fontMemBuffer = null;
    }
}
