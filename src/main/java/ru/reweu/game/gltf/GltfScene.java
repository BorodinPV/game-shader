package ru.reweu.game.gltf;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.animation.AnimationManager;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.GltfAnimations;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.gltf.GltfPrimitiveBuilder.GltfMeshDraw;
import ru.reweu.game.render.ShaderProgram;

/**
 * glTF 2.0 scene: native jgltf graph, GPU primitives, optional skinning/morph/animation.
 */
public final class GltfScene {

    private static final Method ANIMATION_PERFORM_STEP;

    static {
        try {
            Method m = AnimationManager.class.getDeclaredMethod("performStep", long.class);
            m.setAccessible(true);
            ANIMATION_PERFORM_STEP = m;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final GltfModel gltfModel;
    private final GltfMaterialExtensionFlags materialExtensionFlags;
    private final GltfTextureRegistry textures;
    private final List<GltfDrawableInstance> instances = new ArrayList<>();
    private final int jointUbo;
    private final AnimationManager animationManager;
    private boolean cleanedUp;

    public GltfScene(GltfModel model, GltfMaterialExtensionFlags materialExtensionFlags) {
        this.gltfModel = model;
        this.materialExtensionFlags = materialExtensionFlags != null
            ? materialExtensionFlags
            : GltfMaterialExtensionFlags.empty();
        this.textures = new GltfTextureRegistry(model);
        this.jointUbo = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, jointUbo);
        glBufferData(GL_UNIFORM_BUFFER, 64 * 4 * 16, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        for (SceneModel sm : model.getSceneModels()) {
            for (NodeModel root : sm.getNodeModels()) {
                collectPrimitives(root);
            }
        }

        this.animationManager = GltfAnimations.createAnimationManager(AnimationManager.AnimationPolicy.LOOP);
        this.animationManager.addAnimations(GltfAnimations.createModelAnimations(model.getAnimationModels()));

        this.textures.preflightDecodeAll();
    }

    public static GltfScene load(Path path) throws IOException {
        GltfModel m = new GltfModelReader().read(path);
        GltfMaterialExtensionFlags ext = GltfMaterialExtensions.readFromGlb(path);
        return new GltfScene(m, ext);
    }

    /**
     * Наибольшая длина ребра AABB геометрии (bind pose, глобальные матрицы узлов).
     * Нужно, чтобы подобрать {@code root scale} так, чтобы разные GLB визуально совпадали по габариту.
     */
    public static float boundingMaxEdgeLength(GltfScene scene) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float[] ng = new float[16];
        Matrix4f nodeMat = new Matrix4f();
        Vector3f v = new Vector3f();
        for (GltfDrawableInstance inst : scene.getInstances()) {
            float[] pos = inst.mesh.cpuPositions;
            if (pos == null || pos.length < 3) {
                continue;
            }
            inst.node.computeGlobalTransform(ng);
            nodeMat.set(ng);
            for (int i = 0; i + 2 < pos.length; i += 3) {
                v.set(pos[i], pos[i + 1], pos[i + 2]);
                nodeMat.transformPosition(v);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        if (!Float.isFinite(minX)) {
            return 1f;
        }
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        return Math.max(dx, Math.max(dy, dz));
    }

    private void collectPrimitives(NodeModel node) {
        for (MeshModel mesh : node.getMeshModels()) {
            for (MeshPrimitiveModel prim : mesh.getMeshPrimitiveModels()) {
                GltfMeshDraw draw = GltfPrimitiveBuilder.build(prim);
                instances.add(new GltfDrawableInstance(draw, node, mesh));
            }
        }
        for (NodeModel c : node.getChildren()) {
            collectPrimitives(c);
        }
    }

    /** Карта теней: отрисовка глубины glTF. */
    public void renderShadowDepth(ShaderProgram depthShader, Matrix4f rootModel, Matrix4f lightSpaceMatrix) {
        float[] ng = new float[16];
        for (GltfDrawableInstance inst : instances) {
            inst.node.computeGlobalTransform(ng);
            Matrix4f world = new Matrix4f().set(ng);
            Matrix4f modelMat = new Matrix4f(rootModel).mul(world);
            GltfPbrRenderer.drawShadowDepth(
                depthShader,
                jointUbo,
                inst.mesh,
                inst.node,
                inst.meshModel,
                modelMat,
                lightSpaceMatrix,
                null
            );
        }
    }

    public void updateAnimation(float deltaTimeSeconds) {
        long deltaNs = (long) (deltaTimeSeconds * 1_000_000_000L);
        try {
            ANIMATION_PERFORM_STEP.invoke(animationManager, deltaNs);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Требует предварительного {@link ru.reweu.game.render.WorldRenderer#prepareFrameFor} с тем же
     * {@link ru.reweu.game.render.LightingFrame} на кадр и тем же {@code shader} (свет, камера, тени, IBL).
     *
     * @param opaquePassOnly {@code true}: OPAQUE + MASK; {@code false}: BLEND only
     */
    public void render(
        ShaderProgram shader,
        Matrix4f rootModel,
        Matrix4f view,
        Matrix4f projection,
        boolean opaquePassOnly
    ) {
        float[] ng = new float[16];
        for (GltfDrawableInstance inst : instances) {
            boolean opaquePass = GltfMaterialPasses.drawnInOpaquePass(inst.mesh.material);
            if (opaquePassOnly && !opaquePass) {
                continue;
            }
            if (!opaquePassOnly && opaquePass) {
                continue;
            }
            inst.node.computeGlobalTransform(ng);
            Matrix4f world = new Matrix4f().set(ng);
            Matrix4f modelMat = new Matrix4f(rootModel).mul(world);
            GltfPbrRenderer.draw(
                shader,
                textures,
                jointUbo,
                inst.mesh,
                inst.node,
                inst.meshModel,
                modelMat,
                view,
                projection,
                null,
                gltfModel,
                materialExtensionFlags
            );
        }
    }

    public void cleanup() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;
        for (GltfDrawableInstance i : instances) {
            GltfPrimitiveBuilder.delete(i.mesh);
        }
        textures.cleanup();
        glDeleteBuffers(jointUbo);
    }

    public List<GltfDrawableInstance> getInstances() {
        return instances;
    }

    public static final class GltfDrawableInstance {
        public final GltfMeshDraw mesh;
        public final NodeModel node;
        public final MeshModel meshModel;

        GltfDrawableInstance(GltfMeshDraw mesh, NodeModel node, MeshModel meshModel) {
            this.mesh = mesh;
            this.node = node;
            this.meshModel = meshModel;
        }
    }
}
