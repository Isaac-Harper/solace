# Solace — Technical Spec

A Fabric mod for **Minecraft 26.1** that gives any individual player a configurable, opt-in **"safe survival"** state on a shared world — a middleground between Survival and Creative — without changing world difficulty for anyone else.

---

## 1. Goal & guiding principle

Two (or more) people play one survival world. One wants full vanilla survival; another doesn't enjoy combat, damage, or threat but still wants the survival loop (gather, build, craft, progress). Vanilla can't express this because **difficulty is world-level**, while only **game mode** is per-player. Solace bridges the gap: it keeps the player in Survival game mode but layers per-player protections and conveniences on top, server-authoritative, leaving everyone else's experience untouched.

**Server-authoritative.** All gameplay logic runs on the server/host. The partner doesn't strictly need the mod to benefit. The jar is *also* a client mod so it can expose a **native Mod Menu config screen** for whoever hosts.

---

## 2. Target environment & toolchain

26.1 is the first **fully unobfuscated** Java release: no Yarn, no Intermediary — Minecraft ships with official Mojang names and parameter names, and Loom no longer remaps.

| Item | Value |
|---|---|
| Minecraft | 26.1.2 |
| Java | **25** (required) |
| Gradle | 9.5.1 (installed) |
| Loom plugin | `net.fabricmc.fabric-loom` `1.16-SNAPSHOT` (non-remapping) |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.150.0+26.1.2 |
| Mappings | Mojang official (no `mappings` line, no Yarn) |
| Mod Menu | native integration (client config screen) |

**Build consequences of unobfuscation:** plain `implementation`/`compileOnly` (not `modImplementation`), `jar` (not `remapJar`), no `mappings` dependency, access-widener header `official`. In-code Minecraft classes use Mojang names (`Mob`, `Player`, `ServerPlayer`, `Level`, `ServerLevel`, `ItemStack`, `Abilities`, `FoodData`, `Explosion`, …).

> **No JDK installed locally.** The build provisions JDK 25 automatically via Gradle Java toolchains + the foojay resolver (`settings.gradle`).

---

## 3. Architecture — state model

Per-player, persisted with the world (server-authoritative):

```
SolaceState {
  enabled: boolean
  preset:  SURVIVAL_PLUS | COMFORT | CREATIVE_LITE
  overrides: Map<Feature, boolean>   // per-player fine-tuning over the preset
}
```

**Effective feature value** resolves as: `preset default` → `per-player override` → `server cap` (admin master-switch). Stored via the **Fabric Attachment API** on the player (survives relog/death); falls back to UUID-keyed `SavedData` if needed.

---

## 4. Features

### Baseline — always on when Solace is enabled
- **Damage immunity** — no damage from any source (mobs, fall, fire/lava, drowning, suffocation, cactus, starvation)
- **Mobs ignore her** — hostiles never target/aggro her (per-player; they still hunt other players)
- **Creeper/explosion base protection**
- **Teleport to a player** (`/solace tp`)
- **No phantoms / insomnia** (per-player)
- **Can't-die backstop**

### The dial — what each preset stacks on top

| Feature | Survival+ | Comfort *(default)* | Creative-Lite |
|---|:--:|:--:|:--:|
| Real mining / crafting / gathering | ✅ | ✅ | ✅ |
| Tool durability required | ✅ | ✅ | optional off |
| Hunger | drains (harmless) | off | off |
| Underground night vision | — | ✅ | ✅ |
| Infinite torches | — | ✅ | ✅ |
| Optional flight (toggle) | — | — | ✅ |
| Reach boost | — | — | ✅ |
| Faster mining | — | — | ✅ |
| Infinite basic blocks (dirt/stone/wood) | — | — | ✅ |

Presets are starting points; any row is an independent flag via `/solace set`.

---

## 5. Commands (Brigadier, root `/solace`)

```
/solace on | off | status
/solace preset survival+ | comfort | creative-lite
/solace set <feature> on | off          # per-player override
/solace fly                             # toggle flight (if cap allows)
/solace tp <player>                     # warp to a player (cooldown)
/solace home set | tp                   # optional personal warp
/solace admin <player> <on|off|preset…> # op-only: manage others
/solace reload                          # op-only: reload config
```

**Permissions:** ops always; non-ops gated by `allowSelfService` (self only). Honor `solace.use` / `solace.admin` nodes if a permissions mod is present.

---

## 6. Config (`config/solace.json`, server-side)

```json
{
  "defaultPreset": "comfort",
  "allowSelfService": true,
  "allowMultiplePlayers": true,
  "pacifist": false,
  "teleport": { "enabled": true, "cooldownSeconds": 30, "crossDimension": true },
  "baseProtection": { "mode": "hostile_explosions", "homeRadius": 64 },
  "featureCaps": {
    "flight": true, "reachBoost": true, "miningBoost": true,
    "infiniteBasicBlocks": true, "noDurability": true
  },
  "infiniteBlockTag": "#solace:infinite"
}
```

- `pacifist` *(default false)* — when true, Solace players can't damage mobs/players.
- `baseProtection.mode` — `hostile_explosions` *(default: creeper/ghast/wither don't break blocks; TNT/beds still work)* · `all_explosions` · `home_region` · `off`.
- `featureCaps` — admin master-switches that override any preset/override.
- Per-player enabled/preset/overrides live on the player attachment, **not** here.

---

## 7. Technical mapping (mechanism → 26.1 API; exact identifiers confirmed at build)

- **Damage immunity** → `ServerLivingEntityEvents.ALLOW_DAMAGE` → false for Solace players (no mixin)
- **Can't-die** → `ServerPlayerEvents.ALLOW_DEATH` → cancel, full-heal, extinguish, lift out of void
- **Mobs ignore her** → mixin on `Mob#setTarget(LivingEntity)` → drop Solace targets; clear nearby targets on enable; handle `NeutralMob` anger
- **No phantoms** → reset the `time_since_rest` stat each tick for Solace players (per-player; others' phantoms intact)
- **Explosion protection** → mixin the explosion block-destruction step → skip blocks for hostile explosions (or within home region)
- **Teleport** → `ServerPlayer#teleportTo` to the target's level+pos, cross-dimension, per-player cooldown
- **Hunger off** → top up `FoodData` each tick (Survival+ relies on damage immunity for starvation)
- **Night vision / mining speed** → hidden infinite mob effects (`night_vision` / `haste`)
- **Flight** → `Abilities.mayFly = true` + sync; restore with fall-safety on disable
- **Reach** → attribute modifiers on `block_interaction_range` / `entity_interaction_range`
- **Infinite torches / basic blocks** → cancel stack shrink on place for items in `#solace:infinite`
- **No durability (CL opt)** → mixin `ItemStack#hurtAndBreak` → no-op for Solace holder
- **Per-player state** → Fabric Attachment API

---

## 8. Shared-world rules

- Hostiles still target and damage **other players** normally — safety is strictly per-player; no free meat-shield.
- Solace players are immune to **player** damage too, so accidental hits never hurt them.
- `pacifist` controls whether they can deal damage (help in fights / accidental griefing).
- Explosion protection guards the shared base from creepers regardless of who's nearby.

---

## 9. Edge cases

Void / `/kill` (backstop heals + repositions) · enabling mid-fight (clear existing aggro) · disabling mid-flight (fall-safety) · **hardcore worlds** (backstop prevents the death-ban for Solace players — intentional) · multiple Solace players · performance (per-tick work iterates only the small Solace set).

---

## 10. Mod Menu integration (native)

- Client entrypoint implements `ModMenuApi` and returns a config screen.
- Screen edits the server config; when the player **is** the host (singleplayer / LAN host), edits apply live. On a dedicated server, config is authored via the JSON file or `/solace` commands (the screen reflects/edit-requests where supported).
- Built alongside the config system (M4–M5), not bolted on at the end.

---

## 11. Project structure

```
solace/
├─ build.gradle  settings.gradle  gradle.properties
├─ .gitignore  LICENSE  README.md  SPEC.md
└─ src/
   ├─ main/
   │  ├─ java/net/solace/
   │  │  ├─ Solace.java              # init: attachment + command registration
   │  │  ├─ SolaceState.java         # per-player attachment data
   │  │  ├─ Preset.java  Feature.java
   │  │  ├─ config/SolaceConfig.java
   │  │  ├─ command/SolaceCommand.java
   │  │  ├─ event/{DamageHandler,DeathHandler,TickHandler}.java
   │  │  └─ mixin/{MobMixin,ExplosionMixin,ItemStackMixin}.java
   │  └─ resources/
   │     ├─ fabric.mod.json  solace.mixins.json
   │     └─ assets/solace/lang/en_us.json
   └─ client/
      ├─ java/net/solace/client/
      │  ├─ SolaceClient.java
      │  └─ SolaceModMenu.java        # ModMenuApi entrypoint + config screen
      └─ resources/solace.client.mixins.json
```

---

## 12. Milestones (incremental, each testable)

- **M0 — Scaffold** *(done)*: builds & loads in 26.1, logs init.
- **M1 — State** *(done)*: per-player attachment + `/solace on|off|status` + persistence.
- **M2 — Core safety** *(done)*: damage immunity + can't-die backstop.
- **M3 — Mobs ignore** *(done)*: `Mob#setTarget` mixin.
- **M4 — Config + presets** *(done)*: `config/solace.json`, presets, `/solace preset`/`set`/`reload`, feature caps, permission gating.
- **M5 — Native Mod Menu** *(done)*: Mod Menu + Cloth Config screen (client-only deps) over the M4 config.
- **M6 — Comfort** *(done)*: no phantoms, no hunger, night vision, infinite torches. (Easier taming was dropped from scope.)
- **M7 — Creative-Lite** *(done)*: flight, reach, faster mining, no-durability, and infinite blocks/torches (item-tag consumption mixin).
- **M8 — Base protection + teleport** *(done)*: explosion base protection (`canTriggerBlocks` mixin) with `hostile_explosions`/`all_explosions`/`home_region`/`off` modes, `/solace tp` + cooldown, `/solace home set|clear`, `/solace admin <player>`.

---

## 13. Decisions (locked)

| Decision | Value |
|---|---|
| Name | **Solace** (id `solace`, `/solace`) — verified free on Modrinth |
| Java package | `net.solace` |
| Default preset | Comfort |
| Base protection | `hostile_explosions` |
| Pacifist | off |
| Mod Menu | native (built-in client screen) |
| Authority | server-side; multiple Solace players allowed |
| License | MIT |
