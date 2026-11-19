# Installation guide

## Requirements

| | |
|---|---|
| OS | Windows 10 (1809+) or Windows 11, x64 |
| Minecraft | Java Edition, owned, with the official Minecraft Launcher installed (classic installer, Xbox app or Microsoft Store) |
| Java | Nothing extra: the Minecraft Launcher's bundled Java 21 is used. A custom JDK 21+ can be chosen in Settings |
| .NET | Not required, because `PhantomClient.exe` is self-contained |

## Install and play

1. Download `PhantomClient.exe` (and optionally check it against `SHA256SUMS.txt`).
2. Put it anywhere, e.g. `C:\Games\Phantom\PhantomClient.exe`, and run it. No admin rights are needed.
3. The **Home** page shows what was detected:
   - **Minecraft**: your `.minecraft` folder, downloaded versions, and which Minecraft Launcher was found.
   - **Java runtime**: the selected or bundled Java.
   - **Minecraft process**: whether a Minecraft client is running (PID, version, Fabric/Phantom).
   - **Phantom Client**: live status from the running client (profile, world type, FPS, enabled modules).
4. Pick a **profile** and press **LAUNCH**. The launcher then:
   1. downloads the Fabric Loader 0.16.14 profile for 1.21.1 from `meta.fabricmc.net` into `.minecraft\versions`;
   2. copies the embedded mod into `.minecraft\phantom-instance\mods`;
   3. tells the client which profile to load (`config\phantom\launcher.json`);
   4. adds a **Phantom Client** profile to the Minecraft Launcher (marked last used, with memory and JVM settings);
   5. opens the Minecraft Launcher.
5. In the Minecraft Launcher, choose **Phantom Client** and press **Play**. The first start downloads Minecraft 1.21.1 and the Fabric libraries.
6. In game, press **Right Shift** for the ClickGUI.

Phantom uses its own game folder (`phantom-instance`), so your normal worlds, mods and resource packs are left alone.
You can change that folder in **Settings**.

## Updates

You never need to reinstall. On startup the launcher checks the latest GitHub release of
`theavgjim/phantom`. When a newer version exists:

- **Automatic (default):** it downloads the new `PhantomClient.exe` in the background, verifies it
  against the release's `SHA256SUMS.txt`, and installs it when you close the launcher. You can also click
  **Restart to update** in the sidebar to switch right away.
- **Manual:** turn off *Download updates automatically* in Settings → Launcher. The sidebar then shows
  **Update & restart** instead.

The exe replaces itself in place. The previous version is kept as `PhantomClient.exe.old` until the next
start, and a download whose checksum doesn't match is thrown away. The mod is embedded in the exe, so the
next **LAUNCH** installs the matching mod automatically. Your profiles and settings are untouched.

Self-update needs write access to the folder containing `PhantomClient.exe`, so keep it somewhere like
`Documents` or `Desktop` rather than `Program Files`.

## Using the mod without the launcher

`phantom-client-1.0.0.jar` is a normal Fabric mod. It nests the Fabric API modules it needs, so drop it into the
`mods` folder of any Fabric Loader ≥ 0.16.14 / Minecraft 1.21.1 instance (Prism Launcher, MultiMC, the Fabric
installer, etc.).

## Files and folders

| Path | Contents |
|---|---|
| `%LOCALAPPDATA%\PhantomClient\settings.json` | launcher settings |
| `%LOCALAPPDATA%\PhantomClient\logs\launcher-YYYYMMDD.log` | launcher log (14 days kept) |
| `.minecraft\phantom-instance\` | Phantom game folder (mods, saves, options) |
| `.minecraft\phantom-instance\config\phantom\` | client profiles, backups, exports, status (see [CONFIGURATION.md](CONFIGURATION.md)) |
| `.minecraft\phantom-instance\logs\latest.log` | game log (shown in the launcher console) |
| `.minecraft\launcher_profiles.json.phantom-backup` | copy of your launcher profiles before Phantom edited them |

## Uninstall

1. Delete the **Phantom Client** profile in the Minecraft Launcher (Installations tab).
2. Delete `.minecraft\phantom-instance` (this includes Phantom worlds, so back them up first) and
   `.minecraft\versions\fabric-loader-0.16.14-1.21.1` if nothing else uses it.
3. Delete `PhantomClient.exe` and `%LOCALAPPDATA%\PhantomClient`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "No Minecraft installation" | Start the Minecraft Launcher once, or set the folder in Settings → Game |
| "Minecraft Launcher not found" | Set its path in Settings → Game (e.g. `C:\XboxGames\Minecraft Launcher\Content\Minecraft.exe`) |
| Launch fails while downloading Fabric | Check your internet connection or firewall for `meta.fabricmc.net` |
| Phantom profile missing in the Minecraft Launcher | Restart the Minecraft Launcher; the profile is read when it starts |
| Game crashes on start | Console → **Crash reports**; make sure the profile uses version `fabric-loader-0.16.14-1.21.1` |
| A module shows LOCKED / PAUSED on a server | Intended: restricted modules need the server's permission. See [MULTIPLAYER.md](MULTIPLAYER.md) |
| Disconnected from a server | Use **Reconnect to <server>** on the disconnect screen; a version-mismatch hint appears if the server runs another version |
| Brightness stuck after a crash with Fullbright on | Set Brightness in Video Settings again; Phantom restores it on a normal exit |
| Launcher error | Console → **Logs folder**, attach `launcher-*.log` when reporting |

Windows SmartScreen may warn about an unsigned new executable. Use **More info → Run anyway** only if
the file's SHA-256 matches the published checksum.
