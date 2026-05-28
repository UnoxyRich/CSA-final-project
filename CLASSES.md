# Key Classes

**Main.java** — Game loop. Reads input, updates player, loads/unloads chunks, renders world and HUD each frame. All OpenGL calls happen here.

**World.java** — Voxel database. Manages all loaded chunks, offloads terrain generation to background threads, and marks chunks dirty when blocks change.

**Player.java** — Player state: position, velocity, look direction, inventory. Delegates movement to `Physics` and block targeting to `Raycaster`.

**ChunkMesher.java** — Converts block data into geometry on background threads. Only emits quads for visible faces, then hands the result to the main thread for GPU upload.
