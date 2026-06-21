# Game Shader

3D game engine with PBR rendering, built on LWJGL/OpenGL.

## Architecture

The project is split into three Maven modules:

| Module | Description | Dependencies |
|--------|-------------|-------------|
| **[engine](https://github.com/BorodinPV/engine)** | Render infrastructure: shaders, shadow maps, sky, IBL, lighting | LWJGL, JOML |
| **[assets](https://github.com/BorodinPV/assets)** | Model loading (Assimp/OBJ) + glTF 2.0 PBR rendering | engine, jgltf, LWJGL-Assimp |
| **game-shader** | Game logic, scene rendering, GUI, ray tracing | engine, assets |

## Features

- **PBR Rendering** — physically based materials with metallic/roughness workflow
- **Cascaded Shadow Maps** — directional light with configurable cascade count
- **Image-Based Lighting** — HDR environment maps with prefiltered specular
- **glTF 2.0** — native loading with skinning and morph targets
- **Assimp Loader** — OBJ, GLB and other formats
- **Procedural Sky** — hemisphere gradient with sun disc
- **Ray Tracing** — optional compute shader path (OpenGL 4.3+)
- **Runtime Settings** — in-game graphics menu with live preview

## Build

```bash
# Build all modules (from game-project root)
cd game-project
mvn clean install

# Or build game-shader alone (requires engine + assets in local Maven repo)
cd game-shader
mvn compile
```

## Run

```bash
cd game-shader
mvn exec:java
```

## Requirements

- Java 17+
- OpenGL 3.2+ (4.3+ for ray tracing)
