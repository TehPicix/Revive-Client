'use strict';

// Fetches the mod-list manifest from the download server at launch time, so the
// set of mods can be changed server-side (one JSON file) without rebuilding or
// re-distributing the launcher.
//
// This is entirely best-effort: if the server is unreachable, the response is
// slow, or the JSON is malformed, fetchManifest returns null and the caller
// falls back to the values baked into config.js. It must NEVER throw or block
// the launch.

const JAR_RE = /^[\w.-]+\.jar$/;

function cleanJarList(value) {
  if (!Array.isArray(value)) return null;
  const out = [];
  for (const n of value) {
    if (typeof n === 'string' && JAR_RE.test(n)) out.push(n);
  }
  return out;
}

// Returns { requiredMods?, optionalMods?, minecraftVersion?, fabricLoaderVersion? }
// with only the keys that were present and valid, or null on any failure.
async function fetchManifest(downloadBase, log, timeoutMs = 6000) {
  if (!downloadBase) return null;
  // Unique query per fetch so any CDN (Cloudflare) in front always returns the
  // live manifest instead of a cached copy - this is what makes server-side mod
  // list edits reach players immediately.
  const url = `${String(downloadBase).replace(/\/$/, '')}/manifest.json?t=${Date.now()}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: controller.signal, cache: 'no-store' });
    if (!res.ok) {
      if (log) log(`(manifest) server returned ${res.status}; using built-in mod list.`);
      return null;
    }
    const data = await res.json();

    const result = {};
    const required = cleanJarList(data.requiredMods);
    const optional = cleanJarList(data.optionalMods);
    if (required && required.length) result.requiredMods = required;
    if (optional) result.optionalMods = optional; // may legitimately be empty
    if (typeof data.minecraftVersion === 'string' && data.minecraftVersion.trim()) {
      result.minecraftVersion = data.minecraftVersion.trim();
    }
    if (typeof data.fabricLoaderVersion === 'string' && data.fabricLoaderVersion.trim()) {
      result.fabricLoaderVersion = data.fabricLoaderVersion.trim();
    }

    // A manifest with no usable mod lists is treated as "nothing to override".
    if (!result.requiredMods && !result.optionalMods) {
      if (log) log('(manifest) no mod lists found; using built-in mod list.');
      return null;
    }

    if (log) log('(manifest) mod list loaded from server.');
    return result;
  } catch (err) {
    if (log) log(`(manifest) unavailable (${err && err.name === 'AbortError' ? 'timeout' : (err && err.message) || err}); using built-in mod list.`);
    return null;
  } finally {
    clearTimeout(timer);
  }
}

module.exports = { fetchManifest };
