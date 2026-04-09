package ru.reweu.game.render.pipeline;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static ru.reweu.game.render.ShaderRender.renderWorldMeshes;
import static ru.reweu.game.render.ShaderRender.renderWorldMeshesTransparentSorted;
import static ru.reweu.game.render.ShaderRender.sortMeshesByMaterialState;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.render.ShaderProgram;

/**
 * Пропы (Assimp): непрозрачные меши и отсортированный прозрачный оверлей (SRP: только этот слой).
 */
public final class PropMeshesRasterPass {

    private final List<Mesh[]> propMeshes;
    private final List<Vector3f> propWorldPositions;
    private final Matrix4f tmpPropModel = new Matrix4f();

    public PropMeshesRasterPass(List<Mesh[]> propMeshes, List<Vector3f> propWorldPositions) {
        this.propMeshes = propMeshes;
        this.propWorldPositions = propWorldPositions;
    }

    private static boolean anyNonEmptyGroup(List<Mesh[]> propMeshes) {
        for (Mesh[] g : propMeshes) {
            if (g != null && g.length > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean anyNonEmptyGroup() {
        return anyNonEmptyGroup(propMeshes);
    }

    public void render(
        ShaderProgram worldShader,
        Matrix4f view,
        Matrix4f projection,
        Vector3f cameraWorldPosition
    ) {
        for (int pi = 0; pi < propMeshes.size(); pi++) {
            Mesh[] meshGroup = propMeshes.get(pi);
            if (meshGroup.length == 0) {
                continue;
            }
            float scale = meshGroup[0].getScale();
            tmpPropModel.identity().translate(propWorldPositions.get(pi)).scale(scale);

            List<Mesh> overlay = new ArrayList<>();
            List<Mesh> solid = new ArrayList<>();
            for (Mesh m : meshGroup) {
                if (m.isTransparentOverlayPass()) {
                    overlay.add(m);
                } else {
                    solid.add(m);
                }
            }
            Mesh[] solidArr = solid.toArray(new Mesh[0]);
            if (solidArr.length > 0) {
                sortMeshesByMaterialState(solidArr);
                glDisable(GL_BLEND);
                glDepthMask(true);
                renderWorldMeshes(worldShader, solidArr, tmpPropModel, view, projection, true);
            }
            if (!overlay.isEmpty()) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                renderWorldMeshesTransparentSorted(
                    worldShader, overlay, tmpPropModel, view, projection, cameraWorldPosition);
                glDepthMask(true);
            }
            glDisable(GL_BLEND);
        }
    }
}
