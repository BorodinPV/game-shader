package ru.reweu.game.render;

import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.GameConfig;
import ru.reweu.game.render.ibl.EnvironmentIbl;

/**
 * Процедурное небо (градиент + диск солнца) на полноэкранном треугольнике.
 * Направление луча на пиксель — через {@code invProjection} и {@code invView}, без швов куба.
 */
public final class SkyRenderer {

    private static int emptyVao;
    private static final Matrix4f tmpInvProj = new Matrix4f();
    private static final Matrix4f tmpInvView = new Matrix4f();

    private SkyRenderer() {
    }

    public static void init() {
        if (emptyVao != 0) {
            return;
        }
        emptyVao = glGenVertexArrays();
    }

    public static void draw(
        ShaderProgram sky,
        Matrix4f view,
        Matrix4f projection,
        EnvironmentIbl environmentIbl,
        LightingFrame lit
    ) {
        tmpInvProj.set(projection).invert();
        tmpInvView.set(view).invert();

        int pid = sky.getProgramId();
        sky.use();
        sky.setUniform("invProjection", tmpInvProj);
        sky.setUniform("invView", tmpInvView);

        boolean useEnvSky = GameConfig.SKY_USE_IBL_ENVIRONMENT && environmentIbl != null;
        sky.setUniform("u_useEnvSky", useEnvSky ? 1 : 0);
        if (useEnvSky) {
            int unit = EnvironmentIbl.PREFILTER_UNIT;
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_CUBE_MAP, environmentIbl.getPrefilterMap());
            sky.setUniform("u_envSky", unit);
            sky.setUniform("u_iblIntensity", lit.iblIntensity());
        }

        Vector3f sd = lit.sunDirection();
        int sunDirLoc = ShaderProgram.uniformLocation(pid, "sunDirection");
        if (sunDirLoc != -1) {
            glUniform3f(sunDirLoc, sd.x, sd.y, sd.z);
        }
        Vector3f sc = lit.sunColor();
        int sunColLoc = ShaderProgram.uniformLocation(pid, "sunColor");
        if (sunColLoc != -1) {
            glUniform3f(sunColLoc, sc.x, sc.y, sc.z);
        }
        int sunIntLoc = ShaderProgram.uniformLocation(pid, "sunIntensity");
        if (sunIntLoc != -1) {
            glUniform1f(sunIntLoc, lit.sunIntensity());
        }
        Vector3f sk = lit.skyAmbientColor();
        int skyAmbLoc = ShaderProgram.uniformLocation(pid, "skyAmbientColor");
        if (skyAmbLoc != -1) {
            glUniform3f(skyAmbLoc, sk.x, sk.y, sk.z);
        }
        Vector3f gr = lit.groundAmbientColor();
        int groundAmbLoc = ShaderProgram.uniformLocation(pid, "groundAmbientColor");
        if (groundAmbLoc != -1) {
            glUniform3f(groundAmbLoc, gr.x, gr.y, gr.z);
        }
        int exposureLoc = ShaderProgram.uniformLocation(pid, "exposure");
        if (exposureLoc != -1) {
            glUniform1f(exposureLoc, lit.exposure());
        }
        int sunDiscLoc = ShaderProgram.uniformLocation(pid, "sunDiscScale");
        if (sunDiscLoc != -1) {
            glUniform1f(sunDiscLoc, lit.sunDiscScale());
        }

        boolean cullWasOn = glIsEnabled(GL_CULL_FACE);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glEnable(GL_DEPTH_TEST);
        if (cullWasOn) {
            glEnable(GL_CULL_FACE);
        }
    }

    public static void cleanup() {
        if (emptyVao != 0) {
            glDeleteVertexArrays(emptyVao);
            emptyVao = 0;
        }
    }
}
