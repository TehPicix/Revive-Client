# Revive Launcher (Electron): teardown

Teardown of the **Revive Launcher**, the Electron desktop app Revive distributes as
`Revive.AppImage` (Linux) and `Revive-Setup.exe` (Windows). It covers how the launcher is built,
what it does from launch until it hands the game to the loader, its renderer/IPC surface, network
and host artifacts, hashes, and its weaknesses. Everything here comes from static review: the
AppImage's SquashFS, the installer's NSIS header and 7z payload, and both `app.asar` archives were
parsed with read-only scripts and REA, and the code was read against the reconstruction in
`01. Source Reconstruction/revive-launcher`. **Nothing was executed and no network host was
contacted.**

| | |
|---|---|
| **Artifacts** | `Revive.AppImage` (109,770,754 B) and `Revive-Setup.exe` (81,419,197 B), both downloaded 2026-10-19 and kept outside the repo |
| **Analyzed** | 2026-10-23 |
| **Source** | `01. Source Reconstruction/revive-launcher` (matches the Linux build); the analysis build is that source with `analysis-mode/analysis-mode.patch` applied (see [2.2](#22-the-patched-build)) |
| **Next stages** | [LOADER-TEARDOWN.md](LOADER-TEARDOWN.md) (`revive-loader.jar`), [CLIENT-TEARDOWN.md](CLIENT-TEARDOWN.md) (`revive-client.jar`) |
| **Related** | [WEBSITE-TEARDOWN.md](WEBSITE-TEARDOWN.md) (site, Supabase auth, installer download), [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md) (hosts, DNS, origin IPs) |

**Citations.** `path:line` is relative to the repo root. Code that exists only inside the shipped
artifacts uses a prefix: `[asar]` is a file inside the shipped `app.asar` (the dependency files are
identical on both platforms), `[win]` is a first-party file inside the Windows installer's
`app.asar` where it differs from the Linux build, and `[AppImage]` is a file inside the AppImage's
SquashFS. `[patched]` is a file in the analysis build of [2.2](#22-the-patched-build), i.e. the
reconstructed launcher with `analysis-mode/analysis-mode.patch` applied.

---

## TL;DR

The launcher contains no credential theft, persistence or hidden payload of its own. Its job is to
fetch unsigned code from the operator's server on every Play and run it inside Minecraft with the
player's live session, and to replace itself with whatever build the same server offers.

1. **Stock electron-builder app on Electron 33.4.11** (Chromium 130.0.6723.191). The first-party
   code is 10 JavaScript/HTML files (68,759 B). Everything else is stock Electron plus 75 npm
   packages (`electron-updater` 6.8.9, `minecraft-launcher-core` 3.18.2, `msmc` 4.1.0 and their
   dependencies). **Neither the installer, the AppImage nor either Electron binary is signed.**
2. **The two platforms ship different code under the same version `1.0.0`.** The Windows build adds
   a server-driven login mode (`GET /account/config`), email login for website (Supabase-backed)
   accounts and a sign-up link. The reconstruction matches the **Linux** build file for file.
3. **Remote payload loader by design.** Every Play downloads `revive-loader.jar`,
   `revive-client.jar` and six re-hosted mods from `revive-client.com/downloads/`; a server
   manifest picks the list. The only check is that each file starts with `PK`. The jars go to a
   random hidden folder and are injected with `-Dfabric.addMods`.
4. **The jar downloads are unauthenticated.** The launcher sends no credential to `/downloads/`.
   The Revive token only goes into `revive-token.txt` for the loader. The patched copy's
   retrieval works for that reason, not because the backend accepted its forged `alg: none` token
   (the patched launcher never sends that token anywhere).
5. **The payload changes under a fixed launcher.** `revive-client.jar` served on 2026-10-23
   (34,556,953 B) differs from the 2026-10-19 copy (34,356,543 B). `revive-loader.jar` is unchanged.
6. **Silent self-update with no signature check.** `electron-updater` downloads from
   `revive-client.com/updates` in the background and installs on quit. The only integrity check is a
   SHA-512 published by the same server. On Windows, Authenticode verification is skipped because
   `app-update.yml` has no `publisherName`.
7. **Credentials in plaintext.** The Microsoft **refresh token** (`msSession`) and the Revive
   `accessToken` sit in `revive-launcher.json`. The Revive token is also written to the game folder
   for each session.
8. **Hide and clean up.** Random `.cache-jrt/<24 hex>/` folder, `attrib +h +s` on Windows, the
   launcher hides itself while the game runs, then deletes the jars and token after exit and again
   on the next start.
9. **IPC is isolated but over-trusting.** `contextIsolation` is on, `nodeIntegration` off, the CSP
   is strict. But no handler checks its sender, `save-settings` writes any key (including
   `javaPath`, which is executed on Play, and `gameDir`, which Windows pastes into a shell command), and
   `open-external` accepts any URL.
10. **No persistence or theft code.** No autostart, services, scheduled tasks, browser or Discord
    access, or extra hosts. The Windows installer writes only the standard uninstall key, an
    `InstallLocation` key and shortcuts.

---

## 1. Build and packaging

### 1.1 Linux: `Revive.AppImage`

Type 2 AppImage (`AI\x02` at byte 8). The layout below was re-derived from the file:

| Bytes | Content | Notes |
|---|---|---|
| 0 to 188,391 | AppImage runtime (ELF64) | `.upd_info`, `.sha256_sig`, `.sig_key` and `.digest_md5` sections are all zero: **no signature, no update info** |
| 188,392 to 109,651,776 | SquashFS 4.0, gzip, 128 KiB blocks, 94 entries | Parsed in full. Superblock and every inode carry 2026-09-11 03:19 UTC, eight days after `revive-client.com` was registered ([DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md)) |
| 109,651,777 to 109,653,991 | Zero padding | |
| 109,653,992 to end | electron-builder blockmap (deflated JSON, version 2, 5,250 chunk checksums) plus a 4-byte big-endian length | Fully accounted for |

Notable SquashFS entries:

| Entry | Notes |
|---|---|
| `revive-launcher` (186,312,608 B) | Renamed Electron binary; embeds `Electron/33.4.11` and `Chrome/130.0.6723.191` |
| `resources/app.asar` (6,622,286 B) | The app (see [1.3](#13-appasar)) |
| `resources/app-update.yml` | `provider: generic`, `url: https://revive-client.com/updates`, `updaterCacheDirName: revive-launcher-updater` |
| `AppRun`, `revive-launcher.desktop` | Stock electron-builder template. The desktop entry always runs `AppRun --no-sandbox %U` (`[AppImage] revive-launcher.desktop:3`), and AppRun adds `--no-sandbox` whenever `unshare -Ur true` fails (`[AppImage] AppRun:56-57`) |
| `usr/lib/` | Six libraries (`libXss`, `libXtst`, `libappindicator`, `libgconf-2`, `libindicator`, `libnotify`) put on `LD_LIBRARY_PATH` (`[AppImage] AppRun:27`) |
| `chrome-sandbox` | Mode 0755, not setuid |

### 1.2 Windows: `Revive-Setup.exe`

| Item | Value |
|---|---|
| Format | PE32 (x86) NSIS installer stub, 5 sections, overlay from byte 141,312 |
| Signature | **None** (empty security directory). The PE timestamp (2018-12-15) belongs to the prebuilt NSIS stub, not to Revive's build |
| NSIS header | `NullsoftInst` first header at byte 141,312. The script header is deflate-compressed (48,141 B to 214,714 B) |
| App payload | `app-64.7z` stored raw at bytes 259,952 to 80,945,282 (80,685,331 B, 76 entries) |
| Installer template | Stock electron-builder NSIS (UAC, StdUtils, nsis7z, WinShell, nsExec and nsDialogs plugins; per-user or all-users install) |
| `Revive Launcher.exe` (188,842,496 B) | Electron 33.4.11 / Chrome 130.0.6723.191, PE timestamp 2025-04-15 (consistent with the upstream build). Unsigned. Version resource rewritten to "Copyright © 2026 Revive Development" |
| `resources/app.asar` (6,624,493 B) | The Windows build of the app (see [1.3](#13-appasar)) |
| `resources/app-update.yml` | Byte-identical to the Linux one |
| `resources/elevate.exe` | Stock electron-builder helper |

The installer script contains no `CurrentVersion\Run` key, Startup folder entry, scheduled task,
service, Defender exclusion, firewall rule or URL. Its registry and file footprint is in
[6.2](#62-windows).

### 1.3 `app.asar`

Both archives have 848 entries: 12 first-party files and 836 dependency files, with nothing
unpacked. Every first-party file matches the integrity hash in its ASAR header. **The 836
dependency files are byte-identical between the two platforms**; only first-party files differ.

| File | Linux size (B) | Windows build | Role |
|---|---|---|---|
| `main.js` | 13,995 | differs: adds the `account-config` handler | Main process: window, IPC, Play orchestration |
| `preload.js` | 1,132 | differs: adds `accountConfig` | `contextBridge` API `window.revive` |
| `renderer/index.html` | 16,667 | differs: email label, sign-up hint | UI and CSP |
| `renderer/renderer.js` | 8,057 | differs: `applyAccountConfig()` | UI logic |
| `src/authClient.js` | 1,474 | differs: email login, `/account/config` | Revive account API client |
| `src/config.js` | 3,329 | same | Defaults (hosts, mod list, versions) |
| `src/manifest.js` | 2,882 | same | Server mod-list manifest |
| `src/minecraft.js` | 16,183 | same | Microsoft login, downloads, hiding, launch |
| `src/store.js` | 1,845 | same | JSON settings store |
| `src/updater.js` | 3,195 | same | `electron-updater` wiring |
| `package.json` | 464 | differs: adds `allowScripts` | Metadata |
| `build/icon.png` | 133,312 | same | Icon |

Dependencies: 74 top-level packages plus one nested copy (`minecraft-launcher-core/node_modules/uuid`):

| Package | Version | Pulled in by | Used for |
|---|---|---|---|
| `electron-updater` | 6.8.9 | direct | Self-update (with `builder-util-runtime` 9.7.0, `js-yaml` 4.3.2, `semver` 7.7.4) |
| `minecraft-launcher-core` | 3.18.2 | direct | Downloads Minecraft and spawns Java (with the long-deprecated `request` 2.88.2, `adm-zip` 0.4.16, `checksum` 0.1.1) |
| `msmc` | 4.1.0 | direct | Microsoft / Xbox / Minecraft auth (with `node-fetch` 2.7.0) |

The shipped `package.json` declares only those three dependencies. It has no `electron`
dependency, no scripts and no `build` block, so the app was packaged by an external toolchain. The
Windows copy adds `"allowScripts": {"electron@33.4.11": true, "electron-winstaller@5.4.0": true}`.
That pins the Electron version, and `electron-winstaller` is the Squirrel.Windows builder, which the
shipped NSIS installer does not use (a leftover of another packaging setup).

### 1.4 Identity values

| Value | Where | Notes |
|---|---|---|
| productName `Revive Launcher`, version `1.0.0`, author `Revive Development`, license `UNLICENSED` | shipped `package.json` | Same version on both platforms despite different code |
| appId `com.reviveclient.launcher` | NSIS strings (shortcut AppUserModelID) | Same `reviveclient` spelling as the `reviveclient.com` host the client pings ([CLIENT-TEARDOWN.md](CLIENT-TEARDOWN.md)) |
| App GUID `49dd7377-ed69-51a3-9aed-5de41a5ea766` | NSIS strings | Derived from the appId; names the uninstall key and the install mutex |
| Window `900x620`, background `#0e0a16`, accent `#ff69be` / `#c084fc` | `01. Source Reconstruction/revive-launcher/main.js:17-29`, `01. Source Reconstruction/revive-launcher/src/config.js:56-57` | UI fingerprint |

---

## 2. Reconstruction and patched build

### 2.1 Reconstruction vs the shipped Linux build

`01. Source Reconstruction/revive-launcher` was compared file by file with the Linux `app.asar`:

| File | Result |
|---|---|
| `main.js`, `preload.js`, `renderer/*`, `src/authClient.js`, `src/config.js`, `src/manifest.js`, `src/store.js`, `src/updater.js`, `build/icon.png` | **Byte-identical** (same SHA-256 as [7](#7-file-hashes)) |
| `src/minecraft.js` | Identical text; the reconstruction uses LF line endings, the shipped file CRLF |
| `package.json` | Adds `scripts.start`, `devDependencies.electron: ^32.2.0` and `trustedDependencies`, as its README says (`01. Source Reconstruction/revive-launcher/README.md:68-70`) |

So every source citation below describes the shipped Linux build. Two gaps:

- **Electron version.** The shipped runtime is 33.4.11. The reconstruction's `^32.2.0` resolves to
  32.x (the patched build's lockfile pins 32.3.3, `[patched] bun.lock:95`). Pin
  `electron@33.4.11` for a faithful run.
- **The Windows build is not reconstructed.** Its six changed files are described from the
  installer (`[win]` citations).

The reconstruction's README describes the code inaccurately in two places. It calls
`authClient.js` "Revive/Supabase auth" (`01. Source Reconstruction/revive-launcher/README.md:12`,
`:57`) and says the launcher talks to `dl.revive-client.com` (`:71-73`). The code does neither:
`authClient.js` posts to `revive-client.com/account/*`, and nothing references Supabase or `dl.`.
Supabase appears only as a mode string the server can report to the Windows build
(`[win] src/authClient.js:60`), and `dl.revive-client.com` is the website's installer host
([WEBSITE-TEARDOWN.md](WEBSITE-TEARDOWN.md)). Likewise, the Supabase project host is not in any
shipped launcher file, although [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md#1-host-inventory) section 1 lists the
launcher's `authClient.js` as a source for it. It appears only in the patched build's forged token.

### 2.2 The patched build

The analysis build retrieves the managed jar set without an account and stops before launching. It is
the reconstructed launcher with the patch in
[`01. Source Reconstruction/revive-launcher/analysis-mode/`](../01.%20Source%20Reconstruction/revive-launcher/analysis-mode)
applied; `[patched]` citations below are line numbers in that patched build. Against the
reconstruction it changes:

| File | Change | Source |
|---|---|---|
| `main.js` | `userData` moved to `./run`; auto-update commented out; login and register get a `null` host | `[patched] main.js:9`, `:55`, `:86`, `:93` |
| `src/authClient.js` | No network. Returns a locally built token: header `{"alg":"none","typ":"JWT"}`, payload with `iss` set to the Supabase project's auth URL, random `sub`, `role: "admin"` and an `exp` about 1,000 years out. Only two dot-separated segments, so not even a well-formed unsecured JWT | `[patched] src/authClient.js:7-32` |
| `src/config.js` | `authHost` commented out; game dir forced to `./run/minecraft` | `[patched] src/config.js:11`, `:69` |
| `src/store.js` | `authHost` dropped from `settings()` | `[patched] src/store.js` |
| `src/minecraft.js` | `hideFile()` is a no-op; Microsoft login replaced by a random offline identity (`Player###`); `throw` after the downloads and Fabric profile, so Java is never started | `[patched] src/minecraft.js:48-49`, `:186-192`, `:279` |
| `renderer/*` | Username defaults to `Anonymous`; the password field is ignored in favor of a hard-coded string | `[patched] renderer/renderer.js:65-66`, `[patched] renderer/index.html:236` |
| `preload.js`, `src/manifest.js`, `src/updater.js` | Unchanged | |

**What it actually shows.** Play still runs `fetchManifest()` and `ensureMods()`, which call plain
`fetch(url)` with no headers (`01. Source Reconstruction/revive-launcher/src/manifest.js:35`,
`01. Source Reconstruction/revive-launcher/src/minecraft.js:106`). The forged token is only written
to `run/revive-launcher.json` and `run/minecraft/revive-token.txt`. The loader, which would present
it to `/api/authorize`, never runs. So the retrieval proves that **`/downloads/` needs no credential
at all**. The patched README's claims that "Revive's download path accepts a forged, unsigned token"
(`[patched] README.md:14-17`) and that the launcher "authenticates with the
baked-in forged token" (`:46`) are not supported by the code. Minor drift in the same README: it
quotes the abort message as "Launch aborted for debugging purposes." (`:28`) while the code says
"Launch aborted to prevent execution." (`[patched] src/minecraft.js:279`), and it
says the original auth was commented out (`:27`, `:70`) when `post()` was deleted.

**Evidence it produced.** `[patched] run/` (git-ignored,
`[patched] .gitignore:2`) holds one retrieval from 2026-10-23 03:19 UTC: the
eight jars in `run/minecraft/.cache-jrt/7235bfe17370075925bba9bc/` (hashes in [7.3](#73-payload-served-to-the-launcher)), the Fabric
profile, a Chromium profile and the forged-token state. **The patched build still runs the startup
cleanup** (`[patched] main.js:50`), so starting it again deletes `.cache-jrt` and
`revive-token.txt`. Copy the jars elsewhere first.

---

## 3. Behavior, from launch to loader handoff

### 3.1 Startup

1. **Settings.** `Store` reads `<userData>/revive-launcher.json`, falling back to
   `src/config.js` defaults (`01. Source Reconstruction/revive-launcher/src/store.js:11-37`).
2. **Leftover cleanup.** Deletes managed jars, `.cache-jrt` and `revive-token.txt` from the game
   directory in case a previous session was killed
   (`01. Source Reconstruction/revive-launcher/main.js:45-47`,
   `01. Source Reconstruction/revive-launcher/src/minecraft.js:93-102`).
3. **Window.** One `BrowserWindow` loading `renderer/index.html` with the preload bridge
   (`01. Source Reconstruction/revive-launcher/main.js:16-32`); see [4](#4-renderer-and-ipc-surface).
4. **Self-update** (packaged builds only). `autoDownload = true`, `autoInstallOnAppQuit = true`,
   non-blocking `checkForUpdates()` against the `app-update.yml` feed
   (`01. Source Reconstruction/revive-launcher/src/updater.js:19`, `:33-35`, `:63`). A finished
   download offers "Restart now"; otherwise it installs on the next quit (`:43-56`).
   - **Linux:** `AppImageUpdater` deletes the running AppImage, moves the new one into place with
     `mv -f` and executes it (`[asar] node_modules/electron-updater/out/AppImageUpdater.js:81`,
     `:97`, `:106-112`). On a plain quit the new image runs with `APPIMAGE_EXIT_AFTER_INSTALL`, so
     its runtime and `AppRun` execute but the app itself does not start.
   - **Windows:** `NsisUpdater` runs the downloaded installer. Signature verification returns early
     when `app-update.yml` has no `publisherName`, which is the case here
     (`[asar] node_modules/electron-updater/out/NsisUpdater.js:84-99`).
   - **Both:** the only check is the SHA-512 listed in `latest-linux.yml` / `latest.yml` from the
     same server (`[asar] node_modules/electron-updater/out/DownloadedUpdateHelper.js:123-124`).

### 3.2 Revive account login

1. The renderer reads the username and password fields and calls `window.revive.login()` or
   `register()` (`01. Source Reconstruction/revive-launcher/renderer/renderer.js:64-81`).
2. The main process posts JSON `{username, password}` to `{authHost}/account/login` or
   `/account/register` (`01. Source Reconstruction/revive-launcher/src/authClient.js:7-43`).
   **The password goes in plaintext JSON (over TLS) to the operator's own server**, not to a third
   party. Failures surface the server's `title` and `message` fields (`:27-30`).
3. `username` and `accessToken` from the response are stored in `revive-launcher.json`
   (`01. Source Reconstruction/revive-launcher/main.js:77-91`). The launcher treats the token as
   opaque, and the renderer never receives it: `get-state` returns only a `signedIn` flag (`:62-66`).

Windows build only: on startup the renderer asks `GET {authHost}/account/config` (5 s timeout). If
the server answers `provider: "supabase"`, the form asks for the website email, hides the
"Create account" tab and shows a sign-up link the server supplies (accepted only if it starts with
`http://` or `https://`), opened with `openExternal`
(`[win] src/authClient.js:48-68`, `[win] renderer/renderer.js:53-76`). Login then sends both
`email` and `username` set to the same value (`[win] src/authClient.js:43-46`). The comments say
"the website email when the server is linked to Supabase". This is how the launcher token can be the
same Supabase session described in [WEBSITE-TEARDOWN.md](WEBSITE-TEARDOWN.md), but the launcher
itself never talks to Supabase.

### 3.3 Play

| # | Step | Detail | Source |
|---|---|---|---|
| 1 | Guard | Refuses if already launching or no Revive `accessToken` | `01. Source Reconstruction/revive-launcher/main.js:284-286` |
| 2 | Microsoft login | `msmc` `Auth('select_account')`. Tries `refresh(msSession)` first, else opens an Electron window on `login.live.com` with the official launcher client ID `00000000402b5328`. The password is typed into Microsoft's own page | `01. Source Reconstruction/revive-launcher/src/minecraft.js:187-211`, `[asar] node_modules/msmc/dist/auth/auth.js:10-11` |
| 3 | Persist refresh token | `xboxManager.save()` returns `msToken.refresh_token`; stored in plaintext as `msSession` | `[asar] node_modules/msmc/dist/auth/xbox.js:107-108`, `01. Source Reconstruction/revive-launcher/main.js:295` |
| 4 | Manifest | `GET {downloadBase}/manifest.json?t=<epoch ms>`, 6 s timeout, `cache: no-store`. Jar names must match `^[\w.-]+\.jar$` (no path traversal). It can replace the required and optional lists and the Minecraft and Fabric versions; failure falls back to the built-in defaults | `01. Source Reconstruction/revive-launcher/src/manifest.js:12-35`, `01. Source Reconstruction/revive-launcher/main.js:304-308`, `01. Source Reconstruction/revive-launcher/src/config.js:40-45` |
| 5 | Gate mode | Adds `revive-client.jar` to the required list | `01. Source Reconstruction/revive-launcher/src/minecraft.js:283-286` |
| 6 | Game directory | Creates `mods/` and `versions/`; Windows: `attrib +h +s` on the game directory | `01. Source Reconstruction/revive-launcher/src/minecraft.js:32-52` |
| 7 | Token file | Writes the Revive token to `<gameDir>/revive-token.txt`, then hides it on Windows | `01. Source Reconstruction/revive-launcher/src/minecraft.js:58-63` |
| 8 | Managed directory | `<gameDir>/.cache-jrt/<12 random bytes as hex>/`; the parent is hidden on Windows, the jars are not (Fabric skips hidden files) | `01. Source Reconstruction/revive-launcher/src/minecraft.js:77-84` |
| 9 | Downloads | Purges managed names from `mods/`, then fetches each jar from `{downloadBase}/<name>` with no headers. Required failures abort; optional ones are skipped. The only check is a `PK` prefix | `01. Source Reconstruction/revive-launcher/src/minecraft.js:104-162` |
| 10 | Fabric profile | `GET https://meta.fabricmc.net/v2/versions/loader/<mc>/<loader>/profile/json`, written to `versions/<id>/<id>.json`; its libraries come from `maven.fabricmc.net` | `01. Source Reconstruction/revive-launcher/src/minecraft.js:166-180` |
| 11 | Java choice | Windows prefers `javaw.exe`: the `javaPath` setting, then a `runtime/` folder under the game directory, then `where javaw` / `where java`. Elsewhere MCLC uses `java` from `PATH` | `01. Source Reconstruction/revive-launcher/src/minecraft.js:218-271` |
| 12 | Launch | MCLC downloads vanilla files and spawns Java detached; the launcher calls `unref()` | `01. Source Reconstruction/revive-launcher/src/minecraft.js:297-347` |
| 13 | Hand off | On a log line containing `Setting user:`, `Backend library: LWJGL` or `Sound engine started` (or after 30 s), the window hides 2.5 s later; the process keeps running | `01. Source Reconstruction/revive-launcher/src/minecraft.js:323-358`, `01. Source Reconstruction/revive-launcher/main.js:337-346` |
| 14 | Game exit | Deletes managed jars, `.cache-jrt` and `revive-token.txt`, then `app.quit()`, which is also when a pending launcher update installs | `01. Source Reconstruction/revive-launcher/main.js:324-336`, `01. Source Reconstruction/revive-launcher/src/updater.js:34` |

The default download set (2026-10-23 retrieval order) is `revive-loader.jar`, `fabric-api.jar`,
`xaeroplus.jar`, `xaero-worldmap.jar`, `xaero-minimap.jar`, `revive-client.jar` (required), then
`baritone.jar`, `viafabricplus.jar` (optional). **Even the public mods are re-hosted by the
operator** instead of being fetched from Modrinth or CurseForge.

The code comment states the purpose of the hidden folder plainly: someone browsing `mods/` "finds
only their own mods", and old paths tell them "nothing about the next one"
(`01. Source Reconstruction/revive-launcher/src/minecraft.js:70-76`).

### 3.4 The Java process

| Part | Value | Source |
|---|---|---|
| Executable | `java` / `javaw.exe` (step 11), or any `javaPath` from settings | `01. Source Reconstruction/revive-launcher/main.js:322` |
| JVM arguments | `-Xmx4G -Xms2G` (settings), then `-Drevive.loader.mode=gate` and `-Dfabric.addMods=<managed jar paths joined by ; or :>` | `[asar] node_modules/minecraft-launcher-core/components/launcher.js:77-84`, `01. Source Reconstruction/revive-launcher/src/minecraft.js:300-302` |
| Game arguments | Vanilla set, including `--username`, `--uuid` and **`--accessToken <Minecraft token>`** on the command line | `[asar] node_modules/minecraft-launcher-core/components/handler.js:594` |
| Spawn | `child_process.spawn` with `cwd` = game directory, `detached: true`; on Windows every spawn in the process is forced to `windowsHide: true` by a global monkeypatch | `[asar] node_modules/minecraft-launcher-core/components/launcher.js:183-184`, `01. Source Reconstruction/revive-launcher/src/minecraft.js:21-28` |
| Environment | Inherited unchanged. From an AppImage this includes `APPDIR`, `APPIMAGE` and `LD_LIBRARY_PATH` pointing into the mounted image | `[AppImage] AppRun:25-27` |

### 3.5 How the token reaches the loader

The loader resolves its credential in this order
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:54-76`):

| Channel | Used by this launcher? |
|---|---|
| `-Drevive.loader.token` system property (`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:55`) | No |
| `REVIVE_ACCESS_TOKEN` environment variable (`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:59`) | No |
| `<gameDir>/revive-token.txt` (`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:64`) | **Yes**, written at Play and deleted after exit |

The mode comes from `-Drevive.loader.mode` or `REVIVE_LOADER_MODE`, defaulting to **`inject`**
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:79-85`).
The launcher always passes `gate` because its `loaderMode` setting defaults to it
(`01. Source Reconstruction/revive-launcher/src/config.js:49`). The manifest cannot change that
setting, but a future launcher build, the `save-settings` IPC or an edit to `revive-launcher.json`
can. The loader reads the Minecraft identity (`--uuid`, `--username`, `--accessToken`) from the game
arguments
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:113-120`).
What the loader does with all of this is in [LOADER-TEARDOWN.md](LOADER-TEARDOWN.md).

---

## 4. Renderer and IPC surface

### 4.1 Window settings

| Setting | Value | Source |
|---|---|---|
| `contextIsolation` | `true` | `01. Source Reconstruction/revive-launcher/main.js:26` |
| `nodeIntegration` | `false` | `01. Source Reconstruction/revive-launcher/main.js:27` |
| `sandbox` | not set; Electron 33 sandboxes such renderers by default | `01. Source Reconstruction/revive-launcher/main.js:24-28` |
| Content | Local `renderer/index.html` only; CSP `default-src 'self'; script-src 'self'` with inline styles and `data:` images allowed | `01. Source Reconstruction/revive-launcher/renderer/index.html:5` |
| Navigation / popups | No `setWindowOpenHandler`, `will-navigate` or permission handlers anywhere | (absent) |
| Second window | `msmc` opens its own `BrowserWindow` on `login.live.com` with Electron defaults | `[asar] node_modules/msmc/dist/gui/electron.js:12`, `[asar] node_modules/msmc/dist/assets.js:78-85` |

REA's static graph agrees: 1 app `BrowserWindow`, 1 preload, 13 bridged members and 11 main
handlers on Linux (14 and 12 on Windows), and **0 sender-validation observations**.

### 4.2 The bridge

`window.revive`, from `01. Source Reconstruction/revive-launcher/preload.js:8-24`:

| Member | Channel | Main handler | Capability |
|---|---|---|---|
| `getState()` | `get-state` | `01. Source Reconstruction/revive-launcher/main.js:62-66` | Settings, username, `signedIn` flag (no tokens) |
| `saveSettings(partial)` | `save-settings` | `01. Source Reconstruction/revive-launcher/main.js:68-73` | **Writes any key and value into `revive-launcher.json`** |
| `login(u, p)`, `register(u, p)` | `account-login`, `account-register` | `01. Source Reconstruction/revive-launcher/main.js:77-91` | Posts credentials to `authHost`, stores the token |
| `logout()` | `account-logout` | `01. Source Reconstruction/revive-launcher/main.js:93-98` | Deletes `accessToken`, `username`, `msSession` |
| `play()` | `play` | `01. Source Reconstruction/revive-launcher/main.js:283-356` | The whole Play flow |
| `openExternal(url)` | `open-external` | `01. Source Reconstruction/revive-launcher/main.js:100` | **`shell.openExternal()` on any string.** No caller in the Linux renderer |
| `modsList()` | `mods-list` | `01. Source Reconstruction/revive-launcher/main.js:171-191` | Lists `<gameDir>/mods` |
| `modsAdd()` | `mods-add` | `01. Source Reconstruction/revive-launcher/main.js:193-244` | Native file dialog, copies chosen jars into `mods/`, rejects duplicates of managed mod IDs |
| `modsToggle(file)`, `modsRemove(file)` | `mods-toggle`, `mods-remove` | `01. Source Reconstruction/revive-launcher/main.js:258-273` | Rename or delete inside `mods/`; constrained by `path.basename`, extension and managed-name checks (`01. Source Reconstruction/revive-launcher/main.js:248-256`) |
| `onLog(cb)`, `onStatus(cb)` | `log`, `status` | `01. Source Reconstruction/revive-launcher/main.js:34-39` | Main to renderer events |
| `accountConfig()` (Windows) | `account-config` | `[win] main.js:77` | `GET {authHost}/account/config` |

### 4.3 What a compromised renderer, or anyone who can edit the settings file, can do

`save-settings` has no key allowlist, and `revive-launcher.json` is plain JSON in the user profile.
Either path gives:

| Key | Effect | Source |
|---|---|---|
| `javaPath` | **Arbitrary executable run on the next Play**: MCLC first runs `"<javaPath>" -version` through a shell, then spawns it as the game | `01. Source Reconstruction/revive-launcher/main.js:322`, `[asar] node_modules/minecraft-launcher-core/components/handler.js:21`, `[asar] node_modules/minecraft-launcher-core/components/launcher.js:183` |
| `gameDir` | Windows: interpolated into `attrib +h +s "<path>"` run by `execSync` through `cmd.exe`, so a `"` in the value breaks out into a command. Also redirects the token and jar writes | `01. Source Reconstruction/revive-launcher/src/minecraft.js:50` |
| `authHost`, `downloadBase` | Send the Revive password, or fetch jars, from any host | `01. Source Reconstruction/revive-launcher/src/config.js:11`, `:15` |
| `loaderMode` | Anything other than `gate` stops the launcher from passing `-Drevive.loader.mode`, so the loader falls back to `inject` (encrypted per-user download) | `01. Source Reconstruction/revive-launcher/src/minecraft.js:301` |
| `accessToken`, `msSession` | Swap credentials | `01. Source Reconstruction/revive-launcher/main.js:285`, `:292` |

Reaching this from the network would need script execution in the renderer, which the local-only
content and `script-src 'self'` make hard. The only HTML sink is the header greeting, which puts the
server-returned username into `innerHTML` (`01. Source Reconstruction/revive-launcher/renderer/renderer.js:27`);
the CSP blocks script there, so it allows markup injection only. The more practical consequence is
local: **`revive-launcher.json` is a code-execution config**, so any process that can write it gets
code run every time the user presses Play.

---

## 5. Network endpoints

| Host | Request | When | Credential sent | Source |
|---|---|---|---|---|
| `revive-client.com` | `POST /account/login` | Log in | `username`, `password` (Windows also `email`) | `01. Source Reconstruction/revive-launcher/src/authClient.js:40-43`, `[win] src/authClient.js:43-46` |
| | `POST /account/register` | Create account | `username`, `password` | `01. Source Reconstruction/revive-launcher/src/authClient.js:35-38` |
| | `GET /account/config` | Windows build, renderer start | none | `[win] src/authClient.js:55` |
| | `GET /downloads/manifest.json?t=<epoch ms>` | Every Play | none | `01. Source Reconstruction/revive-launcher/src/manifest.js:30-35` |
| | `GET /downloads/<name>.jar` (8 by default) | Every Play | **none** | `01. Source Reconstruction/revive-launcher/src/minecraft.js:104-151` |
| | `GET /updates/latest-linux.yml` or `/updates/latest.yml`, then the new AppImage or installer | Startup (packaged builds) | none | `resources/app-update.yml`, `01. Source Reconstruction/revive-launcher/src/updater.js:63` |
| `meta.fabricmc.net` | `GET /v2/versions/loader/<mc>/<loader>/profile/json` | Every Play | none | `01. Source Reconstruction/revive-launcher/src/minecraft.js:173` |
| `maven.fabricmc.net` | Fabric loader libraries | First Play | none | Fabric profile JSON |
| `login.live.com` | OAuth authorize page (window) and `/oauth20_token.srf` | Play without a valid `msSession` | Microsoft sign-in (on Microsoft's page), refresh token | `[asar] node_modules/msmc/dist/auth/auth.js:36`, `:145` |
| `user.auth.xboxlive.com`, `xsts.auth.xboxlive.com` | Xbox Live and XSTS tokens | Play | Microsoft access token | `[asar] node_modules/msmc/dist/auth/auth.js:153`, `[asar] node_modules/msmc/dist/auth/xbox.js:24` |
| `api.minecraftservices.com` | `/authentication/login_with_xbox`, `/minecraft/profile` | Play | XSTS / Minecraft token | `[asar] node_modules/msmc/dist/auth/xbox.js:73`, `:83` |
| `launchermeta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, plus the URLs in Mojang's version JSON | Version manifest, client jar, libraries, assets | Play (cached after the first) | none | `[asar] node_modules/minecraft-launcher-core/components/launcher.js:16-19` |

Not contacted by the launcher: `dl.revive-client.com` (website installer host), the Supabase project,
`/api/authorize` (loader), and the client's `team.`, `emotes.` and `reviveclient.com` hosts. Hosting
details for every name above are in [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md).

---

## 6. Host artifacts

### 6.1 Linux

| Path | Contents | Source |
|---|---|---|
| `~/.config/Revive Launcher/revive-launcher.json` | `username`, `accessToken`, **`msSession` (Microsoft refresh token)**, saved settings; written with default permissions | `01. Source Reconstruction/revive-launcher/src/store.js:12`, `:27` |
| `~/.config/Revive Launcher/` (rest) | Chromium profile (`Local Storage`, `Session Storage`, `Network/`, `GPUCache`, `Preferences`, `Local State`). The `msmc` window uses the default persistent session, so `login.live.com` cookies land here too | Electron default `userData` |
| `~/.local/share/ReviveLauncher/minecraft/` | Game directory: `mods/`, `versions/`, `libraries/`, `assets/`, logs | `01. Source Reconstruction/revive-launcher/src/config.js:63-68` |
| `.../minecraft/.cache-jrt/<24 hex>/` | Managed jars, during a session | `01. Source Reconstruction/revive-launcher/src/minecraft.js:77-84` |
| `.../minecraft/revive-token.txt` | Revive token, during a session | `01. Source Reconstruction/revive-launcher/src/minecraft.js:58-63` |
| `.../minecraft/versions/fabric-loader-0.18.4-1.21.11/` | Fabric profile JSON | `01. Source Reconstruction/revive-launcher/src/minecraft.js:166-180` |
| `~/.cache/revive-launcher-updater/pending/` | Downloaded launcher updates | `resources/app-update.yml`, `[asar] node_modules/electron-updater/out/AppAdapter.js:6-18` |
| The `.AppImage` file itself | Replaced in place by an update | `[asar] node_modules/electron-updater/out/AppImageUpdater.js:81`, `:97` |

### 6.2 Windows

| Path or key | Contents | Source |
|---|---|---|
| `%LOCALAPPDATA%\Programs\Revive Launcher\` (per-user) or `C:\Program Files\Revive Launcher\` (all users) | `Revive Launcher.exe`, `Uninstall Revive Launcher.exe`, `resources\app.asar`, `resources\app-update.yml`, `resources\elevate.exe` | NSIS strings, 7z payload |
| `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\49dd7377-ed69-51a3-9aed-5de41a5ea766` (HKLM for all users) | `DisplayName` "Revive Launcher 1.0.0", `Publisher` "Revive Development", `DisplayVersion`, `UninstallString`, `QuietUninstallString`, `DisplayIcon`, `NoModify`, `NoRepair`, `EstimatedSize` | NSIS strings |
| `HKCU\Software\49dd7377-ed69-51a3-9aed-5de41a5ea766` | `InstallLocation` | NSIS strings |
| Desktop and Start Menu `Revive Launcher.lnk` | AppUserModelID `com.reviveclient.launcher` | NSIS strings |
| Mutex `49dd7377-ed69-51a3-9aed-5de41a5ea766` | During install | NSIS strings |
| `%LOCALAPPDATA%\revive-launcher-updater\` | `installer.exe` (copy of the setup used for differential updates), `pending\` | NSIS strings, `app-update.yml` |
| `%APPDATA%\Revive Launcher\revive-launcher.json` and Chromium profile | Same as Linux | `01. Source Reconstruction/revive-launcher/src/store.js:12` |
| `%APPDATA%\ReviveLauncher\minecraft\` | Game directory, **hidden + system** | `01. Source Reconstruction/revive-launcher/src/config.js:64-66`, `01. Source Reconstruction/revive-launcher/src/minecraft.js:40` |
| `...\minecraft\.cache-jrt\` | **Hidden + system**; the `<24 hex>\` folder and jars inside are not | `01. Source Reconstruction/revive-launcher/src/minecraft.js:82` |
| `...\minecraft\revive-token.txt` | **Hidden + system** | `01. Source Reconstruction/revive-launcher/src/minecraft.js:62` |

Hidden + system entries stay invisible in Explorer even with "Show hidden files" on; `dir /a` or
`attrib` shows them. The data under `%APPDATA%` is created by the app, not the installer, so expect
it to survive an uninstall.

### 6.3 Processes

| Process | Behavior | Source |
|---|---|---|
| `revive-launcher` / `Revive Launcher.exe` | Stays alive with no window while the game runs, quits after cleanup | `01. Source Reconstruction/revive-launcher/main.js:337-346`, `:329-332` |
| `java` / `javaw.exe` | Detached child; command line as in [3.4](#34-the-java-process), including `.cache-jrt\<hex>\` paths and the Minecraft access token | `01. Source Reconstruction/revive-launcher/src/minecraft.js:300-312` |
| `cmd.exe` children (Windows) | `attrib +h +s "<path>"` three times per Play, `where javaw`, `where java`, `"<java>" -version` | `01. Source Reconstruction/revive-launcher/src/minecraft.js:50`, `:246`, `[asar] node_modules/minecraft-launcher-core/components/handler.js:21` |

**Not present:** autostart or login items, Run keys, services, scheduled tasks, cron or systemd
units, shell-profile edits, browser, Discord or wallet paths. First-party code has no
`setLoginItemSettings`, `Tray`, `globalShortcut` or `protocol` use, and the installer script has
none of the registry or task strings listed in [1.2](#12-windows-revive-setupexe).

---

## 7. File hashes

### 7.1 Artifacts (SHA-256)

| File | Size (B) | SHA-256 |
|---|---|---|
| `Revive.AppImage` | 109,770,754 | `0fbf96b1280f264aceddf4a926224bc6402e8ba0ede62d3ce17419217321d11c` |
| ↳ SHA-1 / MD5 | | `06eb46f40ce23ca1a5ef0dd244904561a77f6e58` / `d00dd5f31647d32da65b8ef9bc0d2a8c` |
| `Revive-Setup.exe` | 81,419,197 | `a809431580355cafe5341e3ff6ed61e18805c17c0ee713e282c613c0c2a6592b` |
| ↳ SHA-1 / MD5 | | `f40e89b8c23e7f37a8ab9d469a78341c8d343bd9` / `a925b50a10a34da0c56c6d58362e9c3d` |
| `app-64.7z` (carved from the installer) | 80,685,331 | `aa058d0744fcaa7ff584042854374613801754de2b3d062e94ad095b650ba638` |
| `app.asar` (Linux) | 6,622,286 | `85e93dbbb0aff626720066c8fad6616de737984193ee7b83cc94d270dff3e8bb` |
| `app.asar` (Windows) | 6,624,493 | `a0e7944b17be7a64c391d563fa1f2daf9b0692f0f97611fee26ea559f49bc63e` |
| `app-update.yml` (both) | 102 | `3da3b82da574a052d5dfe7be59ce43366e0acb946c4e8ed307806d8b281e55a6` |
| `revive-launcher` (Linux Electron) | 186,312,608 | `ee99780b5878638df40bf577cfcdbc3c157b0b3581ada099a13db446f1af84d8` |
| `Revive Launcher.exe` (Windows Electron) | 188,842,496 | `4923a4a0159caf370390db4e1343a91c4e3aaa9c83defbf55789eb6ccc5976b5` |
| `AppRun` | 3,490 | `fbdcb718617f63b6bd7e8f7091c6e406e4a620f3c134cd6415cf4bd27df7d282` |
| `revive-launcher.desktop` | 331 | `017c035f1389c71eae8c0bc60defef861652aa56e300a6cff15e41bd4948d537` |

### 7.2 First-party files (SHA-256)

| File | Linux build (= reconstruction) | Windows build, where different |
|---|---|---|
| `main.js` | `0e26e77cb93cf16eaea4ae5bc0fb2f6889bf680a6c1e6ca9333da64f49cac44d` | `838a36c7cd13dab191b7a79e561e38912eebf4a128487d54b73d135832fcfac2` |
| `preload.js` | `f3e4f0d10e7f1e1e7d9ea183bacb8c7d96f62fe83eae6f9ed8159dd4dc1129cb` | `60d4cf97a363a28015eaffd17794a2026d19b95cfb0bdeeb30380dfa30a70e80` |
| `renderer/index.html` | `dfd7df51303077c1721780ac18fd8b210798db667ded7a6c3d791a0c134880be` | `163fe68c5ba5b1a2ce404101857aca815ff83c82e2cb6d58a1c210b6762d620f` |
| `renderer/renderer.js` | `a02662e6b46401c36ee5b960e5c951ac62e0888354f6b9cf385589ecdba7f966` | `72dbfa3a94ae9cf1e9f07505794759eff94faef276f4ee0d20f14cef92019334` |
| `src/authClient.js` | `f09c8c61e81564fc3b387994b2250721d159a547c5f2e7addc26bff5fc81b2df` | `695da8c43f9662e89245e4b768cfddf2d0332cacb87335bdab5f82950747a6f7` |
| `src/config.js` | `ad68311872182d3009777cb9de459e09b61b3b7e1e59a48c028c79a530001472` | same |
| `src/manifest.js` | `57be3ecfca6a5a7b5bea386d211756719ed9b6f713080c1f17dfec6ff9bb79ed` | same |
| `src/minecraft.js` | `fdc26750b1baa5bbb0e7a3e8b1433ed2d687f6f924c77894f07d4697d0b980b4` (CRLF) | same |
| `src/store.js` | `2448b6722381e2c1626e67566617f7361c98008b0669901536d7534b4858764c` | same |
| `src/updater.js` | `fff340ec01517d64701deabdcfbac4f14b665c4cd60304bb160506c038565a72` | same |
| `package.json` | `11b7395656f6b7cc9cc6f5c512ea165dee8ed974e6975f9a1db5d983a44e1990` | `ef41d61b8878ebf6c8203fea5ea3680af903085114b58383d76e4f3c0145729b` |
| `build/icon.png` | `8d038af8e069314b30e4d7fa8c67d3dbc6bded7d4f934ecc489714478021a435` | same |

### 7.3 Payload served to the launcher

| File | 2026-10-19 copy | 2026-10-23 03:19 UTC (patched run) |
|---|---|---|
| `revive-loader.jar` | 40,565 B, `9e7a6d9ef7be72b44f8fe8b897d7ab5ffc5dbfa0b16b589b5cd072b1e3e84a35` | unchanged |
| `revive-client.jar` | 34,356,543 B, `4df5c24c7c38b6b58d6bce1b4c9d3552c69e8b37e6785998bfaa02f88ad86aca` | **34,556,953 B, `aaf9559c90cc61879f9f4c1b3f9d81efde050c87929ec99dbae66c3e9e82cdba`** |
| `fabric-api.jar` | not retrieved | 2,426,039 B, `bdff7fd7e220085cfad2ff9b1f40dde6534ae0b96cf378f97a374bc54cb9ed0f` |
| `xaeroplus.jar` | not retrieved | 7,918,679 B, `6f8850026902825d797b8d947cf90bf9ae12dbf098492ad191d3027be21bc2a6` |
| `xaero-worldmap.jar` | not retrieved | 1,435,689 B, `b4cead471ee1bf0984e4628a3a6ebe34041f73784391216d6d789194a20e9b02` |
| `xaero-minimap.jar` | not retrieved | 2,152,212 B, `ffbba377946a59e0e3d19ec043a6a0422ce081f1373f14dd336d05971fc8dcf4` |
| `baritone.jar` | not retrieved | 1,604,068 B, `3e28c73cfe6fb05cc7258f6330a57faa07200a80577d69eff770345e7caa7f40` |
| `viafabricplus.jar` | not retrieved | 26,546,161 B, `d98c7f3e800e1f50537e7295f0214f126e296c0e83c6d86efbe9906ed0ff8248` |

The re-hosted mods still need to be compared against the official Modrinth/CurseForge releases
(not done here: no network). The two Revive jars are covered in
[LOADER-TEARDOWN.md](LOADER-TEARDOWN.md) and [CLIENT-TEARDOWN.md](CLIENT-TEARDOWN.md).

---

## 8. Security weaknesses

| # | Weakness | Impact | Source |
|---|---|---|---|
| W1 | Unsigned code from the server on every Play; the server-side manifest picks the jar list; only a `PK` prefix check | Whoever controls `revive-client.com` (or its CDN) runs arbitrary Java as the user, with the live Minecraft session in the same JVM | `01. Source Reconstruction/revive-launcher/src/minecraft.js:113`, `01. Source Reconstruction/revive-launcher/main.js:304-308` |
| W2 | Payload downloads require no credential | The "DRM" client jar is public to anyone who knows the URL; the gate is only in the loader at game start | `01. Source Reconstruction/revive-launcher/src/minecraft.js:106`, [2.2](#22-the-patched-build) |
| W3 | Auto-download and install-on-quit of launcher updates; no code signing on either platform, no Authenticode check on Windows | Silent replacement of the launcher itself; on Linux the new image is executed at quit | `01. Source Reconstruction/revive-launcher/src/updater.js:33-34`, `[asar] node_modules/electron-updater/out/NsisUpdater.js:84-99` |
| W4 | Microsoft refresh token and Revive token stored in plaintext JSON with default file permissions; Revive token also dropped into the game folder each session | Long-lived Microsoft account access for anything that can read the profile, including the server-supplied jars running as the same user | `01. Source Reconstruction/revive-launcher/main.js:295`, `01. Source Reconstruction/revive-launcher/src/store.js:27`, `01. Source Reconstruction/revive-launcher/src/minecraft.js:58-63` |
| W5 | Minecraft access token on the Java command line | Readable by other local processes (normal for vanilla-style launchers, but worth knowing) | `[asar] node_modules/minecraft-launcher-core/components/handler.js:594` |
| W6 | No IPC sender validation; `save-settings` writes arbitrary keys; `javaPath` is executed | Settings file and renderer become code-execution paths ([4.3](#43-what-a-compromised-renderer-or-anyone-who-can-edit-the-settings-file-can-do)) | `01. Source Reconstruction/revive-launcher/main.js:68-73` |
| W7 | Shell string interpolation of a configurable path | Command injection through `gameDir` on Windows | `01. Source Reconstruction/revive-launcher/src/minecraft.js:50` |
| W8 | `open-external` passes any string to `shell.openExternal` | Opens arbitrary URLs or protocol handlers if the renderer is driven; the Windows build feeds it a server-chosen URL (scheme-checked only in `authClient`) | `01. Source Reconstruction/revive-launcher/main.js:100`, `[win] src/authClient.js:63` |
| W9 | Server-supplied username written with `innerHTML` | Markup injection in the header; scripts blocked by CSP | `01. Source Reconstruction/revive-launcher/renderer/renderer.js:27` |
| W10 | Electron 33 / Chromium 130 (out of upstream support well before the 2026-09 build); Linux desktop entry and AppRun fallback pass `--no-sandbox` | Known Chromium bugs in a window that renders remote `login.live.com` content; OS sandbox off on many installs | `[AppImage] revive-launcher.desktop:3`, `[AppImage] AppRun:56-57` |
| W11 | Revive password sent to the operator's server | The operator sees every account password in clear (inside TLS); reused passwords are exposed | `01. Source Reconstruction/revive-launcher/src/authClient.js:40-43` |
| W12 | Global `child_process.spawn` monkeypatch | Changes spawn options for every module in the main process, including the updater | `01. Source Reconstruction/revive-launcher/src/minecraft.js:21-28` |

---

## 9. Tells

**Operator-facing template comments shipped in the app.** The code addresses the person deploying
it, not a user: "These point at your domain" (`01. Source Reconstruction/revive-launcher/src/config.js:3`),
"Your account/auth server" (`:10`), "Publish each file below into the server's public/downloads/
folder" (`:14`), "your Revive auth server's account endpoints"
(`01. Source Reconstruction/revive-launcher/src/authClient.js:3`), and "the VPS update feed"
(`01. Source Reconstruction/revive-launcher/src/updater.js:4`). The last one fits the OVH VPS origin
found in [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md).

**Comments that do not match reality.**

- `updater.js` says the feed is "configured under build.publish in package.json"
  (`01. Source Reconstruction/revive-launcher/src/updater.js:4`); the shipped `package.json` has no `build` block.
- `findJavaw()` searches "the runtime MCLC downloads"
  (`01. Source Reconstruction/revive-launcher/src/minecraft.js:213-217`), but `minecraft-launcher-core`
  3.18.2 never downloads a Java runtime, so that search only finds something placed there by hand.
- `config.js` says there is "no Baritone build for 1.21.11 yet" and warns against shipping an older
  one (`01. Source Reconstruction/revive-launcher/src/config.js:31-34`); the server served a
  `baritone.jar` on 2026-10-23.
- `config.js` says a user "can override the server in the launcher's settings"
  (`01. Source Reconstruction/revive-launcher/src/config.js:3-4`); the Settings panel only has RAM
  and colors (`01. Source Reconstruction/revive-launcher/renderer/index.html:266-288`), so the
  override exists only through raw IPC or editing the JSON.

**Hard-coded and dead UI.** The header says "Minecraft 1.21.11 · Fabric 0.18.4" and a chip says
"6 mods" no matter what the manifest returns
(`01. Source Reconstruction/revive-launcher/renderer/index.html:226`, `:251`); the 2026-10-23
default set was 8 jars. `openExternal` is bridged but never called in the Linux renderer
(`01. Source Reconstruction/revive-launcher/preload.js:15`).

**Unused capability kept in place.** The `inject` loader mode is documented as an option
(`01. Source Reconstruction/revive-launcher/src/config.js:47-49`) and is the loader's own default;
the launcher only avoids it by always sending `gate`.

**Build hygiene.** Two different builds both called `1.0.0`; CRLF line endings throughout
(authored on Windows); an `allowScripts` entry for `electron-winstaller` that the NSIS installer
does not use; a hand-written ZIP central-directory parser for reading `fabric.mod.json`
(`01. Source Reconstruction/revive-launcher/main.js:136-169`) although `adm-zip` is already bundled.

**Copied code.** No first-party launcher code was traced to another project; none of its
distinctive strings (`.cache-jrt`, `revive-token`, `fabric.addMods`) appear in the local Hackware
client, loader and server source trees. The dependencies are public packages; [REPORT.md](REPORT.md) diffed
them against the npm registry (not repeated here). The gate mode the launcher forces is a Revive
addition on top of the leaked Hackware loader
([LOADER-PROVENANCE.md](LOADER-PROVENANCE.md#what-revive-added-not-in-the-leak)).

---

## 10. If you ran it

The jars served on 2026-10-19 were reversed and did not steal accounts ([REPORT.md](REPORT.md),
[LOADER-TEARDOWN.md](LOADER-TEARDOWN.md), [CLIENT-TEARDOWN.md](CLIENT-TEARDOWN.md)); the
`revive-client.jar` served on 2026-10-23 is already a different build. The server can send different
code to different people at any time, and the launcher keeps working credentials on disk, so these
steps are cheap insurance.

- **Downloaded but never ran it:** delete the file. Nothing happened.
- **Installed or started it:** uninstall (Windows) or delete the AppImage, then remove the paths in
  [6](#6-host-artifacts), including the updater cache. On Windows use `dir /a` or `attrib -h -s` to
  reveal the hidden + system folders.
- **Logged in to a Revive account:** the password went to `revive-client.com`. Change it anywhere
  else it is reused (on the Windows build this is the website account password).
- **Pressed Play (signed in to Microsoft):**
  - Change the Microsoft password and use "Sign out everywhere" in the Microsoft account security
    settings. That revokes the refresh token stored as `msSession` and the cached `login.live.com`
    session.
  - Turn on two-step verification.
- **Because operator-supplied Java ran as you,** also check for persistence:
  - Linux: `~/.config/autostart`, `systemctl --user list-unit-files`, `crontab -l`, `~/.bashrc`,
    `~/.profile`.
  - Windows: Run keys, the Startup folder, Scheduled Tasks.
  - Rotate Discord and saved browser passwords if anything looks off.

---

## 11. Method, sources and limitations

**How the artifacts were opened.** All parsing was read-only, into fresh scratch directories outside
the repo:

- AppImage: ELF section headers read directly; SquashFS 4.0 parsed with a small Python reader (zlib
  only) to list all 94 entries and extract `app.asar`, `app-update.yml`, `AppRun`, the `.desktop`
  file and the Electron binary.
- Installer: PE headers read directly; NSIS first header located and its deflate-compressed script
  header decompressed to recover strings (paths, registry keys, appId); `app-64.7z` carved at its
  signature offset and read with the Windows `tar.exe` (libarchive 3.8.9).
- ASAR: header JSON parsed; every first-party file hashed against its integrity entry; dependency
  trees compared between platforms; selected dependency files extracted for reading.
- REA for artifact inventory and the JavaScript application graph:

| Evidence ID | What it covers |
|---|---|
| `ev_04e2c39fb0bc831235ae8fb2b15b557aa3021d48ec9d6f51bcb8194bc0972ea2` | `Revive-Setup.exe` inventory (REA sees a PE with no child containers; NSIS and 7z were handled manually) |
| `ev_6329d7e1efb2224c02f240bf0cf38d975918ecf3b858e372ac877785d69a2453` (parent `ev_9e7c61443dfb00165131a71059f121c4d385ed9cf5f62c7294507f550ce059d4`) | Linux `app.asar` graph: 1 window, 1 preload, 13 bridged members, 11 handlers, 0 sender checks |
| `ev_a0144fd1926a986f6973d064be72f54ef6d9fe70914bc41bd600a73c9c46c9b3` (parent `ev_3cf344884346d50cefc9bec615146b375c6233ebb1a72ad3b0f7e9a3cdf8b2cb`) | Windows `app.asar` graph: 14 bridged members, 12 handlers, 0 sender checks |

REA's semantic coverage was partial (relation budget reached in `node_modules`), so its counts were
checked by reading every first-party file. The evidence IDs listed in [REPORT.md](REPORT.md#rea-evidence) come
from an earlier REA session and were not retained in this one.

**REPORT.md claims left out because they could not be re-checked offline** (each needs an upstream
file): the AppImage runtime being byte-identical to electron-builder's `appimage-12.0.1`
`runtime-x64`; the Electron files being byte-identical to the official
`electron-v33.4.11-linux-x64.zip`; the six `usr/lib` libraries matching electron-builder's; every
dependency file matching its npm registry tarball; all 510 JavaScript files passing `node --check`.
They are plausible (version strings, file sets and sizes fit), but this document does not rely on
them. Every version string, count, offset and hash stated here was re-derived from the artifacts.

**Corrected from REPORT.md:** it counts "77 npm packages"; this analysis finds 75 package roots (74
top-level plus one nested `uuid`) in 836 files. REPORT.md did not examine `Revive-Setup.exe`; its
first-party code differs from the AppImage's, as described in [1.3](#13-appasar).

**Not covered:** the embedded NSIS uninstaller (not decompiled), anything server-side (what
`/account/*` does with passwords, what the update feed currently offers), and runtime behavior.
