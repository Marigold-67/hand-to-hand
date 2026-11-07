# Hand in Hand

A Minecraft mod that lets two players hold hands and move together. Built for multiplayer roleplay, with clickable chat requests instead of a custom consent screen.

**Current version:** 0.1.0-beta.1 (first beta; gameplay implementation carried over unchanged from alpha.10)

## Features

- Animated hand-holding visible to nearby players, including movement and changes in body direction.
- Consent-based requests sent through Minecraft chat with clickable **Accept** and **Decline** options.
- Coordinated walking and sprinting, with either participant able to lead.
- Side-by-side following across ordinary terrain, steps, hills and jumps, with smoothing for the passive player's movement and view.
- Support for different player sizes, including Pehkui scaling, within the tested range.
- Optional, administrator-controlled roleplay grabbing. This is separate from normal consensual hand-holding.
- Configurable keybinds, server settings and a command to opt out of requests.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft Java Edition | 1.21.1 |
| Mod loader | NeoForge 21.1.250 or compatible 21.1.x version |
| Java | 21 |
| Player Animation Library (PAL) | 1.1.6 for Minecraft 1.21.1 (NeoForge) |

**Install both Hand in Hand and PAL on the server and every participating client.** PAL is a separate required dependency; it is not bundled in this mod.

After updating, restart the server and clients. The first beta retains alpha.10's network protocol (protocol 10); older alpha versions are not compatible with this build.

## Basic controls

| Action | Default |
| --- | --- |
| Ask a nearby player to hold hands | Look at the player and press **H** |
| Accept or decline | Click **Accept** or **Decline** in the request message in chat |
| Release hands | Press **H**, or use `/handholding release` |
| Roleplay grab (if permitted) | Look at the player and press **G** |
| Change keys | Minecraft Options > Controls > Hand in Hand |

Normal hand-holding always requires acceptance. Once connected, players can look around, change who leads, steer and jump. Sneaking does not automatically release the connection. Either participant can release at any time.

Other available player commands include `/handholding ask <player>`, `/handholding cancel`, `/handholding toggle` (opt out of requests), and `/handholding status`.

## Optional roleplay grabbing

This is a separate roleplay feature, **disabled by default**. Unlike an ordinary request, a grab starts without the target accepting a chat prompt and may restrict that player's independent movement until release. It should only be enabled on servers where that interaction is appropriate and understood by players.

Administrators can enable operator-only grabbing using:

```mcfunction
/handholding config grab true
```

This command also enables the movement-following setting required for grabbing. Initiating a grab requires server permission level 2 by default; the target does not need operator permissions.

To allow all players to initiate roleplay grabs, an administrator can also run:

```mcfunction
/handholding config grabOpOnly false
```

To disable grabbing or restore operator-only initiation:

```mcfunction
/handholding config grab false
/handholding config grabOpOnly true
```

More configuration commands:

```mcfunction
/handholding config
/handholding config enabled true
/handholding config movementTether true
```

`/handholding config` displays the effective settings and permission information. The configuration is saved by NeoForge in `handinhand-server.toml` (the exact config directory can depend on the server setup). Disabling grabs does **not** disable ordinary consent-based holding. Either participant can press **H** or run `/handholding release` to disconnect.

## Known limitations

- Contact and movement may look imperfect during quick turns, jumping, network delay or interference from other animation/model mods.
- First-person held-hand rendering is not implemented; other players can see the animated pose in third person.
- Elytra gliding, swimming/crawling, vehicles and several incompatible player states interrupt the connection rather than supporting continued hand-holding.
- Extreme size differences, unusual movement mods, high latency and unfamiliar modpacks have not all been verified.
- This beta is based on the tested alpha.10 gameplay code. The beta version bump itself changes **metadata and documentation only**; it has not received a separate full in-game regression run.

Please report reproducible issues with the Minecraft/NeoForge/PAL versions and the steps needed to trigger the problem.

## Building from source

Use JDK 21. On Windows run `gradlew.bat build`; on Linux/macOS run `./gradlew build`. Gradle retrieves NeoForge and PAL from their configured repositories.

To compile against an existing local PAL JAR instead, add a project property such as `-PlocalPal=/path/to/PlayerAnimationLibNeo-1.1.6+mc.1.21.1.jar`.

The project's unit tests can be run with `gradlew.bat test` or `./gradlew test`. The alpha.10 development archive records 77 passing JUnit tests and additional targeted integration checks. An entirely clean dependency-resolution Gradle build and a separate beta-specific Minecraft regression test have **not** been verified in this packaging pass.

The runtime animation is procedural; editable reference material is located in `animation_source/`.

## Credits and licensing

Copyright (c) 2026 Marigold.

Hand in Hand is licensed under **GNU GPLv3 only** (`GPL-3.0-only`). See [LICENSE](LICENSE) for the complete terms. GPLv3 permits commercial redistribution provided its conditions are followed, including corresponding-source obligations.

Player Animation Library by ZigyTheBird and contributors is a separate dependency licensed under MIT. The Gradle wrapper is licensed under Apache-2.0. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and `LICENSES/` for attribution and license details.

This is an independent community mod and is not affiliated with Mojang or Microsoft.
