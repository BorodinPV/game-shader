package ru.reweu.game.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memAlloc;

public class ResourceLoader {
    public static ByteBuffer loadResourceAsByteBuffer(String resourcePath) {
        try (InputStream inputStream = ResourceLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }

            byte[] data = inputStream.readAllBytes();
            ByteBuffer buffer = memAlloc(data.length);
            buffer.put(data).flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resourcePath, e);
        }
    }

    public static java.io.File loadResourceAsFile(String resourcePath) {
        try {
            return new java.io.File(ResourceLoader.class.getResource(resourcePath).toURI());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource as file: " + resourcePath, e);
        }
    }
}
