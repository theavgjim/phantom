# Architecture

```
┌──────────────────────── PhantomClient.exe (C#, WPF, .NET 8) ────────────────────────┐
│ MainWindow (XAML) ── MainViewModel ── LaunchService                                 │
│   Windows services: JavaLocator (registry/PATH/dirs), ProcessDetector (WMI),        │
│                     MinecraftLauncherLocator                                        │
│   PhantomLauncher.Core (platform-neutral, unit-tested):                             │
│     ClientInstaller · MinecraftInstallation · PhantomProfiles · UpdateChecker       │
│     LogTailer · CompatibilityTable · ClientStatus · Log · AppSettings               │
└───────────────┬─────────────────────────────────────────▲───────────────────────────┘
   writes:      │ versions/fabric-loader-*.json            │ reads: status.json (5 s heartbeat)
                │ phantom-instance/mods/phantom-client.jar │        phantom.json (last profile)
                │ config/phantom/launcher.json (profile)   │        logs/latest.log (console)
                │ launcher_profiles.json ("Phantom Client")│
                ▼                                          │
       Official Minecraft Launcher (sign-in, downloads) ──► Minecraft 1.21.1 + Fabric Loader
                                                           │
┌──────────────────────── phantom-client.jar (Java 21, Fabric) ─────────────▼─────────┐
│ MinecraftIntegration   PhantomClientMod → PhantomClient (runtime)                   │
│   hooks: Fabric ClientTick / WorldRender.LAST / HudRender / ClientStopping          │
│   mixins: Keyboard, KeyboardInput, Camera, Entity, MinecraftClient, GameRenderer,   │
│           ClientPlayerEntity, ClientPlayerInteractionManager, SimpleOption accessor │
│   PolicyGuard · ServerSession · ReconnectHandler · KeyHandler · EntityCache · TargetTracker · Render3D/2D        │
│ Modules/ (Combat, Visuals, Movement, Player, Misc)   GUI/ (ClickGUI, HUD editor)     │
│ HUD/ (HudManager + elements)                                                        │
│ PhantomCore + Configuration (no Minecraft dependency, unit-tested):                 │
│   EventBus · Module/ModuleManager · Settings · ThemeManager · HudRegistry           │
│   ConfigManager · ConfigSerializer · BackupManager · StatusWriter · Compatibility   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Design rules

1. **The core knows nothing about Minecraft.** `PhantomCore` and `Configuration` compile without the
   game and are unit-tested (`./gradlew :PhantomCore:test`). The same applies to `PhantomLauncher.Core`
   on the C# side.
2. **Documented integration only.** Fabric Loader entrypoints, Fabric API events and Mixin
   injections. There are no bytecode tricks, no hidden processes and no network or packet manipulation.
3. **Mixins stay thin.** Each mixin calls one static method in `Hooks`, which is a single
   "is the module enabled?" check, so the cost is negligible when features are off.
4. **Server policy is enforced centrally** (`PolicyGuard` → `PolicyEvaluator`, an `ActivationGuard` on the
   `ModuleManager`, fed by `ServerSession` and the optional `phantom:policy` channel). It is checked when
   enabling, when loading profiles, and every tick; disallowed modules are paused and resumed automatically.
   See [MULTIPLAYER.md](MULTIPLAYER.md).
5. **The launcher never edits a running client's files.** It requests a profile through `launcher.json`,
   which the client consumes once at startup.

## Lifecycle

| Phase | What happens |
|---|---|
| `onInitializeClient` | Version check → `PhantomCore` → register 26 modules and 7 HUD elements → guard → Fabric event hooks. ~60 ms |
| async init | `ConfigManager.initAsync()` reads the profile on the `Phantom-Config` thread and applies it on the game thread; then autosave (2 s) and the status heartbeat (5 s) start |
| tick | Guard enforcement → `EntityCache`/`TargetTracker` tick → `TickEvent` to enabled modules |
| frame | `WorldRenderEvents.LAST`: capture matrices → `Render3DEvent` → one batched flush. HUD: `Render2DEvent` → HUD elements |
| key | `KeyboardMixin` → `KeyHandler` (toggle / activate / hold) when no screen is open |
| shutdown | `CLIENT_STOPPING`: save config → disable modules (restores gamma, culling, flight, FOV) → final status → free GPU buffers |

## Module framework

```java
public final class Spider extends ClientModule {
    private final NumberSetting speed = add(new NumberSetting("Climb Speed", "", 0.2, 0.05, 0.6, 0.05));

    public Spider() {
        super("Spider", "Climb walls (private worlds)", Category.MOVEMENT);
        privateWorldOnly();                       // policy flag
        on(TickEvent.class, e -> { ... });        // subscribed only while enabled
    }
}
```

- Settings: `BooleanSetting`, `NumberSetting` (clamped and snapped), `ModeSetting` (dropdown, static or
  dynamic options), `ColorSetting` (ARGB, HSB helpers), `StringSetting`, `StringListSetting`.
  Each serialises itself and supports `visibleWhen(...)`.
- Flags: `privateWorldOnly()`, `notToggleable()` (action/settings modules), `holdToActivate()`,
  `hideFromList()`, `defaultKey(...)`, `enabledByDefault()`.
- Hooks: `onEnable`, `onDisable`, `onActivate`, `onSettingChanged`, `getSuffix` (module-list text).
- Register new modules in `MinecraftIntegration/.../ModuleRegistry.java`. The GUI, config and HUD
  pick them up automatically.

## Rendering

- **3D** (`Render3D`): shapes from every module are collected camera-relative into two `BufferBuilder`s
  with their own allocators and drawn with **two draw calls per frame** (fills, then lines) using the
  vanilla position-colour shader, see-through. Allocators are freed at shutdown.
- **2D** (`Render2D`): float-precision rounded rectangles, gradients and circles via triangle fans.
  Text uses the see-through text layer, so Phantom's HUD draws in plain painter's order and vanilla HUD
  depth (e.g. chat) can't punch holes in it.
- **Projection** (`Projection`): the world view/projection matrices are captured each frame so 2D
  overlays (nametags, distance labels) line up with the 3D world.

## Performance notes

- `EntityCache` walks the world entity list at most once per tick, lazily, and only if a module asks.
  Results are sorted by distance, so range-limited consumers stop early.
- StorageESP rescans block entities once per second. BlockESP scans a few chunks per tick (configurable)
  and skips chunk sections whose palette cannot contain a target block.
- Disabled modules have no event subscriptions, so they cost nothing.
- Disk IO runs on one low-priority daemon thread. Saves are debounced and atomic (tmp file + move).
- Animations are frame-rate independent (`Animation`, exponential smoothing).
