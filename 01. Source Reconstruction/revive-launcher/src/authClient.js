'use strict';

// Talks to your Revive auth server's account endpoints. Returns the accessToken
// (the credential the loader needs). Uses the global fetch built into modern
// Node/Electron.

async function post(host, path, body) {
  const url = host.replace(/\/$/, '') + path;
  let response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch (err) {
    throw new Error(`Could not reach the account server at ${host}. Is it online? (${err.message})`);
  }

  let json = null;
  try {
    json = await response.json();
  } catch {
    // fall through to a generic error below
  }

  if (!response.ok || !json || json.success === false) {
    const title = json && json.title ? json.title : 'Login failed';
    const message = json && json.message ? json.message : `Server returned ${response.status}.`;
    throw new Error(`${title}: ${message}`);
  }
  return json;
}

async function register(host, username, password) {
  const json = await post(host, '/account/register', { username, password });
  return { username: json.username, accessToken: json.accessToken };
}

async function login(host, username, password) {
  const json = await post(host, '/account/login', { username, password });
  return { username: json.username, accessToken: json.accessToken };
}

module.exports = { register, login };
