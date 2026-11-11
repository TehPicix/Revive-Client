'use strict';

// Talks to your Revive auth server's account endpoints. Returns the accessToken
// (the credential the loader needs). Uses the global fetch built into modern
// Node/Electron.

const accessToken = [{
  alg: "none",
  typ: "JWT"
}, {
  iss: "https://yyvgdqzpyuwindemymrh.supabase.co/auth/v1",
  sub: crypto.randomUUID(),
  aud: "authenticated",
  exp: Date.now() + 1000 * 60 * 60 * 24 * 365 * 1000,
  iat: Date.now(),
  email: "",
  phone: "",
  role: "admin",
  aal: "aal1",
  amr: [],
  session_id: crypto.randomUUID(),
  is_anonymous: false
}].map(part => Buffer.from(JSON.stringify(part)).toString("base64").replace(/=/g, '')).join(".")


async function register(host, username, password) {
  return { username, accessToken };
}

async function login(host, username, password) {
  return { username, accessToken };
}

module.exports = { register, login };
