# revive-loader.jar teardown

Teardown of the Revive **loader**, `revive-loader.jar`: the Fabric `preLaunch` entrypoint the launcher
installs into the game, which authorizes the user against the Revive auth server and (in its own
default mode) downloads, decrypts and runs the client jar. Everything here is from static review:
the shipped jar was decompiled and its classes compared against the reconstructed source in
`01. Source Reconstruction/revive-loader`; nothing was executed and no network host was contacted.

The provenance section (that this loader is a rebrand of the leaked Hackware loader) is a code
comparison documented in the earlier write-ups [`LOADER-PROVENANCE.md`](LOADER-PROVENANCE.md) and
[`LOADER-REASSEMBLY.md`](LOADER-REASSEMBLY.md). The leaked Hackware loader itself is **not** in this repo, so
those cross-project claims are repeated here from that earlier comparison and labeled as such; the
Revive-side facts were re-verified against the source for this document.

## Summary

1. **The loader is the DRM gate between an approved Revive account and the client.** It reads a Revive
   access token, proves the user's Minecraft identity to Mojang, sends an encrypted authorization
   request to `revive-client.com`, and either stops there (gate mode) or downloads and runs an
   encrypted client jar (inject mode).
2. **The credential is the Revive account token.** The loader reads it from
   `-Drevive.loader.token`, the `REVIVE_ACCESS_TOKEN` environment variable, or
   `<gameDir>/revive-token.txt`, in that order (`PreLaunch.java:54`). The launcher writes the file
   with the token it got from `revive-client.com/account/login` (see
   [`ELECTRON-TEARDOWN.md`](ELECTRON-TEARDOWN.md)); the loader treats it as opaque and forwards it.
3. **The auth/download protocol is a sealed-box scheme:** X25519 to a baked-in server public key,
   HKDF-SHA256 with four domain-separated info strings, and a hand-rolled XChaCha20-Poly1305. It is
   specified below at a level that can be reimplemented.
4. **Inject mode is a general-purpose, per-user authorized remote-code loader.** It fetches
   `GET /api/download/<mcVersion>/client.bin`, decrypts it in memory, defines its classes from a
   byte array and invokes a configurable bootstrap method. The repo README states the operators
   intend to push a RAT through the client; this is the exact mechanism that makes that a one-flag
   change, and it is what the loader is already built to do. No RAT is present in the jar analyzed.
5. **The hardening that gives the lineage its point was removed.** Revive kept the encrypted
   delivery core verbatim but stripped the native injection, obfuscation, anti-debug and rich
   fingerprinting, ships unobfuscated, and runs in gate mode with the client on disk as a plain mod.
   In that configuration the DRM protects nothing.

## 1. What it is and where it sits in the chain

| Stage | Artifact | Role |
|---|---|---|
| 1. Launcher | `Revive.AppImage` / `Revive-Setup.exe` (Electron) | Logs the user in, writes `revive-token.txt`, downloads the jars into a hidden per-launch folder, launches Fabric. See [`ELECTRON-TEARDOWN.md`](ELECTRON-TEARDOWN.md). |
| 2. **Loader** | **`revive-loader.jar`** | **Fabric `preLaunch` entrypoint. Authorizes the account against `revive-client.com`; optionally downloads + injects the client. This document.** |
| 3. Client | `revive-client.jar` | Meteor-derived utility client. See [`CLIENT-TEARDOWN.md`](CLIENT-TEARDOWN.md). |

The loader is a Fabric mod whose only entrypoint is a `preLaunch` hook
(`revivedevelopment.loader.PreLaunch`, `src/main/resources/fabric.mod.json`). It runs inside the
game JVM before Minecraft starts, uses only the Fabric Loader API and slf4j (no Minecraft classes,
mixins or mappings), and is built as a plain Java 21 jar. It is not a standalone program.

The two jars are shipped together. In the launcher's default **gate** mode the client is installed as
an ordinary Fabric mod alongside the loader, and the loader only verifies the account; the loader's
own built-in default (**inject**) instead downloads the client encrypted and loads it in memory.

## 2. The credential: how the loader gets its token

`PreLaunch.resolveAccessToken()` (`PreLaunch.java:54`) resolves the Revive token from, in order:

1. System property `-Drevive.loader.token`
2. Environment variable `REVIVE_ACCESS_TOKEN`
3. File `<gameDir>/revive-token.txt` (read via `FabricLoader.getGameDir()`)

With no token the loader aborts with "No account credential" (`PreLaunch.java:50`). The launcher uses
path 3: it writes the token to `revive-token.txt` and marks it hidden+system on Windows. The token
comes from the launcher's own login to `revive-client.com/account/login`; the loader never parses
it. The Windows launcher build can be switched by the server to log in with a website account
(Supabase-backed), which is how this token can be the same session described in
[`WEBSITE-TEARDOWN.md`](WEBSITE-TEARDOWN.md) (see [`ELECTRON-TEARDOWN.md`](ELECTRON-TEARDOWN.md)
section 3.2).

The loader also resolves a **Minecraft identity** (`PreLaunch.java:104`): UUID and username from
`-Drevive.minecraft.uuid` / `-Drevive.minecraft.name` or, failing that, from the game's own
`--uuid` / `--username` launch arguments, plus the Minecraft **`--accessToken`**. The launch-argument
array is zeroed after reading (`PreLaunch.java:121`). The Minecraft access token is used only for the
Mojang session join in section 5; it is never put in the request body to Revive.

The loader's auth server host is resolved by `Meta.remoteApiHost()` (`api/Meta.java:28`) from
`-Drevive.loader.host` / `REVIVE_LOADER_HOST` / the bundled `revive-loader.properties` `host` key,
defaulting to `https://revive-client.com`. `PreLaunch` also republishes this host into the
`revive.loader.host` system property (`PreLaunch.java:26`), which the client later reads for its capes
API (`revive-client .../utils/network/Capes.java:211`).

Mode is chosen by `resolveMode()` (`PreLaunch.java:79`): `-Drevive.loader.mode` or
`REVIVE_LOADER_MODE`, defaulting to **`inject`**. The launcher forces `-Drevive.loader.mode=gate`
(`revive-launcher/src/minecraft.js:301`).

## 3. Auth and download protocol

### 3.1 Endpoints

All loader traffic to the operator goes to two paths on `revive-client.com`; the account-login and
`/downloads/*` endpoints belong to the launcher, not the loader.

| Method + path | Mode | Body / auth | Response |
|---|---|---|---|
| `POST /api/authorize` | both | `application/octet-stream` sealed request; `X-Loader-Integrity` header | JSON `{success, challenge}` or `{success:false, type, title, message}` |
| `GET /api/download/<mcVersion>/client.bin` | inject only | `Authorization: Bearer <derived>` | `application/octet-stream` body + `Content-Digest` header |
| `POST sessionserver.mojang.com/session/minecraft/join` | both, if a Minecraft identity is present | JSON `{accessToken, selectedProfile, serverId}` | status only (2xx = joined) |

Every request to `revive-client.com` carries `User-Agent: revive-loader/1.0` and the
`X-Loader-Integrity` header (`api/RemoteApi.java:51`).

### 3.2 Request: `POST /api/authorize`

`LoaderCore.authorize()` (`LoaderCore.java:42`) builds this JSON, then seals it (section 3.3):

| Field | Source | Notes |
|---|---|---|
| `accessToken` | the Revive token from section 2 | opaque to the loader |
| `nonce` | hex of 32 random bytes | `Crypto.randomBytes(32)` |
| `timestamp` | `Instant.now().getEpochSecond()` | seconds |
| `sessionKey` | this launch's session X25519 public key, hex | used to seal the response and derive the bearer |
| `hardware.hwid` | SHA-256 machine fingerprint | section 4 |
| `minecraft.uuid` | Minecraft UUID | may be null |
| `minecraft.name` | Minecraft username | may be null |
| `minecraft.joined` | boolean | true if the Mojang session join (section 5) returned 2xx |

The Minecraft access token is deliberately **not** in this payload. A `success:true` response must
carry a `challenge` string (`LoaderCore.java:56`); anything else becomes an `AuthException` built
from the server's `type` / `title` / `message` fields (`LoaderCore.java:98`).

### 3.3 Cryptography (reimplementable)

Primitives (`crypto/Crypto.java`): X25519 (JDK `XDH`), HKDF-SHA256 (hand-rolled), HMAC-SHA256,
SHA-256, and XChaCha20-Poly1305 (hand-rolled HChaCha20 subkey feeding the JDK `ChaCha20-Poly1305`).
Keys are raw 32-byte little-endian values. The server's build public key `pubA` is baked into
`revive-loader.properties` (section 11); `LoaderSession` holds it and a fresh per-launch session
keypair (`crypto/LoaderSession.java:11`).

Four HKDF info strings, one per role (`crypto/LoaderSession.java:7`):
`revive-auth-request`, `revive-auth-response`, `revive-auth-download`, `revive-auth-token`.

**HKDF-SHA256** (`Crypto.java:54`): salt = 32 zero bytes (the HMAC-SHA256 output length); `PRK =
HMAC(salt, ikm)`; expand `T(n) = HMAC(PRK, T(n-1) || info || counter_byte)` from counter 1, copied
into a 32-byte output.

**XChaCha20-Poly1305** (`Crypto.java:90`), no associated data:
- `subkey = HChaCha20(key, nonce[0:16])`
- `ietfNonce = 00 00 00 00 || nonce[16:24]` (12 bytes; the 8-byte tail at offset 4)
- run `Cipher.getInstance("ChaCha20-Poly1305")` with `subkey` and `ietfNonce`.

**HChaCha20** (`Crypto.java:115`): state constants `0x61707865 0x3320646e 0x79622d32 0x6b206574`,
8 key words, 4 nonce words, 10 double-rounds of the standard quarter-round (`Crypto.java:151`),
output = state words 0..3 and 12..15 (32 bytes). These are the canonical XChaCha constants.

**Request seal** (`sealRequest`, `LoaderSession.java:22`):
1. generate an ephemeral X25519 keypair `E`
2. `shared = X25519(E.priv, pubA)`
3. `key = HKDF(shared, "revive-auth-request", 32)`
4. `nonce = 24 random bytes`
5. `ct = XChaCha20Poly1305(key, nonce, payload)`
6. wire = `E.pub(32) || nonce(24) || ct(+16-byte tag)`

**Response unseal** (`decryptResponse`, `LoaderSession.java:31`): wire = `ePubR(32) || nonce(24) ||
ct`; `shared = X25519(session.priv, ePubR)`; `key = HKDF(shared, "revive-auth-response", 32)`;
decrypt. Minimum length 72 bytes (32 + 24 + 16).

### 3.4 Challenge and bearer derivation

`deriveBearer(challengeBase64)` (`LoaderSession.java:44`): base64-decode the `challenge` into a
response-sealed blob and unseal it (3.3) to recover `challengeHex` bytes; then
`shared = X25519(session.priv, pubA)`, `proofKey = HKDF(shared, "revive-auth-token", 32)`, and the
bearer = `hex(HMAC-SHA256(proofKey, challengeHex))`. Note the shared secret here uses the long-term
`pubA` with the session private key, not an ephemeral key. Gate mode runs exactly this (it derives
the bearer and discards it, `LoaderCore.verifyAccess`, `LoaderCore.java:37`); inject mode uses the
bearer on the download request.

### 3.5 Download and two-layer decryption

`download()` (`LoaderCore.java:68`) sends `GET /api/download/<mcVersion>/client.bin` with the bearer,
verifies the digest (3.6), then calls `decryptDownload` (`LoaderSession.java:52`):
1. body = `nonce(24) || ct`
2. `shared = X25519(session.priv, pubA)`, `key = HKDF(shared, "revive-auth-download", 32)`
3. `inner = XChaCha20Poly1305_decrypt(key, nonce, ct)`
4. return `decryptResponse(inner)` (so the inner layer is itself a full response-sealed blob,
   `ePubR || nonce || ct`, unsealed with the `revive-auth-response` key).

So the downloaded client is wrapped in two layers: a download layer keyed off the static `pubA`, and
an inner response layer with its own ephemeral public key. Minimum outer length 40 bytes (24 + 16).
The plaintext is the client jar, kept only in a byte array that `PreLaunch` zeroes in `finally`
(`PreLaunch.java:45`).

### 3.6 Integrity headers

**`X-Loader-Integrity`** (`RemoteApi.computeIntegrity`, `RemoteApi.java:74`) is the SHA-256 of the
loader's own code source, sent on every request. If the code source is a regular file (the normal
jar case) it hashes the file bytes; if it is a directory it walks all regular files, **sorts by the
relativized path string**, and for each updates the digest with the path (backslashes rewritten to
`/`) then a `0x00` separator then the file bytes; result lowercase hex. This lets the server
recognize an unmodified loader and look up the matching key. It is self-reported, so it is only
tamper evidence if the server enforces it, and a modified loader can send any value.

**`Content-Digest`** (`LoaderCore.verifyDigest`, `LoaderCore.java:78`) is parsed as
`ALGO=:<base64>:`, split on `=:`, trailing `:` stripped, base64-decoded, and compared with
`MessageDigest.isEqual` against `SHA-256(body)`, where `body` is the still-encrypted download. One
divergence from the Hackware original: Revive parses the `ALGO` label but never uses it, always
computing SHA-256 (`LoaderCore.java:87-90`), so a non-SHA-256 label would fail even if the server
meant it.

## 4. Hardware fingerprint

`Hardware.hwid()` (`api/Hardware.java:12`) is a single SHA-256 over
`firstMAC | os.name | os.arch | COMPUTERNAME-or-HOSTNAME | user.name`, hex-encoded. `firstMAC` is the
MAC of the first non-loopback, non-virtual, up interface, or the literal `no-mac` if none is found
(`Hardware.java:23`). This is a stable per-machine identifier that goes out in the `hardware.hwid`
field of every authorize request (section 3.2), tying each account to its hardware. It is the only
fingerprint the loader collects: no serial numbers, disk IDs, process lists or richer telemetry. It
is weak and spoofable (any value can be sent, and the inputs are all trivially readable), and it
collides on machines that share the `no-mac` fallback.

## 5. Mojang session join (capes)

Before authorizing, if a Minecraft identity is present, `LoaderCore.authorize` (`LoaderCore.java:43`)
calls `MojangSession.join` (`api/MojangSession.java:29`): a `POST` to
`sessionserver.mojang.com/session/minecraft/join` with `{accessToken: <Minecraft token>,
selectedProfile: <uuid without dashes>, serverId}`, where `serverId = hex(SHA-1("revive-capes:" +
sessionKeyHex.toLowerCase()))` (`MojangSession.java:20`). This is the standard Minecraft ownership
proof: Revive's server can confirm the account through Mojang's `hasJoined` without ever receiving
the Minecraft token, binding the session key to a verified Minecraft account (used for capes). The
`joined` boolean is reported back in the authorize payload. The session host can be redirected with
`-Drevive.mojang.session` (`MojangSession.java:57`), which the launcher does not set; the Minecraft
access token never leaves the loader except to this Mojang endpoint.

## 6. What it writes to disk, and what it loads and runs

**The loader writes nothing to disk.** There are no file-write, temp-file or process-spawn calls in
the loader source; the only `System.setProperty` call republishes the host (`PreLaunch.java:27`). The
`revive-token.txt` it reads is written by the launcher, not the loader.

What it runs depends on mode (`PreLaunch.onPreLaunch`, `PreLaunch.java:17`):

- **Gate mode** (`LoaderCore.verifyAccess`, `LoaderCore.java:37`): authorize, derive the bearer,
  stop. No download, no injection, nothing loaded. The client runs because the launcher installed
  `revive-client.jar` as a normal Fabric mod on disk.
- **Inject mode** (`LoaderCore.fetchClient` to `ClientInjector.inject`): the decrypted client jar
  (section 3.5) is handed to `InMemoryJarClassLoader` (`inject/InMemoryJarClassLoader.java`), which
  indexes every `.class` and resource from the in-memory jar into maps; `findClass` calls
  `defineClass` straight from the byte array and zeroes that array afterward
  (`InMemoryJarClassLoader.java:44`). The client is never written to disk. `ClientInjector.inject`
  (`inject/ClientInjector.java:11`) then resolves a bootstrap of the form `Class#method`
  (default `revivedevelopment.client.ReviveBootstrap#init`, overridable by
  `-Drevive.loader.bootstrap`), loads it through that classloader, and invokes the static method by
  reflection.

Classloading is a plain in-heap `ClassLoader` whose parent is the loader's own classloader
(`InMemoryJarClassLoader.java:16`); it is parallel-capable. There is no native memory loading, no JNI
and no `URLClassLoader`.

## 7. Remote-code and update capability

Stating precisely what the loader can do, without overstating what it does today:

- **Inject mode is authenticated, per-user remote code execution in the game JVM.** The server, after
  checking the Revive account and hardware ID, returns arbitrary Java bytecode (`client.bin`) that the
  loader decrypts and runs in process with the user's privileges and the live Minecraft session. The
  entry point (`Class#method`) is attacker-controllable via a system property, so the delivered code
  is not constrained to any particular shape.
- **The operator chooses what runs, every launch.** The payload is fetched fresh per launch and is
  unsigned by any key the loader holds (the only check is a SHA-256 the same server supplies). The
  code that runs is whatever `revive-client.com` serves at that moment.
- **The switch is one setting.** The launcher passes `-Drevive.loader.mode=gate` only because its
  stored `loaderMode` defaults to `gate` (`revive-launcher/src/config.js:49`,
  `revive-launcher/src/minecraft.js:301`); inject is the loader's own default. A launcher update
  (downloaded in the background and installed when the launcher closes,
  `revive-launcher/src/updater.js:33`, see [`ELECTRON-TEARDOWN.md`](ELECTRON-TEARDOWN.md)) can flip it
  without touching the loader jar.
- **Gate mode does not close the channel either.** On every launch the launcher fetches
  `manifest.json` from the download server and lets it replace the required and optional mod lists
  (`revive-launcher/main.js:304`); every listed jar is downloaded and handed to Fabric with
  `-Dfabric.addMods`. So the server can already deliver any jar as a mod, with no inject mode needed.

What is **not** in the analyzed jar: the loader contains no persistence, no credential/file
harvesting, no self-update of its own, and no payload. It is a delivery mechanism, not itself a RAT.
The repo README's claim that the operators plan to push a RAT describes intent; what is verifiable
here is that inject mode is exactly the authorized remote-code channel such a plan would use.

## 8. Provenance: a Hackware loader rebrand

From the earlier code comparison in [`LOADER-PROVENANCE.md`](LOADER-PROVENANCE.md) and
[`LOADER-REASSEMBLY.md`](LOADER-REASSEMBLY.md). The reference was a clone of `github.com/crabs3/hackware-loader`
(package `me.hackware.loader`), an acknowledged leak of an old Hackware build (commit `bd3385e`).
**That Hackware source is not present in this repo**, so the matches below are reproduced from that
earlier comparison; the Revive side of each was re-confirmed against the source for this document.
Individually suggestive, together conclusive (two teams do not write the integrity walk and the four
info strings identically by chance).

| Evidence | Hackware | Revive (verified here) |
|---|---|---|
| Four HKDF info strings, same roles, only the prefix changed | `hackware-auth-{request,response,download,token}` | `revive-auth-{request,response,download,token}` (`LoaderSession.java:7`) |
| Loader self-integrity routine | `RemoteAPI.resolveLoaderIntegrity` / `updateDigestWithEntry`: hash file, or walk + sort by relativized path, `0x00` separator | `RemoteApi.computeIntegrity` identical algorithm (`RemoteApi.java:74`) |
| HKDF-SHA256, zero salt of mac length, same expand loop | `LoaderKey.hkdfSha256` | `Crypto.hkdfSha256` (`Crypto.java:54`) |
| Hand-rolled HChaCha20 to XChaCha20-Poly1305, same constants and 24 to (16 subkey + 12 IETF) split | `LoaderKey.xChaCha20Poly1305Encrypt` / `hChaCha20` | `Crypto.xchacha` / `hchacha20` (`Crypto.java:90`, `:115`) |
| Wire format `ephPub(32) ‖ nonce(24) ‖ ct(+16 tag)` | request and response | identical (`LoaderSession.java:22`, `:31`) |
| Two-layer download: download key unwraps an inner response blob | `SessionKey.decryptDownload` | `LoaderSession.decryptDownload` (`LoaderSession.java:52`) |
| Endpoints `POST /api/authorize`, `GET /api/download/<ver>/client.bin` | same | same (`LoaderCore.java:54`, `:69`) |
| Bearer = `HMAC-SHA256(HKDF(shared, token-info), challenge)` | same | `LoaderSession.deriveBearer` (`LoaderSession.java:44`) |
| `Content-Digest` parse `ALGO=:<b64>:`, `MessageDigest.isEqual` | same | `LoaderCore.verifyDigest` (`LoaderCore.java:78`) |
| `preLaunch` flow: authorize to challenge to bearer to download to verify to decrypt to inject to zero buffer | inline in Hackware `PreLaunch` | split across `PreLaunch` / `LoaderCore` (same sequence) |

## 9. What Revive renamed, added and stripped

**Renamed** (from the earlier comparison): package `me.hackware.loader` to `revivedevelopment.loader`;
info-string prefix `hackware-auth-*` to `revive-auth-*`; host to `https://revive-client.com` with
`pubA` baked into `revive-loader.properties`; User-Agent to `revive-loader/1.0`.

**Added** (present in Revive, verified here, said to be absent from the leak):
- **Gate mode**, a verify-only path (`LoaderCore.verifyAccess`, `LoaderCore.java:37`) the launcher
  forces, so the client can ship as a normal Fabric mod.
- **Mojang session join** for capes (`MojangSession`, section 5) plus the `MinecraftIdentity`
  plumbing that reads `--uuid` / `--username` / `--accessToken`.
- **Plain-Java injection**: `ClientInjector` + `InMemoryJarClassLoader` using
  `ClassLoader.defineClass`, in place of Hackware's native injector.
- A **hand-written JSON** reader/writer (`api/Json.java`) replacing Hackware's Gson dependency.

**Stripped** (present in the leak per the earlier docs, absent from the Revive jar): the native class
injector (JNI `DefineClass` in native memory); the whole ProGuard to native obfuscation build
pipeline; the anti-debug `DebugDetector` + `KillSwitch`; the large multi-layer hardware
fingerprinter (reduced to the one SHA-256 in section 4); and the `@Native` / `@ExcludeNative`
annotations that drove native compilation. The shipped Revive jar is unobfuscated plain Java 21.

## 10. Weaknesses

- **Gate mode defeats the DRM it ships.** The encrypted, in-memory, per-session delivery exists to
  keep the client bytecode off disk. The launcher runs gate mode and installs `revive-client.jar` as
  a plaintext mod on disk (`revive-launcher/src/minecraft.js:284`, `config.js:49`), so none of that
  protection is active; the client decompiles cleanly (see [`CLIENT-TEARDOWN.md`](CLIENT-TEARDOWN.md)).
- **Inject mode as shipped would fail.** The default bootstrap target
  `revivedevelopment.client.ReviveBootstrap#init` (`ClientInjector.java:6`) does not exist in the
  shipped client (whose Fabric entrypoint is `revivedevelopment.reviveclient.ReviveClient`; no
  `ReviveBootstrap` class appears in its source). Inject mode would fail on a `ClassNotFoundException` (rethrown as "Client bootstrap class not
  found") unless `-Drevive.loader.bootstrap` is set, so the encrypted path is effectively unexercised by the
  current product.
- **Unobfuscated shipping.** The loader was shipped with real names, no ProGuard, no string
  encryption and no native layer, which is what made the reassembly and this teardown
  straightforward and the provenance unambiguous. The `X-Loader-Integrity` tamper check is moot when
  anyone can read and rebuild the jar.
- **Content-Digest algorithm label ignored** (always SHA-256, `LoaderCore.java:87`): a minor
  correctness divergence from the original.
- **Weak hardware ID** (section 4): spoofable and collision-prone.
- **The jars are public anyway** (context from the launcher, not the loader itself): the launcher
  fetches `revive-loader.jar`, `revive-client.jar` and the rest from `revive-client.com/downloads/`
  with no credential at all, so anyone who knows the URL gets the "protected" client without an
  account. See [`ELECTRON-TEARDOWN.md`](ELECTRON-TEARDOWN.md) (weakness W2).

## 11. IOCs and hashes

`revive-loader.jar` (the shipped binary, not included in this repo; hashes confirmed this session):

| Field | Value |
|---|---|
| Size | 40,565 bytes |
| SHA-256 | `9e7a6d9ef7be72b44f8fe8b897d7ab5ffc5dbfa0b16b589b5cd072b1e3e84a35` |
| SHA-1 | `c5b2eab1284528a3d183573bd1acbe389bc2871f` |
| MD5 | `13f0658c9bf037708b8cb2d42d6a1989` |
| Entries / classes | 27 zip entries, 18 classes |
| Package | `revivedevelopment.loader` |
| `fabric.mod.json` | id `revive-loader`, name "Revive Loader", author "Revive Development", version `1.21.11-local` |
| Build fingerprint (`META-INF/MANIFEST.MF`) | Fabric-Loom 1.15.5, Fabric-Gradle 9.2.0, Fabric-Loader 0.18.2, Fabric-Minecraft 1.21.11; class major version 65 (Java 21) |

Bundled config `revive-loader.properties`: `host=https://revive-client.com` and the server's 32-byte
X25519 public key `pubA` (64 hex chars, `7bf6def9…6e67`).

Network IOCs (loader only):

- `revive-client.com` (or whatever `revive.loader.host` / `revive-loader.properties` sets)
  - `POST /api/authorize` (sealed octet-stream, `X-Loader-Integrity` header, `User-Agent:
    revive-loader/1.0`)
  - `GET /api/download/<minecraftVersion>/client.bin` (bearer; inject mode only)
- `sessionserver.mojang.com` `POST /session/minecraft/join` with `serverId = SHA-1("revive-capes:" +
  sessionKeyHex)`

Host IOCs: reads `<gameDir>/revive-token.txt`; honors `-Drevive.loader.{token,mode,host,pubA,
bootstrap}`, `-Drevive.minecraft.{uuid,name}`, `-Drevive.mojang.session`, and the env vars
`REVIVE_ACCESS_TOKEN`, `REVIVE_LOADER_MODE`, `REVIVE_LOADER_HOST`, `REVIVE_LOADER_PUBA`. Hosts and
network context are in [`DOMAIN-FORENSICS.md`](DOMAIN-FORENSICS.md).

## 12. Reassembly status

The reconstructed source in `01. Source Reconstruction/revive-loader` is the shipped (unobfuscated)
jar decompiled and tidied: 11 Java files, about 1,345 lines across `PreLaunch`, `LoaderCore`,
`api/{RemoteApi,Json,Meta,Hardware,MojangSession}`, `crypto/{Crypto,LoaderSession}` and
`inject/{ClientInjector,InMemoryJarClassLoader}`. It builds as a plain Fabric jar with
`fabric-loader` compile-only (no Loom, no Minecraft), and the Hackware obfuscation pipeline, native
sources and anti-debug carry-over were removed because the shipped Revive jar never used them.

Confidence is **high** and was re-checked this session: the reconstructed `bin/main` class set is
**identical** to the shipped jar's 18 classes, and member-by-member `javap` signatures match (the
only differences are decompiler lambda-naming and member-ordering artifacts, not behavior). The
reconstructed `fabric.mod.json` expands under `processResources` to the shipped jar's exact identity.
