package ru.reweu.game.loader;

import static org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_OPACITY;
import static org.lwjgl.assimp.Assimp.aiGetMaterialColor;
import static org.lwjgl.assimp.Assimp.aiGetMaterialFloatArray;
import static org.lwjgl.assimp.Assimp.aiGetMaterialTexture;
import static org.lwjgl.assimp.Assimp.aiImportFileEx;
import static org.lwjgl.assimp.Assimp.aiProcess_FlipUVs;
import static org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReturn_SUCCESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_AMBIENT;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;
import static org.lwjgl.assimp.Assimp.aiTextureType_NONE;
import static org.lwjgl.assimp.Assimp.aiTextureType_NORMALS;
import static org.lwjgl.assimp.Assimp.aiTextureType_OPACITY;
import static org.lwjgl.assimp.Assimp.aiTextureType_SHININESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR;
import static ru.reweu.game.loader.Utils.addWheel;
import static ru.reweu.game.loader.Utils.addWheels;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.system.MemoryStack;

public class ModelLoader {
    public static Mesh[] loadModel(String resourcePath, float scale) {
        URL resourceUrl = ModelLoader.class.getResource(resourcePath);
        String filePath = null;
        try {
            filePath = Paths.get(resourceUrl.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        AIScene scene = aiImportFileEx(filePath,
            aiProcess_Triangulate | aiProcess_FlipUVs | aiProcess_JoinIdenticalVertices | aiProcess_GenSmoothNormals,
            null);

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Error loading model");
        }

        List<Mesh> meshes = new ArrayList<>();
        processNode(scene.mRootNode(), scene, meshes, scale);
        return meshes.toArray(new Mesh[0]);
    }

    private static void processNode(AINode node, AIScene scene, List<Mesh> meshes, float scale) {
        Pattern WHEEL_PATTERN = Pattern.compile("\\bwheel\\d+\\b", Pattern.CASE_INSENSITIVE);
        List<Mesh> meshesStream = IntStream.range(0, node.mNumMeshes())
            .mapToObj(i -> AIMesh.create(scene.mMeshes().get(node.mMeshes().get(i))))
            .map(aiMesh -> processMesh(aiMesh, scene, scale))
            .collect(Collectors.toList());

        for (Mesh mesh : meshesStream) {
            String name = mesh.getName();
            if (WHEEL_PATTERN.matcher(name).find()){
                addWheel(name, mesh);
            } else {
                meshes.add(mesh);
            }
        }

        for (int i = 0; i < node.mNumChildren(); i++) {
            processNode(AINode.create(node.mChildren().get(i)), scene, meshes, scale);
        }
    }

    private static Mesh processMesh(AIMesh aiMesh, AIScene scene, float scale) {
        String meshName = aiMesh.mName().dataString();
        List<Vector3f> vertices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Vector3f> texCoords = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < aiMesh.mNumVertices(); i++) {
            // Вершины
            vertices.add(new Vector3f(
                aiMesh.mVertices().get(i).x(),
                aiMesh.mVertices().get(i).y(),
                aiMesh.mVertices().get(i).z()
            ));

            // Проверка на наличие нормалей
            if (aiMesh.mNormals() != null) {
                normals.add(new Vector3f(
                    aiMesh.mNormals().get(i).x(),
                    aiMesh.mNormals().get(i).y(),
                    aiMesh.mNormals().get(i).z()
                ));
            } else {
                // Если нормалей нет, используем дефолтное значение или пропускаем
                normals.add(new Vector3f(0.0f, 0.0f, 0.0f));
            }

            // Текстурные координаты
            if (aiMesh.mTextureCoords(0) != null) {
                texCoords.add(new Vector3f(
                    aiMesh.mTextureCoords(0).get(i).x(),
                    aiMesh.mTextureCoords(0).get(i).y(),
                    0.0f
                ));
            } else {
                texCoords.add(new Vector3f(0.0f, 0.0f, 0.0f));
            }
        }


        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            AIFace face = aiMesh.mFaces().get(i);
            for (int j = 0; j < face.mNumIndices(); j++) {
                indices.add(face.mIndices().get(j));
            }
        }

        AIMaterial material = AIMaterial.create(scene.mMaterials().get(aiMesh.mMaterialIndex()));

        // Загрузка различных текстур
        Texture diffuseTexture = loadMaterialTexture(material, aiTextureType_DIFFUSE);
        Texture ambientTexture = loadMaterialTexture(material, aiTextureType_AMBIENT);
        Texture specularTexture = loadMaterialTexture(material, aiTextureType_SPECULAR);
        Texture normalTexture = loadMaterialTexture(material, aiTextureType_NORMALS);
        Texture alphaTexture = loadMaterialTexture(material, aiTextureType_OPACITY);
        Texture specularHighlightTexture = loadMaterialTexture(material, aiTextureType_SHININESS);

        // Получение цвета материала
        Vector3f color = loadMaterialDiffuseColor(material);

        // Получение параметра прозрачности (d)
        float materialAlpha = loadMaterialAlpha(material);
        // Возвращаем меш с загруженными текстурами и цветом
        return new Mesh(vertices, normals, texCoords, indices, diffuseTexture, ambientTexture, specularTexture,
            normalTexture, alphaTexture, specularHighlightTexture, color, materialAlpha, scale, meshName);
    }

    private static float loadMaterialAlpha(AIMaterial material) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer alphaBuffer = stack.mallocFloat(1);
            int result = aiGetMaterialFloatArray(material, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, alphaBuffer,
                null);

            if (result == aiReturn_SUCCESS) {
                return alphaBuffer.get(0); // Возвращаем значение альфа-канала
            }
        }
        // Если не удалось получить прозрачность, возвращаем 1.0f (полная непрозрачность)
        return 1.0f;
    }

    private static Texture loadMaterialTexture(AIMaterial material, int textureType) {
        AIString path = AIString.calloc();
        int result =
            aiGetMaterialTexture(material, textureType, 0, path, (IntBuffer) null, null, null, null, null, null);
        if (result == aiReturn_SUCCESS) {
            String texturePath = path.dataString().trim();
            if (!texturePath.isEmpty()) {
                System.out.println("Found texture path: " + texturePath);
                String fullPath =
                    "C:/Users/User/Desktop/game-shader-master/src/main/resources/all/chevy/" + texturePath;

                try {
                    Texture texture = new Texture(fullPath);
                    return texture;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private static Vector3f loadMaterialDiffuseColor(AIMaterial material) {
        AIColor4D color = AIColor4D.create();
        int result = aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color);

        if (result == aiReturn_SUCCESS) {
            return new Vector3f(color.r(), color.g(), color.b());
        } else {
            return new Vector3f(1.0f, 1.0f, 1.0f); // Цвет по умолчанию (белый)
        }
    }

}