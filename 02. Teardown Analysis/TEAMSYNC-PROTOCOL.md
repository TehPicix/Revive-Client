# TeamSync protocol (ReviveClient 1.21.11)

TeamSync is the Revive client's team feature: a WebSocket link to a relay that shares your position,
health, supplies and everything your client can see with the rest of your team, and feeds the team's
data back into ESP, radar, HUD and a browser "combat map". This document describes the wire protocol
between the client and the relay.

| | |
|---|---|
| **Client source** | `revive-client.jar` (SHA-256 `4df5c24c…6aca`), decompiled with Vineflower into `01. Source Reconstruction/revive-client` |
| **Live capture** | 2026-10-21 about 20:30 UTC to 2026-10-23 16:45 UTC: about 618,000 frames from the relay at `wss://team.reviveon.top` |
| **Capture method** | A passive client that sent `hello` (Revive account token mode, `client: "revive/1.21.11"`) and a `ping` every 15 seconds, and logged every frame it got back. It never sent `state`, `seen`, events, terrain or team commands. |
| **Relay host** | The URL compiled into the client, `team.revive-client.com`, is NXDOMAIN. The relay now answers at `team.reviveon.top`, a DigitalOcean droplet outside Cloudflare (see [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md)). |

Source paths below are relative to
`01. Source Reconstruction/revive-client/src/main/java/revivedevelopment/reviveclient/`:

| File | Role |
|---|---|
| `systems/managers/TeamSyncManager.java` | Transport, every outbound and inbound message |
| `systems/modules/player/TeamSync.java` | Settings, and the identity sent in `hello` |
| `systems/managers/team/TerrainSampler.java` | The `tile` encoder |
| `systems/managers/InformationManager.java` | What triggers `pop`, `death`, `regear`, `login` and `logout` |
| `commands/commands/TeamCommand.java` | `.team` subcommands |

The send code in `TeamSyncManager` was read in full, so the client to server list is complete for
this build. `TerrainSampler` is the only other class that sends anything, and it sends only `tile`.

Every server message and field is tagged with where it comes from:

- **live**: seen in the capture. The field list is what the relay actually sent.
- **client**: read by the 1.21.11 client. This is what the client understands, not proof the relay
  sends it.

---

## TL;DR

1. **JSON over one WebSocket.** One JSON object per text frame, told apart by a short `t` field.
   13 client to server types, 10 server to client types.
2. **You are a sensor once the module is on.** Four times a second the client reports your exact
   position, health, server and supplies (`state`), plus the identity, position and health of **every
   other player in render distance** (`seen`). Neither needs you to be in a team. In a team it also
   uploads an overhead color map of every chunk around you (`tile`) and reports totem pops, deaths,
   regears and logout spots of non-teammates.
3. **The relay pushes the whole team picture four times a second.** A `snapshot` arrives every
   250 ms with every online teammate's position, health and supplies, and every non-teammate any of
   them has seen, some last seen more than six hours earlier. The capture client got this without
   sending a single `state` or `seen`: a valid `hello` is enough to listen.
4. **The relay hands out account emails.** `welcome.you.revive` and every `team.members[].revive`
   carry the member's Revive account identifier, which on this relay is the account's **email
   address**. Every member sees every other member's address.
5. **What the relay can make the client do:** print text in chat, show a clickable link, add names to
   your Friends list, and inject fake ESP data. Nothing inbound runs code or touches files.

---

## 1. Transport

| Property | Value |
|---|---|
| Transport | WebSocket, UTF-8 JSON text frames, one object per frame |
| Type field | `t` |
| Protocol version | `1`, sent as `hello.v` (`TeamSyncManager.java:72`) |
| Default URL | `wss://team.revive-client.com/ws` (`TeamSyncManager.java:70`), now NXDOMAIN |
| Self-hosted default | `ws://<host>:8090/ws`. A bare IP or `localhost` gets `ws` and port 8090, a bare hostname gets `wss`, and any address with an explicit port gets `ws` (`TeamSyncManager.java:447`). |
| Auth modes | `ReviveAccount` (default): the Revive token from the `token` setting or `<gameDir>/revive-token.txt`, sent as `auth.token`. `ServerKey`: a shared password for a self-hosted relay, sent as `auth.key`. |
| When it connects | Only while the **team-sync** module is enabled. Nothing turns it on automatically, and disabling the module closes the socket. |
| Handshake | The client sends `hello` and must receive `welcome` within **15 s**, or it drops and reconnects (`TeamSyncManager.java:988`). |
| Keep-alive | The client sends `ping` every **5 s** and treats the link as dead after **20 s** with no inbound frame (`TeamSyncManager.java:991`). |
| Reconnect | Exponential backoff from 1 s to 30 s; 30 s after an auth failure. |
| Close codes | `4000` stop, no retry. `4001` auth failed, retry in 30 s. `4002` replaced by another session with the same Minecraft account, stop (and say so in chat). `4008` retry in 10 s. `4003` to `4007` and anything else: normal backoff (`TeamSyncManager.java:883`). |
| Outbound rate limit | Token bucket of **20 messages/s**, burst 40. Queue capped at 256, oldest dropped first. |
| Terrain rate limit | A separate bucket of **8 tiles/s**, burst 8, queue of 8. Tiles go out only while at least 10 general tokens remain, so they never starve position messages. |
| Inbound limits | 4,194,304 characters per message (larger ones are discarded). Lists are capped at 2,048 remote players or logout spots, 64 team members, 16 invites and 4,096 pop counters. |
| Latest-wins slots | `state` and `seen` are not queued. Only the newest copy of each is held, and it goes out ahead of the regular queue. |

Coordinates and yaw are rounded to **0.01** (`TeamSyncManager.java:1798`). Health values that are not
finite numbers are sent as `0`. Event `at` timestamps are the client clock corrected by the offset
estimated from `ping`/`pong`, in milliseconds.

---

## 2. Client to server (13 types)

### 2.1 Session

| `t` | Fields | When | Source |
|---|---|---|---|
| `hello` | `v`: 1, `auth`: {`token`} or {`key`}, `mcUuid`, `mcName`, `client`: `"revive/<version>"` | Once, right after the socket opens | `:802` |
| `ping` | `id` (counter), `sent` (local ms) | Every 5 s after `welcome` | `:996` |
| `leaveworld` | (none) | When you leave a world or server (`GameLeftEvent`) | `:542` |

`auth.token` is the **Revive** account token, not the Minecraft session token. `hello` carries no
hardware ID, no IP data and no Minecraft access token.

### 2.2 About you

| `t` | Fields | Gate | Source |
|---|---|---|---|
| `state` | `dim` (e.g. `minecraft:overworld`), `x`, `y`, `z`, `yaw`, `hp`, `absorb`, `eid` (your entity id), `server`, and with share-supplies `inv`: {`crystals`, `totems`, `pearls`, `obsidian`, `gapples`, `xp`} | `share-position` (default **on**). `inv` also needs `share-supplies` (default **on**). | `:565` |

- Built every 5 client ticks (250 ms) while connected and in a world. **It does not require being in a
  team**; enabling the module is enough.
- `server` is the address of the multiplayer server you are on, lowercased exactly as typed in your
  server list (e.g. `2b2t.org`), or `singleplayer`.
- `inv` totals your 36 inventory slots plus the offhand. `gapples` counts golden and enchanted golden
  apples together; `xp` counts experience bottles.

### 2.3 About other players

| `t` | Fields | Gate | Source |
|---|---|---|---|
| `seen` | `dim`, `server`, `players`: up to **256** x {`uuid`, `name`, `x`, `y`, `z`, `yaw`, `hp`, `absorb`, `eid`} | Each entry needs `share-seen-players` (default **on**). With it off, an empty `players` array still goes out every 250 ms. | `:612` |

Every player your client has loaded (everyone in render distance) is included, teammates too. Only
you are left out.

### 2.4 Combat and presence events

These pass through `canReport()`, which keeps them to **non-teammates**, never you, and only while you
are in a team.

| `t` | Fields | Trigger | Gate | Source |
|---|---|---|---|---|
| `pop` | `id` (counter the relay acknowledges), `uuid`, `name`, `at` | An entity-status packet with status 35 (a totem pop) for a player | `share-pops` (on) | `:254` |
| `death` | `uuid`, `at` | A visible player dies or hits 0 HP, a death message names them, or a queued death signal fires | `share-pops` (on) | `:266` |
| `regear` | `uuid`, `at`, `kind` (`"shulker"` in this build; `"echest"` is only a fallback default) | The player nearest an ender chest that just appeared (block-update packet) is also the nearest player to a **shulker box opening** (block-event packet) within the configured `regearWindow` | `share-pops` (on), plus the client's `popResetRegear` config | `:276` |
| `logout` | `uuid`, `name`, `dim`, `x`, `y`, `z`, `hp` (health plus absorption), `at` | A player leaves the tab list while in render distance, or within 3 s of leaving it, and is not dead | `share-logouts` (on) | `:286` |
| `login` | `uuid`, `at` | A player joins the tab list **and** the relay's logout list already has a spot for them | `share-logouts` (on) | `:301` |

### 2.5 Map terrain

| `t` | Fields | Gate | Source |
|---|---|---|---|
| `tile` | `dim`, `cx`, `cz` (chunk coordinates), `c`: base64 of **768 bytes**, 16 x 16 RGB map colors | `share-terrain` (default **on**), connected, in a team | `TerrainSampler.java:150` |

How tiles are produced:

- The sampler spirals outward from your chunk to your view distance (2 to 32 chunks).
- It samples 2 chunks per tick (1 in the Nether) and rechecks each chunk every 30 s.
- A tile is resent only when its colors change.
- Colors are Minecraft's own map-item colors, with map-style height shading and water depth.
- In the Nether it scans down from your Y + 16 (capped at 120) to the first solid block under air, so
  it maps the layer you are on rather than the bedrock roof.

**Effect:** wherever you go, the relay builds an overhead map of every loaded chunk around you and
feeds it to the team's browser combat map. Builds, bases, highways and stash structures within render
distance all show up on it.

### 2.6 Team management (only from `.team` commands)

| `t` | Fields | Command |
|---|---|---|
| `team` | `op`: `"invite"`, `name` | `.team invite`, `inv` or `add <player>` |
| `team` | `op`: `"accept"`, `name` | `.team accept` or `join <player>` |
| `team` | `op`: `"deny"`, `name` | `.team deny` or `decline <player>` |
| `team` | `op`: `"leave"` | `.team leave` or `quit` |
| `team` | `op`: `"kick"`, `name` | `.team kick` or `remove <player>` |
| `maplink` | (none) | `.team map`, only while in a team; asks for a link to the browser combat map |

`.team list`/`ls` and `.team status` only read local state and send nothing.

---

## 3. Server to client (10 types)

The client dispatches on `t` at `TeamSyncManager.java:1228` and ignores any other value.

| `t` | Frames in capture | Evidence |
|---|---|---|
| `snapshot` | 607,152 | live, client |
| `pong` | 9,488 | live, client |
| `pops` | 704 | live, client |
| `team` | 146 | live, client |
| `logouts` | 84 | live, client |
| `welcome` | 78 | live, client |
| `notice` | 1 | live, client |
| `invite` | 0 | client |
| `error` | 0 | client |
| `maplink` | 0 | client |

### 3.1 `welcome`

| Field | Type | Notes | Evidence |
|---|---|---|---|
| `now` | ms | Relay clock | live |
| `you.mcUuid`, `you.mcName` | string | The Minecraft identity from `hello` | live, client |
| `you.role` | string | `user` in every capture session | live, client |
| `you.revive` | string | Revive account identifier: the account's **email address** | live, client |
| `team` | Team or null | null when you are in no team | live, client |
| `invites` | [{`from`, `fromUuid`, `team`}] | Pending invites; always empty in the capture | live, client |

The client marks itself connected, restarts terrain sampling, and prints "Connected. Team: X (n
members)" plus any pending invites.

### 3.2 `pong`

`id` and `sent` echoed from the `ping`, and `now` (relay ms). The client derives round-trip time and
clock offset, keeping the lowest-RTT sample of the last 8.

### 3.3 `snapshot`

Sent every **250 ms** (median gap 251 ms, 99th percentile 752 ms over the capture), whether or not
the receiving client sends anything. The client ignores it while you are not in a world.

| Field | Type | Notes | Evidence |
|---|---|---|---|
| `now` | ms | Relay clock | live, client |
| `mates[]` | array | Teammates currently reporting `state`; 0 to 3 at a time in the capture | live, client |
| `players[]` | array | Non-teammates seen by anyone on the team; up to 17 at a time in the capture | live, client |

**`mates[]` entry:** `uuid`, `name`, `dim`, `x`, `y`, `z`, `yaw`, `hp`, `absorb`, `inv` {`crystals`,
`totems`, `pearls`, `obsidian`, `gapples`, `xp`}, `age` (ms since the relay got this teammate's last
`state`; up to 27 s in the capture).

**`players[]` entry:** `uuid`, `name`, `dim`, `x`, `y`, `z`, `yaw`, `hp`, `absorb`, `seenBy` (names of
the teammates who see them, up to 32; empty once nobody does), `visible` (anyone sees them right now),
`outMs` (how long since anyone did), `pops` (team-wide totem pop count), `age`. In the capture `age`
and `outMs` reach 22,199,409 ms (6.2 hours): the relay keeps reporting players long after anyone last
saw them, with `visible: false`.

**`eid` and `server`.** The client reads `eid` and `server` from each entry and uses `eid` to name
entities whose `server` matches yours (`TeamSyncManager.java:1346`). The live relay sends neither
field in either list, so that labeling never fires against it.

The snapshot replaces the client's teammate and remote-player tables and feeds ESPPlus,
PlayerRadarHud, TeamSuppliesHud, PearlOwners and the pop counter.

### 3.4 `team`

The team arrives either nested under `team` or as top-level fields; the live relay always nests it.

| Field | Type | Notes |
|---|---|---|
| `id` | string | Short hex id |
| `name` | string | e.g. "*player*'s team" |
| `leader` | string | Leader's Minecraft UUID |
| `members[]` | {`uuid`, `name`, `online`, `revive`} | 8 to 9 members in the capture. `revive` is the member's Revive account email; several members share one address (alternate Minecraft accounts on one Revive account). |

The client replaces its team with this. With **`auto-friend` (default on) it adds every member to
your Friends list**; it never removes friends. A new team id restarts terrain sampling.

### 3.5 Other messages

| `t` | Fields | Effect in the client | Evidence |
|---|---|---|---|
| `pops` | `uuid`, `name` (string or null), `count`, `acks` (pop ids) | Updates that player's team-wide pop counter and clears your acknowledged `pop` reports. `acks` was always empty in the capture, which sent no pops. | live, client |
| `logouts` | `spots[]`: {`uuid`, `name`, `dim`, `x`, `y`, `z`, `hp`, `pops`, `at`, `by`} | Replaces the shared logout-spot list (rendered by LogoutSpots). `by` is who reported it; `hp` is health plus absorption. 0 to 2 spots at a time in the capture. | live, client |
| `notice` | `message` | Printed in chat as-is (if `chat-notices` is on). The one live example announced a new member joining the team. | live, client |
| `invite` | `from`, `fromUuid`, `team` (string or {`name`}) | Stores the invite and shows "X invited you to team Y" | client |
| `error` | `code`, `message` | `forbidden` and `map_disabled` become combat-map messages, `auth` marks auth failed, anything else prints "Team server: ... [code]" in chat | client |
| `maplink` | `url`, `expires` (seconds or ms) | Shows a clickable **[Open combat map]** link if the scheme is `http` or `https`, otherwise prints the URL as text | client |

---

## 4. What this means

**What the relay receives while TeamSync is on, at the defaults:**

- **Four times a second, in a team or not:**
  - your exact position, dimension, health, yaw and entity id
  - the server you are playing on
  - your crystal, totem, pearl, obsidian, gapple and XP bottle counts
  - **the identity, position and health of every other player in your render distance**
- **Continuously, once you are in a team:** an overhead color map of every chunk around you.
- **As they happen, once you are in a team:** totem pops, deaths, ender chest regears, and the logout
  coordinates of every non-teammate you see log off.

Every message carries the `server` field and the relay timestamps everything, so the operator can
follow each user across servers over time, and builds a live map of players who never installed this
client. One user with TeamSync on turns their render distance into a sensor that reports to the
relay.

**What the relay hands to every team member:** four times a second, every teammate's position,
health and supplies and every non-teammate the team has seen; the shared logout spots; and every
member's Revive account email.

**What it does not send:**

- your Minecraft access token
- your Microsoft account
- your hardware ID (that goes only to the loader's `/api/authorize`, see
  [LOADER-TEARDOWN.md](LOADER-TEARDOWN.md))
- chat contents
- files from your computer
- your IP address, beyond what any WebSocket connection reveals

**What the relay can make your client do:**

- **Put arbitrary text in your chat** (`notice`, `error`, invite text).
- **Show a clickable link** (`maplink`). The client only checks for `http` or `https`; a malicious or
  compromised relay could use it for phishing.
- **Add names to your Friends list** (`team` with `auto-friend`). Combat modules normally skip
  friends, so whoever controls the team roster can make your client ignore those players.
- **Feed fake ESP data** (`snapshot`, `logouts`). Fake positions, health and logout spots would show
  up in your ESP, radar and HUD.

Nothing inbound runs code, opens files or touches your session. Every inbound field is parsed into
fixed numbers and strings, and the size caps above limit what one message can do.

**Reducing exposure, if you use it anyway:**

- Leave team-sync off. It is off unless you enable it.
- Or self-host the relay (`server-url` plus `ServerKey` mode), so the data goes to a box you control.
- Or turn off `share-seen-players`, `share-terrain` and `share-logouts`. You would still send your own
  `state`.
- Turn off `auto-friend` if you do not fully trust the team roster.

---

## 5. Method and limitations

- Client side: `TeamSyncManager`, `TeamSync`, `TerrainSampler`, `InformationManager` and `TeamCommand`
  were read statically from the decompiled source.
- Relay side: one passive capture from one team over about two days. The capture client sent only
  `hello` and `ping`, so `invite`, `error` and `maplink` replies, `pops.acks`, and the relay's handling
  of uploads are known only from the client code.
- The capture contains other users' Minecraft names, UUIDs, coordinates and email addresses; none are
  reproduced here.
