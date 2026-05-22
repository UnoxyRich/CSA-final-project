# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

- Run the game: `gradle run` (requires JDK 17+, Gradle 8+)
- On macOS, the build script auto-injects `-XstartOnFirstThread`, which GLFW requires. Do not run with `java -jar ...` directly on macOS without that flag.
- LWJGL natives are selected at config time from `os.name` + `os.arch` in `build.gradle`. If adding a new platform, update the `lwjglNatives` block there.
- There is no test suite or lint step. Verification is manual: launch and confirm world renders, movement collides, left/right click edit blocks, hotbar 1–9 switches the placed block.

## Architecture

The game loop lives in `Main.java`: input → player update → world update (chunk load/unload) → world render → HUD render → swap. **All OpenGL calls and all shared-state mutation happen on this thread.** Two kinds of pure-CPU work are offloaded to a `ChunkWorkerPool` (a daemon thread pool sized to CPU cores): terrain generation and mesh vertex-array building. Workers only *read* shared state and *produce* results into concurrent queues; the main thread integrates those results (inserts generated chunks, uploads built meshes to GL). This keeps generation/meshing spikes off the frame while using all cores.

The codebase is layered; respect the dependency direction (lower layers must not import upper ones):

1. `engine/` – platform/GL primitives. `Window` owns the GLFW window + GL context. `Shader` and `Mesh` are thin GL wrappers. `Texture.buildBlockAtlas()` builds an 8×8 tile atlas — from `Default_HD_128x_Demo_1.8.2.4.zip` if present, otherwise `Texture.buildProcedural()` generates it procedurally. Adding a new block type means (a) adding the enum case in `Block` (append after `WATER` so existing ordinals stay fixed — the world shader hard-codes water=11/glass=8/leaves=5), (b) adding a palette entry in `Texture.buildProcedural` and a `putTile` in `buildFromResourcePack`, (c) mapping faces in `Block.atlasIndex`, and (d) adding a hotbar color in `HudRenderer.colorFor`. The atlas tile count is mirrored in `ChunkMesher` (`atlasSize`).

2. `world/` – voxel data. `Block` is an enum where ordinal == block id stored in `Chunk.blocks` (bytes). `Chunk` is 16×128×16; coordinates are local. `World` keeps a `ConcurrentHashMap<Long, Chunk>` keyed by packed (cx,cz) and is the only thing that knows about world-space block coords (concurrent because worker threads read it while the main thread mutates it; only the main thread ever inserts/removes). `World.update` submits missing chunks to the worker pool for generation and integrates finished ones (`generating`/`generated`). `ChunkMesher.buildMesh` builds a chunk's vertex array from scratch when `chunk.dirty` is set — pure CPU, runs on a worker thread; it reads neighbor blocks via `world.getBlock` for cross-chunk face culling, so editing a block at a chunk boundary must mark the neighbor dirty (see `World.setBlock`). `TerrainGenerator` uses seeded value-noise fBm and classifies each column into a `Biome` (ocean, plains, forest, desert, savanna, taiga, snowy tundra, cherry mountains) from temperature/humidity/elevation noise; the biome drives surface blocks, water freezing, and which tree/decoration is scattered. It is deterministic per (seed, chunk).

3. `player/` – `Player` holds position/velocity/yaw/pitch and owns its `Inventory`. `Physics.moveAndCollide` does axis-by-axis AABB resolution against the world (resolves X, then Y, then Z; setting `onGround` only when Y resolution stops downward motion). `Raycaster` uses Amanatides–Woo voxel traversal and returns both the hit block and the adjacent placement cell.

4. `render/` – `WorldRenderer` iterates `world.loaded()` and draws each chunk with a per-chunk translation. `updateMeshes` dispatches the nearest dirty chunks to the worker pool (`ChunkMesher.buildMesh`) and uploads finished vertex arrays to GL within a per-frame time budget. `HudRenderer` is a separate shader that draws 2D quads in NDC with depth-test off and alpha blending on.

## Important Invariants

- `Block.AIR` must remain ordinal 0 (the byte array is zero-initialized to AIR).
- `Chunk.SY` is the world height limit. `World.getBlock` returns AIR outside [0, SY); do not change this without auditing `Raycaster` and `Physics`.
- `ChunkMesher.buildMesh` is pure CPU and runs on worker threads — it must never touch OpenGL. Only the main thread may call `Mesh.upload`/`Mesh.destroy` or any GL function. Likewise, only worker threads run `TerrainGenerator.generate`; both must stay free of GL and shared-state writes.
- When `World.setBlock` modifies a border block, it marks the up-to-four neighboring chunks dirty; `World.update` does the same for a freshly integrated chunk. New world-mutating code must do the same or visible seams will appear.
- A chunk may be unloaded while a generation or mesh job for it is still in flight. `Chunk.unloaded` flags this; result-integration code must skip flagged chunks (a destroyed `Mesh` must not be uploaded to).
- `Player.WIDTH=0.6, HEIGHT=1.8`. Collision queries iterate floor()..floor() of the AABB; very fast velocities can tunnel through 1-block-thin walls — keep `dt` clamped (currently 0.05 in `Main`).

## Out of Scope (by design)

Mobs, lighting/shadows, day/night, water physics, crafting, sound, world persistence, and multiplayer are not implemented. The plan file at `~/.claude/plans/write-me-minecraft-quirky-falcon.md` lists these as deferred. If adding any, treat it as a new subsystem — don't try to retrofit into existing classes without a layering pass first.
