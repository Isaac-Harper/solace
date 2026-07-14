# Solace playtest plan

The code is build-verified, boot-verified, and unit-tested (35 tests), but four
adversarial review rounds all agreed on one thing: mob AI behavior, the flight
feel, and the two-player flows can only be proven in game. This is the checklist.

## Setup

A ready-made arena world ships in `run/playtest` and the dev server is already
pointed at it. For everything (solo and two-player):

1. `./gradlew runServer`
2. `./gradlew runClient`, then Multiplayer, connect to `localhost`.
3. In the server console: `op <your dev player name>` (shown in the join log).
4. A second player joins the same way from another machine on the LAN, or with
   a second client instance.

The world is a flat plains void of structures, hard difficulty, locked at
night (`advance_time`/`advance_weather` off, `keep_inventory` on). Spawn is a
smooth-stone pad at `0 -60 0` with:

| Where | What |
|---|---|
| `4 -60 2` | Chest: spawn eggs (creeper, zombie, zombie villager, wolf, warden), golden apples, splash/lingering poison, splash weakness, bow and arrows, bones (wolf taming) |
| `4 -60 -2` | Chest: elytra, near-broken diamond pickaxe, torches, dirt, cobblestone, glass (untagged control), water bucket, TNT, flint and steel |
| `20..26, y=40` | Obsidian fall/flight tower, 101 blocks up (`/tp @s 23 41 0`) |
| `-20, z=0` | Fenced void pit through bedrock (jump in for the rescue test) |
| `z=20..36` | Glass mob pen: creepers, zombies, skeleton, zombified piglins, and one iron-armored zombie (all persistent), pre-spawned for section B and C |

- `/time set day` when a test needs daylight (the armored zombie burn check).
- Config lives at `run/config/solace.json`. `/solace reload` applies edits live.
- To reset the arena: stop the server, then
  `rm -rf run/playtest && cp -r run/playtest-backup run/playtest`.

Priorities: **P0** proves the core promise or guards a past crash. **P1** covers
the reworked machinery. **P2** is polish. Do P0 in one sitting before anything else.

## A. Core safety (P0)

- [ ] `/solace on`, stand on a creeper: explosion deals no damage, and with the
      default `hostile_explosions` mode it breaks no blocks. TNT still breaks blocks.
- [ ] Fall 50+ blocks: no damage.
- [ ] Swim in lava: no damage, no lingering fire.
- [ ] `/kill @s`: survives at full health (backstop).
- [ ] Void: in the End (or `/tp @s ~ -100 ~` in the overworld), fall below the
      world: warped to the respawn point within about a second, no death.
- [ ] `/solace preset survival+`, sprint and jump until hunger empties: hunger
      drains but starvation never damages.

## B. Mobs ignore (P0, heavily reworked in review round 4)

- [ ] Night, hard difficulty, stand among zombies and skeletons: none target,
      chase, or shoot.
- [ ] Punch a zombie (pacifist off): it dies eventually and never retaliates.
- [ ] With Solace OFF, aggro a zombie line, then `/solace on` mid-chase: all of
      them drop aggro immediately (enable-edge clear).
- [ ] Punch a zombified piglin in a group: no group anger toward you
      (`ANGRY_AT` + `canAttack` veto).
- [ ] Walk a bastion without gold armor: piglins ignore you (brain AI).
- [ ] Summon a warden (`/summon minecraft:warden`), make noise, hit it: no
      stalking, no roar aimed at you, no sonic boom (`WardenMixin`, new in round 4).
- [ ] Bring the second player near the same mobs WITHOUT Solace: mobs attack
      them normally. Safety must be strictly per-player.

## C. Past crash regression checks (P0)

- [ ] Equip a zombie with armor (`/summon minecraft:zombie ... ArmorItems`) and
      let a skeleton or the second player hit it, or let it burn in daylight
      wearing a helmet: server must not crash (durability mixin null-player path,
      the round-1 crash).
- [ ] Hand-edit `run/config/solace.json` to `"featureCaps": null`, run
      `/solace reload`, wait several seconds: no tick-loop crash, defaults restored.

## D. Creative-lite and the flight machine (P1, rebuilt in round 4)

- [ ] `/solace preset creative-lite`: haste applies (fast mining), reach is
      noticeably longer (~7 blocks), flight available (double-tap space).
- [ ] `/solace fly` twice mid-air: flight cuts out, slow falling icon appears,
      gentle landing, no damage; the icon disappears promptly after landing.
- [ ] Fly high, `/solace off`: same gentle descent even though Solace (and
      immunity) is off. This is the disable-mid-flight death case.
- [ ] Fly up, double-tap space to STOP flying, and while falling normally run
      `/solace off`: still gets slow falling (revoke keyed on airborne, not gliding).
- [ ] Revoke flight over deep water: lands in water fine, no residual floatiness
      after swimming out.
- [ ] Revoke flight high up, open an elytra mid-fall: the glide is NOT slowed
      (suspension), and after closing the elytra mid-air slow falling resumes
      until touching ground.
- [ ] Fly, quit the world, reopen it, `/solace off`: flight is actually revoked
      (marker persistence across restart, the round-3/4 leak).
- [ ] Grab a half-damaged pickaxe from creative, equip it with Solace
      creative-lite on: repaired within a second; mine with it: durability frozen.
- [ ] Place dirt/cobblestone/torches: stack counts never drop, including placing
      the LAST item in a stack (round-3 fix). Glass or other untagged blocks are
      consumed normally.

## E. Teleport and home (P1)

- [ ] `/solace tp <player>`: works; immediate retry says cooldown with a
      sensible remaining time; works again after ~30s.
- [ ] `/solace tp` with Solace off: refused with "Enable it first".
- [ ] Op: `/solace home set` at a base; `/solace home tp` from far away and
      from the nether (cross-dimension on).
- [ ] Set `baseProtection.mode` to `home_region`, reload: creeper inside the
      radius breaks nothing; creeper far outside breaks blocks normally.

## F. Two players: slot and pacifist (P0 for pacifist, P1 for slot)

Config: `allowMultiplePlayers: false`, `pacifist: true`, reload.

- [ ] A has Solace on; B runs `/solace on`: refused, names A.
- [ ] A logs out; B retries: refused ("held by an offline player"). Op runs
      `/solace admin slot`: shows the holder. `/solace admin slot clear`: B can
      now enable. A rejoins: A is disabled with a message within a second.
- [ ] Pacifist, A = Solace player: A punches B: no damage. A shoots B with a
      bow: no damage. A splash-poisons B: no effect. A throws a LINGERING poison
      at B and B stands in the cloud: no effect (round-4 fix). A's tamed wolf is
      sicced on B: no damage.
- [ ] Pacifist curing still works: A throws splash Weakness at a zombie villager
      and feeds a golden apple: the cure starts (round-4 exemption).
- [ ] Known gap check (should FAIL to protect, by design): A places lava under
      B: it hurts B. Confirm the SPEC note matches reality.

## G. Config surface (P2)

- [ ] Mod Menu: Solace entry shows the heart icon (no "broken icon" warning in
      the log) and the config screen opens with General, Teleport, Base
      protection, and Feature caps categories.
- [ ] The Default-preset dropdown reads "Survival+ / Comfort / Creative-Lite"
      and the Base-protection Mode dropdown reads "Hostile explosions / All
      explosions / Home region / Off" (readable names, not raw ids).

### My Solace player panel (new)

- [ ] In a world, the screen opens with a "My Solace" category. Its toggles
      match your current state: run `/solace on` and `/solace preset comfort`,
      reopen the screen, and Solace-enabled + Comfort features show as on.
- [ ] In the panel, flip Solace on, pick creative-lite, toggle a feature, then
      Save: the matching `/solace` commands run (chat confirmations appear), the
      state actually changes, and reopening the panel reflects it.
- [ ] Untouched toggles send no command: change only the preset, Save, and no
      per-feature `/solace set` spam appears; features follow the new preset.
- [ ] On the dev `runServer` (remote, not host): the My Solace panel still works
      (toggles show your synced state, Save dispatches commands and applies), while
      the server-config side shows the "Configured on the server" notice.
- [ ] With `allowSelfService: false` and you as a non-op: panel Saves are refused
      by the server with the usual message (no client-side bypass).
- [ ] Open the screen from the title screen (no world): only the server-config
      editor shows, no My Solace category.
- [ ] While a Solace player is flying, uncheck the Flight cap in the screen and
      save: their flight revokes within a second, with the gentle descent.
- [ ] Set `allowSelfService: false`, `/solace reload`: a non-op immediately
      loses `/solace on` from tab completion without relogging; ops keep it.
- [ ] Fresh player on a server with `defaultPreset: "survival+"`:
      `/solace status` shows "survival+ (server default)"; `/solace off` first,
      then `/solace on`: still receives survival+ (pristine preserved);
      `/solace on` twice: second says "already on".

## H. Edge (P2)

- [ ] Hardcore world: `/kill` a Solace player: survives, no spectator ban
      (intentional per SPEC).
- [ ] Phantoms: pin `/time add` through 3+ sleepless nights with Solace on: no
      phantoms spawn for the Solace player; they still spawn for the second
      player if sleepless.

## Recording results

Note failures with the exact commands used and the log tail
(`run/logs/latest.log`). Anything that fails here goes back through a targeted
fix, not another blind review round.
