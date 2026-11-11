# revive-loader

Reconstructed, buildable source of the **Revive loader**, the Fabric `preLaunch` entrypoint that
authenticates the user against the Revive auth server, downloads the encrypted client, and injects it
into the running game.

It is a de-obfuscated reskin of the leaked Hackware loader. The full forensic provenance and the
class-by-class map are in [`LOADER-PROVENANCE.md`](../../02.%20Teardown%20Analysis/LOADER-PROVENANCE.md) and
[`LOADER-REASSEMBLY.md`](../../02.%20Teardown%20Analysis/LOADER-REASSEMBLY.md); the behavior is covered in
[`LOADER-TEARDOWN.md`](../../02.%20Teardown%20Analysis/LOADER-TEARDOWN.md).

## Stack

- **Fabric** mod, **Java 21**, Fabric Loader `0.18.2`, Minecraft `1.21.11`.
- **Plain Java build, no Loom, no Minecraft, no mappings.** The loader touches only the Fabric
  Loader API (`net.fabricmc.loader.api.*`) and slf4j, so it compiles as an ordinary jar with
  `fabric-loader` as a compile-only dependency.

## Prerequisites

- **JDK 21**.
- The Gradle wrapper (`./gradlew`); nothing global needed.

## Build

```bash
./gradlew build
```

Output: `build/libs/revive-loader-1.21.11-local.jar`, a Fabric mod jar whose expanded
`fabric.mod.json` matches the shipped loader's identity, with the identical class set.

## Run

The loader is not a standalone program; it is a `preLaunch` entrypoint. It runs when Fabric starts a
Minecraft instance that has this jar in `mods/`, before the game launches. On its own it does nothing;
to exercise it you drop the jar into a 1.21.11 Fabric instance (alongside a client) and provide an
access token via `-Drevive.loader.token=…`, the `REVIVE_ACCESS_TOKEN` env var, or a `revive-token.txt`
in the game directory. It then authenticates and attempts to fetch + inject the client.

> It authenticates against Revive's live auth/download servers. Running it for real requires a valid
> Revive account token; this repo is for source analysis, not for operating the service.

## Project layout

```
build.gradle              plain Java build (fabric-loader compile-only), processResources
settings.gradle           rootProject = revive-loader
gradle.properties         archives name, maven group, loader/minecraft versions
src/main/java/revivedevelopment/loader/
  PreLaunch.java          the entrypoint: resolves token + identity, drives LoaderCore
  LoaderCore.java         authorize -> challenge -> download -> inject orchestration
  api/                    RemoteApi, Json, Meta, Hardware, MojangSession
  crypto/                 Crypto (X25519/HKDF/XChaCha20-Poly1305), LoaderSession
  inject/                 ClientInjector, InMemoryJarClassLoader (pure-Java in-memory loading)
src/main/resources/fabric.mod.json
```

## Reconstruction notes

- Source is the clean decompile of the shipped (unobfuscated) loader; decompiler placeholder locals
  were renamed to readable names (`exception`/`ignored`/`result`/`defined`).
- Hackware carry-over that the shipped Revive loader never used (the ProGuard→native obfuscation
  pipeline, `src/main/native/`, anti-debug/killswitch, the elaborate hardware fingerprinter) was
  removed. See [`LOADER-REASSEMBLY.md`](../../02.%20Teardown%20Analysis/LOADER-REASSEMBLY.md) for exactly what was stripped, renamed, and added.
