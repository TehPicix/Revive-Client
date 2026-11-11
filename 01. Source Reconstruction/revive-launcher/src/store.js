'use strict';

// Tiny JSON settings store in the app's userData dir. Keeps the saved account
// token, the Microsoft refresh token, and user preferences between runs.

const fs = require('node:fs');
const path = require('node:path');
const { DEFAULTS, defaultGameDir } = require('./config');

class Store {
  constructor(userDataDir) {
    this.file = path.join(userDataDir, 'revive-launcher.json');
    this.data = this._read();
  }

  _read() {
    try {
      return JSON.parse(fs.readFileSync(this.file, 'utf8'));
    } catch {
      return {};
    }
  }

  _write() {
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      fs.writeFileSync(this.file, JSON.stringify(this.data, null, 2));
    } catch (err) {
      console.error('Failed to write settings:', err);
    }
  }

  get(key) {
    if (this.data[key] !== undefined) return this.data[key];
    if (key === 'gameDir') return defaultGameDir();
    return DEFAULTS[key];
  }

  set(key, value) {
    this.data[key] = value;
    this._write();
  }

  delete(key) {
    delete this.data[key];
    this._write();
  }

  // Convenience: the full resolved settings object the UI shows.
  settings() {
    return {
      authHost: this.get('authHost'),
      downloadBase: this.get('downloadBase'),
      minecraftVersion: this.get('minecraftVersion'),
      fabricLoaderVersion: this.get('fabricLoaderVersion'),
      loaderMode: this.get('loaderMode'),
      maxMemory: this.get('maxMemory'),
      minMemory: this.get('minMemory'),
      gameDir: this.get('gameDir'),
      requiredMods: this.get('requiredMods'),
      optionalMods: this.get('optionalMods'),
      accentColor: this.get('accentColor'),
      accentColor2: this.get('accentColor2'),
    };
  }
}

module.exports = { Store };
