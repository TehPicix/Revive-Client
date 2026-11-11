# Revive client teardown: revive-client.jar

Teardown of `revive-client.jar`, the Fabric mod that is the Revive "client" itself. It covers what
the mod is, every network call it makes, what it touches on disk and on the machine, how it gets the
Revive token, where its code comes from, and what that means for someone who runs it. The main
subject is the build served on **2026-10-19** (the one in `REPORT.md`), read through the
reconstructed source and checked against the original bytecode. A second build served on
**2026-10-23** was diffed against it ([section 10](#10-the-2026-10-23-build)). Everything here is
static: no jar or other artifact was run, and no host was contacted.

| | |
|---|---|
| **Artifact** | `revive-client.jar`, 34,356,543 bytes, SHA-256 `4df5c24c…6aca`, retrieved 2026-10-19. Not included in this repo |
| **Mod** | `revive-client` "ReviveClient" `1.21.11-local`, Fabric, Minecraft 1.21.11, Java 21 |
| **Contents** | 1,298 first-party classes under `revivedevelopment.reviveclient`, 170 mixins, 9 jar-in-jar libraries, 127 asset files |
| **Source** | `01. Source Reconstruction/revive-client` (decompiled, remapped to Yarn, hand-fixed to compile: 811 `.java` files, about 148,000 lines) |
| **Second build** | 34,556,953 bytes, SHA-256 `aaf9559c…cdba`, retrieved 2026-10-23 by a patched copy of the launcher. Not included in this repo |
| **Related** | [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md) (launcher), [LOADER-TEARDOWN.md](LOADER-TEARDOWN.md) (loader), [TEAMSYNC-PROTOCOL.md](TEAMSYNC-PROTOCOL.md), [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md), [GRIDFLY-PROTOCOL.md](GRIDFLY-PROTOCOL.md), [WEBSITE-TEARDOWN.md](WEBSITE-TEARDOWN.md) |

Citations are `path:line`, relative to the repo root. Line numbers point into the reconstructed
source. Where the decompiled code looked doubtful, the claim was re-checked against the shipped
bytecode with `javap`; those spots say "bytecode".

---

## TL;DR

1. **It is Meteor Client with the name changed.** Meteor's framework, startup sequence, addon API,
   telemetry and cape endpoints, double-click installer dialog, license file naming and even its
   title-screen splash list are all here, with "Meteor" replaced by "Revive" and Meteor's lead
   developer replaced by "Crownizzle". Every "meteor" string outside Meteor's own Orbit and Starscript
   libraries was scrubbed. Several modules credit other clients (Lambda, BepHax, Shoreline) in their
   own setting text. The README's "4% original" figure is not measured here. See [section 8](#8-provenance).
2. **No stealer in this build.** Nothing reads browser, Discord, wallet or launcher account files.
   Nothing defines classes, evaluates scripts or runs downloaded code. The Minecraft access token is
   read in exactly one place (PingBypass) and goes only to Mojang's session server.
3. **Always-on telemetry to a domain that is not on record as theirs.** Every 5 minutes, with no
   setting to turn it off, the client POSTs to `https://reviveclient.com/api/online/ping` (no hyphen).
   This is Meteor's `meteorclient.com` ping after a find-and-replace. `reviveclient.com` appears
   nowhere in the operator infrastructure mapped in DOMAIN-FORENSICS.md, and its owner is unknown.
4. **Capes poll the operator every 5 minutes**, also ungated, and the client then downloads cape
   images from whatever URLs the server lists, on any http or https host.
5. **TeamSync and Emotes send your Revive account token to whatever server they are pointed at**,
   including plaintext `ws://` for IP addresses. Both are off by default. TeamSync, when on, streams
   your position, server, health, supplies and nearby players 4 times a second, plus terrain. Its default
   host `team.revive-client.com` does not resolve. The emote server can write and delete emote files
   in your game directory.
6. **The client has no DRM of its own.** It never checks the Revive account and does not depend on
   the loader. A remote-key routine (`Auth` / `Obf`, `revive-auth.crownizzle.workers.dev`) is dead
   code. The loader's "encrypted download and inject" path cannot start this client: the bootstrap
   class it calls does not exist in the jar.
7. **On Windows it spawns PowerShell and enumerates processes for HUD gadgets.** The Spotify HUD
   writes and runs `spotify_media.ps1` with `-ExecutionPolicy Bypass` and uses JNA to walk every
   process. The Media HUD launches PowerShell every 2 seconds to read the window titles of VLC,
   Chrome, Firefox and Edge. All of it stays local and only runs if you add those HUD elements, but
   REPORT.md listed none of it.
8. **Sloppy.** A dev build was shipped (`1.21.11-local`, empty build number and commit, while the
   title screen says `PRE-RELEASE-0.0.2`). There is dead code, an unreachable GitHub update check,
   media keys that cannot work, mixin gates for compat packages that were deleted, and a dead default
   TeamSync host. See [section 9](#9-quality-tells).
9. **The 2026-10-23 build** adds 64 classes (a headless PingBypass server mode and one module) and
   changes 100, but contacts no new hosts.

---

## 1. What the client is

| Field | Value | Source |
|---|---|---|
| Mod id / name | `revive-client`, "ReviveClient", *"ReviveClient - A powerful Minecraft utility mod."* | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:3-6` |
| Version | `1.21.11-local`; `revive-client:build_number` and `revive-client:commit` are empty strings | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:5`, `:33-35` |
| Authors | `Crownizzle`, `_Synful`. The title screen also credits `Zandax` and `King_Soft` | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:7-10`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/titlemenu/CustomTitleScreen.java:76-79` |
| License | `GPL-3.0`; the full GPLv3 text ships as `LICENSE_revive-client` (674 lines) | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:17` |
| Contact links | `https://reviveclient.com`, `https://github.com/ReviveClient/revive-client`, `https://reviveclient.com/discord` | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:11-16` |
| Requires | Minecraft `1.21.11`, Fabric Loader `>=0.18.2`, Java `>=21`, client side only. Declares no dependency on `revive-loader` | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:19`, `:42-48` |
| Breaks | `optifabric`, `feather`, `origins`, `sodium <0.6.12`, `morechathistory` | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:49-55` |
| Entrypoints | `client`: `revivedevelopment.reviveclient.ReviveClient`; `modmenu`: `ModMenuIntegration` | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:20-27` |
| `Main-Class` | `revivedevelopment.reviveclient.Main`: a Swing dialog shown if the jar is double-clicked | jar manifest; `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:15-51` |
| Build | Fabric Loom 1.15.5, Gradle 9.2.0, intermediary namespace, static mixin remap (so no refmap in the jar) | jar manifest |
| Mixins | `revive-client.mixins.json`: 170 client mixins (140 in the root package, 25 `pingbypass.*`, 5 `baritone.*`; 59 are accessors or invokers). `required: true`, `JAVA_21`, config plugin `MixinPlugin` | `01. Source Reconstruction/revive-client/src/main/resources/revive-client.mixins.json` |
| Jar-in-jar | fabric-api-base 1.0.5, fabric-resource-loader-v0 3.3.4, javassist 3.28.0-GA, jorbis 0.0.17.4, orbit 0.2.4, reflections 0.10.2, starscript 0.2.2, tritonus-share 0.3.7.4, vorbisspi 1.0.3.3 | jar `META-INF/jars/` |
| Assets | 127 files, 30.8 MB: 59 JSON, 27 PNG, 19 shaders, 10 TTF, 9 OGG (menu music), 1 JPG, 1 GIF, 1 TXT | jar `assets/revive-client/` |

**Startup.** `ReviveClient.onInitializeClient` creates `<gameDir>/revive-client/`, loads addons,
runs every `@PreInit` method, registers categories and systems, creates the managers (including
`TeamSyncManager` and `EmoteSyncManager`), loads saved config, runs every `@PostInit` method, and
installs a shutdown hook that sends the telemetry "leave" and saves config
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:86-151`).

**How it is delivered.** In practice the client is not delivered encrypted. The launcher runs the
loader in `gate` mode (`01. Source Reconstruction/revive-launcher/src/config.js:49`), downloads
`revive-client.jar` as a plain jar into a hidden per-launch folder, and hands it to Fabric with
`-Dfabric.addMods` (`01. Source Reconstruction/revive-launcher/src/minecraft.js:283-302`). In gate
mode the loader only verifies the account
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:31-33`).
The loader's own default is `inject`
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:79-86`),
which downloads an encrypted `client.bin`, defines its classes in memory and calls
`revivedevelopment.client.ReviveBootstrap#init`
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/inject/ClientInjector.java:6`, `:13`).
Neither client build contains that class (the package is `revivedevelopment.reviveclient` and there
is no `ReviveBootstrap`), and an in-memory classloader would also sit outside Fabric's mixin and
entrypoint handling. So inject mode, as written, cannot start this client. Details are in
[LOADER-TEARDOWN.md](LOADER-TEARDOWN.md) and [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md).

---

## 2. Feature inventory

124 modules in 6 categories. Every module class in the source is registered; there are no orphans.
Only one module is on by default (`ElytraSaver`,
`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/movement/ElytraSaver.java:17`).

| Category | Modules | Notable |
|---|---|---|
| Combat | 23 | AutoCrystal (its engine `AutoCrystalFeature.java` alone is 4,645 lines), Surround, AutoTrap, AnchorAura, SwordAura, MaceAura, MaceDive, PistonCrystal, ChineseAura, Offhand, AutoMine, Phase, Criticals, Velocity |
| Player | 32 | **PingBypass**, **TeamSync**, **Irc**, SilentMine (4,060 lines), SpeedMine, Nuker, MapDuplicator, AutoLogPlus, PacketLimiter, Rubberband, Timer, XCarry, ChestStealer |
| Movement | 22 | ElytraFly, ElytraControl, ElytraRecast, ElytraSaver, BaritoneElytraOverworld, TrailFollower, AFKVanillaFly, GrimShulkerLaunch, GrimEntityBoost, RocketBoost, BoatFly, Scaffold |
| Render | 26 | **Emotes**, **BetterScreenshot**, ESP / ESPPlus, Tracers / TracersPlus, Nametags, PearlOwners, LogoutSpots, StorageESP, HoleESP, PopChams, Freecam, FreeLook, NoRender |
| Hunting | 16 | **StashFinderPlus**, **DiscordNotifs**, NewChunks, AreaLoader, AutoRegear (3,175 lines), ChatSigns, LoreLocator, VanityESP, CaveAirESP, FlowESP, LavaColumns, NetherMobDetection |
| Gui | 5 | Gui, HudModule, AntiCheatConfig, MultiTask, Notifier |

Bold modules are the ones with network, process or file side effects covered below. Registration is
in `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/Modules.java:183-315`
and `:616-622`. The registration blocks and the declared categories don't line up: Follower, NoSlow,
ElytraReplace and ElytraSaver are registered in `initCombat` but declare Movement; TrailFollower and
AFKVanillaFly live in the `hunting` package but declare Movement.

Other surfaces:

- **HUD:** 20 element types, including Spotify HUD, Media HUD, TeamSupplies, PlayerRadar and
  TargetHud (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/Hud.java:125-144`).
  Neither Spotify nor Media HUD is in the default layout (`:224-235`).
- **Commands:** 7 (`config`, `emote`, `fakeplayer`, `friend`, `nbt`, `surface`, `team`), prefix `.`
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/commands/Commands.java:24-30`).
- **GUI:** two click-GUI styles, `NewGuiScreen` and `ModernGuiScreen`
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:191-194`),
  plus a custom title screen with menu music that replaces the vanilla one
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/mixin/TitleScreenMixin.java:24-30`).
- **Baritone:** optional. A mixin plugin rewrites Baritone's native elytra pathfinder calls to a
  Java implementation to allow elytra pathing in the Overworld ([6.2](#62-reflection-classloading-and-bytecode-rewriting)).

---

## 3. Network calls

### 3.1 Every destination

"Gate" is what has to be true for the call to happen. "Off by default" means the module or setting
ships disabled.

| # | Destination | When | What goes out | Gate | Source |
|---|---|---|---|---|---|
| 1 | `POST https://reviveclient.com/api/online/ping` | First client tick, then every 300,000 ms | Empty body. Your IP, plus a spoofed Chrome 112 user agent | **None, always on** | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/OnlinePlayers.java:9-15`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/mixin/MinecraftClientMixin.java:106-111` |
| 2 | `POST https://reviveclient.com/api/online/leave` | JVM shutdown (queued to a daemon thread, so best effort) | Empty body | **None** | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/OnlinePlayers.java:17-19`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:133-137` |
| 3 | `GET <capes host>/api/capes` and `/api/capeowners` | At startup, then every 6,000 ticks (about 5 min) | Nothing but IP; user agent `Revive-Client` | **None** | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/Capes.java:37-40`, `:58-81` |
| 4 | Cape image URLs listed by the server (relative to the capes host, or absolute on **any** http/https host) | When a player with a listed cape is rendered | `GET` | **None** | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/Capes.java:94-108`, `:159-167`, `:302-318` |
| 5 | `wss://emotes.revive-client.com/ws` (configurable) | While Emotes is on | Revive token, Minecraft UUID and name, client version; emote start/stop events; pings | Emotes module, off by default | [3.4](#34-emotes) |
| 6 | `https://emotes.revive-client.com/api/emotes`, then each listed file URL (any host) | After `welcome`, and when the server sends `library` | `GET` | Emotes module | [3.4](#34-emotes) |
| 7 | `wss://team.revive-client.com/ws` (configurable; **NXDOMAIN**) | While TeamSync is on | Revive token, identity, live position and more | TeamSync module, off by default | [3.5](#35-teamsync-summary) |
| 8 | PingBypass VPS, TCP `vps-ip:vps-port` (default `127.0.0.1:25570`) | While PingBypass is on | Minecraft name and UUID, a password proof, then encrypted frames: module settings, friends, inputs; your server joins are routed through it | PingBypass module, off by default | [3.6](#36-pingbypass) |
| 9 | Mojang session server (`joinServer` via authlib) | During a routed PingBypass login, when the VPS asks | Minecraft access token and a VPS-supplied `serverId`, to Mojang | PingBypass on and `route-joins` (default on) | [section 5](#5-minecraft-session-token-and-microsoft-account) |
| 10 | IRC server, TCP (default `127.0.0.1:6667`) | While Irc is on | `NICK`/`USER` = your Minecraft name; lines you type after `!!` | Irc module, off by default; plaintext | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/Irc.java:44-49`, `:74-80`, `:158-166` |
| 11 | Discord webhooks you supply | DiscordNotifs, StashFinderPlus, TrailFollower | Chat lines, stash coordinates, rendered terrain images, player names | A webhook URL must be set; StashFinderPlus `send-webhook` is off by default | [3.7](#37-user-configured-outputs) |
| 12 | Your own Supabase project, `/rest/v1/rpc/ingest_stash_hit` | StashFinderPlus hit | Hit JSON, your publishable key and ingest token | `send-database`, off by default; host must match `*.supabase.co` | [3.7](#37-user-configured-outputs) |
| 13 | `sessionserver.mojang.com/session/minecraft/profile/<uuid>`, `api.mojang.com/users/profiles/minecraft/<name>` | Friends updates, PearlOwners lookups, player heads | A UUID or a name | Friends list, PearlOwners module | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/friends/Friend.java:48-62`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/PearlOwners.java:315-330`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/render/PlayerHeadUtils.java:37-50` |
| 14 | Skin URL from the Mojang profile (normally `textures.minecraft.net`) | Player head textures | `GET` | Same as 13 | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/render/PlayerHeadTexture.java:28` |
| 15 | `itunes.apple.com/search`, then the artwork URL it returns | Spotify HUD, on each track change | `"<artist> <song>"` as the search term | Spotify HUD element added | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyArtFetcher.java:35-69`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/elements/SpotifyHud.java:246` |
| 16 | Spotify's `mpris:artUrl` (Linux) | Spotify HUD on Linux, track change | `GET` | Spotify HUD element added | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyMediaBridge.java:150-153`, `:171-188` |
| 17 | `api.github.com/repos/<owner>/<repo>/branches/...` and `/compare/...` | Title-screen credits and commits screen | Repo, branch, commit; a bearer token from env `revive.github.authorization` if set | Only for addons that declare a repo **and** a commit. The stock build's commit is empty, and the vanilla title screen these credits draw on is replaced, so in practice never | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/player/TitleScreenCredits.java:35-73`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/screens/CommitsScreen.java:26-47`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/addons/GithubRepo.java:19-28` |
| 18 | Browser opens (not client HTTP): `https://reviveclient.com/faq/installation`; TeamSync map link; commits-screen buttons | Only on a click | n/a | User action | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:29`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:1643-1662` |

All requests made through the shared `Http` helper (rows 1, 2, 13 to 17) carry the user agent
`Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/112.0.0.0 Safari/537.36`, whatever the real
OS (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/Http.java:54`).

**Not present:** no self-update or version check, no crash reporter or analytics SDK, no Discord
Rich Presence, and nothing that switches a networked module on by itself. The only network traffic
that happens on a fresh install without touching any setting is rows 1 to 4.

### 3.2 Telemetry: `reviveclient.com`

`OnlinePlayers.update()` is injected at the head of every client tick and fires a ping whenever
300,000 ms have passed since the last one, so the first ping goes out on the first tick
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/OnlinePlayers.java:9-15`;
the call site and the `300000` constant were confirmed in bytecode). `leave()` is queued from the
shutdown hook (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:133-137`).
There is no setting for either.

The body is empty, so what the server learns is the source IP, the time, and that a Revive client is
running. Two things make this worth flagging:

- The host is **`reviveclient.com`**, not the operator's `revive-client.com`. The same spelling is
  in the mod's homepage, Discord link, installer dialog, addon metadata and a splash text. It is
  the result of replacing `meteorclient` with `reviveclient` throughout Meteor's code
  ([8.1](#81-meteor-client)).
- `reviveclient.com` is not in DOMAIN-FORENSICS.md's host inventory and was not resolved for this
  write-up (no network contact). If the operator does not hold it, whoever does receives the IP of
  every running Revive client every 5 minutes.

### 3.3 Capes

The capes host comes from the `revive.loader.host` system property, then the `REVIVE_LOADER_HOST`
environment variable, then the default `https://revive-client.com`
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/Capes.java:210-227`).
The loader sets that property during `preLaunch`
(`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:26-28`),
so in a normal launch the capes requests go to the same host as the license check. `/api/capes`
returns `name url [frameTimes]` lines and `/api/capeowners` returns `uuid name` lines
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/Capes.java:88-138`).

`resolve()` resolves each cape URL against the capes host, but an absolute `http` or `https` URL
passes through unchanged (`:159-167`). The capes server can therefore make every client fetch an
image from any third-party host, revealing the client IPs to that host. Images are decoded with
Minecraft's `NativeImage` and split into animation frames (`:302-362`). Capes are drawn by
`CapeFeatureRendererMixin` and `ElytraFeatureRendererMixin`. The mapping from Minecraft UUID to cape
is what the loader's account check feeds (the loader warns *"capes will not be linked for this
launch"* when it cannot read the Minecraft identity:
`01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:128`).

### 3.4 Emotes

Emotes are not covered by TEAMSYNC-PROTOCOL.md, so here is the full picture.

| Property | Value | Source |
|---|---|---|
| Default URL | `wss://emotes.revive-client.com/ws`. Bare IP or `localhost` becomes `ws://<host>:8091/ws` (plaintext); `http://` becomes `ws://` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:58`, `:202-246` |
| When | Only while the **emotes** module is on (off by default) | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:260-297` |
| Auth | `ReviveAccount` (default): token from the module's `token` setting, else `<gameDir>/revive-token.txt`. `ServerKey`: a shared password | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/Emotes.java:39-75`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:765-782` |
| `hello` | `v`:1, `auth`:{`token`} or {`key`}, `mcUuid`, `mcName`, `client`:`"revive/<version>"` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:545-567` |
| Other outbound | `ping`; `emote` with `op` `start`/`stop`, the emote UUID and a tick. No position or server address | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:371-383` |
| Inbound | `welcome` (incl. `you.revive`, your Revive account name, and the running emotes), `pong`, `emote`, `library`, `notice`, `error` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:842-982` |
| Toggles | `send-emotes` and `show-others`, both default on | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/Emotes.java:76-79` |

**Library sync.** After `welcome`, and whenever the server sends `library`, the client fetches
`<http base>/api/emotes`, a listing of `{file, sha256, url}` entries
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:1010-1127`).
For each entry whose file name matches `[A-Za-z0-9][A-Za-z0-9._-]{0,63}\.json` (`:1072`) it
downloads the URL (relative to the server, or absolute on any host), keeps it only if the SHA-256
matches the listing, and writes it to `<gameDir>/revive/emotes/server/`. It then deletes every other
`.json` in that folder (`:1098-1108`). Limits: 512 files, 512 KiB each. The file name filter prevents
path traversal, and the files are parsed only as keyframe animation data
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/emotes/EmoteLoader.java:34-60`).
The checksum comes from the same server as the file, so it guards against corruption, not against the
server. As with capes, absolute URLs let the server point clients at third-party hosts.

DOMAIN-FORENSICS.md (5.4) found that `emotes.revive-client.com` (behind Cloudflare) and
`team.reviveon.top` return byte-identical error bodies, i.e. the same backend app.

### 3.5 TeamSync (summary)

The wire protocol is documented in [TEAMSYNC-PROTOCOL.md](TEAMSYNC-PROTOCOL.md). The points that
matter for a user:

- **Off by default** and never enabled by code. The connection is opened only while the
  **team-sync** module is on
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:470-514`).
- **Default host is dead.** `wss://team.revive-client.com/ws`
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/TeamSync.java:24`)
  is NXDOMAIN (DOMAIN-FORENSICS.md, TL;DR item 5). The live relay appears to be `team.reviveon.top`,
  which a user would have to type in.
- **Your Revive token goes to whatever server is configured.** In the default `ReviveAccount` mode,
  `hello.auth.token` carries the token from the `token` setting or `revive-token.txt`
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:803-814`, `:1015-1032`).
  A bare IP or `localhost` is turned into plaintext `ws://<host>:8090/ws` (`:419-467`). Pointing
  TeamSync at a self-hosted or third-party server without switching to `Server Key` hands that server
  your Revive account credential, in clear text if it is an IP.
- **What streams out, 4 times a second, as soon as you are connected** (no team needed): dimension,
  x/y/z, yaw, health, absorption, entity id, the server address you are on and, with
  `share-supplies`, counts of crystals, totems, pearls, obsidian, gapples and XP bottles. At the same
  rate: up to 256 players in your render distance with their coordinates and health
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:561-618`).
  On top of that come event messages for totem pops, deaths, regears and logout spots, and, while you
  are in a team, map-style terrain tiles of the chunks around you (`:516-534`).
  Every sharing toggle defaults to on
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/TeamSync.java:56-87`).
- **The server can edit your friends list.** With `auto-friend` (default on), every member name in a
  `team` message is added to your friends
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:1474-1511`, `:1688-1712`).
  Combat modules skip friends (for example
  `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/combat/autocrystal/AutoCrystalFeature.java:2174`,
  `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/combat/SwordAura.java:797`),
  so whoever runs the team server can stop your combat modules from targeting chosen players.
- **Map links** from the server are shown as a clickable chat link and opened only on click (`:1643-1662`).

### 3.6 PingBypass

PingBypass routes your server connections through a "headless" client on a VPS. Everything about it
is user-configured; the default VPS is `127.0.0.1:25570`
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/PingBypass.java:116`, `:124`).

- The control link is a TCP socket to the VPS
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/pingbypass/client/PbClient.java:176-181`).
  Both sides prove knowledge of a password with PBKDF2-HMAC-SHA256 and HMAC; the password itself is
  not sent. Frames are then AES-GCM encrypted
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/pingbypass/PbCrypto.java:38-121`).
  The `auth` message carries your Minecraft name and UUID
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/pingbypass/client/PbClient.java:207-214`).
- Once connected, the client sends the VPS your module settings, friends list, inputs and options
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/PingBypass.java:1135`, `:1302`, `:1375`).
- With `route-joins` on (default), joins are redirected to the VPS, and the VPS asks the client to
  perform the Mojang session join for the real server. See [section 5](#5-minecraft-session-token-and-microsoft-account).

### 3.7 User-configured outputs

| Feature | Destination | What is sent | Notes | Source |
|---|---|---|---|---|
| Irc | Your IRC server (default `127.0.0.1:6667`) | Nick = Minecraft name; channel messages you prefix with `!!` | Plaintext TCP | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/Irc.java:44-49`, `:158-166` |
| DiscordNotifs | Your webhook | Selected chat, whispers, deaths, disconnects, queue position, players in range, portals; `all-messages` forwards every chat line | Outgoing whispers (lines starting `to `) are forwarded even with `whisper` off (bytecode confirms) | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/DiscordNotifs.java:312-337`, `:345-360` |
| StashFinderPlus | Your webhook | Stash hits with exact coordinates, sign text, optional rendered terrain map and 3D preview images | `send-webhook` off by default; HTTPS required | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/StashFinderPlus.java:360-366`, `:1010-1056` |
| StashFinderPlus | Your Supabase project | Hit JSON, publishable key, ingest token | `send-database` off by default; host must be `*.supabase.co` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/StashFinderPlus.java:117-150`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/StashArchive.java:107-160` |
| TrailFollower | Your webhook | Status updates, your player name | JSON built by string concatenation without escaping | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/TrailFollower.java:263-267`, `:659-660`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/HuntingUtils.java:114-129` |

---

## 4. The Revive token: how the client gets it and what it does with it

| Step | What happens | Source |
|---|---|---|
| Launcher | Writes the Revive account token to `<gameDir>/revive-token.txt`, hides it on Windows (`attrib +h +s`), deletes it after the game exits | `01. Source Reconstruction/revive-launcher/src/minecraft.js:58-63`, `:101` |
| Loader | Reads `-Drevive.loader.token`, then `REVIVE_ACCESS_TOKEN`, then `revive-token.txt`, and uses it for `/api/authorize`. It passes nothing to the client except the system property `revive.loader.host` | `01. Source Reconstruction/revive-loader/src/main/java/revivedevelopment/loader/PreLaunch.java:26-28`, `:54-77` |
| Client | Reads the token **itself**, from the module's `token` setting or `<gameDir>/revive-token.txt`, and only when TeamSync or Emotes connects in `ReviveAccount` mode. It ignores `-Drevive.loader.token` and `REVIVE_ACCESS_TOKEN` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:1015-1032`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:765-782` |
| Use | Sent as `hello.auth.token` to the TeamSync or Emotes server. Never logged (the debug log prints only the URL) | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/TeamSyncManager.java:803-814` |

What the client does **not** do:

- **No license check.** It never calls `/api/authorize`, never validates the token, and does not
  check that the loader ran. Nothing in `fabric.mod.json` depends on `revive-loader`. All the access
  control lives in a separate mod that sits next to the client in the same folder.
- **The leftover remote-key routine is dead.** `ReviveClient.checkAuth()` would read
  `revive-key.txt` from the process working directory, send it to
  `https://revive-auth.crownizzle.workers.dev/?key=...`, and use the returned secret as the key for
  `Obf.s()`, a repeating-key XOR string decoder with a hard-coded fallback key (`rvC9…mWf`)
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:153-160`,
  `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/Auth.java:15-40`,
  `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/Obf.java:7-19`).
  In bytecode, `checkAuth` is never invoked and no class calls `Obf.s`; no string in the jar is
  XOR-encoded. `crownizzle` is the operator's Cloudflare Workers account name (DOMAIN-FORENSICS.md,
  section 9).
- If you type a token into the `token` override, it is saved in plaintext in
  `<gameDir>/revive-client/modules.nbt` and included in "copy full config" ([7.3](#73-clipboard)).

---

## 5. Minecraft session token and Microsoft account

**The session token is read in exactly one place.** `PingBypass.handleJoin()` calls
`sessionService().joinServer(uuid, session.getAccessToken(), serverId)`, which is authlib's call to
Mojang's session server
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/PingBypass.java:777-800`).
In bytecode, `Session.getAccessToken` (`method_1674`) is referenced by `PingBypass.class` and nothing
else. The token itself never goes to the VPS, the operator or any other host.

The catch is the `serverId`. It comes from the VPS message (`:779`) and the client does not compute
it. The only guard is that a routed login of this client must be in progress: the connection must
be open, the call must arrive within 60 s of the routed connect, and the VPS must echo a binding that
equals the SHA-256 of the encrypted key this client sent
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/pingbypass/client/PbRouting.java:365`, `:386-389`, `:516-521`).
Within that window, a VPS you do not control could ask your client to authorize a join to a different
server as you. That is inherent to how PingBypass works, so only use a VPS you run yourself.

Not present anywhere in the client:

- no reads of launch arguments (`--accessToken` is read by the **loader**, see
  [LOADER-TEARDOWN.md](LOADER-TEARDOWN.md)),
- no Microsoft, Xbox Live or MSAL endpoints, and no access to `launcher_accounts.json`,
  `launcher_profiles.json`, the Revive launcher's `revive-launcher.json` or its `msSession`,
- no account manager: the client registers only the Modules, Config, Friends, Profiles and Hud
  systems (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/Systems.java:25-36`).
  Meteor's account system, which stores Microsoft refresh tokens, is gone.

---

## 6. Code execution surface

### 6.1 Remote or downloaded code: none

A byte search of all 1,298 classes found no `defineClass`, `URLClassLoader`, `ScriptEngine` /
`javax/script`, `sun/misc/Unsafe`, `Instrumentation` or `loadLibrary`. No `Class.forName` takes a
name from the network. Everything the client downloads is parsed as data: cape PNGs
(`NativeImage`), emote JSON (`EmoteLoader`), album art (LWJGL's STB image decoder), skins (`ImageIO`)
and JSON from Mojang, iTunes and GitHub.

The `revive` addon entrypoint loads other Fabric mods that declare it, the same way Meteor addons
work (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/addons/AddonManager.java:57-87`).
That only covers mods already installed locally.

The real remote-code risk is one layer up: the launcher downloads fresh, unsigned jars every launch,
and the loader has an in-memory inject path. See [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md) and
[LOADER-TEARDOWN.md](LOADER-TEARDOWN.md).

### 6.2 Reflection, classloading and bytecode rewriting

| Mechanism | What it does | Source |
|---|---|---|
| `ReflectInit` + Reflections | Scans the client package (and addon packages) for `@PreInit` / `@PostInit` methods and invokes them in dependency order | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/ReflectInit.java:25-89` |
| Orbit lambda factory | Registers `MethodHandles.lookup` factories for the event bus | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:104-115`, `:224-230` |
| ASM read of Baritone classes | Reads Baritone `.class` resources to check mixin targets before applying the `baritone.*` mixins | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/BaritoneMixinGuard.java:131-149` |
| Bytecode rewrite in `postApply` | Rewrites `INVOKESTATIC dev/babbaj/pathfinder/NetherPathfinder` calls in four Baritone elytra classes to `BaritoneOverworldPathfinder` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/MixinPlugin.java:68-72`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/BaritoneNativeCalls.java:34-57` |
| `Class.forName` on fixed names | ViaFabricPlus / ViaVersion internals for PingBypass, Baritone presence | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/pingbypass/PbVia.java:66-70`, `:272-291`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/BaritoneBridge.java:16` |

Javassist is bundled only because Reflections depends on it; the client never calls it.

### 6.3 Native code

The jar contains no native libraries (no `.dll`, `.so` or `.dylib`, inside the jar or the nested
jars, and no executable magic bytes in the assets). It does call into Windows through **JNA**, which
Minecraft ships:

- `SpotifyGrabber` loads `kernel32` and `user32`, takes a process snapshot, opens **every process**
  with `PROCESS_QUERY_LIMITED_INFORMATION` to read its image path, and then calls `EnumWindows` /
  `GetWindowTextW` on the Spotify windows to read the song title
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyGrabber.java:21-95`, `:98`, `:110`).
  It runs every 2 s while a Spotify HUD element has ticked in the last 5 s
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/elements/SpotifyHud.java:199-215`).

Album art is decoded with LWJGL's STB image library, which is also native code shipped by Minecraft.

### 6.4 Child processes

| Command | When | Source |
|---|---|---|
| `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <gameDir>\revive-client\spotify_media.ps1` | Windows, Spotify HUD added. Long-running; restarted 15 s after it exits. The script is a constant in the jar, rewritten to disk on every start; it reads the Windows media session for Spotify (title, artist, position, thumbnail) | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyMediaBridge.java:45-92`, `:258-271` |
| `dbus-send --session --print-reply --dest=org.mpris.MediaPlayer2.spotify ...` | Linux, Spotify HUD added, every 750 ms | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyMediaBridge.java:132-169`, `:190-201` |
| `powershell -Command (Get-Process -Name vlc, chrome, firefox, msedge ...).MainWindowTitle` | Windows, Media HUD added, **every 2 s** for the rest of the session (the scheduler is never stopped). Reads the window title of those browsers and players and shows it on screen | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/WindowsMediaController.java:8-23`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/elements/MediaHud.java:59-73` |
| `powershell -Command (New-Object -ComObject WScript.Shell).SendKeys('^{VK_MEDIA_...}')` | Media HUD key binds (default `P`, `N`, `B`) | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/WindowsMediaController.java:37-43` |
| `powershell -NoProfile -STA -Command Add-Type ...; [Clipboard]::SetImage(...)` | BetterScreenshot fallback on Windows, if Java's clipboard call fails | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/BetterScreenshot.java:125-136` |
| `pactl list sink-inputs`, `pactl set-sink-input-volume`, `paplay --volume=... /tmp/revive_music_*.wav` | Linux, title-screen music | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/titlemenu/TitleMusicPlayer.java:199`, `:214`, `:457-530` |
| `rundll32 url.dll,FileProtocolHandler <url>` / `open` / `xdg-open` | Only when the jar is double-clicked outside Minecraft | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:64-114` |

None of these commands takes text from the network. The BetterScreenshot fallback embeds a local
screenshot path in a PowerShell here-string.

---

## 7. Local footprint: disk, clipboard, screenshots

### 7.1 Files

| Path | Read / write | Contents | Source |
|---|---|---|---|
| `<gameDir>/revive-client/` | read/write | Config root (`ReviveClient.FOLDER`) | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:68` |
| `revive-client/modules.nbt`, `config.nbt`, `friends.nbt`, `profiles.nbt`, `hud.nbt` | read/write | All settings, in plaintext NBT, **including** TeamSync/Emotes `token` and `key`, the PingBypass password, webhook URLs (which contain the webhook secret), Supabase key and ingest token | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/System.java:22-57` |
| `revive-client/profiles/`, `gui/`, `gui-styles/`, `newgui-layout.nbt` | read/write | Profiles, themes, GUI layout | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/profiles/Profiles.java:18`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/GuiThemes.java:16` |
| `revive-client/spotify_media.ps1` | write, then executed | The Spotify media script | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyMediaBridge.java:258-271` |
| `revive-client/stash-finder-plus/` (`stash-hits.jsonl`, `<world>/stashes.json`, `.csv`); legacy `better-stash-finder/` | read/write | Stash hits with coordinates | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/StashFinderPlus.java:451`, `:1335`, `:1419-1423` |
| `revive-client/pearl-owners/`, `arealoader/`, `flow-esp-cache/`, `chatsigns-blacklist.txt` | read/write | Module data | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/PearlOwners.java:351`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/FlowESP.java:229`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/ChatSigns.java:303` |
| `<gameDir>/revive/emotes/` and `revive/emotes/server/` | read; `server/` written and pruned by the emote server | Emote JSON. Note `revive/`, not `revive-client/` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/emotes/EmoteLibrary.java:36-42` |
| `<gameDir>/revive-token.txt` | read | Revive token (written by the launcher) | [section 4](#4-the-revive-token-how-the-client-gets-it-and-what-it-does-with-it) |
| `<gameDir>/screenshots/*.png` | read | BetterScreenshot | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/BetterScreenshot.java:138-141` |
| `revive-key.txt` in the working directory | read (dead code) | Remote-key input | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/Auth.java:19` |
| System temp dir: `revive-client*.nbt`, `revive_music_*.wav` (Linux) | write | Temporary save copies; decoded music | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/System.java:39`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/titlemenu/TitleMusicPlayer.java:463` |

### 7.2 Outside the game directory

Besides the temp files above, the client lists the system and user font folders to offer custom fonts
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/render/FontUtils.java:117-128`),
reads other processes' image paths and window titles on Windows ([6.3](#63-native-code),
[6.4](#64-child-processes)), and, only when the jar is double-clicked, creates and opens
`%APPDATA%\.minecraft\mods` (on Linux it opens `~/.minecraft`, not its `mods` folder)
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:31-49`).
Nothing reads or writes user documents, browser profiles, SSH keys or other applications' files.
There is no persistence: no autostart, scheduled task, service or shell-profile entry.

### 7.3 Clipboard

Clipboard **reads** happen only in Ctrl+V handlers and explicit "paste" actions (setting fields,
colors, module and full-config import). There is no background clipboard polling. Clipboard
**writes** come from `.nbt copy`, color and config copy, and BetterScreenshot.

Two config details matter:

- **Copy full config** puts every module setting, the HUD and client config on the clipboard as
  Base64 NBT
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/newgui/NewGuiScreen.java:2271-2283`).
  That includes the secrets listed in 7.1, so sharing a config can leak your Revive token override,
  webhooks and passwords.
- **Paste full config** applies everything, including `server-url` and `auth-mode` for TeamSync and
  Emotes (`:2285-2303`). A config pasted from someone else can point those modules at their server; in
  `ReviveAccount` mode your token goes there the next time you enable the module. Decoding uses
  `NbtSizeTracker.ofUnlimitedBytes()`
  (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/misc/NbtUtils.java:77-81`).

### 7.4 Screenshots and images

No code captures the screen, reads the framebuffer or uses `java.awt.Robot`. BetterScreenshot
(off by default) lists `<gameDir>/screenshots` every tick and copies each new PNG that Minecraft
saved to the clipboard
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/BetterScreenshot.java:57-123`).
Its class initializer also flips `java.awt.headless` to `false` (`:143-147`). The StashFinderPlus
webhook images are drawn in software from world block data, not captured from the screen
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/StashScene.java:78-79`).

### 7.5 Logs

The client logs through SLF4J into Minecraft's normal `logs/` and prints a few debug lines to stdout
(Spotify and GUI errors). Failed cape and emote downloads are logged with their URL and HTTP status.
No token, key or password is logged; TeamSync's own debug option says so and its debug calls print
only the URL.

---

## 8. Provenance

### 8.1 Meteor Client

The framework is [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) (GPL-3.0). The
strongest evidence is identifiers and strings that only make sense as a mechanical rename. The right
column describes Meteor's public source; no byte-level diff against a specific Meteor commit was done
for this write-up (it was offline).

| Revive | Where | Meteor equivalent |
|---|---|---|
| Splash list: *"Revive on Crack!"*, *"Star Revive Client on GitHub!"*, *"Based utility mod."*, *"§6Crownizzle §fbased god"*, *"§4reviveclient.com"*, *"§4Revive on Crack!"*, *"§6Revive on Crack!"* | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/mixin/SplashTextResourceSupplierMixin.java:40-48` | The same seven lines with "Meteor", `meteorclient.com` and Meteor's lead developer MineGame159. *"Based utility mod."* is Meteor's own mod description |
| `revive-client:build_number`, `revive-client:color` = `145,61,226`, `revive-client:commit`, the Mod Menu Discord link | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:32-41` | `meteor-client:*` keys; 145,61,226 is Meteor's purple. Revive also uses it as Crownizzle's credit color (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/titlemenu/CustomTitleScreen.java:76`) |
| `reviveclient.com` (homepage, `/discord`, `/faq/installation`, `/api/online/*`) | `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:12-15`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:29`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/OnlinePlayers.java:12`, `:18` | `meteorclient.com` with the same paths |
| `POST /api/online/ping` every 5 min and `/api/online/leave` on exit | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/OnlinePlayers.java:3-20` | Meteor's `OnlinePlayers`, same class name, logic and paths |
| `/api/capeowners` + `/api/capes`, space-separated line formats | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/Capes.java:38-39`, `:88-138` | Meteor's capes API |
| Built-in addon with `GithubRepo("ReviveDevelopment", "revive-client")`; addon entrypoint `revive`; env var `revive.github.authorization`; *"Addon \"%s\" is too old and cannot be ran."* | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/addons/AddonManager.java:16-41`, `:57`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/addons/GithubRepo.java:23`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:112` | `MeteorDevelopment/meteor-client`, entrypoint `meteor`, `meteor.github.authorization`, the same error string |
| Double-click dialog *"To install Revive Client you need to put it in your mods folder and run Fabric for latest Minecraft version."* with "Open Wiki" / "Open Mods Folder" | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:17-26` | Meteor's launcher `Main`, same text with "Meteor Client" |
| `LICENSE_revive-client`, version `1.21.11-local` | jar root; `01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:5` | Meteor's build renames `LICENSE` to `LICENSE_<archivesName>` and uses the suffix `local` when no build number is set |
| `MixinPlugin` gating mixin packages `sodium`, `indigo`, `lithium`, `viafabricplus` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/MixinPlugin.java:38-56` | Meteor ships those compat mixin packages. **The Revive jar has none of them**, only `baritone` and `pingbypass`, so these branches are leftovers |
| Mixin interface prefix `revive$` (used in 171 source files), e.g. `IBox.revive$expand`, `IText.revive$invalidateCache` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/mixininterface/IBox.java:6` | Meteor's `meteor$` prefix on the same interfaces |
| `ReviveExecutor`, `ReviveToast`, `ReviveStarscript`, `ReviveTextHud`, `ReviveAddon`, `events/revive/` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/network/ReviveExecutor.java:8`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/render/ReviveToast.java:24`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/misc/ReviveStarscript.java:70`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/elements/ReviveTextHud.java:6`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/addons/ReviveAddon.java:5`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/events/revive/` | `MeteorExecutor`, `MeteorToast`, `MeteorStarscript`, `MeteorTextHud`, `MeteorAddon`, `events/meteor/` |
| Startup order: addons, lambda factories, `ReflectInit` PreInit, categories, systems, subscribe, addon init, sort, load, PostInit, shutdown hook saving systems and themes | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/ReviveClient.java:86-151`, version parsing `:235-245` | `MeteorClient.onInitializeClient` and its static block |
| Meteor's own libraries Orbit (event bus) and Starscript, bundled | `META-INF/jars/orbit-0.2.4.jar`, `starscript-0.2.2.jar` | Published by MeteorDevelopment |

The scrub was thorough: apart from the `meteordevelopment/orbit` and `meteordevelopment/starscript`
library references, no class in the jar contains the string "meteor". Systems that Meteor has and
Revive does not include accounts (the alt-account manager), macros, waypoints and proxies.

### 8.2 Other clients credited in the code

| Credit | Where |
|---|---|
| **Lambda** (the 2b2t client): *"Lambda's packet sequence"*, *"Lambda's PacketLimiter"*, *"Lambda's Rubberband"*, *"Lambda PacketMine's tool handling"*, *"Lambda's OldGrim bypass"* | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/combat/Criticals.java:32`, `:42`; `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/PacketLimiter.java:30`, `:50`, `:72`; `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/Rubberband.java:21`; `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/SilentMine.java:217`, `:243`, `:252` |
| **BepHax**: *"BepHax-style rockets"*, and `"author": "BepHax"` in **all 23** bundled emote files | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/config/AntiCheatConfig.java:180`; `01. Source Reconstruction/revive-client/src/main/resources/assets/revive-client/emotes/*.json` |
| **Shoreline**: AutoCrystal `shoreline-mode` and `tryShorelinePlace` | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/combat/AutoCrystal.java:174-181`; `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/combat/autocrystal/AutoCrystalFeature.java:609`, `:3932` |
| **Emote format**: the emote files use the Emotecraft / playerAnimator JSON schema (`emote.beginTick`, `endTick`, `stopTick`, `degrees`, `moves`), and their comments refer to "PAL" | `01. Source Reconstruction/revive-client/src/main/resources/assets/revive-client/emotes/bow.json:1-12`; `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/emotes/EmoteLoader.java:34-60` |

These are the client's own attributions. They show where techniques or assets came from; they do not
show how much code was copied, because no Lambda, BepHax or Shoreline source was compared.

### 8.3 Overlap with GridFly

`StashSignals.waypointPayload()` builds a Discord embed titled *"GridFly Waypoint Completed"* with
server, player, dimension, waypoint coordinates and progress
(`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/StashSignals.java:14-43`).
Nothing calls it, in either build (checked in bytecode). GridFly is the operators' scanner-bot
fleet ([GRIDFLY-PROTOCOL.md](GRIDFLY-PROTOCOL.md)), whose bots also produce 3D stash renders, like
StashFinderPlus's `webhook-3d-preview`. The stash-hunting code and the GridFly bots appear to share
code; which came first is not shown.

### 8.4 What is and is not shown

- **Shown:** the client is a renamed Meteor Client fork. The framework, GUI widgets, settings, HUD
  system, addon API, telemetry and capes plumbing, installer dialog and splash list are Meteor's, with
  the branding replaced. Several features self-credit Lambda, BepHax and Shoreline, and the 23 stock
  emotes credit BepHax.
- **Also shown:** a lot of code is not stock Meteor. PingBypass (about 5,000 lines with its module),
  TeamSync and Emotes, the two new GUI styles (about 14,000 lines), the custom title screen and
  music, the Spotify and Media HUDs, and most of the large combat and hunting modules have no Meteor
  counterpart. The decompiled source is about 148,000 lines, much larger than a Meteor checkout.
- **Not shown:** the README's "roughly 4% original" figure. Measuring it would need a diff against a
  specific Meteor commit and against Lambda, BepHax and Shoreline sources. None was done here. How
  much of the non-Meteor code was written for Revive and how much was ported from those clients is
  open.

### 8.5 License

The jar declares `GPL-3.0` and ships the GPLv3 text, consistent with a Meteor fork. The source links
in `fabric.mod.json` (`github.com/ReviveClient/revive-client`) and in `AddonManager`
(`ReviveDevelopment/revive-client`) name two different organizations, and neither was checked (no
network contact). Nothing in the distribution chain analyzed in this repo (website, launcher, loader,
client) offers the source. The loader, by contrast, is marked "All Rights Reserved"
(`01. Source Reconstruction/revive-loader/src/main/resources/fabric.mod.json:7`).

---

## 9. Quality tells

| Tell | Evidence |
|---|---|
| A dev build was shipped | Version `1.21.11-local` with empty `build_number` and `commit` (`01. Source Reconstruction/revive-client/src/main/resources/fabric.mod.json:5`, `:33-35`); the title screen hard-codes `PRE-RELEASE-0.0.2` (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/gui/titlemenu/CustomTitleScreen.java:481`) |
| Telemetry and links point at `reviveclient.com`, a find-and-replace artifact, while the real infrastructure is `revive-client.com` and `reviveon.top` | [3.2](#32-telemetry-reviveclientcom) |
| Default TeamSync host does not resolve | `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/player/TeamSync.java:24`; DOMAIN-FORENSICS.md |
| The loader's inject path cannot start this client | Bootstrap `revivedevelopment.client.ReviveBootstrap` is absent ([section 1](#1-what-the-client-is)) |
| Dead DRM scaffolding | `checkAuth()`, `Auth`, `Obf` never called ([section 4](#4-the-revive-token-how-the-client-gets-it-and-what-it-does-with-it)) |
| The GitHub update check can never run | It needs a non-empty commit (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/addons/AddonManager.java:37-40`) and draws on the vanilla title screen, which `TitleScreenMixin` replaces on init (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/mixin/TitleScreenMixin.java:24-30`) |
| Media keys cannot work | `SendKeys('^{VK_MEDIA_PLAYPAUSE}')` is not a key code WScript's `SendKeys` understands, and `^` adds Ctrl (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/WindowsMediaController.java:39`) |
| Pollers that never stop | Media HUD and Spotify HUD schedulers start in the element constructor and are never shut down; Media HUD keeps launching PowerShell every 2 s after the element is removed (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/elements/MediaHud.java:54-73`) |
| Directory scan every tick | BetterScreenshot lists the screenshots folder 20 times a second (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/BetterScreenshot.java:57-83`) |
| Privacy toggle that does not hold | DiscordNotifs forwards outgoing whispers with `whisper` off ([3.7](#37-user-configured-outputs)) |
| Unescaped JSON | `HuntingUtils.sendWebhook(title, message, ...)` concatenates raw strings into JSON, so a quote in a message breaks the payload (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/hunting/HuntingUtils.java:114-129`) |
| Leftover Meteor gates | `MixinPlugin` checks for compat mixin packages that are not in the jar ([8.1](#81-meteor-client)) |
| Unused GridFly code | `StashSignals.waypointPayload` ([8.3](#83-overlap-with-gridfly)) |
| Inconsistent naming | Config in `revive-client/`, emotes in `revive/`; GitHub org `ReviveClient` in one place and `ReviveDevelopment` in another; modules registered under one category and declared in another ([section 2](#2-feature-inventory)) |
| Default key clash | Emote wheel and Media HUD "previous track" both default to `B` (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/modules/render/Emotes.java:89`, `01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/hud/elements/MediaHud.java:44`) |
| Debug output left in | `System.out.println` in the Spotify and GUI code, e.g. *"[ERROR] Failed to create process snapshot."* (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/utils/other/SpotifyGrabber.java:43`); an empty `if` body at `:38-39` |
| Installer dialog bug | On Linux "Open Mods Folder" opens `~/.minecraft`, not `~/.minecraft/mods` (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/Main.java:41`) |

---

## 10. The 2026-10-23 build

A patched analysis copy of the launcher (not included in this repo) pulled a newer `revive-client.jar` on
2026-10-23 at 03:19 UTC. Compared with the 2026-10-19 build:

| | 2026-10-19 | 2026-10-23 |
|---|---|---|
| Size | 34,356,543 B | 34,556,953 B |
| First-party classes | 1,298 | 1,362 (64 added, 100 changed, 0 removed) |
| Mixins | 170 | 196 (26 new `headless.*`) |
| `fabric.mod.json`, manifest, `LICENSE_revive-client` | | Identical |
| URL strings in class files | 25 | The same 25 |

What was added:

- **A headless mode** (`revivedevelopment.reviveclient.headless`, 26 headless mixins), enabled by
  `-Drevive.headless=true` or `REVIVE_HEADLESS=1`. It lets the same jar run as the PingBypass VPS.
- **A PingBypass server** (`pingbypass/server/`): a `ServerSocket` configured from `pingbypass.json`
  or the `PB_PORT`, `PB_BIND`, `PB_PASSWORD` and `PB_USERS` environment variables, binding `0.0.0.0`
  by default. It only starts in headless mode, which the launcher never sets.
- **A `baritone-stop` module** that runs Baritone's `#stop`.

Of the 100 changed class files, 49 are combat modules and 15 player modules (PingBypass among them);
the rest are spread over managers (elytra durability, rotation, packets, block placement), render
and utility classes and a few mixins. `ReviveClient`, `Capes` and
`MixinPlugin` changed to hook in headless mode (`Headless.ENABLED`, `HeadlessRuntime.init`). A byte
search of every changed class for process, classloading, socket, HTTP, URL, clipboard, token and
`method_1674` references found the same counts in both builds, so nothing of that kind was added
outside the headless server. This build does show the point made in REPORT.md: the jar changes
between launches, and a clean result for one build says nothing about the next.

---

## 11. IOCs and hashes

### File hashes

| File | Size | SHA-256 | SHA-1 | MD5 |
|---|---|---|---|---|
| `revive-client.jar` (2026-10-19) | 34,356,543 | `4df5c24c7c38b6b58d6bce1b4c9d3552c69e8b37e6785998bfaa02f88ad86aca` | `51206be97df4c9e064b6b457764ab4da4865e7d9` | `03b42e1951ccc10937c97b5e65486c82` |
| `revive-client.jar` (2026-10-23) | 34,556,953 | `aaf9559c90cc61879f9f4c1b3f9d81efde050c87929ec99dbae66c3e9e82cdba` | `cd41d9b6e402fee84068b1d3a14c22d017b800ff` | `6f726986f7f48e55a139d9e22ac58edb` |
| `LICENSE_revive-client` (both builds) | 35,828 | `a4fdc1ad711d2db30a2ae3056588f95a1b6237fd4fc3ee39f842f9e7936499f0` | | |

The nine nested library jars were compared entry by entry against the copies of the same versions in
the local Gradle cache (downloaded from Maven Central, the Fabric Maven and the Meteor Maven by the
reconstruction build). Every entry is identical; the only addition is the `fabric.mod.json` that Loom
generates for nested non-mod jars (none was added to the two Fabric API modules).

### Network

| Indicator | Role |
|---|---|
| `reviveclient.com` (`/api/online/ping`, `/api/online/leave`) | Always-on telemetry. Ownership unknown |
| `revive-client.com` (`/api/capes`, `/api/capeowners`) | Capes, always on |
| `emotes.revive-client.com` (`wss://.../ws`, `https://.../api/emotes`) | Emotes module |
| `team.revive-client.com` (`wss://.../ws`) | TeamSync default, NXDOMAIN |
| `revive-auth.crownizzle.workers.dev` | Dead remote-key code |
| User-Agent `Revive-Client` | Capes and emote library downloads |
| User-Agent `...Chrome/112.0.0.0 Safari/537.36` | Telemetry, Mojang, iTunes, GitHub, MPRIS art |
| User-Agent `StashFinderPlus-Webhook/1.0`, `Mozilla` | Webhooks |
| TCP 25570 (PingBypass), 6667 (IRC), WebSocket ports 8090 / 8091 (self-hosted team / emote servers) | Defaults for user-configured links |

### Host

| Indicator | Notes |
|---|---|
| `<gameDir>/revive-client/` with `modules.nbt`, `config.nbt`, `friends.nbt`, `profiles.nbt`, `hud.nbt` | Always created |
| `<gameDir>/revive-client/spotify_media.ps1` | Spotify HUD on Windows |
| `<gameDir>/revive/emotes/server/*.json` | Emotes module |
| `<gameDir>/revive-token.txt` | Written by the launcher, read by the client |
| `%TEMP%\revive-client*.nbt`, `/tmp/revive_music_*.wav` | Transient |
| Child `powershell.exe` with `-ExecutionPolicy Bypass -File ...\revive-client\spotify_media.ps1` | Spotify HUD |
| Child `powershell.exe` running `Get-Process -Name vlc, chrome, firefox, msedge` every 2 s | Media HUD |
| Child `dbus-send ... org.mpris.MediaPlayer2.spotify`, `pactl`, `paplay` | Linux |
| Thread names `Revive-Executor-N`, `Revive-TeamSync(-IO)`, `Revive-Emotes(-IO)`, `Revive-IRC`, `PingBypass-control`, `PingBypass-join`, `revive-spotify-media`, `revive-media-poll`, `revive-spotify-poll` | In a thread dump |
| Properties and environment read: `revive.loader.host`, `REVIVE_LOADER_HOST`, `revive.github.authorization`; newer build also `revive.headless`, `REVIVE_HEADLESS`, `PB_PORT`, `PB_BIND`, `PB_PASSWORD`, `PB_USERS` | |
| Mod id `revive-client`, package `revivedevelopment.reviveclient`, mixin config `revive-client.mixins.json` | In `latest.log` and the mod list |

---

## 12. What this means for a user

- **This build does not steal your account.** The jars analyzed here contain no credential theft,
  no file grabbing, no keylogging, no screen capture and no persistence, and your Minecraft token goes
  only to Mojang.
- **It phones home whether you like it or not.** From the first tick, every 5 minutes, your IP goes to
  `reviveclient.com`, a domain nobody has tied to the operator, and to `revive-client.com` for capes.
  There is no setting to stop either.
- **Treat TeamSync and Emotes as "send my account token to this server".** If you enable them, the
  operator gets your Revive token and Minecraft identity, and with TeamSync your live coordinates, the
  server you are on and the players around you, 4 times a second. On an anarchy server, coordinates
  are your base security. Never aim either module at a server you don't trust while in
  `Revive Account` mode, and never paste a config from someone else.
- **Your config is a secrets file.** `revive-client/modules.nbt` and "copy full config" hold tokens,
  passwords and webhook URLs in plain text.
- **PingBypass trusts the VPS.** Use it only with a VPS you run.
- **HUD gadgets are noisy.** The Spotify and Media HUDs run PowerShell and read process and window
  information on your PC. The Media HUD shows your browser's window title, which can be the page
  you are on, on screen, which matters if you stream.
- **The bigger risk is the delivery, not this jar.** The launcher downloads a fresh, unsigned client
  every time you press Play, and the build already changed between 2026-10-19 and 2026-10-23. What
  was clean here can be replaced at any launch; see [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md).
- **Removing it:** delete `<gameDir>/revive-client/`, `<gameDir>/revive/` and `revive-token.txt`. The
  client installs nothing outside the game directory apart from temp files. If you ever put your
  Revive token in the `token` setting, shared a config, or pointed TeamSync or Emotes at a third-party
  server, change your Revive password; whether that revokes old tokens is not known.

---

## 13. Method, sources and limitations

### Sources

- `01. Source Reconstruction/revive-client`: the reconstructed source, surveyed package by package,
  with the network, token, process, file and classloading code read in full.
- The original 2026-10-19 jar and the 2026-10-23 jar retrieved by the patched launcher copy (neither
  is included in this repo).
- `01. Source Reconstruction/revive-loader` and `01. Source Reconstruction/revive-launcher`, for the
  token handoff and delivery mode.
- [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md) for DNS facts. No lookup was made for this document.

### Tools

`unzip` (listing and extraction), `javap` (bytecode for spot checks: `checkAuth` and `Obf` callers,
the `OnlinePlayers` call site, the emote sync loop, the DiscordNotifs whisper branch, `method_1674`
references), byte searches over every `.class` file for sensitive APIs and paths, and a short Python
script comparing nested jars with Gradle cache copies. Nothing was executed apart from these local
tools, and no host was contacted.

### Caveats about the reconstruction

- **`EmoteSyncManager.startLibrarySync` is decompiled wrong.** The source has `while (true)` with no
  exit (`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/systems/managers/EmoteSyncManager.java:1019-1025`).
  The shipped bytecode (`lambda$startLibrarySync$13`) is a `do { ... } while (syncRequests > 0)` loop
  that ends. A client rebuilt from this source would re-fetch `/api/emotes` forever once Emotes
  connects.
- **`build.gradle.kts` is a Meteor build template, not Revive's build.** It sets
  `Main-Class: meteordevelopment.meteorclient.Main`, publishes to the Meteor Maven, nests libraries
  the shipped jar does not have (discord-ipc, netty proxy and SOCKS, waybackauthlib, minecraft-auth)
  and uses fabric-resource-loader-v1 instead of v0
  (`01. Source Reconstruction/revive-client/build.gradle.kts:145`, `:193-198`, `:342`, `:424-429`).
  None of that is evidence about the original.
- The reconstruction README says a refmap ships in the original jar; it does not (static mixin remap,
  no refmap entry). Mixin sources keep intermediary names such as `method_1574`.
- The source compiles but was not run, so runtime behavior is inferred from code.

### Corrections to `REPORT.md` (client section)

| REPORT.md says | Verified finding |
|---|---|
| Process execution is `pactl` / `paplay` and `rundll32` / `open` | Also PowerShell in three places (Spotify HUD script with `-ExecutionPolicy Bypass`, Media HUD, BetterScreenshot), `dbus-send`, and JNA process enumeration ([6.3](#63-native-code), [6.4](#64-child-processes)) |
| Capes: "At startup, GET only" | At startup **and every 5 minutes**, plus image downloads from server-listed URLs on any host |
| Emotes send token, identity and emote events | Also a library sync that downloads files from server-listed URLs and writes and deletes files in `revive/emotes/server/` |
| GitHub calls on the credits and commits screens | Unreachable in the stock build |
| iTunes receives the song title | Artist and title |
| Network table | Missing IRC, the PingBypass VPS link, skin texture downloads and Linux MPRIS art |
| Bundled libraries byte-identical to Maven Central, Meteor Maven and the Fabric Maven | Confirmed entry by entry against local Gradle cache copies, not against the remote repositories |

Not re-verified and left out: REPORT.md's statement that the Vineflower jar's SHA-1 matched Maven
Central, and its REA evidence IDs (process records, not checkable from the source).

### Still unknown

- Who controls `reviveclient.com`, and whether `github.com/ReviveClient` or `ReviveDevelopment` exist.
- What the capes and emote servers return at any given time (cape URLs, emote library contents).
- Whether different users are served different client builds.
- How much of the non-Meteor code was written for Revive versus ported from Lambda, BepHax, Shoreline
  or others.
- Whether the operator can revoke a leaked Revive token.
