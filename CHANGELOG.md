# Changelog

The `## <version>` section matching `mod_version` becomes the Modrinth changelog for that release
(see the `publishMods` block in `build.gradle`).

## 1.0.1

- Supports Minecraft 26.3.
- Fix explosion base protection, which never stopped blocks from breaking. Protected explosions (creepers, ghasts, withers by default; every explosion under `all_explosions`; anything inside the home radius under `home_region`) now leave blocks intact and start no fires, while still hurting and knocking back entities as usual.
- Wind charges flip buttons, levers, doors and trapdoors as in vanilla under every protection mode.
- Protected explosions also leave item frames, paintings, leash knots, armor stands and dropped items intact.
- Ghast fireballs and wither skulls count as hostile under `hostile_explosions` even after their shooter dies or unloads mid-flight. Fireballs deflected by a player stay unprotected.

## 1.0.0

Initial release, for Minecraft 26.2 (Fabric).

- Per-player Solace state: damage immunity, hostile mobs ignore you (including the Warden), no phantoms, can't-die backstop with void rescue.
- Three presets (Survival+, Comfort, Creative-Lite) with every feature also an independent per-player override via `/solace set`.
- Comforts: no hunger, underground night vision, infinite torches.
- Creative-lite: toggleable flight, reach boost, faster mining, no tool durability, infinite basic blocks (`solace:infinite_blocks` / `solace:infinite_torches` item tags).
- Explosion base protection with hostile-only, all-explosions, home-region, and off modes.
- `/solace tp <player>` and a shared `/solace home` warp, both with a cooldown.
- Optional pacifist mode and a single-player Solace slot (`allowMultiplePlayers: false`).
- Server-side JSON config with `/solace reload`, permission nodes (`solace:use`, `solace:admin`) with op fallback, and a Mod Menu + Cloth Config screen covering every setting.
