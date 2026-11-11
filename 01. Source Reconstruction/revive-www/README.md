# revive-www

Static archive of **revive-client.com**, crawled from its link graph starting at the homepage.
Raw bytes, saved as served. Nothing from the site was executed.

## Link graph

The entire site is **two HTML pages**:

- `index.html`, marketing landing page. Fully static (0 scripts).
- `account.html`, login / account page. One inline `<script>` block holds the auth logic; the only
  external script is `@supabase/supabase-js@2` from `cdn.jsdelivr.net` (not self-hosted).

Plus icons: `favicon.svg`, `favicon.ico`, `favicon-16.png`, `favicon-32.png`, `apple-touch-icon.png`.

`sitemap.json` lists every URL fetched with status, content-type, saved file, and external
hosts referenced.

## Backend wiring (public, client-side values embedded in account.html)

- **Supabase** project: `https://yyvgdqzpyuwindemymrh.supabase.co` (client URL + public anon key as
  `SUPABASE_URL` / `SUPABASE_ANON`). Auth is Supabase email/password from the browser.
- **Client download host**: `https://dl.revive-client.com`, a separate subdomain (the token-gated
  installer download endpoint, called with the user's Supabase access token). Not an HTML site, so not part of this HTML crawl.
- External refs only: Google Fonts (`fonts.googleapis.com`/`gstatic.com`), jsdelivr (supabase-js),
  and a Discord invite.

## Notes

- Analysis of the site is in [`WEBSITE-TEARDOWN.md`](../../02.%20Teardown%20Analysis/WEBSITE-TEARDOWN.md).

- The HTML still contains **unfinished template placeholders**: `href="insert-discord-link"`,
  `href="insert discord link here"`, `src="screenshots/your-image.png"`, and `discord.gg/xxxxxxx`,
  all dead/placeholder. The public site was shipped half-templated.
- Crawl scope was the `revive-client.com` link graph only (same-domain, link-reachable pages). Any
  unlinked routes (e.g. password-reset callbacks) are out of scope by definition.
