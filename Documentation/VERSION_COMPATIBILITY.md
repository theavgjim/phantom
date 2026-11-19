# Version compatibility

The single source of truth is [`Resources/shared/phantom/compatibility.json`](../Resources/shared/phantom/compatibility.json).
It is packed into **both** the mod jar and `PhantomClient.exe`, so they always agree.

```json
{
  "clientVersion": "1.0.0",
  "supported": [
    { "minecraft": "1.21.1", "fabricLoader": "0.16.14", "java": 21, "status": "supported" }
  ],
  "planned": ["1.21.4"]
}
```

## Matrix

| Phantom | Minecraft | Fabric Loader | Fabric API modules (nested) | Java | Mappings | Status |
|---|---|---|---|---|---|---|
| 1.0.0 | **1.21.1** | ≥ 0.16.14 | api-base 0.4.42, lifecycle-events-v1 2.6.0, rendering-v1 5.2.1 (from Fabric API 0.116.18+1.21.1) | 21 | Yarn 1.21.1+build.3 | Supported, in-game self-test passes |
| — | 1.21.2 – 1.21.x | | | 21 | | Not supported (rendering and input APIs changed) |
| — | ≤ 1.21 | | | | | Not supported |

## How compatibility is detected

**Launcher (before launch)**

- Scans `.minecraft\versions` for installed vanilla and Fabric versions.
- Detects Java runtimes (JAVA_HOME, PATH, registry, common install folders, the Minecraft Launcher's
  bundled runtimes) and reads each version from its `release` file.
- Shows **Compatible** / **Warning** / **Unsupported** on the Home page. A missing 1.21.1 is only a warning,
  because the Minecraft Launcher downloads it automatically.
- Always installs the exact Fabric Loader version from the table.

**Mod (at startup)**

- `fabric.mod.json` declares `minecraft: 1.21.1`, `fabricloader >= 0.16.14` and `java >= 21`, so Fabric
  refuses to load Phantom on other versions with a clear error, before any code runs.
- `VersionCheck` compares the running game, loader and Java version with the table and logs
  `Version check passed: ...` (or warns, plus a toast in game).
- All mixins are `required` with `defaultRequire = 1`: if an injection point were missing, the game would
  stop at startup with a named mixin error instead of misbehaving silently.

## What breaks across Minecraft versions

When porting, these are the touch points (all in `MinecraftIntegration/.../mixin` and `render`):

| Area | 1.21.1 API used | Changed in |
|---|---|---|
| Keyboard input mixin | `KeyboardInput.tick(boolean, float)` | 1.21.2 (now `tick()`, `PlayerInput` record) |
| FOV mixin (Zoom) | `GameRenderer.getFov(...)` returns `double` | 1.21.2 (returns `float`) |
| Tick delta | `RenderTickCounter.getTickDelta(boolean)` | 1.21.5 (`getTickProgress`) |
| 3D rendering | `RenderSystem.applyModelViewMatrix`, `BufferRenderer` | 1.21.2 / 1.21.5 (render pipeline rewrite) |
| HUD callback | `HudRenderCallback(DrawContext, RenderTickCounter)` | Fabric API in 1.21.6+ (HUD layers API) |

## Adding a version

1. Add a branch or Gradle property set (`minecraft_version`, `yarn_mappings`, `fabric_api_version`, `loader_version`).
2. Port the touch points above; build until mixin refmap warnings are gone.
3. Run the in-game self-test (`./gradlew :MinecraftIntegration:runSelftest`) and get to zero failures.
4. Add the entry to `compatibility.json` (this updates the launcher and the mod together).
