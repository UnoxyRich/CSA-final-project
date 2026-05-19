# Minecraft (CSA final project)

A Java 3D voxel sandbox built with LWJGL 3 / OpenGL 3.3.

## Features
- Procedurally generated infinite terrain (value-noise heightmap + trees)
- Chunk streaming (16x128x16 chunks, render distance 6)
- Place / break blocks with mouse (left = break, right = place)
- 9-slot hotbar (keys 1–9): grass, dirt, stone, wood, leaves, sand, planks, glass
- WASD + space + mouse-look, gravity, jumping, AABB collision
- Press **F** to toggle fly mode (space = up, shift = down)
- Press **ESC** to release mouse; click to re-grab
- Procedurally generated texture atlas (no external assets needed)

## Requirements
- macOS / Linux / Windows
- JDK 17+
- Gradle 8+ (or use `./gradlew` — generate the wrapper once with `gradle wrapper`)

## Install prerequisites on macOS
```bash
brew install openjdk@17 gradle
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

## Run
```bash
gradle run
```

(Or `gradle wrapper && ./gradlew run` to lock the Gradle version.)

On macOS the build automatically uses `-XstartOnFirstThread` (required by GLFW).

## Project structure
- `engine/`  – Window, Input, Shader, Mesh, Camera, Texture (procedural atlas)
- `world/`   – Block enum, Chunk, ChunkMesher (face-culling), World (chunk map + streaming), TerrainGenerator (fBm noise)
- `player/`  – Player, Physics (axis-by-axis AABB), Raycaster (Amanatides–Woo), Inventory
- `render/`  – WorldRenderer, HudRenderer (crosshair + hotbar)
- `Main.java` – game loop
