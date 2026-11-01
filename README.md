# WorldGuard for NeoForge 26.2

An unofficial NeoForge port of WorldGuard, maintained here for OKOCRAFT.
Forked from [xcrowfeathers/WorldGuard-NeoForge](https://github.com/xcrowfeathers/WorldGuard-NeoForge).
The region and flag engine is the unmodified [EngineHub WorldGuard Core](https://github.com/EngineHub/WorldGuard).

## Requirements

- Minecraft 26.2, Java 25
- NeoForge 26.2.0.88
- [WorldEdit 7.4.5 for NeoForge / Minecraft 26.2](https://modrinth.com/plugin/worldedit/version/7.4.5)
- Optional: LuckPerms for NeoForge

WorldGuard Core 7.0.19 is bundled. WorldEdit is installed separately in the server's mods folder.
This is a server mod; players do not need to install WorldGuard.

## Configuration and permissions

WorldGuard stores its configuration and per-dimension regions under the active world's
worldguard directory. Dimension IDs use reversible file names:
minecraft:overworld becomes minecraft~overworld, and slashes become ~s.
Each dimension has separate configuration and region storage.

Use WorldEdit's selection tools, then WorldGuard commands such as /rg define, /rg flag and /rg info.
From the console, specify a dimension with -w minecraft:overworld.
Commands and region flags follow the WorldGuard Core syntax.

When LuckPerms is installed, permission nodes and inherited groups are resolved with
the player's active LuckPerms contexts. Explicit denials override operator status.
Unset permissions fall back to operator status only when the world's op-permissions
setting permits it. Without LuckPerms, op-permissions controls operator access.
Other mods may install a custom RegionPermissionResolver through the platform API.

## Build and verification

Run ./gradlew build with Java 25. The build uses the Gradle wrapper.
The distribution jar is created in build/libs.

JUnit tests cover claim boundaries, member build permissions, explicit block-break denial,
flag priorities, live region changes and reading/saving existing YAML region data.
src/test/node/server-smoke.mjs installs a fresh NeoForge server under build/smoke,
loads the packaged WorldGuard and WorldEdit mods, and exercises console region flags
and persistence through a restart. It also runs the test-only event fixture for
owner/member/outsider break, placement and chest-use decisions. It also loads LuckPerms 5.5.85 against a separate local database and verifies explicit
permission denial, inherited permissions/groups and group-based region membership.
It binds only to loopback and never uses a production world.

## Compatibility limits

Protection depends on the vanilla/NeoForge events and the Minecraft hooks covered by this port.
Mods can change blocks, inventories or entities through other paths.
DQM spells, custom explosions, area mining and custom dimensions require gameplay testing.
A successful build or server startup does not establish protection for those operations.
WorldGuard is protection software; it does not provide CoreProtect-style change history or rollback.

## License and credits

Licensed under LGPL-3.0-or-later; see LICENSE.
EngineHub created WorldGuard and WorldEdit. Kuro created the original NeoForge port.
