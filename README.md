# Solace

[![Modrinth](https://img.shields.io/modrinth/v/solace-mod?logo=modrinth&color=00AF5C&label=Modrinth)](https://modrinth.com/mod/solace-mod)
[![Downloads](https://img.shields.io/modrinth/dt/solace-mod?logo=modrinth&color=00AF5C&label=downloads)](https://modrinth.com/mod/solace-mod)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A per-player middleground between Survival and Creative, for Fabric on Minecraft 26.2.

One player opts into a safe, no-combat "Solace" state (immune to harm, ignored by hostile
mobs, with a dial from pure safe-survival up to creative-lite) while everyone else keeps
full vanilla survival on the same world. World difficulty is never changed, and nothing
about the world itself is touched: Solace lives entirely on the player.

Built for mixed-comfort servers: one person wants to build, garden, and explore without
ever being chased off a cliff by a creeper, and everyone else wants survival to stay
survival. Solace gives that one player a bubble instead of giving the whole server
peaceful mode.

![A Solace player at dusk, a spider brushing past and a creeper in the treeline, none of them caring](https://cdn.modrinth.com/data/6bhLd12e/images/96c878370ad0c2c7798650d5194a33f3fd528ae1.png)

![A Solace player standing in a lava pool, burning and completely fine](https://cdn.modrinth.com/data/6bhLd12e/images/67e93815a898cd5b37a73a604e753a3018b22a8a.png)

## What Solace players get

Always on while Solace is enabled:

- **Damage immunity** from every source: mobs, falls, fire and lava, drowning, suffocation, starvation
- **Hostile mobs ignore them** entirely (per player; mobs still hunt everyone else, including the Warden's sniffing and anger)
- **No phantoms**, ever, regardless of sleep
- **Explosion base protection**: creepers and other hostile explosions can't break blocks (configurable: hostile only, all explosions, a protected home region, or off)
- **A can't-die backstop** with void rescue, in case something slips through
- **`/solace tp <player>`** to warp to a friend, and a shared **`/solace home`** warp

## The dial

Three presets stack quality-of-life on top of that baseline. Every row is also an
independent per-player flag via `/solace set`, so a preset is just a starting point.

| Feature | Survival+ | Comfort *(default)* | Creative-Lite |
|---|:--:|:--:|:--:|
| Real mining / crafting / gathering | ✅ | ✅ | ✅ |
| Tool durability required | ✅ | ✅ | optional off |
| Hunger | drains (harmless) | off | off |
| Underground night vision | no | ✅ | ✅ |
| Infinite torches | no | ✅ | ✅ |
| Optional flight (`/solace fly`) | no | no | ✅ |
| Reach boost | no | no | ✅ |
| Faster mining | no | no | ✅ |
| Infinite basic blocks | no | no | ✅ |

Infinite blocks and torches are driven by the `solace:infinite_blocks` and
`solace:infinite_torches` item tags, so datapacks can retune what counts as "basic."

## Commands

```
/solace on | off | status
/solace preset survival+ | comfort | creative-lite
/solace set <feature> on | off          # per-player override
/solace fly                             # toggle flight (if the cap allows)
/solace tp <player>                     # warp to a player (cooldown)
/solace home set | clear | tp           # shared home warp (set/clear are op-only)
/solace admin <player> ...              # op-only: manage other players
/solace reload                          # op-only: reload config
```

Ops can always use everything; non-ops manage themselves when `allowSelfService` is on.
The `solace:use` and `solace:admin` permission nodes are honored when a Fabric
permissions mod is present, falling back to op level 2.

## Server-side config

Everything lives in `config/solace.json` on the server (editable in-game via Mod Menu +
Cloth Config on the client, or with a text editor and `/solace reload`):

- **`defaultPreset`**: which preset new Solace players start on
- **`allowMultiplePlayers`**: limit Solace to a single slot, held persistently even while the holder is offline
- **`pacifist`**: Solace players can't hurt mobs or players (directly, with projectiles, via pets, or with harmful potions)
- **`teleport`**: enable/disable, cooldown, cross-dimension
- **`baseProtection`**: `hostile_explosions` / `all_explosions` / `home_region` / `off`
- **`featureCaps`**: admin master switches for flight, reach, mining speed, no-durability, and infinite blocks that override any preset or personal override

## License

[MIT](LICENSE).
