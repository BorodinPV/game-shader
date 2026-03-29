package ru.reweu.game.gltf;

import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import org.joml.Matrix4f;

/**
 * glTF 2.0 joint matrices: {@code inverse(meshGlobal) * jointGlobal * inverseBindMatrix}.
 */
public final class GltfSkinMatrices {

    public static final int MAX_JOINTS = 64;

    private GltfSkinMatrices() {
    }

    /**
     * Fills {@code out} with {@code jointCount} matrices (column-major); returns actual count (capped).
     */
    public static int computeJointMatrices(SkinModel skin, NodeModel meshNode, Matrix4f[] out) {
        if (skin == null || meshNode == null || out == null) {
            return 0;
        }
        float[] meshGlobalF = meshNode.computeGlobalTransform(new float[16]);
        Matrix4f meshGlobal = new Matrix4f().set(meshGlobalF);
        Matrix4f invMesh = new Matrix4f(meshGlobal).invert();
        int n = skin.getJoints().size();
        int count = Math.min(n, Math.min(out.length, MAX_JOINTS));
        float[] tmp = new float[16];
        for (int j = 0; j < count; j++) {
            NodeModel jointNode = skin.getJoints().get(j);
            float[] jg = jointNode.computeGlobalTransform(new float[16]);
            Matrix4f jointGlobal = new Matrix4f().set(jg);
            skin.getInverseBindMatrix(j, tmp);
            Matrix4f ibm = new Matrix4f().set(tmp);
            out[j] = new Matrix4f(invMesh).mul(jointGlobal).mul(ibm);
        }
        return count;
    }
}
