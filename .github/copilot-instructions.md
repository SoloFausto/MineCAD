# MineCAD - AI Coding Instructions

## Project Overview

MineCAD is a Minecraft Fabric mod targeting **Minecraft 1.21.11** with **Java 21**. It uses the Fabric Loom build system with split environment source sets (client/server separation).

## Architecture

### Source Set Structure

```
src/
├── main/          # Server-side + common code (runs on both client & server)
│   └── java/org/solofausto/minecad/
│       └── Minecad.java              # Main mod entrypoint (ModInitializer)
├── client/        # Client-only code (rendering, GUI, keybindings)
│   └── java/org/solofausto/minecad/client/
│       ├── MinecadClient.java        # Client entrypoint (ClientModInitializer)
│       └── MinecadDataGenerator.java # Asset/data generation
```

**Important**: Client code in `src/client/` cannot be referenced from `src/main/`. Server-side code goes in `main/`, client-specific code (rendering, GUI) in `client/`.

### Entrypoints (fabric.mod.json)

- `main` → `Minecad.onInitialize()` - Register items, blocks, commands, events
- `client` → `MinecadClient.onInitializeClient()` - Register renderers, keybindings, screens
- `fabric-datagen` → `MinecadDataGenerator` - Generate JSON assets (models, recipes, loot tables)

### Mixin System

- Server/common mixins: `minecad.mixins.json` → package `org.solofausto.minecad.mixin`
- Client mixins: `minecad.client.mixins.json` → package `org.solofausto.minecad.mixin.client`
- Mixins require `@Overwrite` annotations (configured with `requireAnnotations: true`)

## Build & Run Commands

```bash
./gradlew build              # Compile and package mod JAR
./gradlew runClient          # Launch Minecraft with mod (development)
./gradlew runServer          # Launch dedicated server with mod
./gradlew runDatagen         # Generate data files (models, recipes)
./gradlew genSources         # Decompile Minecraft sources for IDE navigation
```

## Key Conventions

### Package Structure

- Base package: `org.solofausto.minecad`
- Client-specific: `org.solofausto.minecad.client`
- Mixins: `org.solofausto.minecad.mixin` / `org.solofausto.minecad.mixin.client`

### Version Management

All versions are centralized in `gradle.properties`:

- `minecraft_version`, `fabric_version`, `loader_version` - Check https://modmuss50.me/fabric.html for updates
- `mod_version` - Current: `1.0-SNAPSHOT`

### Mod ID

The mod ID is `minecad`. Use this for:

- Resource paths: `assets/minecad/`, `data/minecad/`
- Registry identifiers: `Identifier.of("minecad", "my_item")`

## Fabric API Patterns

When adding features, use Fabric API where available:

- Items/Blocks: Register via `Registry.register()` in `Minecad.onInitialize()`
- Client rendering: Use `BlockRenderLayerMap`, `ColorProviderRegistry` in client entrypoint
- Events: Use Fabric's callback system (e.g., `ServerTickEvents`, `ClientTickEvents`)

## Dependencies

- **Fabric Loader**: Core mod loading
- **Fabric API**: Hooks and utilities for mod development
- **Yarn Mappings**: Human-readable Minecraft source names

## General Scope

MineCAD is a mod that ports a CAD-like building experience into Minecraft, allowing players to create precise structures using familiar CAD tools and techniques within the Minecraft environment.

## How does the mod work

First the user creates a blueprint that is in a block face, then the user can extrude, and manipulate the blueprint to create complex 3D structures that can be placed in the Minecraft world.

## Blueprint Creation

After defining a 2D blueprint on a block face, the user can use various CAD-like sketch tools to manipulate the blueprint. These tools include:

- Line Tool: Draw straight lines between points.
- Circle Tool: Create circles by defining a center point and radius.
- Arc Tool: Draw arcs by specifying start, end, and bulge.
- Dimension Tool: Measure distances and angles within the blueprint.
- Rectangle Tool: Quickly create rectangles by defining two opposite corners.

Once the user picks the block face to draw on, the mod enters blueprint creation mode, where the user can use the above tools to create and modify the blueprint.

## Shape Creation

Once the blueprint is complete, the user can convert it into a 3D shape using operations such as:

- Extrude: Extend the 2D shape along a specified axis to create a 3D object. The user can define the extrusion height and direction, along with boolean operations like union, subtract, and intersect.
- Break Block: Remove blocks within the defined shape area in the Minecraft world.

The mod keeps track of the created shapes and allows users to modify or delete them as needed.

## History

The mod keeps track of all the operations performed on the blueprint and shape, allowing users to undo and redo actions as needed. This history feature ensures that users can experiment with different designs without losing their progress.
