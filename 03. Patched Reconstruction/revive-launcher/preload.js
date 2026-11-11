'use strict';

// Secure bridge between the UI (renderer) and the main process. The renderer
// gets only these named functions — no direct Node access.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('revive', {
  getState: () => ipcRenderer.invoke('get-state'),
  saveSettings: (partial) => ipcRenderer.invoke('save-settings', partial),
  login: (username, password) => ipcRenderer.invoke('account-login', { username, password }),
  register: (username, password) => ipcRenderer.invoke('account-register', { username, password }),
  logout: () => ipcRenderer.invoke('account-logout'),
  play: () => ipcRenderer.invoke('play'),
  openExternal: (url) => ipcRenderer.invoke('open-external', url),

  modsList: () => ipcRenderer.invoke('mods-list'),
  modsAdd: () => ipcRenderer.invoke('mods-add'),
  modsToggle: (file) => ipcRenderer.invoke('mods-toggle', file),
  modsRemove: (file) => ipcRenderer.invoke('mods-remove', file),

  onLog: (cb) => ipcRenderer.on('log', (_e, line) => cb(line)),
  onStatus: (cb) => ipcRenderer.on('status', (_e, s) => cb(s)),
});
