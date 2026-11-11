# Analysis mode (patched launcher)

This folder holds a single patch, [`analysis-mode.patch`](analysis-mode.patch), that turns the clean
launcher in the parent folder into the **analysis build** used to pull Revive's distributed mod set
for the teardown **without creating or signing into any account, and without launching the game**.

Keeping it as a patch (rather than a second copy of the whole launcher) means there is one source of
truth: the pristine reconstruction is what sits in `..`, and the analysis build is that source plus
this diff. Both are always available, and the patch *is* the exact, reviewable record of what the
analysis build changes.

## What the patch changes

Seven files, all behavioral, no new dependencies:

| File | Change |
|---|---|
| `src/authClient.js` | The `/account/login` and `/account/register` network calls are removed. `login()`/`register()` instead return a locally built, **unsigned `alg:none` JWT** (`role: "admin"`, ~1000-year expiry) as the access token. |
| `src/minecraft.js` | `microsoftLogin()` is stubbed to return a random offline name/UUID (no `msmc`, no Microsoft window); `hideFile()` becomes a no-op; and `launchGame()` **throws right after the mods are downloaded**, so the build fetches the payload but never starts Minecraft. |
| `src/config.js` | `authHost` is commented out and `defaultGameDir()` is redirected to `./run/minecraft`, so everything lands inside this launcher folder. |
| `main.js` | `userData` is pointed at `./run`, the auto-updater is disabled, and `login`/`register` are called with a `null` host. |
| `src/store.js` | Drops `authHost` from the settings object. |
| `renderer/index.html`, `renderer/renderer.js` | The sign-in form is reduced to a single username field (defaults to `Anonymous`); the password is not used. |

## What it demonstrates

- **Revive's download path accepts a forged, unsigned token.** No real Microsoft or Revive account
  is needed to obtain the client, their backend still serves the mods against an `alg:none` JWT.
- **The client ships as a plain managed mod set** (`revive-client.jar`, `revive-loader.jar`,
  `fabric-api.jar`, `baritone.jar`, `viafabricplus.jar`, `xaero-minimap.jar`, `xaero-worldmap.jar`,
  `xaeroplus.jar`), downloaded into `run/` for comparison against the reconstruction.

## Apply / reverse

Run from the launcher root (the parent of this folder):

```bash
cd "01. Source Reconstruction/revive-launcher"
git apply analysis-mode/analysis-mode.patch       # switch to the analysis build
git apply -R analysis-mode/analysis-mode.patch     # switch back to the clean reconstruction
```

Convenience wrappers that do exactly that and can be run from anywhere:

```bash
bash analysis-mode/apply.sh          # or: apply.sh -R   to reverse
```

```powershell
analysis-mode\apply.ps1              # or: analysis-mode\apply.ps1 -Reverse
```

`git apply --check analysis-mode/analysis-mode.patch` dry-runs it. The patch is generated against the
committed launcher source, so it applies cleanly to a pristine checkout and reverses back to it
byte-for-byte.

## Run the analysis build

Same toolchain as the clean launcher (Bun installs; the app runs under Electron):

```bash
bun install
bun run start
```

Then enter any username. The launcher authenticates with the baked-in forged token, downloads the
managed mod set, and **aborts before launching** (by design). The payload lands under:

```
run/minecraft/.cache-jrt/<hash>/   revive-client.jar, revive-loader.jar, fabric-api.jar,
                                   baritone.jar, viafabricplus.jar, xaero-*.jar, xaeroplus.jar
run/minecraft/versions/fabric-loader-<ver>-1.21.11/
run/revive-launcher.json           launcher state
```

That `.cache-jrt` set is the client exactly as Revive ships it. `run/` is git-ignored.

## Notes

- Running this hits **Revive's live backend** with the forged token. It is a one-off retrieval of the
  distributed payload for analysis, not a service to operate or hammer.
- The payload is itself a skid of the original client, see
  [`LOADER-PROVENANCE.md`](../../../02.%20Teardown%20Analysis/LOADER-PROVENANCE.md) and the Electron
  write-up [`ELECTRON-TEARDOWN.md`](../../../02.%20Teardown%20Analysis/ELECTRON-TEARDOWN.md).
