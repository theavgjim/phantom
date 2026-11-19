# Multiplayer compatibility

Phantom is a normal Fabric client mod. It joins servers with the unmodified vanilla 1.21.1 network
protocol; it adds no packets, changes no packets, and hides nothing. What it adds for multiplayer is
**rules about which of its own features may run**, decided by the server.

## The rule

| Where you are | Cosmetic modules | Restricted modules |
|---|---|---|
| Main menu, singleplayer, a LAN world **you host** | on | on |
| Any other server, default | on (unless the server denies them) | **locked** |
| A server that sends a `phantom:policy` | on, unless denied | only those it **allows** |

- **Deny always wins.** A server can switch off even cosmetic modules.
- **You can only restrict further** (ClickGUI → Servers → "Turn modules off for this server"). Nothing on
  the client can unlock a module a server has not granted.
- **Grants are per connection.** They are never cached: every server, and every backend behind a proxy,
  must state its policy again on each join. Switching servers resets to the default rules.
- **Paused, not lost.** A module you had on is *paused* while it isn't allowed: still saved as on,
  shown as `PAUSED` in the GUI, and resumed automatically when you return to singleplayer or a server
  that allows it.

### Module classes

| Always allowed (cosmetic / vanilla-equivalent) | Restricted (hidden information or automation) |
|---|---|
| ClickGUI, HUD, Notifications, Reconnect, ESP Colors (settings only) | **Combat:** KillAura, AimBot, Criticals |
| Zoom (like the spyglass / OptiFine zoom) | **Visuals:** ESP, StorageESP, BlockESP, Tracers, Nametags, Highlight, DistanceIndicators, Freecam |
| Fullbright (like a high Brightness setting) | **Movement:** Fly, Speed, BunnyHop, Spider, NoSlow |
| AutoSprint (like vanilla toggle-sprint) | **Player:** AutoTool |
| Reach (draws your real range; changes nothing), TargetHUD (shows what vanilla shows) | |

"Always allowed" is Phantom's default, **not a promise that a server permits it**. Server rules come
first: if a server forbids fullbright or zoom, turn them off for that server (or the server can deny them).

## What is detected

| | Source |
|---|---|
| Address and name | the server list entry / direct-connect address |
| Software (brand) | the vanilla `minecraft:brand` the server sends, e.g. `vanilla`, `Paper`, `Velocity (...) <- Paper` |
| Version and protocol | the server-list ping (version text, protocol number); Phantom's client protocol is 767 (1.21.1) |
| Ping | the vanilla player list |
| Proxy backend switch | a new play session (1.20.2+ proxies reconfigure the client) or a brand change |

Everything is shown in **ClickGUI → Servers**, on the optional **Server Info** HUD element, and in the
launcher (`status.json` → "Multiplayer: host (brand)"). Each server you join gets a profile in
`config/phantom/servers.json` (see below).

## Server switching, reconnecting and stability

- **On every join / leave / proxy switch / dimension change:** entity caches, targets, block and container
  scans are cleared, Freecam is turned off, and the policy is re-evaluated before any module runs.
- **Reconnect:** the vanilla disconnect screen gets a **Reconnect to <server>** button. If the server
  rejected the client for its version, the screen also says which version the server runs.
  *Auto Reconnect* (ClickGUI → Misc → Reconnect) is **off by default**. When on, it only reacts to lost
  connections (timeouts, restarts), never kicks, bans, whitelists or full servers, waits 10 s (doubling
  each try) and stops after 3 attempts.
- **Crash containment:** if a module throws, only that module is switched off (with a toast and a log
  entry); a failing HUD element is skipped; the 3D renderer always restores GL state. A broken or
  oversized `phantom:policy` payload is ignored: it cannot disconnect you.
- **Performance:** the shared entity scan is capped to the nearest 1,500 entities on crowded servers.
  Phantom's own cost per frame is measured and can be shown on the Server Info element ("Phantom Cost").

## Diagnostics: when a server restricts a feature

- Toggling a locked module shows a toast with the exact reason: **Not permitted on this server**,
  **Disabled by this server** or **Blocked in your profile for this server**.
- Module cards show **LOCKED** (not allowed here), **PAUSED** (you had it on; it resumes when allowed)
  or **RESTRICTED** (allowed here, but gated on servers). The expanded card repeats the reason.
- On join you get a toast with how many modules are locked, and the log (also in the launcher console)
  records `Server <address> (<brand>), policy: default|phantom:policy, locked: ...`.
- **ClickGUI → Servers → Copy diagnostics** puts a full report on the clipboard: versions, protocol,
  server software, ping, policy, your blocks, enabled / paused / locked modules with reasons, and Phantom's
  per-frame cost.

## Network traffic and false positives

- **Traffic:** Phantom sends nothing of its own. The only extra bytes are Fabric's standard
  `minecraft:register` channel list, which includes `phantom:policy`. Launcher communication is a
  local file (`status.json`), never the network.
- **Cosmetic modules are purely local:** HUD, ClickGUI, Zoom, Fullbright, Reach, TargetHUD and
  notifications only change rendering. The server receives exactly what a vanilla client sends.
- **AutoSprint** uses vanilla's exact sprint conditions (forward input, food > 6 or flight, not sneaking,
  using an item, riding, gliding, blind or blocked), so its sprint state is one an unmodified client could send.
- **Freecam** never moves the player: input is frozen and no position changes are sent.
- **Restricted modules** that a server grants (e.g. Fly, Speed, KillAura) are still subject to that
  server's own movement and combat checks; Phantom does not try to get around them. Fly in particular
  also needs `allow-flight=true` on the server.

## Per-server profiles (`config/phantom/servers.json`)

```json
[
  {
    "address": "play.example.net",
    "name": "Example Network",
    "brand": "Velocity (git-...) <- Paper",
    "version": "Paper 1.21.1",
    "protocol": 767,
    "lastJoined": 1763560000000,
    "joins": 12,
    "configProfile": "PvE",
    "blocked": ["fullbright"],
    "lastPolicySource": "phantom:policy",
    "lastAllowed": ["esp"],
    "lastDenied": []
  }
]
```

- `configProfile`: a Phantom config profile to load automatically when you join (ClickGUI → Servers →
  "Profile on join").
- `blocked`: modules you turned off for this server.
- `lastPolicySource` / `lastAllowed` / `lastDenied`: **informational only**. They are never used to grant anything.

## For server owners: `phantom:policy`

Send a plugin message on channel **`phantom:policy`** to players whose client registered that channel.
Phantom registers it automatically through the standard `minecraft:register` handshake. The body is
raw UTF-8 JSON (no length prefix, at most 16 KB):

```json
{ "version": 1, "allow": ["ESP", "Fly"], "deny": ["Zoom"], "message": "Build server: ESP and fly OK" }
```

| Field | Meaning |
|---|---|
| `allow` | restricted modules you permit; `"*"` permits all of them |
| `deny` | any modules you forbid, including cosmetic ones; `"*"` forbids everything; deny beats allow |
| `message` | shown to the player in a toast (max 200 chars) |

Module names are case-insensitive and ignore spaces (`"StorageESP"`, `"storage esp"`). Send the
message after join, and again after a proxy moves the player to another backend; you can resend it at any
time to change the rules. If you never send anything, players get the default (cosmetic-only) rules.

**Paper / Spigot**

```java
public final class PhantomPolicy extends JavaPlugin implements Listener {
    private static final String CHANNEL = "phantom:policy";
    private static final byte[] POLICY = "{\"allow\":[\"ESP\"],\"deny\":[]}".getBytes(StandardCharsets.UTF_8);

    @Override public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void onRegister(PlayerRegisterChannelEvent e) {   // the client announced phantom:policy
        if (e.getChannel().equals(CHANNEL)) e.getPlayer().sendPluginMessage(this, CHANNEL, POLICY);
    }
}
```

**Fabric server mod:** see `MinecraftIntegration/src/selftest/.../SelfTestServer.java`
(`PayloadTypeRegistry.playS2C().register(...)` + `ServerPlayNetworking.send(...)`).

### What Phantom never does

- It never sends extra packets beyond Fabric's normal channel registration, never alters movement or
  combat packets, and never spoofs rotations, ground state or anything else.
- It never hides itself: the client brand is the normal `fabric`, and `phantom:policy` is visible in
  the channel registration.
- It never tries to detect, evade, test or probe anti-cheat or server security systems. If a server or
  its anti-cheat objects to something, the answer is to switch it off, and the server can switch it off itself.

## Compatibility report

Run it yourself: `tools/compat-test.sh --accept-eula` (local servers, see [TESTING.md](TESTING.md)).

| Environment | How it was tested | Result |
|---|---|---|
| Singleplayer (1.21.1, flat creative) | in-game self-test | see TESTING.md |
| Private vanilla 1.21.1 server | `compat-test.sh`: join, default rules, GUI/HUD, 10 s stability, kick → Reconnect | see TESTING.md |
| Fabric 1.21.1 server + policy "plugin" | `compat-test.sh`: grants/denials, malformed + oversized payloads, per-server block, server switch | see TESTING.md |
| Vanilla 1.20.4 server (other version) | `compat-test.sh`: rejected by the server, clear message, no crash | see TESTING.md |
| Public online-mode servers | **not tested**: needs a Microsoft account and real servers | unknown |
| Paper / Spigot / Purpur with plugins, anti-cheats | **not tested**: Paper downloads were unreachable from the build environment | unknown |
| Velocity / BungeeCord proxies | **not tested** (same reason); switching logic is covered by the server-switch test | unknown |

### Unsupported

| | Why / workaround |
|---|---|
| Servers that only accept other Minecraft versions | Phantom is a 1.21.1 client. Servers with **ViaVersion** accept 1.21.1 clients. Client-side protocol translation (e.g. ViaFabricPlus) is untested with Phantom |
| Modded servers that require Forge/NeoForge client mods | a Fabric client cannot join them |
| Bedrock servers (or Java servers via Geyser from Bedrock) | Phantom is Java Edition only |
| Realms | not tested |
| Restricted modules on public servers | by design, only with the server's permission |
