<div align="center">

# Aleeve Atlas 袖里舆图

**A minimap and world map mod for Minecraft 1.21.11 featuring waypoints, entity radar, team map syncing, and quick pings.**

English | [简体中文](README.zh_CN.md)

</div>

## Overview

**Aleeve Atlas (`aleeve_atlas`)** is a NeoForge map mod for Minecraft 1.21.11 that provides both a customizable HUD minimap and an interactive fullscreen world map. Beyond standard terrain mapping, waypoints, and entity radar, it is built with **multiplayer co-op** in mind—allowing teammates to sync explored chunks in real time, share waypoints, and drop quick tactical pings using a middle-click radial wheel.

* **Singleplayer / Client-Only**: Works on any server out of the box for local terrain mapping, waypoints, and radar.
* **Client + Server**: Unlocks live team map syncing, waypoint sharing, and multiplayer quick pings.

## Requirements & Installation

**Base Environment**: Minecraft `1.21.11` + NeoForge `21.11.x`

This mod depends on **AnvilLib 2.x** (`2.0.0` or later), but this dependency is already embedded, so no separate installation is required.

## Features

* **Customizable Minimap**: Toggle between round or square frames, rotating or north-locked views, and custom zoom levels. Displays coordinates, biome, light level, and in-game time underneath.
* **Fullscreen World Map**: Pan and zoom smoothly, manage waypoints directly on the canvas, and access client settings straight from the map UI.
* **Player Indicator**: Choose an antialiased droplet or the vanilla player icon independently for each map, both with a black outline. Both default to the droplet. The minimap indicator scales with its panel; the world map indicator stays at a fixed screen size. The vanilla style supports resource packs.
* **Isolated Map Cache**: Automatically saves explored terrain. Map data is neatly separated by world, server, and dimension so caches never collide.
* **Entity & Player Radar**: Displays nearby mobs and player heads on the map, with configurable visibility rules per entity category.
* **Waypoints & Death Markers**: Supports in-world markers/beacons, automatic death waypoints, and chat commands for importing and exporting waypoint data.
* **Vanilla Map Banners**: Hold a filled map and select **Import map** on the world map to save its marked banners as persistent waypoints with their names, base colors, and exact positions. Reimporting preserves personal edits. Available in singleplayer or with a server that supports banner imports.
* **Quick Pings (Radial Wheel)**: Middle-click directly in the world or on the fullscreen map to drop temporary pings. Hold middle-click to open the radial wheel and choose between *Go To*, *Normal*, *Warning*, and *Danger* pings.
* **Team Map Sync**: When installed on the server, teammates automatically merge explored map regions and can share waypoints with one click. Optional [FTB Teams](https://github.com/FTBTeam/FTB-Teams) integration uses its team API; [FTB Chunks](https://github.com/FTBTeam/FTB-Chunks) claims appear on both maps through its client update events.
* **Mod Integration & API**: Built-in support for chunk claim overlays and a developer API for external markers.

## Default Controls

All keyboard bindings can be customized in Minecraft's standard `Options -> Controls -> Key Binds` menu:

| Input | Action |
| --- | --- |
| `M` | Open / close the fullscreen world map |
| `N` | Create a new waypoint at your current location |
| `=` / `-` | Zoom the minimap in / out |
| Hold `Z` | Temporarily center and enlarge the minimap (release to restore) |
| **Click** Middle Mouse | Drop a quick *Normal* ping at your crosshair or map cursor |
| **Hold** Middle Mouse | Open the ping radial wheel; release to send the selected ping type |

## Configuration & Data

* **Client Config**: Edit directly in-game via the settings button on the world map (`M`), or in `<game_dir>/config/aleeve_atlas-client.toml`.
* **Server Config**: Located at `<world_dir>/serverconfig/aleeve_atlas-server.toml`.
* **Map Cache & Waypoints**: Stored in `<game_dir>/data/aleeve_atlas/`. Explored map tiles are kept inside the `mapdata` subfolder, organized by save/server and dimension (back up this folder to keep your map progress when updating modpacks).

For detailed configuration options, check out the [Configuration & Controls Guide](docs/CONFIG.md).

## Documentation

* [Configuration & Controls Guide](docs/CONFIG.md)
* [Technical Docs & Developer API](docs/TECH.md)

## License

* **Source Code**: Licensed under `LGPL-3.0-only`. See [LICENSE](LICENSE) for details.
* **Assets** (Textures, models, etc.): All Rights Reserved. See [ASSETS_LICENSE](ASSETS_LICENSE) for details.
