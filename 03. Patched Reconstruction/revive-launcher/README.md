# revive-launcher (patched)

A modified build of the Revive launcher used to **pull Revive's distributed mod set for analysis
without creating or logging into any account**. It strips the Microsoft and Revive account steps,
takes any username, downloads the managed jars from Revive's server into a local `run/` folder, and
stops before launching the game.

This is an analysis/evidence artifact for the teardown, not a daily-driver launcher. For the clean,
unmodified launcher source see `02. Source Reconstruction/revive-launcher`; for context see the repo
root `README.md` and `03. Analysis`.

## What it demonstrates

- **Revive's download path accepts a forged, unsigned token.** The real account login is removed and
  replaced with a hardcoded JWT whose header is `{"alg":"none"}` (no signature). Their backend still
  serves the mods, which means the download side does not verify token signatures, no real Microsoft
  or Revive account is needed to obtain the client.
- **The client is distributed as a plain managed mod set.** With auth out of the way, the launcher
  fetches the same jars the official flow would: `revive-client.jar`, `revive-loader.jar`,
  `fabric-api.jar`, `baritone.jar`, `viafabricplus.jar`, `xaero-minimap.jar`, `xaero-worldmap.jar`,
  `xaeroplus.jar`.

## What was patched (vs `02. Source Reconstruction/revive-launcher`)

| File | Change |
|---|---|
| `src/authClient.js` | The real `post()` login/register against the account server is commented out and replaced with a static forged `alg:none` JWT, used as the access token for the download requests. |
| `src/minecraft.js` | A hard `throw new Error('Launch aborted for debugging purposes.')` is added after the mod-download step, so the launcher **downloads but never launches** the game. |
| `src/config.js` | `authHost` is commented out, and `gameDir` is redirected from the OS user-data path to `./run/minecraft`, so everything lands inside this folder. |
| `main.js`, `renderer/index.html`, `renderer/renderer.js` | The account login/register/MS-auth flow is reduced to a single **username** field (offline username, anything works). |
| `src/store.js` | Default settings adjusted to match (no auth host / account state). |
| `src/manifest.js`, `src/updater.js`, `preload.js` | Unchanged, the mod-manifest/download logic is the stock launcher's; only the auth around it was removed. |

## How to use

Same toolchain as the unpatched launcher (Bun as installer + runner; the app runs under Electron).

```bash
bun install
bun run start
```

Then:

1. Enter any **username** (offline, not validated).
2. The launcher authenticates with the baked-in forged token and downloads the managed mod set.
3. It **aborts before launching** (by design, this build is for retrieving the mods, not playing).

### Output

The downloaded mods and launch profile land under this folder:

```
run/minecraft/.cache-jrt/<hash>/   revive-client.jar, revive-loader.jar, fabric-api.jar,
                                   baritone.jar, viafabricplus.jar, xaero-*.jar, xaeroplus.jar
run/minecraft/versions/fabric-loader-0.18.4-1.21.11/
run/revive-launcher.json           launcher state
```

That `.cache-jrt` set is the client exactly as Revive ships it, ready to decompile/compare against
`02. Source Reconstruction`.

## Notes

- Running this hits **Revive's live backend** with the forged token. It is a one-off retrieval of the
  distributed payload (which is itself a skid of the original client, see
  `03. Analysis/LOADER-PROVENANCE.md`) for analysis, not a service to operate or hammer.
- `package.json` carries the same `electron` devDependency / `start` script / `trustedDependencies`
  additions as the reconstructed launcher so `bun install && bun run start` works.
- To restore normal behavior, un-comment the auth blocks in `src/authClient.js` / `src/config.js`
  and remove the abort `throw` in `src/minecraft.js`.
