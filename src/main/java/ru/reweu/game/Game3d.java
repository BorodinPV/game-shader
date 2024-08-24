package ru.reweu.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import ru.reweu.game.car.Car;
import ru.reweu.game.loader.ModelLoader;
import ru.reweu.game.render.ShaderProgram;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL13C.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.KHRRobustness.GL_NO_ERROR;
import static org.lwjgl.system.MemoryUtil.NULL;
import static ru.reweu.game.StaticMesh.getCubeMesh;
import static ru.reweu.game.StaticMesh.getPlaneMesh;
import static ru.reweu.game.car.Car.carSpeed;
import static ru.reweu.game.main.Variables.*;
import static ru.reweu.game.render.ShaderRender.renderObjects;
import static ru.reweu.game.weather.Fog.startFog;

public class Game3d {

    private long window;
    private ShaderProgram worldShaderProgram;
    private ShaderProgram shaderProgram;
    private Mesh cubeMesh, planeMesh;
    private static Camera camera;
    private Texture skyboxTexture, groundTexture;
    private float lastX = 400, lastY = 300;
    private boolean firstMouse = true;
    private Vector3f modelPosition = new Vector3f(0.0f, 0.7f, 0.0f); // начальная позиция модели
    private Vector3f carPosition = new Vector3f(0.0f, 0.0f, 0.0f); // Позиция машины
    private Vector3f carDirection = new Vector3f(1.0f, 0.0f, 0f); // Направление машины (вдоль оси Z)
    private float turnSpeed = 30.0f; // Скорость поворота машины
    private boolean cameraAttachedToModel = false; // Флаг привязки камеры
    private float carRotationAngle = 0f; // Угол поворота машины вокруг оси Y
    private final float maxSpeed = 10.0f; // Максимальная скорость
    private final float acceleration = 2.0f; // Ускорение машины
    private final float deceleration = 3.0f; // Замедление машины
    private Car car;

    private List<ru.reweu.game.loader.Mesh[]> meshes = new ArrayList<>();

    public void run() {
        System.out.println("Starting LWJGL " + Version.getVersion() + "!");
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        long primaryMonitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4); // Включаем 4x антиалиасинг

        window = glfwCreateWindow(vidmode.width(), vidmode.height(), "Simple Cube", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        worldShaderProgram = new ShaderProgram(
                "/shaders/vertex_shader.glsl",
                "/shaders/fragment_shader.glsl"
        );
        shaderProgram = new ShaderProgram("/shaders/vertex.glsl", "/shaders/fragment.glsl");
        car = new Car();
        cubeMesh = getCubeMesh();
        planeMesh = getPlaneMesh();
        meshes.add(ModelLoader.loadModel("/models/suburban/suburban.obj", 0.2f));
        meshes.add(ModelLoader.loadModel("/models/suburban/wheel.obj", 0.2f));

        skyboxTexture = new
                Texture("/textures/skybox.png");
        groundTexture = new
                Texture("/textures/landscape.png");

        camera = new
                Camera(new Vector3f(0.0f, 1.0f, 3.0f), new
                Vector3f(0.0f, 1.0f, 0.0f), 90.0f, 0.0f);


        glfwSetCursorPosCallback(window, this::mouseCallback);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        int errorCode = glGetError();
        if (errorCode != GL_NO_ERROR) {
            System.out.println("OpenGL Error: " + errorCode);
        }

        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void loop() {

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            float currentFrame = (float) glfwGetTime();
            setDeltaTime(currentFrame - getLastFrame());
            setLastFrame(currentFrame);

            processInput();

            // Обновите позицию камеры, если она прикреплена к модели
            if (cameraAttachedToModel) {
                camera.updateCameraPosition(carPosition, carDirection, 6.0f, 2f); // Настройте расстояние и высоту по желанию
            }

            Matrix4f view = camera.getViewMatrix();
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), 800f / 600f, 0.1f, 1000.0f);

            // Отрисовка непрозрачных объектов
            renderOpaqueObjects(view, projection);

            // Отрисовка прозрачных объектов
            renderTransparentObjects(view, projection);

            car.updateWheelRotation(getDeltaTime(), carRotationAngle, carPosition, shaderProgram);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        cleanup();
    }

    private void renderOpaqueObjects(Matrix4f view, Matrix4f projection) {
        worldShaderProgram.use();

        glClearColor(0.6f, 0.7f, 0.9f, 1.0f); // Голубой цвет, напоминающий небо
        // Установка параметров солнечного света
        glUniform3f(glGetUniformLocation(worldShaderProgram.getProgramId(), "sunDirection"), -1.0f, -10.0f, -1.0f);
        glUniform3f(glGetUniformLocation(worldShaderProgram.getProgramId(), "sunColor"), 1.0f, 0.9f, 0.7f);
        glUniform1f(glGetUniformLocation(worldShaderProgram.getProgramId(), "sunIntensity"), 0.6f);

        // Установка параметров амбиентного освещения
        glUniform3f(glGetUniformLocation(worldShaderProgram.getProgramId(), "ambientColor"), 0.3f, 0.3f, 0.3f);

//        startFog(camera, worldShaderProgram);

        // Параметры атмосферы
        worldShaderProgram.setUniform("skyColor", new Vector3f(0.6f, 0.7f, 0.9f));
        worldShaderProgram.setUniform("atmosphereStart", 50.0f);
        worldShaderProgram.setUniform("atmosphereEnd", 200.0f);

        // Параметры облаков
        worldShaderProgram.setUniform("cloudDensity", 1f); // Попробуйте разные значения, чтобы найти оптимальные
        worldShaderProgram.setUniform("cloudColor", new Vector3f(1.0f, 1.0f, 1.0f)); // Белые облака

        // Установка матриц вида и проекции
        worldShaderProgram.setUniform("view", view);
        worldShaderProgram.setUniform("projection", projection);

        // Рендеринг земли
        worldShaderProgram.setUniform("model", new Matrix4f());
        groundTexture.bind();
        planeMesh.render();
    }

    private void renderTransparentObjects(Matrix4f view, Matrix4f projection) {
        for (ru.reweu.game.loader.Mesh[] mesh : meshes) {
            for (ru.reweu.game.loader.Mesh meshRender : mesh) {
                Matrix4f model = new Matrix4f().translate(modelPosition)
                        .translate(carPosition)
                        .rotateY((float) Math.toRadians(carRotationAngle))
                        .scale(meshRender.getScale());
                renderObjects(shaderProgram, mesh, model, camera, view, projection);
            }
        }
    }

    private void processInput() {
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            camera.processKeyboard(GLFW_KEY_W, getDeltaTime(), true);
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            camera.processKeyboard(GLFW_KEY_S, getDeltaTime(), true);
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            camera.processKeyboard(GLFW_KEY_A, getDeltaTime(), true);
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            camera.processKeyboard(GLFW_KEY_D, getDeltaTime(), true);
        }
        // Поворот машины
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) {
            if (carSpeed < 0.0f) {
                car.setWheelTurnAngle(30.0f);
                carRotationAngle += turnSpeed * getDeltaTime();
            }
            if (carSpeed > 0.0f) {
                car.setWheelTurnAngle(30.0f);
                carRotationAngle -= turnSpeed * getDeltaTime();
            }
        } else if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) {
            if (carSpeed > 0.0f) {
                car.setWheelTurnAngle(-30.0f);
                carRotationAngle += turnSpeed * getDeltaTime();
            }
            if (carSpeed < 0.0f) {
                car.setWheelTurnAngle(-30.0f);
                carRotationAngle -= turnSpeed * getDeltaTime();
            }
        } else {
            car.setWheelTurnAngle( 0.0f); // Сброс угла поворота колес, если нет ввода
        }

        // Обновление направления движения машины
        carDirection.x = (float) Math.sin(Math.toRadians(carRotationAngle));
        carDirection.z = (float) Math.cos(Math.toRadians(carRotationAngle));
        carDirection.normalize();

        // Ускорение вперед и назад
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
            carSpeed -= acceleration * getDeltaTime();
            if (carSpeed > maxSpeed) {
                carSpeed = maxSpeed;
            }
        } else if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
            carSpeed += acceleration * getDeltaTime();
            if (carSpeed < -maxSpeed) {
                carSpeed = -maxSpeed;
            }
        } else {
            // Плавное замедление, если ни одна клавиша не нажата
            if (carSpeed > 0) {
                carSpeed -= deceleration * getDeltaTime();
                if (carSpeed < 0) {
                    carSpeed = 0;
                }
            } else if (carSpeed < 0) {
                carSpeed += deceleration * getDeltaTime();
                if (carSpeed > 0) {
                    carSpeed = 0;
                }
            }
        }

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            if (carSpeed> 0) {
                carSpeed -= deceleration * getDeltaTime();
                if (carSpeed < 0) {
                    carSpeed = 0;
                }
            } else if (carSpeed < 0) {
                carSpeed += deceleration * getDeltaTime();
                if (carSpeed > 0) {
                    carSpeed = 0;
                }
            }
        }

        // Добавляем обработку Enter для переключения на вид третьего лица
        if (glfwGetKey(window, GLFW_KEY_ENTER) == GLFW_PRESS) {
            if (!cameraAttachedToModel) {
                cameraAttachedToModel = true;
                camera.setThirdPersonView(carPosition, 1.0f, 10.0f); // Вызываем метод перемещения камеры
            } else {
                cameraAttachedToModel = false;
            }
        }

        // Применение скорости для перемещения машины
        carPosition.add(new Vector3f(carDirection).mul(carSpeed * getDeltaTime()));

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            cleanup();
            System.exit(-1);
        }
    }

    private void cleanup() {
        cubeMesh.cleanup();
        planeMesh.cleanup();
        worldShaderProgram.cleanup();
        skyboxTexture.cleanup();
        groundTexture.cleanup();
    }


    private void mouseCallback(long window, double xpos, double ypos) {
        if (firstMouse) {
            lastX = (float) xpos;
            lastY = (float) ypos;
            firstMouse = false;
        }

        float xOffset = (float) xpos - lastX;
        float yOffset = lastY - (float) ypos;

        lastX = (float) xpos;
        lastY = (float) ypos;

        camera.processMouseMovement(xOffset, yOffset);
    }

    public static void main(String[] args) throws Exception {
        new Game3d().run();
    }
}