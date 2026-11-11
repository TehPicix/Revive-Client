# GridFly dashboard WebSocket protocol

GridFly is the Revive operators' fleet of scanner bots: Minecraft accounts running a ZenithProxy
plugin (`gridfly-zenith`) that fly elytra lanes across assigned rectangles, map every chunk they
load, and record containers, signs and other finds in a server-side database. Operators watch and
steer the fleet from a browser console ("Revive GridFly") that talks to `gridfly-server` over a
single WebSocket. This document describes that protocol.

| | |
|---|---|
| **Console source** | `01. Source Reconstruction/revive-gridfly/index.html`, console build **1.9.5**, `protocolVersion: 1` |
| **Live capture** | 2026-10-23, about 04:45 to 16:45 UTC. 51 sessions, about 9.4 million frames (11.8 GB), from a server that reports itself as **`GridFly Server 0.16.0`** |
| **Server** | `ws://40.160.138.207:25600`, TooTallNate Java-WebSocket on an OVH VPS (see [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md)) |
| **Capture method** | A passive client that sent `ui_hello` with the token preset in the console and logged every frame it got back. It sent no query and no control message. |

Every message and field is tagged with where it comes from:

- **live**: seen in the capture. The field list is what the server actually sent.
- **console**: sent or handled by the 1.9.5 console. For server messages this is the set of fields
  the console reads, so it is a lower bound.

---

## TL;DR

1. **JSON over one WebSocket.** One JSON object per text frame, told apart by a string `type`. There
   are no binary frames; images travel inside the JSON as base64.
2. **One shared operator token.** The console authenticates with a single token in `ui_hello`. The
   0.16.0 server answers with a `welcome` that also contains a **`botToken`**, the credential the bots
   use. The console never reads it, but every dashboard connection is handed it.
3. **Mostly push.** After `welcome` the server streams with no subscription step: every bot's status,
   every chunk the bots scan as a raw 16x16 RGBA tile (`map_chunk`), and invalidation notices for
   region tiles, summary tiles and chunk-age masks. Over the 12 hour capture that was **7.0 million
   `map_chunk` frames** (about 160 a second) against 189,000 bot updates.
4. **The snapshot is the database's front page.** A `snapshot` (about 12 MB) carries every bot with
   its full inventory, every target with lane coverage, the fleet settings, the last 500 log lines,
   and about 43,500 of the server's roughly **1.86 million waypoints** (stashes, chests, shulker
   boxes, signs, pearls).
5. **Not read-only.** Seven console messages steer the fleet (`create_target`, `delete_target`,
   `reset_coverage`, `assign`, `queue_targets`, `cancel`, `clear_queue`), even though the console's
   connect screen says "the dashboard watches the fleet and never sends it commands"
   (`index.html:472`).
6. **Connections are announced.** The server logs "Operator connected from connection #N
   (*clientName*)" to the fleet log, which is broadcast to every dashboard, so the `clientName` from
   `ui_hello` is visible to all connected operators.

---

## 1. Transport and session

| Property | Value |
|---|---|
| Transport | WebSocket, UTF-8 JSON text frames, one object per frame |
| Type field | `type` (string) |
| Protocol version | `1`, sent as `ui_hello.protocolVersion`. Server 0.16.0 also puts `protocolVersion: 1` in `welcome`. |
| URL | `ws://HOST:PORT/dash`, or `wss://` when the console's Secure box is ticked (TLS or a tunnel). If the first socket closes with a reason containing `unknown path`, the console retries once on `/ui` for older servers (`index.html:1399`). When the console is itself served over http or https, it uses `location.host` and hides the server field. |
| Preset server | `40.160.138.207:25600` (`index.html:4201`) |
| Auth | Operator token in `ui_hello.token`, the token printed by `gridfly-server --print-tokens`. The prefilled console ships one and sends it automatically (`index.html:4202`). A bookmark such as `http://host:25600/#token=...` also signs in; the console removes the fragment from the address bar straight away (`index.html:4194`). |
| Handshake | The client sends `ui_hello` as soon as the socket opens. The server answers `welcome` (accepted) or `rejected` (refused, and the console forgets its stored token). |
| Reconnect | Backoff of `min(30 s, 1 s x 1.7^min(retry, 8))`. On the next `welcome` the console fails every pending request and asks again, because answers do not survive a reconnect. |
| Request correlation | Queries carry a client-made `requestId` (`"q"` plus a counter). The answer echoes it; a failed query carries an `error` string. |
| Console limits | At most 24 tile requests in flight, 4 of them full-detail regions, and about 120 tiles on screen. Stash pictures and chunk-age masks are fetched from small queues. |

---

## 2. Message index

### Client to server (14 types)

All control and query messages below come from the console. The capture client sent only
`ui_hello`, so the replies listed for the others are what the console's handlers expect.

| `type` | Purpose | Reply | Evidence |
|---|---|---|---|
| `ui_hello` | Authenticate | `welcome` or `rejected` | live, console |
| `query_finds` | Search the finds database | `finds_result` | console |
| `query_sightings` | Search player sightings | `sightings_result` | console |
| `request_map_region` | Full-detail map tile for one 512-block region | `map_region` | console |
| `request_map_summary` | Lower-detail summary tile | `map_summary` | console |
| `request_chunk_ages` | New and old chunk masks for one region | `chunk_ages` | console |
| `request_stash_picture` | A bot's 3D render of a stash | `stash_picture` | console |
| `create_target` | Create a search rectangle | `target_updated` | console |
| `delete_target` | Delete a target | `target_removed` | console |
| `reset_coverage` | Mark every lane of a target unflown | `target_updated` | console |
| `assign` | Send bots to a target now, replacing their current task | `bot_updated`, `log` | console |
| `queue_targets` | Append to, or replace, bots' target queues | `queue_updated` | console |
| `cancel` | Cancel bots' current tasks | `bot_updated` | console |
| `clear_queue` | Empty bots' target queues | `queue_updated` | console |

### Server to client (22 types)

| `type` | Purpose | Sent | Frames in capture | Evidence |
|---|---|---|---|---|
| `welcome` | Auth accepted | once per connection | 51 | live, console |
| `rejected` | Auth refused | once | 0 | console |
| `snapshot` | Full fleet state | once, seconds after `welcome` | 44 | live, console |
| `bot_updated` | One bot changed | push | 188,654 | live, console |
| `bot_removed` | A bot or proxy left | push | 0 | console |
| `target_updated` | A target was created or changed | push | 90 | live, console |
| `target_removed` | A target was deleted | push | 0 | console |
| `queue_updated` | A bot's target queue changed | push | 0 | console |
| `settings_updated` | Fleet settings changed | push | 0 | console |
| `log` | One activity-log line | push | 6,476 | live, console |
| `waypoint_updated` | A find was added or changed | push | 3,128 | live |
| `map_chunk` | One scanned chunk, raw RGBA | push | 7,036,827 | live |
| `map_region_updated` | A region tile changed | push | 344,697 | live, console |
| `map_summary_updated` | A summary tile changed | push | 1,570,749 | live, console |
| `chunk_ages_updated` | A region's chunk-age masks changed | push | 276,831 | live, console |
| `sighting_updated` | A new player sighting was stored | push | 0 | console |
| `map_region` | Region tile | reply | (not requested) | console |
| `map_summary` | Summary tile | reply | (not requested) | console |
| `chunk_ages` | Chunk-age masks | reply | (not requested) | console |
| `stash_picture` | Stash render | reply | (not requested) | console |
| `finds_result` | Finds query answer | reply | (not requested) | console |
| `sightings_result` | Sightings query answer | reply | (not requested) | console |

"Frames in capture" is 0 for push messages that simply did not happen while the capture ran (no bot
left, no target was deleted, no settings changed).

---

## 3. Client to server

### 3.1 `ui_hello`

```json
{ "type": "ui_hello", "token": "<operator token>",
  "clientName": "Revive GridFly v1.9.5 (browser)", "protocolVersion": 1 }
```

`clientName` is `"Revive GridFly v" + build + " (" + device + ")"`, where `device` is `iPhone`
(iPhone or iPad), `Android`, or `browser` (`index.html:1387`). The server repeats it in the
"Operator connected" log line that every dashboard receives.

### 3.2 Queries

**`query_finds`**: `{ type, requestId, query }`. The `query` object (built at `index.html:2154`):

| Field | Type | Meaning |
|---|---|---|
| `mode` | `"BEST"` or `"SEARCH"` | `BEST` pages through stash spots (`sites`, one record per stash). `SEARCH` returns single finds (`finds`). |
| `kinds` | string[] | Waypoint kinds to include; empty means all |
| `dimension` | Dimension or null | |
| `text` | string or null | Free text: sign text, shulker box color, bot name. Typing `x, z` in the search box sends a point query with a 300 block radius instead. |
| `nearX`, `nearZ` | int or null | Center of a radius query, in the dimension's own coordinates. The console divides overworld coordinates by 8 for the Nether. |
| `radius` | int or null | Defaults to 5,000 blocks when a point is set |
| `minCount` | int or null | Minimum count at a spot; sent only when greater than 1 |
| `since` | ms or null | Only finds after this time |
| `until` | ms or null | Set on later pages to the first page's time, so paging stays stable |
| `sort` | string or null | `SEARCH` only: `NEWEST`, `OLDEST`, `MOST`, `NEAREST` (`NEAREST` falls back to `NEWEST` without a point) |
| `rank` | string or null | `BEST` only: `"OVERALL"` |
| `limit` | int | 1,000 per page for `BEST`, 50 for `SEARCH`, 300 for the nearby list on a stash sheet |
| `offset` | int | Paging offset |
| `visited` | bool or null | Filter on whether a bot has visited the spot |
| `looted` | bool or null | Filter on looted or unlooted |
| `changed` | true or null | Only spots that changed since the last visit |

Ranking happens in the console, not on the server. In `BEST` mode the console fetches pages of 1,000
spots back to back, stepping 700 so pages overlap, up to 50,000 spots (`index.html:1699`), then scores
them itself from the Ranking panel (kind weights, spawn radius, map coverage, obsidian, sky, signs,
recency). The server's own order only decides paging.

**`query_sightings`**: `{ type, requestId, text, dimension, nearStash, limit }`. `text` and
`dimension` may be null, `nearStash` is a boolean, and the console always asks for `limit: 300`.

### 3.3 Map and picture requests

| `type` | Fields |
|---|---|
| `request_map_region` | `dimension`, `regionX`, `regionZ` |
| `request_map_summary` | `dimension`, `lod` (0 to 8), `x`, `z` |
| `request_chunk_ages` | `dimension`, `regionX`, `regionZ` (servers 0.10 and newer) |
| `request_stash_picture` | `stashId` |

See [section 6](#6-map-tiles-and-coordinates) for the tile grid.

### 3.4 Fleet control

| `type` | Fields | Console action |
|---|---|---|
| `create_target` | `name` (or null), `area` (Target area), `colorIndex` (always null from the console) | Targets tab, new target |
| `delete_target` | `id` | Delete (tap twice to confirm) |
| `reset_coverage` | `targetId` | "Mark all unflown" |
| `assign` | `targetId`, `botIds[]`, `laneSpacing` | "Send to picked" |
| `queue_targets` | `botIds[]`, `targetIds[]`, `replace` | "Queue for picked" sends `replace: false`. Removing one entry rewrites that bot's queue with `replace: true`, or sends `clear_queue` when nothing is left. Servers 0.12 and newer. |
| `cancel` | `botIds[]` | Cancel picked bots' tasks |
| `clear_queue` | `botIds[]` | Clear picked bots' queues |

On `assign`, the server splits the target into one band per bot and logs how it did so ("Dispatched
Target N to M bots: K bands of W blocks, L of T axis lanes"). Each bot then gets a `task` with its own
`area`, `part` of `parts`, and lane range (see [Task](#task)).

---

## 4. Server to client

### 4.1 Session

**`welcome`** (live): `serverName`, `serverVersion`, `protocolVersion`, `botToken`.

```json
{ "type": "welcome", "serverName": "GridFly Server", "serverVersion": "0.16.0",
  "protocolVersion": 1, "botToken": "<28 characters>" }
```

The console reads only `serverName` and `serverVersion`. `botToken` is the same value in every
session of the capture.

**`rejected`** (console): `reason`. The console shows "The server refused that token." for
`bad token`, otherwise "Refused: " plus the reason, and deletes the stored token.

### 4.2 `snapshot`

| Field | Type | Notes |
|---|---|---|
| `bots` | Bot[] | All 8 fleet bots in every captured snapshot |
| `targets` | Target[] | 9 targets during the capture |
| `queues` | object, username to targetId[] | Empty (`{}`) throughout the capture |
| `settings` | Fleet settings | Servers 0.13 and newer |
| `log` | LogLine[] | The last 500 lines; the console keeps 80 |
| `waypoints` | Waypoint[] | **live only.** A subset of about 43,500 records |
| `waypointsTotal` | int | **live only.** Size of the whole waypoint table: 1,854,964 to 1,860,556 during the capture |

The server sends one snapshot per connection, usually 2 to 4 seconds after `welcome` and occasionally
several minutes later; meanwhile the push stream has already started. Connections that dropped
before then never got one, which is why the capture has 44 snapshots for 51 sessions. Each was about
12 MB.

The console replaces its whole bot table with each snapshot. A bot that was present before and is
missing now is marked "lost link".

### 4.3 Fleet state pushes

| `type` | Fields | Notes |
|---|---|---|
| `bot_updated` | `bot` (Bot) | The whole bot record each time, 1 KB to 4.6 KB. About once a second per bot (median 1,010 ms between a bot's updates, matching `statusIntervalMs: 1000`). |
| `bot_removed` | `botId` | The console keeps the bot's last position as "lost link" |
| `target_updated` | `target` (Target) | Also sent each time the fleet finishes a lane: in the capture `coverage.done` grows by one or two entries per frame |
| `target_removed` | `id` | |
| `queue_updated` | `username`, `targetIds[]` | Empty or missing `targetIds` clears the queue |
| `settings_updated` | `settings` | |
| `waypoint_updated` | `waypoint` (Waypoint) | **live only.** One frame per new or changed find |
| `log` | `line` (LogLine) | See [LogLine](#logline) |

### 4.4 Map pushes

**`map_chunk`** (live only): one chunk the bots just scanned.

| Field | Type | Notes |
|---|---|---|
| `dimension` | Dimension | Only `OVERWORLD` in the capture |
| `chunkX`, `chunkZ` | int | Chunk coordinates (block coordinate divided by 16) |
| `rgba` | string | Base64 of **1,024 bytes**: 16 x 16 pixels, 4 bytes each (R, G, B, A), one pixel per block column. Rows run toward +Z (south), columns toward +X (east). |
| `updatedAt` | ms | Server time of the scan |

Every `map_chunk` frame is 1,477 bytes. The chunks in the capture all lie within a few hundred blocks
of the two targets the fleet was flying at the time, so the stream is a live feed of what the bots are
loading.

**Invalidation notices** tell the console to refetch a tile if it is on screen and to drop it from its
cache otherwise:

| `type` | Fields |
|---|---|
| `map_region_updated` | `dimension`, `regionX`, `regionZ`, and on 0.16.0 `patched` (boolean, live only, not read by the console, meaning unknown; `true` on 99.5% of frames) |
| `map_summary_updated` | `dimension`, `lod` (0 to 8), `x`, `z` |
| `chunk_ages_updated` | `dimension`, `regionX`, `regionZ` |

### 4.5 Replies

| `type` | Fields the console reads |
|---|---|
| `map_region` | `dimension`, `regionX`, `regionZ`, `png` (base64 PNG, 512 x 512, one pixel per block, up to about 1 MB), `chunks` (a chunk count; 0 means the tile is empty) |
| `map_summary` | `dimension`, `lod`, `x`, `z`, `png` (base64 PNG, 128 x 128), `chunks` |
| `chunk_ages` | `dimension`, `regionX`, `regionZ`, `newMask`, `oldMask` |
| `stash_picture` | `stashId`, `png` (base64, absent when there is no picture), `bot`, `takenAt` |
| `finds_result` | `requestId`, `error`, `total`, `stats`, and `sites[]` (`BEST`) or `finds[]` (`SEARCH`) |
| `sightings_result` | `requestId`, `error`, `total`, `sightings[]` |

`newMask` and `oldMask` are base64 bitmaps of 1,024 bits (128 bytes), one bit per chunk of the 32 x 32
chunk region. Bit `i` is byte `i >> 3`, bit `i & 7` (least significant first), and `i = z * 32 + x`.
The console tints "new" chunks red and "old" chunks blue (`index.html:2694`).

`sighting_updated` carries nothing the console reads; it re-queries sightings 3 seconds later if the
Players tab is open.

---

## 5. Object shapes

### Bot

| Field | Type | Notes | Evidence |
|---|---|---|---|
| `id` | string (UUID) | Bot record id | live, console |
| `username` | string | Minecraft name. The fleet is `REVIVE_` plus a color: RED, ORANGE, YELLOW, GREEN, BLUE, WHITE, BLACK, BROWN. | live, console |
| `uuid` | string | Minecraft UUID, only on some bots | live |
| `address` | string | e.g. `connection #17416` | live |
| `clientVersion` | string | `gridfly-zenith 0.14.4 (1.21.4)` | live |
| `connectedAt`, `lastSeen` | ms | | live |
| `status` | Bot status | | live, console |
| `task` | Task or null | | live, console |
| `taskState` | TaskState | Only `ACTIVE` in the capture | live, console |
| `inventory` | `{ items: InventoryItem[] }` | 20 to 36 items per bot | live |
| `inventoryAt` | ms | When the inventory was last read | live |
| `settings` | `{ logoutWhenDone, resumeTask, saverMode }` | Booleans, and `saverMode: "FLASH"` | live |

The console works out speed itself from successive positions; the server sends none.

### Bot status

| Field | Type | Notes | Evidence |
|---|---|---|---|
| `connection` | Connection | | live, console |
| `activity` | Activity | | live, console |
| `dimension` | Dimension | | live, console |
| `position` | `{ x, y, z }` | Floats. In flight the bots cruise at y 520 to 840, far above the build limit. | live, console |
| `health`, `food` | number | | live, console |
| `viewDistance` | int | 10 in the capture. In-game plugin 0.9 and newer. | live, console |
| `taskId` | string | e.g. `task-725787af` | live |
| `progress` | 0 to 1 | Share of the current task done | live |
| `detail` | string | Progress line, e.g. `lane 8 of 25, band 720 to 780` | live |
| `lanesDone` | int | | live |
| `lanesReversed` | bool | | live |
| `rocketsUsed` | int | Firework rockets used | live |
| `elytraWear`, `elytraDurabilityLeft` | int | | live |
| `players` | array | Always empty in the capture | live |
| `queuePosition`, `queueSince` | int, ms | Only while `connection` is `QUEUE` | live, console |
| `queueEtaSeconds` | int | Read by the console, not seen live | console |

### Task

| Field | Type | Notes |
|---|---|---|
| `taskId` | string | Same as `status.taskId` |
| `label` | string | e.g. `Target 9` |
| `targetId` | string | |
| `area` | Target area | This bot's band of the target |
| `fullArea` | Target area | The whole target |
| `part`, `parts` | int | 1-based, e.g. part 3 of 8 |
| `pattern` | Pattern | |
| `laneSpacing` | int | Blocks between lanes (255 in the capture) |
| `laneStart`, `laneEnd` | int | This bot's lane index range within the target |
| `moreQueued` | bool | The bot has further targets queued |

Example from the capture (one of eight bands of a 50,000 x 50,000 target):

```json
{ "taskId": "task-725787af", "label": "Target 9", "targetId": "23990468-…",
  "area":     { "x1": 25000, "z1": -149874, "x2": 75000, "z2": -143782 },
  "fullArea": { "x1": 25000, "z1": -150000, "x2": 75000, "z2": -100000 },
  "part": 1, "parts": 8, "pattern": "AXIS", "laneSpacing": 255,
  "laneStart": 0, "laneEnd": 24, "moreQueued": true }
```

### InventoryItem

`slot` (int), `id` (e.g. `minecraft:elytra`), `count`, `name` (custom anvil name, when set), `damage`,
`maxDamage`, `enchantments[]` (display strings such as `Unbreaking III`, `Mending I`).

### Target

| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | |
| `name` | string | e.g. `Target 9` |
| `colorIndex` | int | 0 to 7 |
| `area` | `{ x1, z1, x2, z2 }` | Inclusive block corners |
| `coverage` | `{ laneSpacing, pattern, done[] }` | `done` lists the indices of lanes already flown at that spacing |

The nine targets in the capture reach from a 103,000 block square centered on spawn out to
x = 600,000.

### Waypoint

A single find. The `snapshot` and `waypoint_updated` carry these (live); `SEARCH` results use the same
records (console).

| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | |
| `kind` | Waypoint kind | |
| `dimension` | Dimension | |
| `x`, `y`, `z` | int | |
| `label` | string | Container name, shulker box color name, or a sign's lines joined with ` / ` |
| `count` | int | Number of blocks at the spot |
| `bot` | string | Account that found it |
| `foundAt`, `updatedAt` | ms | The oldest record in the capture dates from early October 2026 |
| `stashId` | string | Groups finds into one stash |
| `note` | string | `CHEST_GROUP` only, e.g. "6 chests at y 64-66, forest; around them 18 cobblestone, 2 carpet blocks" |

### Site

A stash spot as returned in `finds_result.sites` (console): `stashId` (or an `ids[]` group),
`dimension`, `x`, `y`, `z`, per-kind counts (`shulkers`, `pearls`, `chests`, `enderChests`, `hoppers`,
`crafters`, `netherite`, `gateways`, `signs`), `finds`, `firstFound`, `lastSeen`, `visited`, `players`
(how many sightings are tied to it), and `picture` (a bot render exists).

### Sighting

A player seen by a bot (console, servers 0.12 and newer): `uuid`, `name`, `dimension`, `x`, `z`,
`seenAt`, `lastSeen`, `bot`, and the nearest stash as `stashId`, `stashX`, `stashZ`, `stashDistance`.

### LogLine

`time` (ms), `level` (`INFO` or `WARN` in the capture; the console also colors `ERROR`), `source` (a
bot username or `server`), `message`. The message families seen live:

| Source | Example (numbers and names elided) |
|---|---|
| bot | `cruise minute: net +N blocks (y A -> B), apex mean ..., bottom mean ...` (flight telemetry, logged at WARN) |
| bot | `Found <sign text> at x, y, z` and `Found N chests at x, y, z (<note>)` |
| bot | `Finds: Left out N hopper at x, z: a trial chamber (...)` (generated loot the bot ignores) |
| bot | `Assigned Target N part i/n: lanes a-b, a band W blocks across` and `Re-dealt ...` |
| bot | `<bot> resumed Target N part i/n at lane k of n` |
| server | `Dispatched Target N to M bots: ...` |
| server | `Operator connected from connection #N (Revive GridFly v1.9 (Android))` and `Operator disconnected ...` |

### Fleet settings

Values from the capture:

| Field | Value |
|---|---|
| `laneSpacing` | 255 |
| `pattern` | `AXIS` |
| `statusIntervalMs` | 1000 |
| `chatStaggerMs` | 0 |
| `viewRadiusChunks` | 5 |
| `autoDispatch` | false |
| `skipMappedDays` | 0 |
| `logoutOutOfWork` | true |

### Enumerations

| Name | Values | Seen live |
|---|---|---|
| Dimension | `OVERWORLD`, `NETHER`, `END` | `OVERWORLD`, `END` |
| Connection | `ONLINE`, `QUEUE`, `CONNECTING`, `OFFLINE` | `ONLINE`, `QUEUE`, `OFFLINE` |
| Activity | `IDLE`, `TRAVELLING`, `SCANNING`, `ARRIVED`, `COMPLETE`, `ERROR` | `IDLE`, `TRAVELLING`, `SCANNING` |
| TaskState | `NONE`, `PENDING`, `ACTIVE`, `FAILED`, `COMPLETE`, `ERROR` | `ACTIVE` |
| Pattern | `AXIS`, `DIAGONAL` | `AXIS` (the 1.9.5 console only knows `AXIS`, its default, and `DIAGONAL`) |
| Waypoint kind | `SHULKER_BOX`, `CHEST_GROUP`, `ENDER_CHEST`, `ENDER_PEARL`, `CRAFTER`, `NETHERITE_BLOCK`, `END_GATEWAY`, `SIGN`, `HOPPER`, `MANUAL` | all but `END_GATEWAY`, `HOPPER`, `MANUAL` |

`HOPPER` is no longer stored on servers 0.12 and newer. `NETHERITE_BLOCK` and `END_GATEWAY` arrived
with 0.16. `MANUAL` marks a spot added by hand. `TRAVELLING` is spelled that way on the wire.

---

## 6. Map tiles and coordinates

| Unit | Size | Addressed by |
|---|---|---|
| Chunk | 16 x 16 blocks | `chunkX`, `chunkZ` = block coordinate / 16, rounded down |
| Region | 512 x 512 blocks (32 x 32 chunks) | `regionX`, `regionZ` = block coordinate / 512, rounded down (= chunk coordinate >> 5) |
| Summary tile at level `lod` | 512 x 2^lod blocks a side, drawn as 128 x 128 pixels (4 x 2^lod blocks a pixel) | `x`, `z` = block coordinate / (512 x 2^lod), rounded down; `lod` 0 to 8 |

The console uses full-detail regions only when a screen pixel covers fewer than 4 blocks, and
otherwise the summary level whose pixels fit best (`index.html:2792`).

---

## 7. Version gates

Server and plugin versions the console checks for or mentions:

| Feature | Needs |
|---|---|
| Chunk-age masks | server 0.10+ |
| Target queues, player sightings; hoppers dropped from storage | server 0.12+ |
| Fleet `settings` | server 0.13+ |
| `NETHERITE_BLOCK`, `END_GATEWAY` finds | server 0.16 |
| Bot `viewDistance` | in-game plugin 0.9+ |

---

## 8. Notes

- **The bot credential leaks to every dashboard.** `welcome.botToken` goes to any connection that
  presents the operator token, and the 1.9.5 console has no use for it.
- **One token, no roles.** Nothing in the protocol tells a viewer apart from an operator. Whoever holds
  the operator token gets the full database stream and every control message.
- **Live versus console.** The 0.16.0 server sends more than the 1.9.5 console reads: `map_chunk`,
  `waypoint_updated`, `snapshot.waypoints`, `waypointsTotal`, `welcome.botToken`,
  `welcome.protocolVersion`, `map_region_updated.patched`, and most bot, status and task fields. The
  console ignores them.
- **Field names** in this document are exactly as they appear on the wire. Server payloads may carry
  fields that neither the capture nor the console revealed.

## 9. Method and limitations

- Client side: the console source was read statically. Message handlers are at `index.html:1416`
  (dispatcher) and the send sites are listed in the tables above.
- Server side: one passive capture, from one operator token, over 12 hours on one server version. The
  capture client never queried or steered anything, so replies and control effects are known only
  from the console's handlers. Push messages that did not occur during the window (`bot_removed`,
  `target_removed`, `queue_updated`, `settings_updated`, `sighting_updated`) are documented from the
  console alone.
- Nothing was sent to the server beyond the handshake.
