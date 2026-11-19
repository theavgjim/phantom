# Configuration & profiles

All client configuration is JSON under `<game dir>/config/phantom/`
(with the launcher: `.minecraft\phantom-instance\config\phantom\`).

```
config/phantom/
├── phantom.json          { "schema": 1, "activeProfile": "default" }
├── launcher.json         one-shot request from PhantomClient.exe: { "activeProfile": "..." } (deleted when read)
├── status.json           client heartbeat for the launcher (every 5 s)
├── profiles/
│   ├── default.json
│   └── <name>.json
├── backups/              <profile>_<yyyyMMdd-HHmmss-SSS>.json (newest 10 per profile)
├── exports/              <profile>_<yyyyMMdd-HHmmss>.json
└── import/               drop profiles here to import them in game
```

## Profile schema

```json
{
  "schema": 1,
  "savedAt": "2026-11-19T13:18:25Z",
  "modules": {
    "ESP": {
      "enabled": true,
      "key": -1,
      "settings": { "Mode": "Outline", "Range": 96.0, "Players": true }
    },
    "ESP Colors": { "key": -1, "settings": { "Hostile": "#FFEF5350" } },
    "ClickGUI":   { "key": 344, "settings": { "Theme": "Phantom", "Animation Speed": 14.0 } }
  },
  "hud": {
    "modulelist": { "x": 1.0, "y": 0.0, "settings": { "Visible": true, "Scale": 1.0, "Suffixes": true } }
  }
}
```

- `key` is a GLFW key code (`-1` = none). Examples: 344 Right Shift, 82 R, 67 C.
- Colours are `#AARRGGBB`.
- HUD `x`/`y` are fractions of the screen (0–1) for the element's top-left corner, so layouts survive
  resolution and GUI-scale changes. Elements snapped to the right/bottom edge are stored as `1.0`.

## Behaviour

| Feature | Details |
|---|---|
| Automatic saving | Any change marks the profile dirty; a background thread saves within ~2 s. Writes are atomic (tmp + move). Also saved on exit and before switching profiles |
| Defaults first | Loading a profile resets everything to defaults, then applies the file, so new profiles start clean and missing keys fall back to defaults |
| Robust loading | Unknown keys are ignored and malformed values keep their defaults. An unreadable file is logged and skipped, never crashing the game |
| Multiple profiles | ClickGUI → Profiles: create from defaults or "Save as" from the current state, load, copy, export, delete (with confirmation) |
| Import / export | Export writes to `exports/`. Import: drop files in `import/` → Refresh → Import, or use Import... in the launcher. Imports are validated |
| Backups | Automatic backup before overwriting a profile (at most every 10 min), on delete, and on demand (**Backup now**). **Restore latest** in the GUI |
| Launcher sync | The launcher's profile selector writes `launcher.json`. The client loads that profile at its next start and records it in `phantom.json`, which the launcher reads back |
| Private-world policy | Enabling a PRIVATE module from a profile while on a remote server is silently skipped |

## Launcher settings

`%LOCALAPPDATA%\PhantomClient\settings.json`: Minecraft directory, instance directory, Java path,
memory (MB), extra JVM args, Minecraft Launcher path, selected profile, close-on-launch, update checks, automatic updates
and release-feed URL, console filter.

The release feed defaults to `https://api.github.com/repos/theavgjim/phantom/releases/latest`; change it in Settings → Launcher, or in
`Resources/shared/phantom/compatibility.json` (`updateUrl`) before building. The placeholder
`OWNER` URL is ignored.
