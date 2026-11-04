<div align="center">

<img src="src/main/resources/pack.png" width="256" height="256" alt="Aleeve Atlas icon">

# Aleeve Atlas 袖里舆图

**A Minecraft minimap with entity radar and local waypoints.**

English | [简体中文](README.zh_CN.md)

</div>

## Overview

Aleeve Atlas is a client-side minimap mod for Minecraft 26.1.2, with terrain rendering, entity radar, and local waypoints.

## Requirements and Installation

- Minecraft 26.1.2, NeoForge 26.1.2.36-beta, and Java 25.
- Install the main JAR from `build/libs/` in the client's `.minecraft/mods/` directory. AnvilLib modules are bundled in the JAR.

## Features

- A configurable minimap with shape, rotation, zoom, position, and size settings.
- Optional lighting, cave mapping, coordinates, and environment information.
- Radar filters for hostile mobs, friendly mobs, dropped items, and players.
- Local waypoint storage and in-world waypoint rendering.

## Controls and Data

Press `M` to open settings. In development runs, `B` opens the waypoint list and `N` opens quick waypoint creation. These two waypoint hotkeys are registered only in development environments on this branch.

Waypoints are stored in `<game_dir>/config/aleeve_atlas-waypoints.json`. Back up this file to preserve them. See the [configuration and resource guide](docs/CONFIG.md) for configuration entry points and icon details.

## Building from Source

Use JDK 25 and the included Gradle wrapper: `./gradlew build` on Linux/macOS or `gradlew.bat build` on Windows. Build artifacts are written to `build/libs/`.

Run `./gradlew runClient` for the development client and `./gradlew runData` for data generation.

## License

- Source code: [GNU LGPL 3.0](LICENSE).
- Assets: [All Rights Reserved](ASSETS_LICENSE).
