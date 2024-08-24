//package ru.reweu.game.control;
//
//import org.joml.Vector3f;
//import ru.reweu.game.Camera;
//import ru.reweu.game.car.Car;
//
//import static org.lwjgl.glfw.GLFW.*;
//import static ru.reweu.game.car.Car.carSpeed;
//import static ru.reweu.game.main.Variables.getDeltaTime;
//
//public class InputKey {
//
//    private void processInput(Camera camera, long window, Car car) {
//        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
//            camera.processKeyboard(GLFW_KEY_W, getDeltaTime(), true);
//        }
//        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
//            camera.processKeyboard(GLFW_KEY_S, getDeltaTime(), true);
//        }
//        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
//            camera.processKeyboard(GLFW_KEY_A, getDeltaTime(), true);
//        }
//        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
//            camera.processKeyboard(GLFW_KEY_D, getDeltaTime(), true);
//        }
//        // Поворот машины
//        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) {
//            if (carSpeed < 0.0f) {
//                car.setWheelTurnAngle(30.0f);
//                carRotationAngle += turnSpeed * getDeltaTime();
//            }
//            if (carSpeed > 0.0f) {
//                car.setWheelTurnAngle(30.0f);
//                carRotationAngle -= turnSpeed * getDeltaTime();
//            }
//        } else if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) {
//            if (carSpeed > 0.0f) {
//                car.setWheelTurnAngle(-30.0f);
//                carRotationAngle += turnSpeed * getDeltaTime();
//            }
//            if (carSpeed < 0.0f) {
//                car.setWheelTurnAngle(-30.0f);
//                carRotationAngle -= turnSpeed * getDeltaTime();
//            }
//        } else {
//            car.setWheelTurnAngle( 0.0f); // Сброс угла поворота колес, если нет ввода
//        }
//
//        // Обновление направления движения машины
//        carDirection.x = (float) Math.sin(Math.toRadians(carRotationAngle));
//        carDirection.z = (float) Math.cos(Math.toRadians(carRotationAngle));
//        carDirection.normalize();
//
//        // Ускорение вперед и назад
//        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) {
//            carSpeed -= acceleration * getDeltaTime();
//            if (carSpeed > maxSpeed) {
//                carSpeed = maxSpeed;
//            }
//        } else if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) {
//            carSpeed += acceleration * getDeltaTime();
//            if (carSpeed < -maxSpeed) {
//                carSpeed = -maxSpeed;
//            }
//        } else {
//            // Плавное замедление, если ни одна клавиша не нажата
//            if (carSpeed > 0) {
//                carSpeed -= deceleration * getDeltaTime();
//                if (carSpeed < 0) {
//                    carSpeed = 0;
//                }
//            } else if (carSpeed < 0) {
//                carSpeed += deceleration * getDeltaTime();
//                if (carSpeed > 0) {
//                    carSpeed = 0;
//                }
//            }
//        }
//
//        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
//            if (carSpeed> 0) {
//                carSpeed -= deceleration * getDeltaTime();
//                if (carSpeed < 0) {
//                    carSpeed = 0;
//                }
//            } else if (carSpeed < 0) {
//                carSpeed += deceleration * getDeltaTime();
//                if (carSpeed > 0) {
//                    carSpeed = 0;
//                }
//            }
//        }
//
//        // Добавляем обработку Enter для переключения на вид третьего лица
//        if (glfwGetKey(window, GLFW_KEY_ENTER) == GLFW_PRESS) {
//            if (!cameraAttachedToModel) {
//                cameraAttachedToModel = true;
//                camera.setThirdPersonView(carPosition, 5.0f, 2.0f); // Вызываем метод перемещения камеры
//            } else {
//                cameraAttachedToModel = false;
//            }
//        }
//
//        // Применение скорости для перемещения машины
//        carPosition.add(new Vector3f(carDirection).mul(carSpeed * getDeltaTime()));
//
//        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
//            cleanup();
//            System.exit(-1);
//        }
//    }
//
//}
