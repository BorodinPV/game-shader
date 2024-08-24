package ru.reweu.game;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

public class TextureLoader {

    public static int loadTexture(String resourcePath) {
        int width, height;
        ByteBuffer image;

        // Загрузка изображения
        try (MemoryStack stack = stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            IntBuffer compBuffer = stack.mallocInt(1);
            URL resourceUrl = TextureLoader.class.getResource(resourcePath);
            String filePath;
            try {
                filePath = Paths.get(resourceUrl.toURI()).toString();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            // Загружаем изображение
            image = STBImage.stbi_load(filePath, widthBuffer, heightBuffer, compBuffer, 4);
            if (image == null) {
                throw new RuntimeException("Failed to load texture file " + filePath + "\n" + STBImage.stbi_failure_reason());
            }

            width = widthBuffer.get(0);
            height = heightBuffer.get(0);
        }

        // Создаем текстуру
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Устанавливаем параметры текстуры
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);


        // Освобождаем изображение
        STBImage.stbi_image_free(image);

        return textureId;
    }
}
