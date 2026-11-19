# Modules

**PRIVATE**: runs only in singleplayer or a LAN world you host. It is refused and switched off on remote
servers. Combat modules target **training dummies (armor stands) and mobs only**, never players.

## Combat

| Module | Private | What it does | Key settings |
|---|---|---|---|
| **KillAura** | yes | Combat testing: attacks dummies/mobs in range, optionally turning the camera to them | Range (1–6 m), Priority (Distance/Health/Angle), Full Cooldown or CPS, Look At Target, Line Of Sight, target types |
| **AimBot** | yes | Aim training: smooth assist toward targets inside an FOV cone, with an on-target percentage in the module list | Range, FOV, Speed (°/s), Only While Attacking, Training Stats |
| **Criticals** | yes | Shows CRIT under the crosshair when vanilla's critical-hit conditions are met and counts crits/hits. Jump mode hops while attacking | Mode (Indicator/Jump), Crosshair Indicator |
| **Reach** | no | Visualises your real attack range (1.21 entity-interaction attribute) as a ring and outlines what you can hit. Does not change range | Ring/In-range colours, Block Range ring |
| **TargetHUD** | no | Enables the Target Info HUD element (face, health bar, distance, armor) | Armor, Distance, Player Head |

## Visuals

| Module | What it does | Key settings |
|---|---|---|
| **ESP Colors** | Shared custom colours per entity type (players, hostile, passive, dummies, items, other) used by all ESP-style modules | one colour picker each |
| **ESP** | Boxes around entities through walls | Mode (Outline/Filled/Both), Range, Fill Opacity, Line Width, types |
| **StorageESP** | Chests, ender chests, barrels, shulkers, furnaces, hoppers/droppers | per-type toggle + colour, chunk range |
| **BlockESP** | Any blocks by id (default: diamond ores, ancient debris) | Blocks list, Radius, Chunks/Tick budget, Max Blocks, Color |
| **Tracers** | Lines from the crosshair to entities | Range, Distance Fade, Body Line, types |
| **Nametags** | Clean tags with health and distance | Range, Scale, Health, Distance, types |
| **Highlight** | Entity highlighting with vanilla's glowing outline, coloured by type | Range, types |
| **DistanceIndicators** | Distance labels, plus screen-edge markers for off-screen entities | Range, Edge Markers, types |
| **Fullbright** | Raises gamma; restores the old value on disable/exit | Brightness |
| **Freecam** | Detached camera (client-side only; your body stays put) | Speed (sprint = ×2) |

## Movement (all PRIVATE)

| Module | What it does | Key settings |
|---|---|---|
| **Fly** | Abilities mode grants creative-style flight on the integrated server; Velocity mode drives you directly | Mode, Speed, Safe Landing |
| **Speed** | Faster ground (or air-strafe) movement | Mode (Ground/Strafe), Multiplier |
| **BunnyHop** | Auto-jump while moving, optional air boost | Air Boost |
| **Spider** | Climb walls when walking into them | Climb Speed |
| **NoSlow** | No slowdown while eating, drinking, blocking or drawing a bow | — |

## Player

| Module | What it does |
|---|---|
| **AutoSprint** | Sprints while moving forward (vanilla sprint rules) |
| **AutoTool** | Picks the fastest hotbar tool while mining, optionally switching back |

## Misc

| Module | Default key | What it does |
|---|---|---|
| **ClickGUI** | Right Shift | Opens the GUI; holds Theme, Animation Speed, Corner Radius, Background Dim, Descriptions |
| **HUD** | — | HUD on/off, Color Mode (Theme/Static/Rainbow), text colour, background opacity, shadow |
| **Notifications** | — | Toggle toasts and duration (warnings always show) |
| **Zoom** | C (hold) | Smooth zoom with optional cinematic camera |

## ClickGUI controls

| Action | Input |
|---|---|
| Open / close | Right Shift (rebindable), Esc, or click outside |
| Search | Start typing, or Ctrl+F |
| Expand module settings | Left-click the card |
| Toggle module | Click the switch, or right-click the card |
| Bind key | Middle-click the card, or the Keybind button. Esc/Delete/Backspace clears it |
| Reset a slider / colour | Right-click it |
| Cycle a dropdown | Right-click it |
| HUD editor | Sidebar → HUD Editor: drag (snaps to edges/centre), wheel = scale, right-click = hide, select = settings panel |
