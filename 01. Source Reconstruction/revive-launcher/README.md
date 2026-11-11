# revive-launcher

Reconstructed source of the **Revive Launcher**, the Electron desktop app that signs a user into
their Revive account and Minecraft, installs the loader, and launches the game.

Part of the Revive teardown; see the [repo root `README.md`](../../README.md) and [`ELECTRON-TEARDOWN.md`](../../02.%20Teardown%20Analysis/ELECTRON-TEARDOWN.md) for context.

## Stack

- **Electron** desktop app, `main.js` (main process), `preload.js` (context-isolated IPC bridge),
  `renderer/` (the UI: `index.html` + `renderer.js`).
- **`src/`** modules: `authClient.js` (Revive account login/register), `minecraft.js` (Microsoft auth
  via `msmc` + launch via `minecraft-launcher-core`), `manifest.js` (mod-list manifest), `updater.js`
  (`electron-updater`), `store.js` (settings), `config.js`.
- Runtime deps: `electron-updater`, `minecraft-launcher-core`, `msmc`.

> Bun is used here as the package manager and script runner. The app itself runs under **Electron's**
> bundled runtime, not under Bun; Bun installs the dependencies and starts Electron.

## Prerequisites

- [Bun](https://bun.sh) (1.1+).
- A desktop OS with a display (it opens a 900×620 window).

## Install & launch (Bun)

```bash
bun install
bun run start
```

- `bun install` pulls the dependencies **and** runs Electron's post-install (which downloads the
  Electron binary). That post-install only runs because `electron` is listed in
  `trustedDependencies` in `package.json`; Bun blocks lifecycle scripts otherwise.
- `bun run start` runs `electron .`, which loads `main.js`.

Equivalent one-shot without the script:

```bash
bun install
bunx electron .
```

## Analysis build (patched)

A patch under [`analysis-mode/`](analysis-mode) turns this clean launcher into the **analysis build**
used to pull Revive's distributed mod set without an account and without launching the game (forged
`alg:none` token, offline username, aborts after download). Apply it with
`git apply analysis-mode/analysis-mode.patch` and reverse with `-R`; see
[`analysis-mode/README.md`](analysis-mode/README.md). The clean source here is unchanged until you
apply it, so both builds are available from one tree.

## Build a distributable (optional)

The shipped app used `electron-updater` (so it was packaged with electron-builder). No builder config
is included in this reconstruction; to package it, add `electron-builder` and a `build` block, then
`bunx electron-builder`. For analysis you only need `bun run start`.

## Project layout

```
main.js          Electron main process (window, IPC, orchestration)
preload.js       context-isolated bridge exposed to the renderer
renderer/        UI (index.html, renderer.js)
src/
  authClient.js  Revive account login/register (POST {authHost}/account/*)
  minecraft.js   Microsoft/Minecraft auth (msmc) + game launch (minecraft-launcher-core)
  manifest.js    fetches the mod-list manifest ({downloadBase}/manifest.json)
  updater.js     electron-updater wiring
  store.js       persisted settings (under Electron userData)
  config.js      endpoints / constants
build/           app icon (icon.png)
```

## Reconstruction notes

- The shipped `package.json` had **no `electron` dependency and no scripts**; it relied on an
  external/global Electron. This reconstruction adds `electron` as a devDependency, a `start` script,
  and `trustedDependencies` so `bun install && bun run start` works out of the box.
- The launcher talks to **Revive's live backend** at `revive-client.com` (the `/account/*` login
  endpoints and the `/downloads/` mod files and manifest, see
  [`ELECTRON-TEARDOWN.md`](../../02.%20Teardown%20Analysis/ELECTRON-TEARDOWN.md)). It does not talk to Supabase or
  `dl.revive-client.com` itself; those belong to the website
  ([`WEBSITE-TEARDOWN.md`](../../02.%20Teardown%20Analysis/WEBSITE-TEARDOWN.md)). The window and UI come up offline, but
  signing in and launching require those services and a real account.
