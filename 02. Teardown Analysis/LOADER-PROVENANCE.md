# Loader provenance: Revive loader vs. leaked Hackware loader

**Finding:** `revive-loader.jar` is a rebrand of the leaked **Hackware loader**. The authentication protocol, cryptographic implementation, loader-integrity routine, HTTP endpoints, wire format and `preLaunch` flow are the same down to structure and constants. Revive renamed the package and the HKDF info-string prefix, **stripped** Hackware's native-injection, obfuscation, anti-debug and hardware-fingerprinting machinery, and **added** a verify-only "gate" mode plus a Mojang session-join step.

This is a code-evidence comparison only. It establishes that one body of code is derived from the other; it does not assert who any individual is.

## Artifacts compared

| Side | Source | Identity |
|---|---|---|
| Revive loader | `revive-loader.jar`, decompiled with Vineflower 1.11.1 (the decompile, tidied, is in `01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/`; the jar itself is not included in this repo) | SHA-256 `9e7a6d9ef7be72b44f8fe8b897d7ab5ffc5dbfa0b16b589b5cd072b1e3e84a35`; package `revivedevelopment.loader`; `fabric.mod.json` id `revive-loader`, version `1.21.11-local` |
| Hackware loader (leak) | `github.com/crabs3/hackware-loader`, cloned this session | commit `bd3385e791fd4fe5a0901a90aa40682c732f216d` (2026-06-25, *"really old version idk"*); package `me.hackware.loader`; `fabric.mod.json` name "Hackware Loader", authors `TehPicix`, `d.m.t` |

The repo is a leak of an **old** version; per the loader's author the current Hackware loader has since been rebuilt. So this documents Revive copying a historical leak, not the current Hackware code.

## Matching evidence (Revive ← Hackware)

Each of these is individually suggestive; together they are conclusive. The loader-integrity routine and the four info-strings in particular are not things two teams write identically by chance.

1. **Loader self-integrity routine, same algorithm.**
   Both compute `X-Loader-Integrity` as the SHA-256 of the loader's own code source: if a regular file, hash the bytes; if a directory, walk it, **sort by the relativized path**, and for each entry update the digest with `path.replace('\\','/')` bytes, then a `0x00` separator, then the file contents; emit lowercase hex.
   - Hackware: `RemoteAPI.resolveLoaderIntegrity()` / `updateDigestWithEntry()`
   - Revive: `RemoteApi.computeIntegrity()`
   The header is sent on every request and used server-side to look up the matching private key.

2. **Four HKDF info-strings, identical roles, only the prefix changed.**

   | Role | Hackware | Revive |
   |---|---|---|
   | request seal | `hackware-auth-request` | `revive-auth-request` |
   | response unseal | `hackware-auth-response` | `revive-auth-response` |
   | client download | `hackware-auth-download` | `revive-auth-download` |
   | bearer token | `hackware-auth-token` | `revive-auth-token` |

3. **HKDF-SHA256, same hand-rolled extract/expand.**
   Zero salt of `mac.getMacLength()`, PRK = `HMAC(salt, ikm)`, then the `T(n) = HMAC(PRK, T(n-1) ‖ info ‖ counter)` loop copying into the output.
   - Hackware: `LoaderKey.hkdfSha256()` · Revive: `Crypto.hkdfSha256()`

4. **XChaCha20-Poly1305, same construction.**
   Derive a subkey with `hChaCha20(key, nonce[0..16])`, build the IETF nonce as `[0,0,0,0] ‖ nonce[16..24]`, then `Cipher.getInstance("ChaCha20-Poly1305")`. Identical hChaCha20 constants (`0x61707865 0x3320646e 0x79622d32 0x6b206574`), 10 double-rounds, same quarter-round.
   - Hackware: `LoaderKey.xChaCha20Poly1305Encrypt()` / `hChaCha20()` · Revive: `Crypto.xchacha()` / `hchacha20()`

5. **Wire format, identical.**
   `[ ephemeralPub(32) ‖ nonce(24) ‖ ciphertext(+16-byte Poly1305 tag) ]` for both request and response.

6. **Two-layer download decryption, identical.**
   `client.bin` is decrypted with a `*-auth-download` key, and the inner result is then decrypted again as a `*-auth-response` blob.
   - Hackware: `SessionKey.decryptDownload()` · Revive: `LoaderSession.decryptDownload()`

7. **Endpoints, identical.**
   `POST /api/authorize` (encrypted body) and `GET /api/download/<minecraftVersion>/client.bin` (bearer-authenticated).

8. **`Content-Digest` verification, identical.**
   Parse `ALGO=:<base64>`, strip the trailing `:`, `MessageDigest.getInstance(algo)`, compare with `MessageDigest.isEqual`.

9. **preLaunch flow, identical sequence.**
   Fabric `preLaunch` entrypoint → authorize → derive bearer from the server's challenge → download `client.bin` → verify digest → decrypt → inject client classes → zero the plaintext buffer.

10. **Bearer derivation, identical.**
    From the `challenge` in the authorize response, via the `*-auth-token` HKDF key and an HMAC-SHA256 over the challenge.

## What Revive renamed

- Package `me.hackware.loader` → `revivedevelopment.loader`
- Info-string prefix `hackware-auth-*` → `revive-auth-*`
- Host → `https://revive-client.com`; build public key `pubA` baked into `revive-loader.properties`
- User-Agent → `revive-loader/1.0`

## What Revive added (not in the leak)

- **Gate mode.** A `revive.loader.mode` switch with a verify-only path (`LoaderCore.verifyAccess`): authorize the account, then stop, no download, no injection. The launcher forces this mode (`-Drevive.loader.mode=gate`) and ships the client as a normal Fabric mod. The leak has only the download-and-inject path.
- **Mojang session join.** `MojangSession.join()` → `sessionserver.mojang.com/session/minecraft/join` with `serverId = SHA1("revive-capes:" + sessionKeyHex)`, plus the `MinecraftIdentity` plumbing to read `--uuid` / `--username` / `--accessToken`. Used to prove Minecraft account ownership (for capes). Not present in the leak.
- **Plain-Java injection.** `ClientInjector` + `InMemoryJarClassLoader` (standard `ClassLoader.defineClass`) replaces Hackware's native injector.

## What Revive stripped (present in the leak, gone from Revive)

This is the hardened half of the original, and its removal is the strongest signal that the Revive author could rename the loader but not reproduce it.

- **Native class injection.** Hackware's `NativeClassInjector` loads the decrypted jar in native memory and defines classes via JNI `DefineClass`, wiping the bytes on both sides. Revive uses an ordinary in-heap Java classloader.
- **Obfuscation pipeline.** Hackware builds through a Gradle task chain: `02-proguard`, `03-reflect-dispatch`, `04-opaque-predicates`, `04b-fake-control-flow`, `05-encrypt-strings`, `06-number-encrypt`, `07-jnic`, `08-native`, `10-build-key`. Revive's loader is plain, unobfuscated Java.
- **Anti-debug.** Hackware's `DebugDetector` + `KillSwitch` (halt if a debugger is attached). Absent from Revive.
- **Hardware fingerprinting.** Hackware's `Hardware` / `Instance` collectors are ~1,900 and ~383 lines gathering a rich machine profile. Revive reduces the HWID to a single SHA-256 of `firstMAC | os.name | os.arch | COMPUTERNAME/HOSTNAME | user.name`.
- **`@Native` / `@ExcludeNative`** annotations that drive the native-compilation step.

## Consequence: the copy defeats its own purpose

The Hackware loader exists to keep the client bytecode off disk, encrypted, fetched per-session, decrypted in native memory, never written out, behind obfuscation and anti-debug. Revive kept the encrypted-delivery core of that apparatus (and stripped the rest) but runs it in **gate mode with a plaintext client shipped as a normal mod**. In that configuration nothing is protected: `revive-client.jar` sits on disk unobfuscated and decompiles cleanly (it was retrieved and fully decompiled during this analysis; see [REPORT.md](REPORT.md)). The elaborate delivery/anti-tamper design is inert decoration. Leftover dead code in the client (an unused `Obf` XOR routine keyed from a Cloudflare-worker fetch) and the `1.21.11-local` build with empty `build_number`/`commit` fit the same picture: copied scaffolding, no release discipline.

## Assessment

On the evidence, `revive-loader.jar` is a reskin of the leaked Hackware loader: the auth/crypto/delivery core was taken verbatim (renamed), the hardening was removed because it could not be rebuilt, and a simpler gate mode was bolted on so the client could ship as an ordinary mod. The genuinely difficult engineering in this lineage, the native injection, the obfuscation toolchain, the anti-debug and fingerprinting, belongs to the original Hackware loader, not to Revive.

## Reproduction

```bash
# Revive loader (decompiled source in 01. Source Reconstruction/revive-loader/; jar not included in this repo)
#   SHA-256 9e7a6d9ef7be72b44f8fe8b897d7ab5ffc5dbfa0b16b589b5cd072b1e3e84a35

# Leaked Hackware loader
git clone https://github.com/crabs3/hackware-loader       # commit bd3385e

# Compare:
#   RemoteAPI.resolveLoaderIntegrity  vs  RemoteApi.computeIntegrity
#   LoaderKey.hkdfSha256 / hChaCha20  vs  Crypto.hkdfSha256 / hchacha20
#   SessionKey (info strings, decryptDownload)  vs  LoaderSession
#   PROTOCOL.md (wire format, endpoints)  vs  LoaderCore / RemoteApi
```
