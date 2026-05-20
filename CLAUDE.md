# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

- Run the game: `gradle run` (requires JDK 17+, Gradle 8+)
- On macOS, the build script auto-injects `-XstartOnFirstThread`, which GLFW requires. Do not run with `java -jar ...` directly on macOS without that flag.
- LWJGL natives are selected at config time from `os.name` + `os.arch` in `build.gradle`. If adding a new platform, update the `lwjglNatives` block there.
- There is no test suite or lint step. Verification is manual: launch and confirm world renders, movement collides, left/right click edit blocks, hotbar 1–9 switches the placed block.

## Architecture

The game loop lives in `Main.java` and is single-threaded: input → player update → world update (chunk load/unload) → world render → HUD render → swap. All OpenGL calls happen on this thread.

The codebase is layered; respect the dependency direction (lower layers must not import upper ones):

1. `engine/` – platform/GL primitives. `Window` owns the GLFW window + GL context. `Shader` and `Mesh` are thin GL wrappers. `Texture.buildBlockAtlas()` builds an 8×8 tile atlas — from `Default_HD_128x_Demo_1.8.2.4.zip` if present, otherwise `Texture.buildProcedural()` generates it procedurally. Adding a new block type means (a) adding the enum case in `Block` (append after `WATER` so existing ordinals stay fixed — the world shader hard-codes water=11/glass=8/leaves=5), (b) adding a palette entry in `Texture.buildProcedural` and a `putTile` in `buildFromResourcePack`, (c) mapping faces in `Block.atlasIndex`, and (d) adding a hotbar color in `HudRenderer.colorFor`. The atlas tile count is mirrored in `ChunkMesher` (`atlasSize`).

2. `world/` – voxel data. `Block` is an enum where ordinal == block id stored in `Chunk.blocks` (bytes). `Chunk` is 16×128×16; coordinates are local. `World` keeps a `HashMap<Long, Chunk>` keyed by packed (cx,cz) and is the only thing that knows about world-space block coords. `ChunkMesher` rebuilds a chunk's mesh from scratch when `chunk.dirty` is set — it reads neighbor blocks via `world.getBlock` for cross-chunk face culling, so editing a block at a chunk boundary must mark the neighbor dirty (see `World.setBlock`). `TerrainGenerator` uses seeded value-noise fBm and classifies each column into a `Biome` (ocean, plains, forest, desert, savanna, taiga, snowy tundra, cherry mountains) from temperature/humidity/elevation noise; the biome drives surface blocks, water freezing, and which tree/decoration is scattered. It is deterministic per (seed, chunk).

3. `player/` – `Player` holds position/velocity/yaw/pitch and owns its `Inventory`. `Physics.moveAndCollide` does axis-by-axis AABB resolution against the world (resolves X, then Y, then Z; setting `onGround` only when Y resolution stops downward motion). `Raycaster` uses Amanatides–Woo voxel traversal and returns both the hit block and the adjacent placement cell.

4. `render/` – `WorldRenderer` iterates `world.loaded()`, rebuilds dirty meshes lazily, and draws each with a per-chunk translation. `HudRenderer` is a separate shader that draws 2D quads in NDC with depth-test off and alpha blending on.

## Important Invariants

- `Block.AIR` must remain ordinal 0 (the byte array is zero-initialized to AIR).
- `Chunk.SY` is the world height limit. `World.getBlock` returns AIR outside [0, SY); do not change this without auditing `Raycaster` and `Physics`.
- Mesh rebuilds destroy and recreate GL buffers; never call `ChunkMesher.rebuild` off the GL thread.
- When `World.setBlock` modifies a border block, it marks the up-to-four neighboring chunks dirty. New world-mutating code must do the same or visible seams will appear.
- `Player.WIDTH=0.6, HEIGHT=1.8`. Collision queries iterate floor()..floor() of the AABB; very fast velocities can tunnel through 1-block-thin walls — keep `dt` clamped (currently 0.05 in `Main`).

## Out of Scope (by design)

Mobs, lighting/shadows, day/night, water physics, crafting, sound, world persistence, and multiplayer are not implemented. The plan file at `~/.claude/plans/write-me-minecraft-quirky-falcon.md` lists these as deferred. If adding any, treat it as a new subsystem — don't try to retrofit into existing classes without a layering pass first.
