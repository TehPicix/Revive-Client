'use strict';


const fs = require('node:fs');
const path = require('node:path');
const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');

// Set the userData path to a local "run" directory for storing app data.
app.setPath('userData', path.resolve("./run"));

const { Store } = require('./src/store');
const authClient = require('./src/authClient');
const minecraft = require('./src/minecraft');
const { fetchManifest } = require('./src/manifest');
const { initAutoUpdate } = require('./src/updater');

let win = null;
let store = null;

function createWindow() {
  win = new BrowserWindow({
    width: 900,
    height: 620,
    resizable: false,
    title: 'Revive Launcher',
    icon: path.join(__dirname, 'build', 'icon.png'),
    backgroundColor: '#0e0a16',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

function log(line) {
  if (win && !win.isDestroyed()) win.webContents.send('log', line);
}
function status(text, kind = 'info') {
  if (win && !win.isDestroyed()) win.webContents.send('status', { text, kind });
}

app.whenReady().then(() => {
  store = new Store(app.getPath('userData'));
  // Safety net: if a previous session was killed before its post-game cleanup
  // ran, remove the managed client files now.
  try {
    minecraft.cleanupManagedFiles(store.get('gameDir'), store.get('requiredMods'), store.get('optionalMods'));
  } catch { /* ignore */ }
  createWindow();

  // Best-effort launcher self-update. Never blocks startup or the game launch.
  // try { initAutoUpdate(() => win, log); } catch { /* ignore */ }
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });



});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// ---- IPC: state + settings --------------------------------------------------

ipcMain.handle('get-state', () => ({
  settings: store.settings(),
  username: store.get('username') || null,
  signedIn: Boolean(store.get('accessToken')),
}));

ipcMain.handle('save-settings', (_e, partial) => {
  for (const [key, value] of Object.entries(partial || {})) {
    if (value !== undefined && value !== null && value !== '') store.set(key, value);
  }
  return store.settings();
});

// ---- IPC: account -----------------------------------------------------------

ipcMain.handle('account-login', async (_e, { username, password }) => {
  const { username: name, accessToken } = await authClient.login(null, username, password);
  store.set('username', name);
  store.set('accessToken', accessToken);
  return { username: name };
});

ipcMain.handle('account-register', async (_e, { username, password }) => {
  const { username: name, accessToken } = await authClient.register(null, username, password);
  store.set('username', name);
  store.set('accessToken', accessToken);
  return { username: name };
});

ipcMain.handle('account-logout', () => {
  store.delete('accessToken');
  store.delete('username');
  store.delete('msSession');
  return true;
});

ipcMain.handle('open-external', (_e, url) => shell.openExternal(url));

// ---- IPC: custom mods -------------------------------------------------------
//
// Players can drop their own Fabric mods (Litematica, etc.) next to the managed
// ones. The launcher only ever clears/re-downloads the managed jar names on
// launch (see ensureMods), so user jars survive updates untouched. Disabling a
// mod renames it to <name>.jar.disabled, which Fabric ignores.

function modsDir() {
  return path.join(store.get('gameDir'), 'mods');
}

// Jar names the launcher itself installs and updates. Users can't add, toggle,
// or remove these — the next launch would overwrite whatever they did anyway.
function managedModNames() {
  const names = new Set([
    ...(store.get('requiredMods') || []),
    ...(store.get('optionalMods') || []),
  ]);
  names.add('revive-client.jar'); // installed automatically in gate mode
  return names;
}

// Fabric mod IDs the managed jars declare. Fabric aborts the whole launch on a
// duplicate mod id, so a player adding e.g. their own Fabric API jar (under any
// filename) would brick the game — reject those in the add dialog instead.
const MANAGED_MOD_IDS = new Set([
  'revive-client', 'revive-loader', 'fabric-api',
  'xaeroplus', 'xaerominimap', 'xaeroworldmap', 'baritone',
  'viafabricplus',
]);

// Read the fabric mod id out of a jar without any zip dependency: walk the zip
// central directory for fabric.mod.json and inflate it. Returns null for
// non-Fabric or unreadable jars (those are left to Fabric itself to judge).
function readModId(jarPath) {
  try {
    const buf = fs.readFileSync(jarPath);
    // End-of-central-directory record: scan back over the (max 64KB) comment.
    let eocd = -1;
    for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 65535); i--) {
      if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) return null;
    const count = buf.readUInt16LE(eocd + 10);
    let off = buf.readUInt32LE(eocd + 16);
    for (let n = 0; n < count; n++) {
      if (buf.readUInt32LE(off) !== 0x02014b50) return null;
      const method = buf.readUInt16LE(off + 10);
      const compSize = buf.readUInt32LE(off + 20);
      const nameLen = buf.readUInt16LE(off + 28);
      const extraLen = buf.readUInt16LE(off + 30);
      const commentLen = buf.readUInt16LE(off + 32);
      const localOff = buf.readUInt32LE(off + 42);
      if (buf.toString('utf8', off + 46, off + 46 + nameLen) === 'fabric.mod.json') {
        // The local header's name/extra lengths can differ from the central ones.
        const lNameLen = buf.readUInt16LE(localOff + 26);
        const lExtraLen = buf.readUInt16LE(localOff + 28);
        const start = localOff + 30 + lNameLen + lExtraLen;
        const data = buf.subarray(start, start + compSize);
        const json = method === 8 ? require('node:zlib').inflateRawSync(data) : data;
        const id = JSON.parse(json.toString('utf8')).id;
        return typeof id === 'string' ? id : null;
      }
      off += 46 + nameLen + extraLen + commentLen;
    }
  } catch { /* treat as unknown */ }
  return null;
}

ipcMain.handle('mods-list', () => {
  const dir = modsDir();
  fs.mkdirSync(dir, { recursive: true });
  const managed = managedModNames();
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isFile()) continue;
    const file = entry.name;
    const lower = file.toLowerCase();
    const enabled = lower.endsWith('.jar');
    const disabled = lower.endsWith('.jar.disabled');
    if (!enabled && !disabled) continue;
    const name = disabled ? file.slice(0, -'.disabled'.length) : file;
    let size = 0;
    try { size = fs.statSync(path.join(dir, file)).size; } catch { /* ignore */ }
    out.push({ file, name, enabled, managed: managed.has(name), size });
  }
  // Managed mods first, then the user's own, both alphabetical.
  out.sort((a, b) => (a.managed !== b.managed ? (a.managed ? -1 : 1) : a.name.localeCompare(b.name)));
  return out;
});

ipcMain.handle('mods-add', async () => {
  const res = await dialog.showOpenDialog(win, {
    title: 'Add Fabric mods',
    filters: [{ name: 'Fabric mods (*.jar)', extensions: ['jar'] }],
    properties: ['openFile', 'multiSelections'],
  });
  if (res.canceled || !res.filePaths.length) return { added: [], skipped: [] };

  const dir = modsDir();
  fs.mkdirSync(dir, { recursive: true });
  const managed = managedModNames();

  // Mod ids already present in the folder (the player's earlier additions), so
  // adding the same mod twice under a different filename is caught too.
  const existingIds = new Map(); // id -> filename
  try {
    for (const f of fs.readdirSync(dir)) {
      if (!f.toLowerCase().endsWith('.jar') || managed.has(f)) continue;
      const id = readModId(path.join(dir, f));
      if (id) existingIds.set(id, f);
    }
  } catch { /* ignore */ }

  const added = [];
  const skipped = [];
  for (const src of res.filePaths) {
    const name = path.basename(src);
    if (managed.has(name)) {
      skipped.push(`${name} — managed by the launcher`);
      continue;
    }
    const id = readModId(src);
    if (id && MANAGED_MOD_IDS.has(id)) {
      skipped.push(`${name} — the launcher already provides '${id}', a second copy would crash the game`);
      continue;
    }
    if (id && existingIds.has(id) && existingIds.get(id) !== name) {
      skipped.push(`${name} — '${id}' is already added as ${existingIds.get(id)}`);
      continue;
    }
    try {
      fs.copyFileSync(src, path.join(dir, name));
      // If a disabled copy of the same mod exists, drop it so the fresh jar wins.
      fs.rmSync(path.join(dir, name + '.disabled'), { force: true });
      if (id) existingIds.set(id, name);
      added.push(name);
    } catch (err) {
      skipped.push(`${name} — ${err.message}`);
    }
  }
  return { added, skipped };
});

// Resolve a renderer-supplied file name to a safe path inside the mods dir,
// refusing managed names. Throws on anything fishy.
function customModPath(file) {
  const name = path.basename(String(file || ''));
  if (!name || (!name.toLowerCase().endsWith('.jar') && !name.toLowerCase().endsWith('.jar.disabled'))) {
    throw new Error('Not a mod file.');
  }
  const base = name.toLowerCase().endsWith('.disabled') ? name.slice(0, -'.disabled'.length) : name;
  if (managedModNames().has(base)) throw new Error('This mod is managed by the launcher.');
  return path.join(modsDir(), name);
}

ipcMain.handle('mods-toggle', (_e, file) => {
  const from = customModPath(file);
  const to = from.toLowerCase().endsWith('.disabled')
    ? from.slice(0, -'.disabled'.length)
    : from + '.disabled';
  // On Windows renameSync refuses to overwrite; if a stale copy with the
  // target name already exists (enabled + disabled side by side), drop it.
  fs.rmSync(to, { force: true });
  fs.renameSync(from, to);
  return true;
});

ipcMain.handle('mods-remove', (_e, file) => {
  fs.rmSync(customModPath(file), { force: true });
  return true;
});

// ---- IPC: play --------------------------------------------------------------

let launching = false;
// Set once the game window is up and the launcher hides itself. From then on
// the launcher lives in the background solely to clean up the managed client
// files when the game exits, then quits.
let hiddenForGame = false;

ipcMain.handle('play', async () => {
  if (launching) return { ok: false, error: 'Already launching.' };
  const accessToken = store.get('accessToken');
  if (!accessToken) return { ok: false, error: 'Log in to your Revive account first.' };

  launching = true;
  try {

    status('Signing in to Minecraft…', 'info');
    const { auth, saved } = await minecraft.microsoftLogin({
      savedToken: store.get('msSession') || null,
      log,
    });
    if (saved) store.set('msSession', saved);
    log(`Minecraft account: ${auth.name}`);

    status('Preparing and launching…', 'info');
    const s = store.settings();

    // Ask the server for the current mod list so mods can be added/removed
    // server-side without a launcher rebuild. Falls back to the values baked
    // into config.js if the server is unreachable or the manifest is invalid.
    const manifest = await fetchManifest(s.downloadBase, log);
    const requiredMods = (manifest && manifest.requiredMods) || s.requiredMods;
    const optionalMods = (manifest && manifest.optionalMods) || s.optionalMods;
    const minecraftVersion = (manifest && manifest.minecraftVersion) || s.minecraftVersion;
    const fabricLoaderVersion = (manifest && manifest.fabricLoaderVersion) || s.fabricLoaderVersion;
    await minecraft.launchGame({
      gameDir: s.gameDir,
      mcAuth: auth,
      accessToken,
      minecraftVersion,
      fabricLoaderVersion,
      downloadBase: s.downloadBase,
      loaderMode: s.loaderMode,
      requiredMods,
      optionalMods,
      maxMemory: s.maxMemory,
      minMemory: s.minMemory,
      javaPath: store.get('javaPath') || undefined,
      log,
      onClose: (code) => {
        launching = false;
        // The game is gone — remove the managed client files so they don't sit
        // on disk between sessions. The player's own mods are left alone.
        try { minecraft.cleanupManagedFiles(s.gameDir, requiredMods, optionalMods); } catch { /* ignore */ }
        if (hiddenForGame) {
          // We only stayed alive for this cleanup; we're done now.
          app.quit();
          return;
        }
        status(code === 0 ? 'Game closed.' : `Game exited (code ${code}).`,
          code === 0 ? 'info' : 'error');
      },
      onGameStarted: () => {
        // The game window is up. Hide the launcher instead of quitting: it
        // stays alive in the background so it can delete the managed client
        // files the moment the game exits (see onClose), then quits itself.
        status('Minecraft is open — launcher is waiting in the background.', 'ok');
        hiddenForGame = true;
        setTimeout(() => {
          if (win && !win.isDestroyed()) win.hide();
        }, 2500);
      },
    });
    status('Minecraft is starting…', 'ok');
    return { ok: true };
  } catch (err) {
    launching = false;
    log(`ERROR: ${err && err.stack ? err.stack : err}`);
    status(err && err.message ? err.message : 'Launch failed.', 'error');
    return { ok: false, error: err && err.message ? err.message : String(err) };
  }
});
