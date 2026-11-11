'use strict';

// Default endpoints and versions. These point at your domain; a user can
// override the server in the launcher's settings if you ever move it.

const os = require('node:os');
const path = require('node:path');

const DEFAULTS = {
  // Your account/auth server (register, login, authorize, download).
  // authHost: 'https://revive-client.com',

  // Where the launcher fetches the mods it installs into each player's mods
  // folder. Publish each file below into the server's public/downloads/ folder.
  downloadBase: 'https://revive-client.com/downloads',

  // Mods the launcher puts in the game's mods folder.
  //   required  -> launch fails if these are missing (the client can't run)
  //   optional  -> downloaded if present; if missing, those features just
  //                self-disable in-game (no crash)
  // In "gate" mode the client jar (revive-client.jar) is added to required
  // automatically, since the client isn't downloaded encrypted in that mode.
  //
  // XaeroPlus is required: the client's chunk-hunting code (TrailFollower)
  // references XaeroPlus classes directly, so the client cannot even load if
  // it's absent. XaeroPlus in turn requires Xaero's WorldMap AND Minimap to be
  // present, so all three ship together as required. Use a matching set built
  // for this Minecraft version (e.g. XaeroPlus 2.31.5 + WorldMap 1.40.16 +
  // Minimap 25.x for 1.21.11).
  //
  // Baritone stays optional: there is no Baritone build for 1.21.11 yet, and
  // the client runs fine without it (Baritone-only features self-disable via
  // BaritoneBridge). Do NOT put an older-version baritone.jar here — Fabric
  // will refuse to load it against 1.21.11 and abort the whole game.
  //
  // ViaFabricPlus is optional: it lets players join servers running other
  // Minecraft versions (protocol translation). The client has no code
  // references to it, so the game runs fine when it's absent. Publish a build
  // matching this Minecraft version (e.g. ViaFabricPlus 4.4.9 for 1.21.11).
  requiredMods: ['revive-loader.jar', 'fabric-api.jar', 'xaeroplus.jar', 'xaero-worldmap.jar', 'xaero-minimap.jar'],
  optionalMods: ['baritone.jar', 'viafabricplus.jar'],

  // Game versions. Keep these in step with the client + loader gradle.properties.
  minecraftVersion: '1.21.11',
  fabricLoaderVersion: '0.18.4',

  // "gate" (verify the account, client installed as a normal mod — the reliable
  // default) or "inject" (encrypted download + runtime injection).
  loaderMode: 'gate',

  // JVM memory for the game.
  maxMemory: '4G',
  minMemory: '2G',

  // Launcher accent colours (editable in Settings). Default = the client's pink.
  accentColor: '#ff69be',
  accentColor2: '#c084fc',
};

// Per-user, isolated game directory so we never touch the player's normal
// .minecraft. On Windows: %APPDATA%/ReviveLauncher/minecraft ; on Linux:
// ~/.local/share/ReviveLauncher/minecraft.
function defaultGameDir() {
  // if (process.platform === 'win32') {
  //   return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'),
  //     'ReviveLauncher', 'minecraft');
  // }
  // return path.join(os.homedir(), '.local', 'share', 'ReviveLauncher', 'minecraft');
  return path.resolve("./run/minecraft")
}

module.exports = { DEFAULTS, defaultGameDir };
