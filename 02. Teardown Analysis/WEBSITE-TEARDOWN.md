# revive-client.com, site analysis

Analysis of the public website `revive-client.com`. The full archive (raw HTML + icons + crawl
manifest) is in [`01. Source Reconstruction/revive-www`](../01.%20Source%20Reconstruction/revive-www/). Everything here is from static review of the
saved pages; nothing was executed and no account was created.

## Summary

`revive-client.com` is a **two-page static site**, a marketing landing page and a Supabase-gated
account/download page, fronting a **manual-approval DRM** for the Revive client. Sign-in is Supabase
email/password straight from the browser; a per-user `authorized` flag gates the download; the
authorized user's Supabase JWT is then presented as a bearer token to a separate binary host. The
launcher gets its own token from `revive-client.com/account/login`; on the Windows build that login
takes the website email when the server reports Supabase mode, so the website, launcher, and loader
can all hang off one Supabase session (see [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md#32-revive-account-login)).
No shipped launcher or loader code references Supabase directly.

The pages still carry **unfinished template placeholders and owner-facing setup instructions**,
consistent with the rest of the project: a lightly customized boilerplate shipped half-done.

## Link graph

Crawled from the homepage; the entire site is two HTML documents:

- **`index.html`**, landing page. Title *"Revive — Utility client for 2b2t"*; copy like
  *"Everything you need past spawn"*, *"Log in. Press play. Done."*, *"Not another minigame client"*.
  Targets 2b2t/anarchy. Fully static (0 scripts); links to `account.html` and Discord.
- **`account.html`**, login / account / download page. One inline `<script>` block holds the logic;
  the only external script is `@supabase/supabase-js@2` from `cdn.jsdelivr.net`.

Icons (`favicon.svg/.ico`, `favicon-16/32.png`, `apple-touch-icon.png`) round out the site. No other
pages are link-reachable.

## Auth + gating flow (account.html)

1. **Sign up / sign in**, `supabase.auth.signUp` / `signInWithPassword` (email + password). Comment
   notes "Confirm email" is off, so new users are signed in immediately.
2. **Route / gate**, `route()` calls `getUser()`, then reads the user's row from the `profiles`
   table: `select authorized, email where id = user.id`.
   - `authorized === true` → show the **download** screen.
   - otherwise → show a **"pending approval"** screen that **polls every 15 s** until the flag flips.
   So access is granted by an admin manually setting `profiles.authorized = true`, a human approval
   step, not an automated purchase/license.
3. **Gated download**, on click, `getSession()` then
   `fetch("https://dl.revive-client.com/download/{windows|linux}", { Authorization: "Bearer " +
   session.access_token })`; the service verifies the token and streams the binary, saved as
   **`Revive-Setup.exe`** (Windows) or **`Revive.AppImage`** (Linux), the same artifacts analyzed in
   [ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md) (and, for the AppImage, [REPORT.md](REPORT.md)).

## Backend wiring (public client-side values)

- **Supabase** project `https://yyvgdqzpyuwindemymrh.supabase.co`, used with its public anon key
  (`SUPABASE_URL` / `SUPABASE_ANON` consts embedded in the page). Access model is the `profiles`
  table + row-level security; the `authorized` boolean is the entitlement.
- **Download host** `https://dl.revive-client.com`, a separate subdomain, a token-gated binary
  endpoint (not an HTML site, so outside the crawl). Distinct from the loader's auth/download API
  host (`revive-client.com/api/*`).
- External references only: Google Fonts, jsdelivr (supabase-js), a Discord invite. The site hosts no
  JavaScript of its own beyond the inline block.

## The credential thread

The Supabase session `access_token` (a JWT) appears to be the single credential across the whole
chain:

- **Website** → `Bearer <access_token>` to `dl.revive-client.com` to download the installer.
- **Launcher / loader** → the launcher posts to `revive-client.com/account/login` (the Windows build
  sends the website email when the server reports Supabase mode) and hands the returned token to the
  loader (`revive.loader.token` / `REVIVE_ACCESS_TOKEN` / `revive-token.txt`), which sends it to the
  auth server's `/api/authorize`; only in `inject` mode does it then download the encrypted client
  ([LOADER-REASSEMBLY.md](LOADER-REASSEMBLY.md)). The launcher treats the token as opaque, so whether
  it is the Supabase JWT itself is decided server-side
  ([ELECTRON-TEARDOWN.md](ELECTRON-TEARDOWN.md#32-revive-account-login)).

One Supabase account, approved by an admin flag, likely unlocks both the installer download and the
in-loader client authorization.

## Tells / notes

- **Owner-facing setup text left in client code.** On a `profiles` read error or missing row,
  `account.html` tells the visitor: *"If you're the site owner, run supabase-setup.sql in the
  Supabase SQL editor"*, boilerplate setup instructions shipped to end users.
- **Unfinished placeholders.** `href="insert-discord-link"`, `href="insert discord link here"`,
  `src="screenshots/your-image.png"`, `discord.gg/xxxxxxx`, all dead/placeholder.
- **Email confirmation disabled**, so sign-up immediately yields a session; the only real gate is the
  manual `authorized` flag.