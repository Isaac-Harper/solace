# Solace

A configurable, **per-player middleground between Survival and Creative** for Minecraft **26.1** (Fabric).

One player can opt into a safe, no-combat "Solace" state — immune to harm, ignored by hostile mobs, with a dial from pure safe-survival up to creative-lite — while everyone else keeps full vanilla survival **on the same world**. World difficulty is never changed.

## Status

**M1–M8 — feature-complete (build & boot-verified on a 26.1 dev server).** Working: per-player toggle / presets / overrides, damage immunity, can't-die, mobs-ignore, no-phantoms, creeper/explosion base-protection, `/solace tp`, comforts (no hunger, night vision), creative-lite (flight, reach, faster mining, unbreakable gear), and a Mod Menu config screen. Deferred niceties: infinite blocks/torches, easier taming, home-region protection. See [SPEC.md](SPEC.md). The gameplay still wants a real in-game test pass with a player.

## Requirements

- Minecraft **26.1.2** (Fabric)
- Fabric Loader **≥ 0.19.3** + Fabric API
- **JDK 25** (e.g. `brew install openjdk@25`). `gradlew` needs a JDK to launch it — set `JAVA_HOME` to it or put it on your `PATH`.

## Build

If JDK 25 isn't on your `PATH`, point Gradle at it first:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Then build:

```sh
./gradlew build
```

Output jar: `build/libs/solace-<version>.jar`.

## Dev runs

```sh
./gradlew runClient   # launch a dev client
./gradlew runServer   # launch a dev dedicated server
```

## License

[MIT](LICENSE).
