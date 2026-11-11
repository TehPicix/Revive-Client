# revive-client

Reconstructed, buildable source of the **Revive client**, a fork of
[Meteor Client](https://github.com/MeteorDevelopment/meteor-client) for Minecraft 1.21.11,
recovered from the decompiled client jar and remapped to Yarn mappings.

Part of the Revive teardown; see the [repo root `README.md`](../../README.md) and [`02. Teardown Analysis`](../../02.%20Teardown%20Analysis) for context, in particular [`CLIENT-TEARDOWN.md`](../../02.%20Teardown%20Analysis/CLIENT-TEARDOWN.md).

## Stack

- **Fabric** mod, built with **Fabric Loom**. Minecraft **1.21.11**, Yarn mappings
  `1.21.11+build.3`, Fabric Loader `0.18.2`, **Java 21**.
- Mixins + access widener (`src/main/resources/revive-client.accesswidener`), the Meteor systems
  (modules, HUD, GUI), and optional integrations (Baritone, Xaero's Minimap/World Map, XaeroPlus).

## Prerequisites

- **JDK 21** (the build pins Java 21).
- The Gradle wrapper (`./gradlew`) handles Gradle/Loom; no global install needed.

## Build

```bash
./gradlew build
```

Output: `build/libs/revive-client-1.21.11-local.jar` (the remapped, production jar, with its
jar-in-jar libraries bundled) plus a `-dev` jar under `build/devlibs/`.

The build is self-contained. The one non-obvious piece is XaeroPlus: its Modrinth jar is built with a
newer Loom than this project pins, so a Gradle step (`loomVersionPatchedJar` in `build.gradle.kts`)
resolves it and rewrites one manifest line at configuration time, no committed artifact, no manual
step.

## Develop

```bash
./gradlew genSources     # decompile Minecraft for IDE navigation (Loom)
./gradlew runClient      # launch Minecraft with the client for testing
```

Import the folder as a Gradle project in IntelliJ IDEA (the usual Fabric/Loom workflow). `runClient`
is provided by Loom.

## Project layout

```
build.gradle.kts         Loom build (deps, mixin AP args, XaeroPlus patch step)
gradle/libs.versions.toml  version catalog (MC, mappings, mods, libraries)
gradle.properties        archives name, maven group, loom version-validation flag
src/main/java/revivedevelopment/reviveclient/   the client source (modules, gui, systems, mixins)
src/main/resources/      fabric.mod.json, revive-client.mixins.json, accesswidener, assets
```

## Reconstruction notes

- Source was decompiled from the shipped jar (intermediary mappings) and **bytecode-remapped to
  Yarn**, then re-decompiled; hundreds of decompiler artifacts were hand-fixed so it compiles.
- The Mixin annotation processor's **target validators are downgraded to warnings** (`-AMSG_*` args
  in `build.gradle.kts`) because the authoritative refmap already ships in the original jar. The
  source **compiles and remaps**; it has **not** been runtime-verified in-game (that needs a launch),
  so some mixin targets are not guaranteed to apply at runtime.
- Further build details are in the comments in `build.gradle.kts`; the analysis of the client is in
  [`CLIENT-TEARDOWN.md`](../../02.%20Teardown%20Analysis/CLIENT-TEARDOWN.md).
