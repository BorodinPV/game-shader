package ru.reweu.game.loader;

import static org.lwjgl.opengl.GL30.GL_LINEAR;
import static org.lwjgl.opengl.GL30.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL30.GL_REPEAT;
import static org.lwjgl.opengl.GL30.GL_RGBA;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL30.glBindTexture;
import static org.lwjgl.opengl.GL30.glDeleteTextures;
import static org.lwjgl.opengl.GL30.glGenTextures;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.opengl.GL30.glTexImage2D;
import static org.lwjgl.opengl.GL30.glTexParameteri;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

public class Texture {
    private final int id;
    private final boolean hasAlpha;
    private final String image;

    public Texture(String resourcePath) {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        image = resourcePath;
        try (MemoryStack stack = stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            IntBuffer channelsBuffer = stack.mallocInt(1);

            URL resourceUrl = Texture.class.getResource(resourcePath);
            String filePath;
            try {
                filePath = Paths.get(resourceUrl.toURI()).toString();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            // Загружаем изображение
            ByteBuffer image = stbi_load(filePath, widthBuffer, heightBuffer, channelsBuffer, 4);
            if (image == null) {
                throw new RuntimeException("Failed to load texture: " + STBImage.stbi_failure_reason());
            }
            if (image == null) {
                System.out.println("Error loading texture: " + STBImage.stbi_failure_reason());
            }
            int width = widthBuffer.get(0);
            int height = heightBuffer.get(0);
            int channels = channelsBuffer.get();
            hasAlpha = (channels == 4);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, widthBuffer.get(), heightBuffer.get(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            glGenerateMipmap(GL_TEXTURE_2D);

            STBImage.stbi_image_free(image);
        }

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    public String getImage() {
        return image;
    }

    public boolean hasAlpha() {
        return hasAlpha;
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void cleanup() {
        glDeleteTextures(id);
    }
}

