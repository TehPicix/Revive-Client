# Revive.AppImage: static malware analysis

Analyzed 2026-10-19 with REA plus manual review. **Nothing was executed.** The Windows installer `Revive-Setup.exe` was not examined.

## Verdict (updated after reversing the second-stage jars)

**Not a session stealer, at least in the version served on 2026-10-19. It is still a high-trust, high-risk install.**

The two jars the launcher downloads (`revive-loader.jar` and `revive-client.jar`, retrieved 2026-10-19 15:35) were reversed in full; see [Second stage](#second-stage-jars). In summary:
- Neither jar sends the Minecraft session token anywhere except Mojang's own session server.
- Neither reads browser, Discord, wallet or launcher-account files.
- Neither installs persistence or loads hidden code.
- The loader is a license (DRM) gate: it sends your Revive token, a hashed hardware ID and your Minecraft UUID and name to `revive-client.com`.
- The client is a fork of the Meteor anarchy hacked client.

The structural risk below still applies. Every launch fetches fresh, unsigned jars from the operator's server, so what was analyzed here is only what the server sent today.

### Original verdict (AppImage only)

**High risk. This is a remote-payload loader. Do not run it.**

The AppImage itself contains no directly malicious code. It does not steal credentials, add persistence or contact any server other than the ones listed below. Every byte except about 70 KB of launcher JavaScript is a verified, unmodified copy of public software.

That 70 KB is the problem. Its job is to:

1. Get your Minecraft (Microsoft) login.
2. Download Java code from `revive-client.com` with no signature check. The server decides which jars.
3. Run that code inside Minecraft with your live session token. It loads from a randomly named folder that is hidden on Windows, and the launcher deletes the folder when the game closes.
4. Install new builds from the same server automatically, then run them when the launcher quits.

So whoever runs `revive-client.com` gets arbitrary code execution as the user, every time Play is pressed. Your Microsoft refresh token sits in plain text on disk where that code can read it. The hide-and-delete handling of the jars is the same pattern Minecraft session-stealer "clients" use. It is also used by paid hacked clients to stop leaks, so the AppImage alone cannot tell the two apart. The actual payload, `revive-loader.jar` / `revive-client.jar`, lives on the server and is **not** in this file.

## What's inside, and what was verified

| Region / file                                                                                                              | Status                                                                                                                                              |
| -------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bytes 0 to 188,391: AppImage runtime                                                                                          | Byte-identical to electron-builder `appimage-12.0.1` `runtime-x64`. No embedded signature or update info.                                           |
| Bytes 188,392 to 109,651,776: SquashFS (gzip)                                                                                 | Extracted with `unsquashfs`                                                                                                                         |
| ↳ 71 Electron files (`revive-launcher` = renamed `electron`, V8 snapshots, `.pak` files, `.so` files, `chrome-sandbox`, …) | **Byte-identical** to the official `electron-v33.4.11-linux-x64.zip` (zip SHA-256 matched Electron's SHASUMS256)                                    |
| ↳ `usr/lib/*.so` (6 libs on `LD_LIBRARY_PATH`)                                                                             | Byte-identical to electron-builder's `appimage-12.0.1` / `13.0.x` libs                                                                              |
| ↳ `AppRun`, `.desktop`, icons                                                                                              | Stock electron-builder template. Read in full; no extra commands.                                                                                   |
| ↳ `resources/app-update.yml`                                                                                               | Update feed `https://revive-client.com/updates`                                                                                                     |
| ↳ `resources/app.asar` → 77 npm packages incl. nested deps (848 files total)                                               | **Every file byte-identical** to the registry tarball for the same version. Only `package.json` metadata differs; entry-point fields are unchanged. |
| ↳ `app.asar` first-party code (10 files, ~70 KB)                                                                           | **Read in full** (see below)                                                                                                                        |
| Bytes 109,651,777 to 109,653,991                                                                                              | Zero padding                                                                                                                                        |
| Bytes 109,653,992 to the end                                                                                               | electron-builder embedded blockmap (deflate JSON with 5,250 chunk checksums) plus its 4-byte length. Fully accounted for.                           |

All 510 JavaScript files pass `node --check`. The renderer HTML has a strict CSP (`default-src 'self'`) and loads nothing remote.

## Behavior, step by step (from the first-party code)

**Startup** (`main.js`)
- Reads `<userData>/revive-launcher.json`.
- Deletes leftover managed jars from any previous run.
- Starts the auto-updater (`src/updater.js`): `autoDownload = true`, `autoInstallOnAppQuit = true`. On quit, `electron-updater`'s `AppImageUpdater` deletes the current AppImage, moves the downloaded one into its place and **runs it**. The only integrity check is a SHA-512 taken from the same server's `latest-linux.yml`; Linux updates have no code signing.

**Revive account login** (`src/authClient.js`)
- POSTs `{username, password}` to `https://revive-client.com/account/login` (or `/account/register`).
- Stores the returned `accessToken` and username in `revive-launcher.json`.

**Play** (`main.js` → `src/minecraft.js`)
1. **Microsoft login** through `msmc` 4.1.0 (unmodified). It opens the real `login.live.com` OAuth page with the official Minecraft launcher client ID `00000000402b5328`, so your Microsoft password goes to Microsoft. However, `xboxManager.save()` returns the **Microsoft refresh token**, and the launcher stores it in plain text as `msSession` in `revive-launcher.json`. That token gives long-lived account access to anything that can read the file.
2. **Server-controlled manifest:** `GET https://revive-client.com/downloads/manifest.json?t=<now>` (cache-busted). It can replace the required/optional jar lists and the Minecraft/Fabric versions without updating the launcher.
3. **Unverified jar downloads** from `https://revive-client.com/downloads/<name>`:
   - Required: `revive-loader.jar`, `fabric-api.jar`, `xaeroplus.jar`, `xaero-worldmap.jar`, `xaero-minimap.jar`, plus `revive-client.jar` in the default "gate" mode.
   - Optional: `baritone.jar`, `viafabricplus.jar`.
   - The only check is that the file starts with `PK`. Even well-known mods (Fabric API, Xaero, Baritone) are re-hosted by the operator rather than fetched from Modrinth or CurseForge, so any of them could be trojanized.
4. **Hidden, randomized drop location:** jars go to `<gameDir>/.cache-jrt/<24 random hex>/`, never to `mods/`. On Windows, `attrib +h +s` is applied to the game directory, `.cache-jrt` and `revive-token.txt`; hidden+system files stay invisible even with "show hidden files" on. The code comment says the goal is that someone looking in `mods/` "finds only their own mods, and the path from a previous session tells them nothing about the next one."
5. **Token handoff:** writes the Revive `accessToken` to `<gameDir>/revive-token.txt` for the loader jar.
6. **Launch** through `minecraft-launcher-core` (unmodified) with `-Drevive.loader.mode=gate` and `-Dfabric.addMods=<hidden jar paths>`. The Minecraft access token is passed to the JVM as normal, so any loaded mod can read it.
7. **Anti-forensics:** after the game window appears, the launcher hides itself and keeps running. When the game exits it deletes the jars, the `.cache-jrt` folder and `revive-token.txt`, then quits. Quitting is also when any pending update gets installed and run.

`src/config.js` also documents an `"inject"` loader mode ("encrypted download + runtime injection"). The default is `"gate"`, but the server-delivered `revive-loader.jar` decides what really happens.

**Not found in the shipped code:** autostart or persistence entries, shell-profile edits, cron or systemd entries, Discord or browser data access, keylogging, screenshots, or any network destination other than those listed below.

## Network IOCs
- `revive-client.com` (the operator's server)
  - `POST /account/login`, `POST /account/register`
  - `GET /downloads/manifest.json?t=<epoch-ms>`
  - `GET /downloads/{revive-loader,revive-client,fabric-api,xaeroplus,xaero-worldmap,xaero-minimap,baritone,viafabricplus}.jar`
  - `GET /updates/latest-linux.yml` plus the update AppImage
- Legitimate services it also contacts:
  - `meta.fabricmc.net`
  - Microsoft/Xbox/Minecraft: `login.live.com`, `user.auth.xboxlive.com`, `xsts.auth.xboxlive.com`, `api.minecraftservices.com`
  - Mojang: `launchermeta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`

## Host IOCs
| Platform | Path                                                  | Contents                                                                    |
| -------- | ----------------------------------------------------- | --------------------------------------------------------------------------- |
| Linux    | `~/.config/Revive Launcher/revive-launcher.json`      | `accessToken`, `username`, `msSession` (Microsoft refresh token)            |
| Linux    | `~/.local/share/ReviveLauncher/minecraft/`            | game dir; `.cache-jrt/<hex>/`; `revive-token.txt`                           |
| Linux    | `~/.cache/revive-launcher-updater/`                   | pending update downloads                                                    |
| Windows  | `%APPDATA%\Revive Launcher\revive-launcher.json`      | same as above                                                               |
| Windows  | `%APPDATA%\ReviveLauncher\minecraft\` (hidden+system) | game dir; `.cache-jrt\` (hidden+system); `revive-token.txt` (hidden+system) |
| Windows  | `%LOCALAPPDATA%\revive-launcher-updater\`             | pending updates                                                             |

Process: `java`/`javaw` started with `-Drevive.loader.mode=gate -Dfabric.addMods=...\.cache-jrt\<hex>\...`, as a detached child process.

## File hashes (SHA-256)
| File                                         | SHA-256                                                            |
| -------------------------------------------- | ------------------------------------------------------------------ |
| `Revive.AppImage` (109,770,754 B)            | `0fbf96b1280f264aceddf4a926224bc6402e8ba0ede62d3ce17419217321d11c` |
| ↳ SHA-1                                      | `06eb46f40ce23ca1a5ef0dd244904561a77f6e58`                         |
| ↳ MD5                                        | `d00dd5f31647d32da65b8ef9bc0d2a8c`                                 |
| `app.asar`                                   | `85e93dbbb0aff626720066c8fad6616de737984193ee7b83cc94d270dff3e8bb` |
| `main.js`                                    | `0e26e77cb93cf16eaea4ae5bc0fb2f6889bf680a6c1e6ca9333da64f49cac44d` |
| `src/minecraft.js`                           | `fdc26750b1baa5bbb0e7a3e8b1433ed2d687f6f924c77894f07d4697d0b980b4` |
| `src/config.js`                              | `ad68311872182d3009777cb9de459e09b61b3b7e1e59a48c028c79a530001472` |
| `src/updater.js`                             | `fff340ec01517d64701deabdcfbac4f14b665c4cd60304bb160506c038565a72` |
| `src/authClient.js`                          | `f09c8c61e81564fc3b387994b2250721d159a547c5f2e7addc26bff5fc81b2df` |
| `src/manifest.js`                            | `57be3ecfca6a5a7b5bea386d211756719ed9b6f713080c1f17dfec6ff9bb79ed` |
| `src/store.js`                               | `2448b6722381e2c1626e67566617f7361c98008b0669901536d7534b4858764c` |
| `preload.js`                                 | `f3e4f0d10e7f1e1e7d9ea183bacb8c7d96f62fe83eae6f9ed8159dd4dc1129cb` |
| `renderer/renderer.js`                       | `a02662e6b46401c36ee5b960e5c951ac62e0888354f6b9cf385589ecdba7f966` |
| `renderer/index.html`                        | `dfd7df51303077c1721780ac18fd8b210798db667ded7a6c3d791a0c134880be` |
| `revive-launcher` (= Electron 33.4.11 stock) | `ee99780b5878638df40bf577cfcdbc3c157b0b3581ada099a13db446f1af84d8` |

## Second-stage jars

Retrieved by the user from `revive-client.com/downloads/` on 2026-10-19 at 15:35. Both are valid jars. REA inventoried and extracted them; Vineflower 1.11.1 decompiled them (its SHA-1 matched Maven Central). Nothing was executed. The raw decompiled output (`analysis/decompiled/`) is not included in this repo; the cleaned-up sources are in [01. Source Reconstruction](../01.%20Source%20Reconstruction/).

| File                | Size         | SHA-256                                                                         |
| ------------------- | ------------ | ------------------------------------------------------------------------------- |
| `revive-loader.jar` | 40,565 B     | `9e7a6d9ef7be72b44f8fe8b897d7ab5ffc5dbfa0b16b589b5cd072b1e3e84a35`              |
| ↳ SHA-1 / MD5       |              | `c5b2eab1284528a3d183573bd1acbe389bc2871f` / `13f0658c9bf037708b8cb2d42d6a1989` |
| `revive-client.jar` | 34,356,543 B | `4df5c24c7c38b6b58d6bce1b4c9d3552c69e8b37e6785998bfaa02f88ad86aca`              |
| ↳ SHA-1 / MD5       |              | `51206be97df4c9e064b6b457764ab4da4865e7d9` / `03b42e1951ccc10937c97b5e65486c82` |

### `revive-loader.jar` (18 classes, all read)
- Fabric `preLaunch` entrypoint `revivedevelopment.loader.PreLaunch`.
- Bundled config `revive-loader.properties`: `host=https://revive-client.com`, plus the server's X25519 public key `pubA=7bf6def9…6e67`.
- **Revive token:** read from `-Drevive.loader.token`, the `REVIVE_ACCESS_TOKEN` environment variable, or `<gameDir>/revive-token.txt`.
- **Minecraft identity:** reads `--uuid`, `--username` and **`--accessToken`** from the game's launch arguments.
- **Where the Minecraft token goes:** only to `MojangSession.join()`, which calls `https://sessionserver.mojang.com/session/minecraft/join` with `serverId = SHA1("revive-capes:" + sessionKey)`. That is the same ownership proof a Minecraft server uses. The Revive server can confirm the account through Mojang's `hasJoined` without ever seeing the token. The `-Drevive.mojang.session` property could redirect this call, but the launcher does not set it.
- **`POST /api/authorize`:** the body is encrypted to the server's public key (X25519 + HKDF + XChaCha20-Poly1305). It contains:
  - your Revive `accessToken`
  - a nonce and a timestamp
  - a per-launch session key
  - `hardware.hwid`: SHA-256 of first MAC address | OS name | architecture | COMPUTERNAME/HOSTNAME | OS username
  - `minecraft.{uuid, name, joined}`

  **The Minecraft access token is not in this payload.** The header `X-Loader-Integrity` carries the SHA-256 of the loader jar itself, as tamper detection.
- **Gate mode** (what the launcher uses) only verifies the account; it never downloads code.
- **Inject mode** (the loader's own default, but overridden by the launcher) downloads `GET /api/download/<mcVersion>/client.bin`, checks a SHA-256 `Content-Digest`, decrypts it, defines its classes from memory and calls `revivedevelopment.client.ReviveBootstrap#init`. This path is **per-user, authorized remote code**. It is unused today, but the operator could switch to it in a launcher update.

### `revive-client.jar` (1,298 first-party classes in `revivedevelopment.reviveclient`)
- **What it is:** "ReviveClient", by Crownizzle and _Synful. A Meteor-derived anarchy utility client with 170 mixins, AutoCrystal, Surround, StashFinder, Baritone hooks and a PingBypass proxy subsystem.
- **Bundled libraries:** the 9 jars in `META-INF/jars` are clean.
  - The two Fabric API modules are byte-identical to `maven.fabricmc.net`.
  - javassist, reflections, jorbis, tritonus-share, vorbisspi, orbit and starscript contain every file byte-identical to Maven Central or the Meteor Maven repository. The only addition in each is Fabric's generated `fabric.mod.json`.
- **Assets** (127 files, 31 MB): only PNG, JPG, GIF, OGG, TTF, JSON, shaders and one index text file. No embedded executables, jars or classes.
- **Constant-pool scan of every class:**
  - no browser, Discord, wallet, `launcher_accounts`, Lunar, Feather or Essential paths
  - no reads of the launcher's `revive-launcher.json` or `msSession`
  - no `defineClass`, `URLClassLoader` or script engines
  - no large encoded blobs
- **Session token** (`Session.getAccessToken`, `method_1674`): used in exactly one place, `PingBypass.handleJoin()`. It passes the token to Minecraft's own `sessionService.joinServer()`, i.e. Mojang, and only while a routed PingBypass login is in progress. The VPS address defaults to `127.0.0.1` and is set by the user. Whichever VPS you connect to can complete a server login as you for that connection, which is how PingBypass is designed, so only use a VPS you control.
- **Process execution:**
  - `pactl` and `paplay` on Linux, for title-screen music
  - `rundll32 url.dll` / `open` in `Main`, the "put this jar in your mods folder" dialog shown if the jar is double-clicked
- **Remote-key string decryption** (`Obf` + `Auth.fetchSecret` → `revive-auth.crownizzle.workers.dev`): dead code. Neither `checkAuth()` nor `Obf.s()` is called anywhere.

**Network use by the client**

| Destination                                              | When                                                                       | What is sent                                                                                                                                                                                                                                               |
| -------------------------------------------------------- | -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `https://reviveclient.com/api/online/ping`, `/leave`     | Every 5 minutes while running, and on exit                                 | Empty POST (your IP), with a spoofed Chrome user agent                                                                                                                                                                                                     |
| `https://revive-client.com/api/capes`, `/api/capeowners` | At startup                                                                 | GET only                                                                                                                                                                                                                                                   |
| `wss://team.revive-client.com/ws`                        | Only while the **TeamSync** module is on                                   | Revive token, Minecraft UUID and name, **live x/y/z, dimension, server address, health, XP, totem/pearl/gapple/crystal/obsidian counts**, nearby players, totem pops, logout spots, terrain. All sharing toggles default to ON once the module is enabled. |
| ↳ full message schema                                    |                                                                            | See [TEAMSYNC-PROTOCOL.md](TEAMSYNC-PROTOCOL.md): 13 client→server and 10 server→client message types                                                                                                                                |
| `wss://emotes.revive-client.com/ws`                      | Only while the **Emotes** module is on                                     | Revive token, Minecraft UUID and name, emote events                                                                                                                                                                                                        |
| `sessionserver.mojang.com`, `api.mojang.com`             | Friends list and profile lookups                                           | Usernames and UUIDs                                                                                                                                                                                                                                        |
| `itunes.apple.com/search`                                | Spotify HUD                                                                | Song title, to fetch album art                                                                                                                                                                                                                             |
| `api.github.com`                                         | Credits and commits screens                                                | Repository and branch names                                                                                                                                                                                                                                |
| User-supplied Discord webhook or Supabase URL            | Only if you configure `DiscordNotifs` or `StashFinderPlus` "Send Database" | Stash and notification data                                                                                                                                                                                                                                |

### Remaining risk
- **The operator can change behavior at any launch.** The launcher re-downloads these jars, unsigned, every time Play is pressed, and silently installs launcher updates. Today's clean result does not cover tomorrow's download, or what was served to your friend at an earlier time.
- **The license check identifies your machine.** Every launch sends a stable hashed hardware ID, your Revive account and your Minecraft identity to `revive-client.com`.
- **TeamSync is invasive when on.** It streams your live location and the server you're on to the operator's server.

## Still unknown
- The **re-hosted mods** the launcher also downloads (`fabric-api.jar`, `xaeroplus.jar`, `xaero-worldmap.jar`, `xaero-minimap.jar`, `baritone.jar`, `viafabricplus.jar`) were not retrieved. They come from the operator's server, not Modrinth, and should be hash-checked against the official releases.
- What `client.bin` (inject mode) would contain. It is not used by the current launcher.
- What the server's current `manifest.json` and update feed deliver. Both can change at any time.
- `Revive-Setup.exe` was not analyzed.

## If your friend ran it
The jars served today don't steal the account. But the server could have sent your friend something different, and these steps are cheap.
- **Downloaded but never ran it:** delete it. Nothing happened.
- **Ran it and logged in or pressed Play:** treat the Microsoft/Minecraft account as possibly exposed.
  - Change the Microsoft password.
  - Use "Sign out everywhere" in the Microsoft account security settings; this invalidates the refresh token.
  - Turn on 2FA.
- Because operator-supplied Java ran as the user, also:
  - Check for persistence:
    - Linux: `~/.config/autostart`, `systemctl --user list-unit-files`, `crontab -l`, `~/.bashrc` and `~/.profile`
    - Windows: Run keys, the Startup folder, Scheduled Tasks
  - Rotate Discord and saved-browser passwords if anything looks off.
- Delete the host paths listed above.

## REA evidence
| Evidence ID                                                           | What it covers                                                                                         |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `ev_e7ff4a3c05e11afae44883dffcd8f49e1c4b11da822f29f10a0172bb898be491` | AppImage artifact inspection (ELF, no child entries seen)                                              |
| `ev_e1f5fe328cf90a9b642cd557e6cf01ec4c0ceb4cc9d2a780ca0ca4e52262ba74` | `app.asar` JavaScript application graph (1 BrowserWindow, 1 preload, 13 bridged APIs, 11 IPC handlers) |
| `ev_0b94064ed3f92a156f9b4dcc76a7bd08752b5dfb6033098dcc6ce686f60c9cbf` | summary view of the above                                                                              |
| `ev_5a726a519fc15a2b3d4f5fc94d220a2cd4db08c42e44ee893377448ca82f99f4` | ASAR extraction (848 files, containment verified, no integrity contradictions)                         |
| `ev_ae0f5eb4c789a9f6cb4f7add04412e71354a8dc4ed430ada1fb35d58279b198b` | `src/minecraft.js` module view                                                                         |
| `ev_9637d026166370888ebc1d28dc824317c3cc8e54c020beb248e65baf81fbc5a9` | `revive-loader.jar` artifact inspection (28 nodes)                                                     |
| `ev_c71419459666ffe2202c3f22c47a3563397001b547d5797d1e91fdaf1cc83f38` | `revive-loader.jar` extraction (21 files, containment verified)                                        |
| `ev_e8ed4dc72bbd16a1580d1c51e157342adfb26eb9d6c223fde57ba3ae8b4483ec` | `revive-client.jar` extraction (1,439 files, containment verified, no integrity contradictions)        |

REA has no JVM decompiler configured on this host: Ghidra isn't set up, and JADX/JEB are Android-only. So class bytecode was decompiled with Vineflower and checked with a direct constant-pool parser. REA labels `.class` members as `mach-o-universal` because both formats start with the bytes `CAFEBABE`; they are ordinary Java classes.

REA's static graph coverage was partial: a semantic-relation budget was hit inside `node_modules`. That gap was closed by diffing every dependency file against npm, and every first-party file was read manually.
