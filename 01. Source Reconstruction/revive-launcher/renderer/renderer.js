'use strict';

const $ = (id) => document.getElementById(id);
let mode = 'login'; // 'login' | 'register'

function setStatus(text, kind = 'info') {
  const el = $('status');
  el.textContent = text;
  el.className = 'status ' + kind;
}

function appendLog(line) {
  const c = $('console');
  c.textContent += line + '\n';
  c.scrollTop = c.scrollHeight;
}

function applyTheme(accent, accent2) {
  const root = document.documentElement.style;
  if (accent) root.setProperty('--accent', accent);
  if (accent2) root.setProperty('--accent2', accent2);
}

function showSignedIn(username, settings) {
  $('authView').style.display = 'none';
  $('playView').style.display = 'block';
  $('who').innerHTML = username ? `Signed in as <b>${username}</b>` : '';
  $('setMaxMem').value = settings.maxMemory;
  $('setMinMem').value = settings.minMemory;
  $('setAccent').value = settings.accentColor || '#ff69be';
  $('setAccent2').value = settings.accentColor2 || '#c084fc';
}

function showSignedOut() {
  $('authView').style.display = 'block';
  $('playView').style.display = 'none';
  $('who').textContent = '';
}

async function refresh() {
  const state = await window.revive.getState();
  applyTheme(state.settings.accentColor, state.settings.accentColor2);
  if (state.signedIn) showSignedIn(state.username, state.settings);
  else showSignedOut();
}

// ---- auth tabs + submit -----------------------------------------------------

$('tabLogin').onclick = () => {
  mode = 'login';
  $('tabLogin').classList.add('active');
  $('tabRegister').classList.remove('active');
  $('authSubmit').textContent = 'Log in';
  $('authError').textContent = '';
};
$('tabRegister').onclick = () => {
  mode = 'register';
  $('tabRegister').classList.add('active');
  $('tabLogin').classList.remove('active');
  $('authSubmit').textContent = 'Create account';
  $('authError').textContent = '';
};

$('authSubmit').onclick = async () => {
  const username = $('username').value.trim();
  const password = $('password').value;
  $('authError').textContent = '';
  if (!username || !password) { $('authError').textContent = 'Enter a username and password.'; return; }
  $('authSubmit').disabled = true;
  try {
    if (mode === 'register') await window.revive.register(username, password);
    else await window.revive.login(username, password);
    $('password').value = '';
    await refresh();
    setStatus('Signed in.', 'ok');
  } catch (err) {
    $('authError').textContent = err.message || String(err);
  } finally {
    $('authSubmit').disabled = false;
  }
};

// ---- play / logout / settings ----------------------------------------------

$('playBtn').onclick = async () => {
  $('playBtn').disabled = true;
  setStatus('Starting…', 'info');
  const res = await window.revive.play();
  if (!res.ok) setStatus(res.error || 'Launch failed.', 'error');
  // Re-enable shortly so the user can retry if something failed.
  setTimeout(() => { $('playBtn').disabled = false; }, 3000);
};

$('logoutBtn').onclick = async () => {
  await window.revive.logout();
  await refresh();
  setStatus('Logged out.', 'info');
};

$('saveSettings').onclick = async () => {
  await window.revive.saveSettings({
    maxMemory: $('setMaxMem').value.trim(),
    minMemory: $('setMinMem').value.trim(),
    accentColor: $('setAccent').value,
    accentColor2: $('setAccent2').value,
  });
  setStatus('Settings saved.', 'ok');
};

// ---- theme colors (live preview + save) ------------------------------------

async function persistColors() {
  await window.revive.saveSettings({
    accentColor: $('setAccent').value,
    accentColor2: $('setAccent2').value,
  });
}

$('setAccent').oninput = () => applyTheme($('setAccent').value, null);
$('setAccent2').oninput = () => applyTheme(null, $('setAccent2').value);
$('setAccent').onchange = persistColors;
$('setAccent2').onchange = persistColors;

$('resetColors').onclick = () => {
  $('setAccent').value = '#ff69be';
  $('setAccent2').value = '#c084fc';
  applyTheme('#ff69be', '#c084fc');
  persistColors();
};

document.querySelectorAll('#presets .preset').forEach((btn) => {
  btn.onclick = () => {
    const a = btn.getAttribute('data-a');
    const b = btn.getAttribute('data-b');
    $('setAccent').value = a;
    $('setAccent2').value = b;
    applyTheme(a, b);
    persistColors();
  };
});

// ---- custom mods ------------------------------------------------------------

function fmtSize(bytes) {
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  if (bytes >= 1024) return Math.round(bytes / 1024) + ' KB';
  return bytes + ' B';
}

// Rows are built with createElement/textContent (never innerHTML) so odd
// characters in jar filenames can't inject markup.
async function renderMods() {
  const list = $('modsList');
  let mods;
  try {
    mods = await window.revive.modsList();
  } catch (err) {
    list.textContent = '';
    const div = document.createElement('div');
    div.className = 'modsempty';
    div.textContent = 'Could not read the mods folder: ' + (err.message || err);
    list.appendChild(div);
    return;
  }

  list.textContent = '';
  const custom = mods.filter((m) => !m.managed);
  for (const mod of mods) {
    const row = document.createElement('div');
    row.className = 'modrow' + (mod.enabled ? '' : ' off');

    if (!mod.managed) {
      const toggle = document.createElement('input');
      toggle.type = 'checkbox';
      toggle.checked = mod.enabled;
      toggle.title = mod.enabled ? 'Disable (kept on disk, not loaded)' : 'Enable';
      toggle.onchange = async () => {
        try { await window.revive.modsToggle(mod.file); } catch (err) { setStatus(err.message || String(err), 'error'); }
        renderMods();
      };
      row.appendChild(toggle);
    }

    const name = document.createElement('span');
    name.className = 'mname';
    name.textContent = mod.name.replace(/\.jar$/i, '');
    name.title = mod.name;
    row.appendChild(name);

    const size = document.createElement('span');
    size.className = 'msize';
    size.textContent = fmtSize(mod.size);
    row.appendChild(size);

    if (mod.managed) {
      const tag = document.createElement('span');
      tag.className = 'mtag';
      tag.textContent = 'client';
      tag.title = 'Installed and updated automatically by the launcher';
      row.appendChild(tag);
    } else {
      const del = document.createElement('button');
      del.className = 'mdel';
      del.textContent = '×';
      del.title = 'Remove this mod';
      del.onclick = async () => {
        try { await window.revive.modsRemove(mod.file); } catch (err) { setStatus(err.message || String(err), 'error'); }
        renderMods();
      };
      row.appendChild(del);
    }

    list.appendChild(row);
  }

  if (custom.length === 0) {
    const div = document.createElement('div');
    div.className = 'modsempty';
    div.textContent = 'No extra mods yet — use “+ Add mods…” to drop in Litematica or anything else.';
    list.appendChild(div);
  }
}

$('modsPanel').addEventListener('toggle', () => {
  if ($('modsPanel').open) renderMods();
});

$('modsAddBtn').onclick = async () => {
  $('modsAddBtn').disabled = true;
  try {
    const res = await window.revive.modsAdd();
    if (res.added.length) setStatus(`Added: ${res.added.join(', ')}`, 'ok');
    for (const s of res.skipped) appendLog(`Skipped ${s}`);
    if (res.skipped.length && !res.added.length) setStatus('Nothing added — see log.', 'error');
  } catch (err) {
    setStatus(err.message || String(err), 'error');
  } finally {
    $('modsAddBtn').disabled = false;
    renderMods();
  }
};

// ---- events -----------------------------------------------------------------

window.revive.onLog(appendLog);
window.revive.onStatus((s) => setStatus(s.text, s.kind));

refresh();
