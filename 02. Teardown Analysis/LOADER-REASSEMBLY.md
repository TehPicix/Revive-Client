# Revive Loader: Forensic Source Reassembly

**Verdict:** the Revive loader (`revive-loader.jar`, 18 classes, package `revivedevelopment.loader`)
is a **de-obfuscated, restructured reskin of the leaked hackware-loader** source
(`github.com/crabs3/hackware-loader`, package `me.hackware.loader`). The authentication/crypto
protocol is a **verbatim clone** with only the HKDF domain-separation strings renamed; the loader
was simplified, de-obfuscated, and had all anti-tamper and native machinery stripped.

This document maps every Revive class back to its Hackware ancestor and records, per piece, whether
it is a verbatim clone, a rename, a rewrite, Revive-new, or a Hackware feature Revive dropped.

## Basis of the reassembly

- **Primary (Revive ground truth):** the shipped `revive-loader.jar` (not included in this repo), 18 classes,
  **shipped UNOBFUSCATED** (real method/field names intact, no ProGuard, no string encryption, no
  native layer). Decompiled cleanly with Vineflower 1.11.1; the source in
  `01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/` is that decompile.
- **Reference (origin):** a clone of `crabs3/hackware-loader` @ `main`. Because Revive did not
  obfuscate, the two can be aligned directly rather than inferred.

Because the Revive jar is unobfuscated, the reassembled Java is **high confidence**: it is what
Revive actually compiled, not a guess. Hackware is used to (a) prove provenance, (b) explain intent,
and (c) show what Revive removed.

## Class provenance map

| Revive class (`revivedevelopment.loader`) | Hackware origin (`me.hackware.loader`) | Relationship |
|---|---|---|
| `PreLaunch` | `PreLaunch` | Rewritten/simplified. Hackware ran the whole flow inline in `onPreLaunch`; Revive thins it to token+identity resolution and delegates to `LoaderCore`. Anti-debug (`DebugDetector`/`KillSwitch`) calls removed. |
| `LoaderCore` (+ `AuthException`, `MinecraftIdentity`) | (none; extracted from Hackware `PreLaunch`) | **Revive-new orchestration class.** Holds the authorize→challenge→download→inject flow Hackware kept inline. `MinecraftIdentity` record + cape-join wiring are new. |
| `crypto/Crypto` (+ `KeyPairRaw`) | `crypto/CryptoKey` (primitives) | Rename + consolidation. X25519, HKDF-SHA256, hand-rolled XChaCha20-Poly1305 (`hchacha20`/`qr`/`leToInt`) are the **same implementation**. |
| `crypto/LoaderSession` | `crypto/SessionKey` + `crypto/LoaderKey` | Consolidation. Hackware split session-keypair (`SessionKey`) from request-sealing (`LoaderKey`); Revive merges both into `LoaderSession`. Protocol identical (see below). |
| `api/RemoteApi` (+ `HttpHeaders`, `Response`) | `api/RemoteAPI` | Rename. Same fluent `GET/POST/header/send` HTTP client over `java.net.http`. |
| `api/Hardware` | `api/obj/Hardware` | **Gutted.** Hackware = 1923 lines of layered device fingerprinting; Revive = 41 lines (`SHA-256(firstMAC\|os.name\|os.arch\|hostname\|user.name)`). |
| `api/Json` (+ `Parser`, `Writer`) | (none; Hackware used Gson) | **Revive-new.** Hand-written JSON reader/writer replacing Hackware's `com.google.gson` dependency. |
| `api/Meta` | `metadata/Meta` | Build constants (`buildPublicKeyHex`, `remoteApiHost`). Same role. |
| `api/MojangSession` | (none) | **Revive-new feature.** Session-server `join` for cape linking; `serverId = SHA-1("revive-capes:" + sessionKeyHex)`. Not present in Hackware. |
| `inject/ClientInjector` | `utils/NativeClassInjector` | Rewrite. Hackware injected via **JNI `DefineClass`** (native); Revive uses a **pure-Java** `InMemoryJarClassLoader` + reflection. |
| `inject/InMemoryJarClassLoader` | (none; Java half of `NativeClassInjector`) | Pure-Java in-memory jar classloader; replaces the native path entirely. |

### Dropped by Revive (present in Hackware, absent from the Revive jar)

- `annotation/Native`, `annotation/ExcludeNative`: markers driving the native-obfuscation pass.
- `utils/DebugDetector`: debugger/VM detection.
- `utils/KillSwitch`: randomized termination on tamper.
- `utils/Dialog`: native error dialogs.
- `api/obj/Authorization`, `api/obj/Instance`: structured payload objects (Revive inlines payload
  building in `LoaderCore.authorize` via `Json.Writer`).
- **The entire obfuscation build pipeline** (`gradle/tasks/02-proguard` … `08-native`,
  `10-build-key`, `src/main/native/hackware_native.cpp`). The Revive jar shows **no** ProGuard,
  string/number encryption, opaque predicates, or native code; it shipped in the clear.

## Protocol = verbatim clone (the decisive evidence)

`LoaderSession`/`Crypto` reproduce Hackware's `SessionKey`/`CryptoKey` exactly. The custom,
non-library pieces match bit-for-bit, which rules out coincidence:

- **Hand-rolled HChaCha20 → XChaCha20-Poly1305**: identical ChaCha constants
  (`0x61707865,0x3320646e,0x79622d32,0x6b206574`), identical quarter-round, identical
  24-byte-nonce → 16-byte HChaCha20 subkey + 12-byte IETF nonce (last 8 bytes at offset 4) split.
- **HKDF-SHA256** with a zero salt of `HmacSHA256` mac length, same expand loop.
- **`decryptDownload`** is the same two-layer scheme (download-info key unwraps an inner blob that is
  then run through the response-info decrypt).
- **Bearer derivation** = `HMAC-SHA256(HKDF(shared, token-info), challenge)`, hex-encoded.

The **only** protocol change is the HKDF domain-separation label, renamed so Revive's server is a
distinct instance:

| HKDF `info` | Hackware | Revive |
|---|---|---|
| request sealing | (in `LoaderKey`) | `revive-auth-request` |
| response decrypt | `hackware-auth-response` | `revive-auth-response` |
| download decrypt | `hackware-auth-download` | `revive-auth-download` |
| token/bearer | `hackware-auth-token` | `revive-auth-token` |

## Tells / notes

- **Injector is not wired to the actual client.** `ClientInjector` bootstraps
  `revivedevelopment.client.ReviveBootstrap#init` by default, but the Revive client (a Meteor fork)
  lives in `revivedevelopment.reviveclient` (Fabric entrypoint `revivedevelopment.reviveclient.ReviveClient`,
  `Main-Class: revivedevelopment.reviveclient.Main`) and has no `ReviveBootstrap`. The loader's inject
  path does not match the client it ships with.
- **Unobfuscated.** Shipping the lifted loader in the clear (no ProGuard/native) is what made this
  reassembly trivial and the provenance unambiguous.

## Reassembled-source status: faithful and buildable

`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/` is the faithful Revive loader (verbatim decompile of the
shipped jar, behaviorally exact, high confidence). The tree has been corrected to represent
*Revive's* loader rather than Hackware's, and it **builds**:

- **Identity fixed.** `fabric.mod.json` and `gradle.properties` carry Revive's real identity
  (`id: revive-loader`, `name: "Revive Loader"`, author `"Revive Development"`, group
  `revivedevelopment.loader`, entrypoint `revivedevelopment.loader.PreLaunch`). The built jar's
  expanded manifest is byte-identical to the shipped `revive-loader.jar` manifest.
- **Hackware carry-over removed.** The obfuscation pipeline (`gradle/tasks/02…08,10`),
  `src/main/native/`, `assets/hackware/`, `obfuscation.md`, and `proguard.dictionary.txt` are gone;
  Revive's loader shipped none of it.
- **Plain Fabric build.** The loader uses only the Fabric Loader API (no Minecraft classes, mixins,
  or mappings), so it builds as an ordinary Java jar with `fabric-loader` compile-only, no Loom, no
  Minecraft, no oshi/Gson/fabric-api. `./gradlew build` → `revive-loader-<mc>-local.jar` with the
  **identical 18-class set** as the shipped jar.
- **Local names restored.** Decompiler placeholders (`var2`, `var11`, …) were renamed following the
  Hackware convention: used catch variables → `exception`, empty/unused catches → `ignored`, and the
  two temporaries → `result` (Crypto XChaCha20 `doFinal`) and `defined` (InMemoryJarClassLoader).
  The jar still builds and keeps the identical 18-class set.
