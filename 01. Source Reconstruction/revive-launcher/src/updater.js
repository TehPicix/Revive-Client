'use strict';

// Launcher self-update via electron-updater. Pulls a newer launcher build from
// the VPS update feed configured under build.publish in package.json
// (https://revive-client.com/updates). The newer installer is downloaded in the
// background and installed when the launcher next closes; the user is also
// offered an immediate restart once a download finishes.
//
// This is entirely best-effort: it only runs in a packaged build, and every
// path is wrapped so a missing dependency, an unreachable feed, or any updater
// error is logged and ignored. It must NEVER block the launcher or the game
// launch.

const { app, dialog } = require('electron');

function initAutoUpdate(getWin, log) {
  // Never run in development: there is no packaged app-update.yml, and
  // electron-updater throws if asked to check in that case.
  if (!app.isPackaged) {
    if (log) log('(update) skipped in development build.');
    return;
  }

  let autoUpdater;
  try {
    ({ autoUpdater } = require('electron-updater'));
  } catch {
    // Dependency not installed in this build; silently skip.
    return;
  }

  try {
    autoUpdater.autoDownload = true;         // fetch the update in the background
    autoUpdater.autoInstallOnAppQuit = true; // install it whenever the launcher next closes
    autoUpdater.allowPrerelease = false;

    autoUpdater.on('checking-for-update', () => log && log('Checking for launcher updates…'));
    autoUpdater.on('update-not-available', () => log && log('Launcher is up to date.'));
    autoUpdater.on('update-available', (info) => log && log(`Launcher update available: v${info && info.version}. Downloading…`));
    autoUpdater.on('download-progress', (p) => log && log(`Update download: ${Math.round((p && p.percent) || 0)}%`));
    autoUpdater.on('error', (err) => log && log(`Update check failed (ignored): ${(err && err.message) || err}`));

    autoUpdater.on('update-downloaded', async (info) => {
      if (log) log(`Launcher update v${info && info.version} ready.`);
      const win = (getWin && getWin()) || undefined;
      try {
        const { response } = await dialog.showMessageBox(win, {
          type: 'info',
          buttons: ['Restart now', 'Later'],
          defaultId: 0,
          cancelId: 1,
          title: 'Update ready',
          message: `A new launcher version (v${info && info.version}) has been downloaded.`,
          detail: 'Restart the launcher to finish updating. Otherwise it installs automatically the next time you close the launcher.',
        });
        if (response === 0) setImmediate(() => { try { autoUpdater.quitAndInstall(); } catch { /* ignore */ } });
      } catch {
        // If the prompt fails, the update still installs on the next quit.
      }
    });

    // Non-blocking check. Never awaited on the launch path.
    autoUpdater.checkForUpdates().catch((err) => {
      if (log) log(`Update check failed (ignored): ${(err && err.message) || err}`);
    });
  } catch (err) {
    if (log) log(`Auto-update init failed (ignored): ${(err && err.message) || err}`);
  }
}

module.exports = { initAutoUpdate };
