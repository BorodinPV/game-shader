package ru.reweu.game.tools;

import ru.reweu.game.GameConfig;
import ru.reweu.game.loader.Mesh;
import ru.reweu.game.loader.ModelLoader;
import ru.reweu.game.loader.ModelLoaderReport;

/**
 * Консольный дамп Assimp и сводки {@link Mesh}.
 * {@link ModelLoader#loadModel} поднимает GPU-буферы — нужен текущий OpenGL-контекст ({@link HeadlessGlContext}).
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=ru.reweu.game.tools.AssimpDumpTool
 *   mvn -q compile exec:java -Dexec.mainClass=ru.reweu.game.tools.AssimpDumpTool -Dexec.args="/models/other.glb"
 * </pre>
 */
public final class AssimpDumpTool {

    private AssimpDumpTool() {
    }

    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : GameConfig.FORD_MUSTANG_1965_GLB;
        float scale = GameConfig.FORD_MUSTANG_MODEL_SCALE;
        ModelLoaderReport.dumpAssimpSceneReport(path);
        try (HeadlessGlContext ignored = new HeadlessGlContext()) {
            Mesh[] meshes = ModelLoader.loadModel(path, scale);
            ModelLoaderReport.dumpLoadedMeshesSummary(meshes);
        }
    }
}
