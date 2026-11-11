# 02. Teardown Analysis

Write-ups and analysis for the Revive investigation. This folder is the home for all analysis
documents; new write-ups go here.

Companion folder: [`01. Source Reconstruction`](../01.%20Source%20Reconstruction) (the rebuilt
client, launcher, loader and GridFly console source, plus the archived website).

> The shipped artifacts were analyzed **statically**: nothing from them was executed. The protocol
> write-ups ([TEAMSYNC-PROTOCOL.md](TEAMSYNC-PROTOCOL.md), [GRIDFLY-PROTOCOL.md](GRIDFLY-PROTOCOL.md))
> also draw on passive live captures, and [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md) on public DNS,
> RDAP and certificate lookups; each document states its own method.

## Documents

| File                                             | What it covers                                                                                                                                                                                                                   |
| ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [REPORT.md](REPORT.md)                           | Static malware analysis of `Revive.AppImage`, what it is, behavior, auth/DRM flow, network endpoints, and IOCs. The top-level verdict for the whole package.                                                                    |
| [WEBSITE-TEARDOWN.md](WEBSITE-TEARDOWN.md)       | Analysis of `revive-client.com`, the two-page site, its Supabase email/password auth, the manual-approval `authorized` gate, and the shared JWT thread to the download host and loader. Archive in `01. Source Reconstruction/revive-www`. |
| [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md)     | The Revive Launcher (Electron), as shipped in `Revive.AppImage` and `Revive-Setup.exe`: how it is built, what it does from launch until it hands off to the loader, its renderer/IPC surface, network and host artifacts, and weaknesses. |
| [LOADER-TEARDOWN.md](LOADER-TEARDOWN.md)         | `revive-loader.jar`, the Fabric `preLaunch` entrypoint: how it authorizes the user against the Revive auth server and downloads, decrypts and runs the client jar.                                                               |
| [CLIENT-TEARDOWN.md](CLIENT-TEARDOWN.md)         | `revive-client.jar`, the client itself: what the mod is, every network call it makes, what it touches on disk, how it gets the Revive token, where its code comes from, and a diff against a second build.                      |
| [TEAMSYNC-PROTOCOL.md](TEAMSYNC-PROTOCOL.md)     | The ReviveClient TeamSync WebSocket protocol (client→server and server→client message types, auth, wire format).                                                                                                                 |
| [GRIDFLY-PROTOCOL.md](GRIDFLY-PROTOCOL.md)       | The Revive GridFly operator dashboard WebSocket protocol, reverse-engineered from the prefilled console: handshake/auth, every client→server and server→client message, and the bot/target/find/sighting object shapes.          |
| [DOMAIN-FORENSICS.md](DOMAIN-FORENSICS.md)       | DNS, WHOIS/RDAP, network-ownership, certificate and proxy data for `revive-client.com`, `reviveon.top`, their subdomains, and the GridFly server.                                                                                 |
| [LOADER-PROVENANCE.md](LOADER-PROVENANCE.md)     | Proof that `revive-loader.jar` is a rebrand of the leaked Hackware loader, matching auth protocol, crypto constants, integrity routine, endpoints, and `preLaunch` flow; what Revive renamed, stripped, and added.               |
| [LOADER-REASSEMBLY.md](LOADER-REASSEMBLY.md)     | Forensic source reassembly of the loader: class-by-class map Revive ↔ Hackware, the verbatim-crypto-clone evidence, and the status of the faithful, buildable reconstruction in `01. Source Reconstruction/revive-loader`.       |

## Reading order

1. **REPORT.md**, start here for the overall picture of the package.
2. **WEBSITE-TEARDOWN.md**, how users obtain and are authorized for the client (site + Supabase gate).
3. **ELECTRON-TEARDOWN.md** → **LOADER-TEARDOWN.md** → **CLIENT-TEARDOWN.md**, the three stages in
   the order they run: launcher, loader, client.
4. **TEAMSYNC-PROTOCOL.md** → **GRIDFLY-PROTOCOL.md**, the client's team/coordination protocol and
   the GridFly bot-fleet operator protocol.
5. **DOMAIN-FORENSICS.md**, who and what sits behind the domains and servers.
6. **LOADER-PROVENANCE.md** → **LOADER-REASSEMBLY.md**, the loader is a de-obfuscated reskin of the
   Hackware loader; provenance proves it, reassembly rebuilds it into clean buildable source.
