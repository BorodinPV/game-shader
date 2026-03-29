package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_INVALID_ENUM;
import static org.lwjgl.opengl.GL11.GL_INVALID_OPERATION;
import static org.lwjgl.opengl.GL11.GL_INVALID_VALUE;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_OUT_OF_MEMORY;
import static org.lwjgl.opengl.GL11.GL_STACK_OVERFLOW;
import static org.lwjgl.opengl.GL11.GL_STACK_UNDERFLOW;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL30.GL_INVALID_FRAMEBUFFER_OPERATION;

/**
 * Ошибки рендеринга (OpenGL, шейдеры, FBO) — stderr с префиксом {@code [Render]}.
 */
public final class RenderErrorLog {

    private static final String PREFIX = "[Render] ";

    private RenderErrorLog() {
    }

    public static void warn(String message) {
        System.err.println(PREFIX + message);
    }

    public static void warn(String message, Throwable cause) {
        System.err.println(PREFIX + message);
        cause.printStackTrace(System.err);
    }

    /**
     * Сбрасывает очередь {@link org.lwjgl.opengl.GL11#glGetError()} и логирует каждый код (может быть несколько).
     */
    public static void checkGl(String context) {
        int e;
        while ((e = glGetError()) != GL_NO_ERROR) {
            warn(context + ": OpenGL " + nameForError(e) + " (0x" + Integer.toHexString(e) + ")");
        }
    }

    private static String nameForError(int code) {
        if (code == GL_INVALID_ENUM) {
            return "GL_INVALID_ENUM";
        }
        if (code == GL_INVALID_VALUE) {
            return "GL_INVALID_VALUE";
        }
        if (code == GL_INVALID_OPERATION) {
            return "GL_INVALID_OPERATION";
        }
        if (code == GL_STACK_OVERFLOW) {
            return "GL_STACK_OVERFLOW";
        }
        if (code == GL_STACK_UNDERFLOW) {
            return "GL_STACK_UNDERFLOW";
        }
        if (code == GL_OUT_OF_MEMORY) {
            return "GL_OUT_OF_MEMORY";
        }
        if (code == GL_INVALID_FRAMEBUFFER_OPERATION) {
            return "GL_INVALID_FRAMEBUFFER_OPERATION";
        }
        return "UNKNOWN";
    }

    /**
     * Логирует статус FBO, если кадровый буфер неполный; возвращает {@code true}, если OK.
     */
    public static boolean logIfFramebufferIncomplete(String context, int target) {
        int status = org.lwjgl.opengl.GL30.glCheckFramebufferStatus(target);
        int complete = org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
        if (status != complete) {
            warn(context + ": framebuffer status " + framebufferStatusName(status) + " (0x" + Integer.toHexString(status) + ")");
            return false;
        }
        return true;
    }

    private static String framebufferStatusName(int status) {
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE) {
            return "GL_FRAMEBUFFER_COMPLETE";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_UNDEFINED) {
            return "GL_FRAMEBUFFER_UNDEFINED";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
            return "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
            return "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER) {
            return "GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER) {
            return "GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_UNSUPPORTED) {
            return "GL_FRAMEBUFFER_UNSUPPORTED";
        }
        if (status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE) {
            return "GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE";
        }
        return "UNKNOWN";
    }
}
