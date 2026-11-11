'use strict';

// The Minecraft side of the launcher: Microsoft login (so the player can join
// online servers), installing Fabric + the loader mod into an isolated game
// directory, and launching the game with the account token in place.
//
// Heavy lifting is delegated to two well-established libraries:
//   - msmc                    : Microsoft / Minecraft authentication
//   - minecraft-launcher-core : download + launch the game (MCLC)

const fs = require('node:fs');
const path = require('node:path');

// Hide the Java console window on Windows. minecraft-launcher-core starts the
// game with java.exe, a console program; when a GUI app (this launcher) spawns
// one, Windows pops up a separate black console window next to Minecraft. Node's
// spawn accepts `windowsHide` to suppress exactly that window, but MCLC never
// sets it — so we wrap child_process.spawn here (before MCLC is required below,
// so it picks up the patched version) and force it on for every spawn. Harmless
// on other platforms, where windowsHide has no effect.
const childProcess = require('node:child_process');
const _origSpawn = childProcess.spawn;
childProcess.spawn = function (command, args, options) {
  if (process.platform === 'win32') {
    options = Object.assign({}, options, { windowsHide: true });
  }
  return _origSpawn.call(childProcess, command, args, options);
};

// ---- filesystem helpers -----------------------------------------------------

function ensureDirs(gameDir) {
  fs.mkdirSync(path.join(gameDir, 'mods'), { recursive: true });
  fs.mkdirSync(path.join(gameDir, 'versions'), { recursive: true });
  // Hide the whole game directory from casual Explorer browsing. NEVER hide
  // the jars themselves: Fabric's mod discovery skips hidden files
  // (DirectoryModCandidateFinder.isValidFile -> Files.isHidden), so hidden
  // jars silently fail to load. Files inside a hidden folder don't carry the
  // attribute themselves, so everything loads normally.
  hideFile(gameDir);
}

// Mark a file or directory hidden + system on Windows so it doesn't show up in
// Explorer (even with "Show hidden files" on; only "Hide protected operating
// system files" reveals +s entries). No-op elsewhere. WARNING: never apply this
// to mod jars — Fabric's mod discovery skips hidden files entirely.
function hideFile(p) {
  // No-op: hiding files is disabled for now.
  return;
}

// Write the account token where the loader looks for it (game-dir file). This
// avoids depending on custom game arguments and works with any launch backend.
// The existing file is removed first: Windows refuses to overwrite a file that
// carries the hidden attribute, and we hide this one after writing.
function writeToken(gameDir, accessToken) {
  const tokenPath = path.join(gameDir, 'revive-token.txt');
  try { fs.rmSync(tokenPath, { force: true }); } catch { /* ignore */ }
  fs.writeFileSync(tokenPath, accessToken, 'utf8');
  hideFile(tokenPath);
}

// Parent of the per-launch managed-jar directories (see createManagedDir).
function managedRoot(gameDir) {
  return path.join(gameDir, '.cache-jrt');
}

// Create the directory the managed client jars live in for THIS launch: a
// random name under a hidden parent, never the visible mods folder. The jars
// are handed to Fabric by explicit path (-Dfabric.addMods), so someone
// browsing to mods/ finds only their own mods, and the path from a previous
// session tells them nothing about the next one. The parent gets hidden+system
// attributes; the jars inside are NOT hidden (files inside a hidden folder
// don't carry the attribute, and fabric.addMods takes them by exact path).
function createManagedDir(gameDir) {
  const crypto = require('node:crypto');
  const root = managedRoot(gameDir);
  const dir = path.join(root, crypto.randomBytes(12).toString('hex'));
  fs.mkdirSync(dir, { recursive: true });
  hideFile(root);
  return dir;
}

// Delete the launcher-managed jars and the account token from the game dir.
// Called after the game exits (the launcher stays alive, hidden, to do this)
// and again on launcher startup as a safety net for crashed/killed sessions —
// so the client files only sit on disk while the game is actually running.
// The player's own mods are left untouched. The mods-dir sweep of managed
// names is kept as a migration/safety net for copies installed by older
// launcher versions that still placed managed jars in mods/.
function cleanupManagedFiles(gameDir, requiredMods, optionalMods) {
  const modsDir = path.join(gameDir, 'mods');
  const names = new Set([...(requiredMods || []), ...(optionalMods || [])]);
  names.add('revive-client.jar'); // installed automatically in gate mode
  for (const name of names) {
    try { fs.rmSync(path.join(modsDir, name), { force: true }); } catch { /* ignore */ }
  }
  try { fs.rmSync(managedRoot(gameDir), { recursive: true, force: true }); } catch { /* ignore */ }
  try { fs.rmSync(path.join(gameDir, 'revive-token.txt'), { force: true }); } catch { /* ignore */ }
}

async function downloadTo(url, dest, log) {
  log(`Downloading ${path.basename(dest)} ...`);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Download failed (${res.status}) for ${url}`);
  const buf = Buffer.from(await res.arrayBuffer());
  // A jar is a zip and must start with the "PK" magic. Anything else means the
  // server sent an error/HTML page with status 200 — saving it as a .jar would
  // make Fabric silently ignore it and fail later with confusing "mod X is
  // missing" errors in-game, so refuse it here where the cause is obvious.
  if (dest.endsWith('.jar') && !(buf.length > 4 && buf[0] === 0x50 && buf[1] === 0x4b)) {
    throw new Error(`Server sent an invalid file for ${path.basename(dest)} (not a jar) — check ${url}`);
  }
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, buf);
  log(`Saved ${path.basename(dest)} (${buf.length} bytes).`);
}

// Fetch the mods from your download host into the per-launch managed dir (NOT
// the visible mods folder — Fabric loads them by explicit path instead), so you
// can update them server-side without re-shipping the launcher. Required mods
// abort the launch if missing; optional mods are skipped with a warning (the
// client self-disables the matching features). Returns the absolute paths of
// the managed jars actually installed, for the -Dfabric.addMods argument.
async function ensureMods(gameDir, managedDir, downloadBase, requiredMods, optionalMods, log) {
  const modsDir = path.join(gameDir, 'mods');
  const base = downloadBase.replace(/\/$/, '');
  const installed = [];

  // Migration/safety: clear copies of managed names that an older launcher
  // version put in the visible mods folder, so nothing double-loads.
  for (const name of [...requiredMods, ...optionalMods]) {
    try { fs.rmSync(path.join(modsDir, name), { force: true }); } catch { /* ignore */ }
  }

  for (const name of requiredMods) {
    const dest = path.join(managedDir, name);
    await downloadTo(`${base}/${name}`, dest, log);
    installed.push(dest);
  }
  for (const name of optionalMods) {
    try {
      const dest = path.join(managedDir, name);
      await downloadTo(`${base}/${name}`, dest, log);
      installed.push(dest);
    } catch (err) {
      log(`(optional) skipping ${name}: ${err.message}`);
    }
  }

  // Jars in the mods folder are the player's own mods (added via the
  // launcher's mods panel or dropped in by hand). They are left untouched;
  // list them so launch logs show exactly what the game is loading.
  try {
    const custom = fs.readdirSync(modsDir).filter((n) => n.endsWith('.jar'));
    if (custom.length) log(`Your mods: ${custom.join(', ')}`);
  } catch { /* ignore */ }

  return installed;
}

// Install a Fabric version profile MCLC can launch. Pulls the ready-made profile
// JSON from Fabric's meta API and writes it under versions/<id>/<id>.json.
async function installFabricProfile(gameDir, mcVersion, loaderVersion, log) {
  const id = `fabric-loader-${loaderVersion}-${mcVersion}`;
  const dir = path.join(gameDir, 'versions', id);
  const jsonPath = path.join(dir, `${id}.json`);
  fs.mkdirSync(dir, { recursive: true });

  log(`Installing Fabric profile ${id} ...`);
  const url = `https://meta.fabricmc.net/v2/versions/loader/${mcVersion}/${loaderVersion}/profile/json`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Fabric meta request failed (${res.status}). Check the Minecraft/Fabric versions.`);
  const profile = await res.text();
  fs.writeFileSync(jsonPath, profile, 'utf8');
  log('Fabric profile installed.');
  return id;
}

// ---- Microsoft / Minecraft authentication -----------------------------------

// Returns { auth, saved, name } where `auth` is the MCLC authorization object,
// `saved` is a token blob to persist for silent re-login, and `name` is the
// Minecraft username.
async function microsoftLogin({ savedToken, log }) {
  // No-op: yea we not doin that shit
  return {
    auth: {
      uuid: crypto.randomUUID(),
      name: `Player${ Math.floor(Math.random() * 1000).toString().padStart(3, '0') }`,
    }
  };
}

// Find javaw.exe (the windowless Java launcher) inside the runtime MCLC
// downloads under the game directory. Using javaw instead of java.exe stops
// Windows from popping up a separate black console window next to the game.
// Returns null if no runtime is present yet (e.g. the very first launch, before
// MCLC has downloaded Java) — in which case we let MCLC pick java.exe as usual.
function findJavaw(gameDir) {
  const runtimeRoot = path.join(gameDir, 'runtime');
  if (!fs.existsSync(runtimeRoot)) return null;
  const stack = [runtimeRoot];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(full);
      else if (entry.name.toLowerCase() === 'javaw.exe') return full;
    }
  }
  return null;
}

// Locate a system-installed javaw.exe via PATH (used when MCLC runs the game on
// the machine's own Java rather than a runtime it downloaded). We ask for java's
// location and take its javaw.exe sibling, since both live in the same bin dir.
function findSystemJavaw() {
  if (process.platform !== 'win32') return null;
  for (const name of ['javaw', 'java']) {
    try {
      const out = childProcess.execSync(`where ${name}`, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
      const first = out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean)[0];
      if (!first) continue;
      const javaw = first.replace(/java\.exe$/i, 'javaw.exe');
      if (fs.existsSync(javaw)) return javaw;
    } catch {
      // `where` not found or no match — try the next name.
    }
  }
  return null;
}

// Resolve the Java binary to use, preferring the windowless javaw.exe on Windows.
function resolveJavaPath(gameDir, javaPath) {
  if (process.platform !== 'win32') return javaPath;
  // If a java.exe path was given, use its javaw.exe sibling when it exists.
  if (javaPath && /java\.exe$/i.test(javaPath)) {
    const sibling = javaPath.replace(/java\.exe$/i, 'javaw.exe');
    if (fs.existsSync(sibling)) return sibling;
    return javaPath;
  }
  if (javaPath) return javaPath;
  // No explicit path: prefer javaw.exe from MCLC's downloaded runtime, then fall
  // back to a system-installed javaw.exe (which is what this machine uses).
  return findJavaw(gameDir) || findSystemJavaw() || undefined;
}

// ---- launch -----------------------------------------------------------------

async function launchGame(opts) {
  const {
    gameDir, mcAuth, accessToken, minecraftVersion, fabricLoaderVersion,
    downloadBase, loaderMode, requiredMods, optionalMods, maxMemory, minMemory,
    javaPath, log, onClose, onGameStarted,
  } = opts;

  // In gate mode the client is a normal mod, so it must be installed too.
  const required = [...requiredMods];
  if (loaderMode === 'gate' && !required.includes('revive-client.jar')) {
    required.push('revive-client.jar');
  }

  ensureDirs(gameDir);
  writeToken(gameDir, accessToken);
  // Managed client jars go to a random-named hidden dir for this launch only —
  // never the visible mods folder — and are handed to Fabric by explicit path
  // below. Deleted again after the game closes (cleanupManagedFiles).
  const managedDir = createManagedDir(gameDir);
  const managedJars = await ensureMods(gameDir, managedDir, downloadBase, required, optionalMods, log);
  const fabricId = await installFabricProfile(gameDir, minecraftVersion, fabricLoaderVersion, log);

  throw new Error('Launch aborted to prevent execution.');

  const { Client } = require('minecraft-launcher-core');
  const launcher = new Client();

  const customArgs = [];
  if (loaderMode === 'gate') customArgs.push('-Drevive.loader.mode=gate');
  if (managedJars.length) customArgs.push(`-Dfabric.addMods=${managedJars.join(path.delimiter)}`);

  const options = {
    authorization: mcAuth,
    root: gameDir,
    version: { number: minecraftVersion, type: 'release', custom: fabricId },
    memory: { max: maxMemory, min: minMemory },
    customArgs,
    // Run the game in its own process group so it keeps running after the
    // launcher closes itself once the game window is up.
    overrides: { detached: true },
  };
  const resolvedJava = resolveJavaPath(gameDir, javaPath);
  if (resolvedJava) {
    options.javaPath = resolvedJava;
    log(`Using Java: ${resolvedJava}`);
  }

  // Fire onGameStarted once, when the game's own window is coming up. These log
  // lines appear right as the LWJGL window / sound engine initialize, which is
  // the natural moment to hand off and close the launcher.
  let gameStartedFired = false;
  const READY_MARKERS = ['Setting user:', 'Backend library: LWJGL', 'Sound engine started'];
  function checkGameStarted(line) {
    if (gameStartedFired || !onGameStarted) return;
    if (READY_MARKERS.some((marker) => line.includes(marker))) {
      gameStartedFired = true;
      onGameStarted();
    }
  }

  launcher.on('debug', (line) => log(String(line)));
  launcher.on('data', (line) => {
    const text = String(line);
    log(text);
    checkGameStarted(text);
  });
  launcher.on('progress', (p) => {
    if (p && p.type) log(`... ${p.type} ${p.task ?? ''}/${p.total ?? ''}`);
  });
  launcher.on('close', (code) => onClose(code));

  log('Launching Minecraft ...');
  const child = await launcher.launch(options);
  // Detach fully so the game is not tied to the launcher's lifetime.
  if (child && typeof child.unref === 'function') child.unref();

  // Fallback: if we never saw a ready marker (log format changed, etc.) but the
  // game process is alive, still hand off after a delay so the launcher closes.
  if (onGameStarted && child) {
    setTimeout(() => {
      if (!gameStartedFired) {
        gameStartedFired = true;
        onGameStarted();
      }
    }, 30000);
  }
}

module.exports = { microsoftLogin, launchGame, cleanupManagedFiles };
