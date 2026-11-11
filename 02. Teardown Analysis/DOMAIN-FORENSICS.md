# Domain forensics: revive-client.com / reviveon.top

DNS, WHOIS/RDAP, network-ownership, certificate and proxy dump for every host in
`DOMAINS.md` (the original target list, not included in this repo), plus the hosts the shipped code
points at.

| | |
|---|---|
| **Capture window** | 2026-10-23, 16:25 to 16:40 UTC |
| **Hosts in scope** | `revive-client.com`, `dl.revive-client.com`, `reviveon.top`, `team.reviveon.top`, **`40.160.138.207`** (GridFly WebSocket server) |
| **Also covered** | `www.` / `emotes.` / `team.revive-client.com`, `www.reviveon.top`, the Supabase and Workers hosts from the source |
| **Raw evidence** | [`raw/`](raw/), see [section 10](#10-method-sources-and-limitations) |

---

## TL;DR

1. **The GridFly server is also the website's hidden origin.** `40.160.138.207` is an **OVH US VPS in
   Hillsboro, Oregon** (AS16276, rDNS `vps-4e3a719f.vps.ovh.us`) running **Windows / IIS 10**.
   - Port **25600** is the GridFly fleet WebSocket server (**TooTallNate Java-WebSocket**). The
     operator dashboard ships with it preset (`revive-gridfly/index.html:4201`).
   - Port **80** serves the `revive-client.com` homepage **byte-for-byte** (SHA-256 `ffbc4683…`,
     same `Last-Modified`). So the box Cloudflare hides is reachable directly, and Cloudflare
     can be bypassed. See [5.5](#55-origin-match-40160138207-is-the-revive-clientcom-origin).
2. **Two separate stacks.** `revive-client.com` uses Cloudflare for everything: registrar, DNS and
   reverse proxy. `reviveon.top` uses Namecheap (registrar, BasicDNS, mail forwarding) with **no proxy
   at all**.
3. **`team.reviveon.top` exposes its origin.** It resolves straight to **`137.184.22.254`, a
   DigitalOcean droplet** (AS14061, NYC region), running **Caddy** as a reverse proxy in front of an
   app. Its Let's Encrypt cert was issued about **3 minutes after** the DNS zone was edited on
   2026-10-09.
4. **`emotes.revive-client.com` (behind Cloudflare) and `team.reviveon.top` (no proxy) run the same
   backend app.** Both return a byte-identical 82-byte JSON 404 (`"Unknown endpoint."`). It is not
   confirmed that they run on the same host (see [5.4](#54-backend-correlation-emotes-vs-team)).
5. **The TeamSync URL compiled into the client is dead.** `team.revive-client.com` is **NXDOMAIN**, so
   the relay now appears to live at `team.reviveon.top`, outside Cloudflare.
6. **`dl.revive-client.com` is a Node.js Express app** behind Cloudflare, with CORS locked to
   `https://revive-client.com`.
7. **`reviveon.top` is older.** It was registered **2025-03-16**, about 18 months before
   `revive-client.com` (2026-09-03). The apex points at `141.11.34.107`, **leased IPXO address space**
   announced by AS213496. Ports 80 and 443 do not answer. Email forwarding is still on.
8. **Registrant leaks:** `revive-client.com` shows registrant **country US, state Illinois**
   (everything else is redacted). `reviveon.top` is behind *Withheld for Privacy* (Delaware address
   is the privacy service's).
9. **Hygiene:** neither domain has **DNSSEC**, **CAA** or **DMARC**. `revive-client.com` has **no
   MX and no SPF**.

---

## 1. Host inventory

| Host | Where it came from | Resolves | Behind proxy | Role (per the teardown docs) |
|---|---|---|---|---|
| `revive-client.com` | DOMAINS.md, loader `Meta.java`, launcher `config.js` | yes | **Cloudflare** | Website, loader API (`/account/login`, `/api/authorize`, `/downloads/manifest.json`, `/downloads/<jar>`), updater feed (`/updates`), capes API |
| `www.revive-client.com` | wordlist | yes | **Cloudflare** | Mirror of the apex (same `Last-Modified`) |
| `dl.revive-client.com` | DOMAINS.md, `account.html` | yes | **Cloudflare** | Installer download, `/download/{windows,linux}` with a Supabase JWT bearer |
| `emotes.revive-client.com` | `EmoteSyncManager.java`, `Emotes.java` | yes | **Cloudflare** | `wss://emotes.revive-client.com/ws` (Emotes module) |
| `team.revive-client.com` | `TeamSyncManager.java`, `TeamSync.java` | **NXDOMAIN** | n/a | Default `wss://team.revive-client.com/ws` (TeamSync). **Dead.** |
| `reviveon.top` | DOMAINS.md | yes | **none** | No HTTP(S) service answering |
| `www.reviveon.top` | wordlist, CT | yes (CNAME to apex) | **none** | Same as apex |
| `team.reviveon.top` | DOMAINS.md, CT | yes | **none** | JSON API behind Caddy; same app as `emotes.` |
| **`40.160.138.207`** (bare IP, no DNS name of theirs) | GridFly dashboard `PRESET_SERVER` (`revive-gridfly/index.html:4201`), [GRIDFLY-PROTOCOL.md](GRIDFLY-PROTOCOL.md) | n/a (rDNS `vps-4e3a719f.vps.ovh.us`) | **none** | `ws://40.160.138.207:25600` `/dash` (fallback `/ui`): GridFly bot-fleet control server. Port 80 is also the **origin of `revive-client.com`** |
| `yyvgdqzpyuwindemymrh.supabase.co` | `account.html` | yes | Cloudflare (Supabase's) | Supabase auth and `profiles` DB |
| `revive-auth.crownizzle.workers.dev` | client `Auth.java` | yes | Cloudflare Workers | Remote-key endpoint (dead code per `REPORT.md`) |

---

## 2. DNS records

All values were checked against **1.1.1.1** and against **each zone's authoritative nameservers**.
The TTLs below are the authoritative ones.

### 2.1 Zone `revive-client.com` (Cloudflare DNS)

| Name | Type | TTL | Value | Notes |
|---|---|---|---|---|
| `revive-client.com` | NS | 86400 | `memphis.ns.cloudflare.com` | Cloudflare account NS pair |
| `revive-client.com` | NS | 86400 | `ullis.ns.cloudflare.com` | |
| `revive-client.com` | SOA | 1800 | `memphis.ns.cloudflare.com. dns.cloudflare.com. 2417248795 10000 2400 604800 1800` | Serial is not a timestamp (Cloudflare format) |
| `revive-client.com` | A | 300 | `104.21.9.128` | Cloudflare anycast (proxied) |
| `revive-client.com` | A | 300 | `172.67.130.221` | Cloudflare anycast (proxied) |
| `revive-client.com` | AAAA | 300 | `2606:4700:3030::6815:980` | Cloudflare anycast (proxied) |
| `revive-client.com` | AAAA | 300 | `2606:4700:3037::ac43:82dd` | Cloudflare anycast (proxied) |
| `revive-client.com` | HTTPS (65) | 300 | `1 . alpn=h3,h2 ipv4hint=104.21.9.128,172.67.130.221 ipv6hint=2606:4700:3030::6815:980,2606:4700:3037::ac43:82dd ech=AEX+DQBB…` | Auto-generated by Cloudflare. ECH on, public name `cloudflare-ech.com` |
| `www.revive-client.com` | A / AAAA / HTTPS | 300 | same four IPs and HTTPS RR as apex | Proxied |
| `dl.revive-client.com` | A / AAAA / HTTPS | 300 | same four IPs and HTTPS RR as apex | Proxied |
| `emotes.revive-client.com` | A / AAAA / HTTPS | 300 | same four IPs and HTTPS RR as apex | Proxied |
| `team.revive-client.com` | (any) | n/a | **NXDOMAIN** | Not a wildcard zone (probe label also NXDOMAIN) |

TTL 300 on every address record is Cloudflare's fixed "Auto" TTL for proxied records. All four
hostnames share one edge IP set, so the real origin IPs are not visible in DNS.

### 2.2 Zone `reviveon.top` (Namecheap BasicDNS)

| Name | Type | TTL | Value | Notes |
|---|---|---|---|---|
| `reviveon.top` | NS | 1800 | `dns1.registrar-servers.com` | Namecheap BasicDNS |
| `reviveon.top` | NS | 1800 | `dns2.registrar-servers.com` | |
| `reviveon.top` | SOA | 3601 | `dns1.registrar-servers.com. hostmaster.registrar-servers.com. 1791589980 43200 3600 604800 3601` | **Serial decodes as Unix time: 2026-10-09 23:53:00 UTC** (last zone edit) |
| `reviveon.top` | A | 1799 | **`141.11.34.107`** | Direct, no proxy. Leased IPXO space, AS213496 |
| `reviveon.top` | MX | 1800 | `10 eforward1.registrar-servers.com` | Namecheap email forwarding |
| `reviveon.top` | MX | 1800 | `10 eforward2.registrar-servers.com` | |
| `reviveon.top` | MX | 1800 | `10 eforward3.registrar-servers.com` | |
| `reviveon.top` | MX | 1800 | `15 eforward4.registrar-servers.com` | |
| `reviveon.top` | MX | 1800 | `20 eforward5.registrar-servers.com` | |
| `reviveon.top` | TXT | 1800 | `v=spf1 include:spf.efwd.registrar-servers.com ~all` | Namecheap's default SPF for forwarding |
| `www.reviveon.top` | CNAME | 1799 | `reviveon.top.` | So it also lands on `141.11.34.107` |
| `team.reviveon.top` | A | 1799 | **`137.184.22.254`** | Direct, no proxy. DigitalOcean |

TTL 1799 / 1800 matches Namecheap's "Automatic" TTL, so these records were left at defaults.

### 2.3 Records that are not there

| Check | `revive-client.com` | `reviveon.top` |
|---|---|---|
| MX | **none**, the domain cannot receive mail | 5x Namecheap eForward |
| SPF | **none** | `include:spf.efwd.registrar-servers.com ~all` (softfail) |
| DMARC (`_dmarc`) | **NXDOMAIN** | **NXDOMAIN** |
| DKIM (`default`, `google`, `selector1`, `k1`) | none found | none found |
| CAA | **none**, any CA may issue | **none** |
| DNSSEC (DS at parent) | **unsigned** (`delegationSigned: false`) | **unsigned** (`delegationSigned: false`, `zoneSigned: false`) |
| DNSKEY | none | none |
| AAAA | yes (Cloudflare) | **none**, IPv4 only |
| HTTPS / SVCB RR | HTTPS on every proxied name | none |
| SRV | none | none |
| Wildcard (`*`) | no | no |

> With no SPF and no DMARC, anyone can spoof `@revive-client.com` mail and receivers have no policy
> to reject it. `reviveon.top` mail forwarding is active, so mail sent there reaches the operator's
> real inbox. The destination address is not visible in DNS.

### 2.4 Nameserver and mail-exchanger hosts

| Host | IPv4 | IPv6 | Operator |
|---|---|---|---|
| `memphis.ns.cloudflare.com` | 108.162.195.189, 162.159.44.189, 172.64.35.189 | 2606:4700:58::a29f:2cbd, 2803:f800:50::6ca2:c3bd, 2a06:98c1:50::ac40:23bd | Cloudflare |
| `ullis.ns.cloudflare.com` | 108.162.194.127, 162.159.38.127, 172.64.34.127 | 2606:4700:50::a29f:267f, 2803:f800:50::6ca2:c27f, 2a06:98c1:50::ac40:227f | Cloudflare |
| `dns1.registrar-servers.com` | 156.154.132.200 | 2610:a1:1024::200 | Namecheap BasicDNS on Vercara anycast (AS12008) |
| `dns2.registrar-servers.com` | 156.154.133.200 | 2610:a1:1025::200 | Namecheap BasicDNS on Vercara anycast (AS12008) |
| `eforward1/3/5.registrar-servers.com` | 162.255.118.51 | none | Namecheap mail forwarding (AS22612) |
| `eforward2/4.registrar-servers.com` | 162.255.118.52 | none | Namecheap mail forwarding (AS22612) |

The SPF include `spf.efwd.registrar-servers.com` expands to
`ip4:209.105.246.192/28 ip4:198.54.122.192/26 ip4:162.255.118.0/26 ip4:198.54.122.0/28 ip4:162.255.118.240/29 ip4:63.250.43.96/27 ip4:198.177.126.16/28 ?all`.
All of those ranges are Namecheap's.

### 2.5 SOA comparison

| Field | `revive-client.com` | `reviveon.top` |
|---|---|---|
| MNAME (primary) | `memphis.ns.cloudflare.com` | `dns1.registrar-servers.com` |
| RNAME (contact) | `dns.cloudflare.com` | `hostmaster.registrar-servers.com` |
| Serial | 2417248795 | 1791589980 (**2026-10-09T23:53:00Z**) |
| Refresh / Retry | 10000 / 2400 | 43200 / 3600 |
| Expire | 604800 | 604800 |
| Minimum (neg. TTL) | 1800 | 3601 |

### 2.6 Subdomain discovery

| Method | Coverage | New names found |
|---|---|---|
| Certificate Transparency (crt.sh, `%.domain` and apex) | All publicly trusted certs | `team.reviveon.top`, `www.reviveon.top`. The `revive-client.com` certs are wildcards, so CT reveals nothing more there. |
| Wordlist, 85 labels x 2 apexes, A/AAAA/TXT | `www api auth admin panel cdn static assets updates download(s) dl files mail smtp ftp status ws socket team emotes capes dev staging test beta discord bot login account dashboard app portal server mc play node origin direct backend db supabase vps git docs shop store pay loader client launcher gridfly feed map tracker teamsync sync relay proxy vpn ns1 ns2 autodiscover _dmarc _domainkey …` | `www.revive-client.com`, `emotes.revive-client.com`, `www.reviveon.top` |
| Wildcard probe (`zz-wildcard-probe-7f3k.*`) | n/a | NXDOMAIN on both, so the hits above are real records |

---

## 3. WHOIS / RDAP

### 3.1 `revive-client.com`

| Field | Value | Source |
|---|---|---|
| Registry handle | `3138726148_DOMAIN_COM-VRSN` | Verisign RDAP |
| Registrar | **Cloudflare, Inc.** (IANA ID **1910**) | Verisign + Cloudflare RDAP |
| Registrar address | 101 Townsend St, San Francisco, CA 94107 | Cloudflare RDAP |
| Registrar abuse | `registrar-abuse@cloudflare.com`, +1.4153197517 | Cloudflare RDAP |
| **Registered** | **2026-09-03 04:55:45 UTC** | Verisign |
| Last changed (registry) | 2026-09-03 04:55:45 UTC | Verisign |
| Last changed (registrar) | **2026-09-08 05:12:41 UTC** | Cloudflare RDAP |
| Expires | 2027-09-03 04:55:45 UTC (1-year term) | Verisign |
| Status | `clientTransferProhibited` | both |
| Nameservers | `MEMPHIS.NS.CLOUDFLARE.COM`, `ULLIS.NS.CLOUDFLARE.COM` | both |
| DNSSEC | `delegationSigned: false` | both |
| Registrant name / org | `DATA REDACTED` | Cloudflare RDAP |
| Registrant street / city / postcode | `DATA REDACTED` | Cloudflare RDAP |
| **Registrant state** | **`IL` (Illinois)** | Cloudflare RDAP |
| **Registrant country** | **`US`** | Cloudflare RDAP (`cc` param) |
| Registrant contact | web form `https://domaincontact.registrar.cloudflare.com/revive-client.com` | Cloudflare RDAP |
| Registrant phone | +1.4153197517 (Cloudflare's relay number, not the registrant's) | Cloudflare RDAP |

### 3.2 `reviveon.top`

| Field | Value | Source |
|---|---|---|
| Registry handle | `D20250316G10001G_42195619-top` | .top registry RDAP (`rdap.zdnsgtld.com`) |
| Registry WHOIS | `whois.nic.top` | registry |
| Registrar | **Namecheap, Inc.** (IANA ID **1068**) | both |
| Registrar abuse | `abuse@namecheap.com`, +1.9854014545 | Namecheap RDAP |
| **Registered** | **2025-03-16 12:52:31 UTC** | both |
| Last changed (registry) | **2026-09-11 23:54:14 UTC** | registry |
| Expires | **2030-03-16 12:52:31 UTC** (paid ~5 years ahead) | both |
| Status | `clientTransferProhibited` | both |
| Nameservers | `dns1.registrar-servers.com`, `dns2.registrar-servers.com` | both |
| DNSSEC | `delegationSigned: false`, `zoneSigned: false` | Namecheap RDAP |
| Registrant (registry view) | handle `C20260912C_09381951-top`, name blank, region `Delaware`, `US` | registry |
| Registrant (registrar view) | handle `20250316c-96906674`, org **"Privacy service provided by WITHHELD FOR PRIVACY LLC"**, 16192 Coastal Highway, Lewes, DE 19958 | Namecheap RDAP |
| Registrant / tech email | `785b30df3fa1408083c7f4fc66489852.protect@withheldforprivacy.com` (privacy relay alias) | Namecheap RDAP |
| Registrant phone | +1.3022061391 (privacy service) | Namecheap RDAP |

> The registry-side contact handle (`C20260912C…`) was created on 2026-09-12, which matches the
> 2026-09-11 23:54 "last changed" event. The registrar-side handle (`20250316c…`) dates from the
> original 2025 registration. So the domain was touched on 2026-09-11/12 (a contact re-sync,
> renewal or account move), about a week after `revive-client.com` was registered.

---

## 4. IP and network ownership

| IP | Used by | ASN | AS name | BGP prefix | RIR / netblock | Geo (ipinfo) | PTR |
|---|---|---|---|---|---|---|---|
| `104.21.9.128` | apex, www, dl, emotes | **13335** | CLOUDFLARENET | 104.21.0.0/20 | ARIN `NET-104-16-0-0-1` (104.16.0.0/12) | anycast | NXDOMAIN |
| `172.67.130.221` | apex, www, dl, emotes | **13335** | CLOUDFLARENET | 172.67.128.0/20 | ARIN `NET-172-64-0-0-1` (172.64.0.0/13) | anycast | NXDOMAIN |
| `2606:4700:3030::6815:980` | apex, www, dl, emotes | **13335** | CLOUDFLARENET | n/a | ARIN `NET6-2606-4700-1` (2606:4700::/32) | anycast | not queried |
| `2606:4700:3037::ac43:82dd` | apex, www, dl, emotes | **13335** | CLOUDFLARENET | n/a | ARIN `NET6-2606-4700-1` | anycast | not queried |
| **`137.184.22.254`** | **`team.reviveon.top`** | **14061** | DIGITALOCEAN-ASN | 137.184.16.0/20 | ARIN `DIGITALOCEAN-137-184-0-0` (137.184.0.0/16) | North Bergen, NJ, US (DO **NYC** region) | NXDOMAIN (no custom rDNS) |
| **`141.11.34.107`** | **`reviveon.top`**, `www.` | **213496** | TELIC-TECHNOLOGIES (TELIC TECHNOLOGIES SOLUCOES EM REDES E TI LTDA, BR) | 141.11.34.0/24 | RIPE `NET-141-11-34-0-24`, ASSIGNED PA, org `ORG-PC1544-RIPE` ("Company 6660e747XPZhMQQ2"), mnt `netutils-mnt`, geofeed `geofeed.ipxo.com` | Bebedouro, SP, BR (unreliable, see below) | NXDOMAIN |
| **`40.160.138.207`** | **GridFly WS `:25600`**, **`revive-client.com` origin `:80`** | **16276** | OVH (OVH SAS) | 40.160.128.0/19 | ARIN `NET-40-160-138-0-1` **`VPS-US-WEST-OR-2`** (40.160.138.0/24), registrant `ON-6` "OVH (NWK)" | Hillsboro, OR, US (OVH **HIL1**) | **`vps-4e3a719f.vps.ovh.us`** (forward-confirmed) |
| `2604:2dc0:222::125f` | same VPS (AAAA of its rDNS name) | **16276** | OVH | 2604:2dc0::/32 | ARIN `NET6-2604-2DC0-222-1` `VPS-US-WEST-OR-2` (2604:2dc0:222::/48) | Hillsboro, OR, US | `vps-4e3a719f.vps.ovh.us` |

### 4.1 `141.11.34.0/24` is leased, rotating space

The `/24` that `reviveon.top` points at is IPXO-brokered lease space (RIPE `geofeed:
https://geofeed.ipxo.com/geofeed.txt`, `remarks: End User Organization`, abuse handled by
`report@abuseradar.com`). It changes hands often. Origin-AS history from RIPEstat:

| Period (origin seen) | Origin AS | Holder |
|---|---|---|
| **2026-02-12 to present** | **AS213496** | TELIC TECHNOLOGIES, BR (ASN itself allocated 2026-02-18) |
| 2026-01-07 to 2026-02-23 | AS53427 | TGLOBAL NETWORKS, US |
| 2025-12-02 to 2026-01-18 | AS216255 | (no Cymru name) |
| 2024-01-06 to 2025-12-01 | AS199414 | NeoProtect GmbH, DE |
| 2023-12-01 to 2024-01-05 | AS201575 | TradeZone LLC, GE |
| 2023-06-16 to 2023-10-01 | AS212027 | PebbleHost Ltd, GB |
| 2022-06-21 to 2025-12-13 (intermittent) | AS834 | IPXO LLC, US (the lease broker itself) |
| 2021-07 to 2023-11 (overlapping) | 8 others | Hivelocity (AS61317), HostLAB (TR), Obehosting (SE), Cogent, AlexHost (MD, on the covering /22), small individual-held ASNs |

RIPEstat reports in bins about 12 days wide, so start and end dates are approximate. That is why
AS213496 appears a few days before its 2026-02-18 registration.

**Geolocation disagrees** for this IP: RIPE inetnum `country: US`, Team Cymru `FR`, ipinfo
`BR`. That is normal for leased space. Treat the location of `reviveon.top` as **unknown**.

### 4.2 `40.160.138.207`, the OVH VPS

| Field | Value | Source |
|---|---|---|
| Netblock | `40.160.138.0/24`, handle `NET-40-160-138-0-1`, name **`VPS-US-WEST-OR-2`**, type ASSIGNMENT | ARIN RDAP |
| Netblock registered | 2026-08-29 23:10:23 -04:00 (OVH carving the /24 into its VPS pool, not the rental date) | ARIN RDAP |
| Parent | `NET-40-160-128-0-1`, OVH US LLC; sibling /24 remarks say **"OVH HIL1"** (Hillsboro, Oregon DC) | ARIN RDAP |
| Registrant | `ON-6` "OVH (NWK)" | ARIN RDAP |
| Abuse / tech / admin POC | `NOC11876-ARIN`, `noc@ovh.net`, +1-855-684-5463 | ARIN RDAP |
| OVH US abuse desk | `abuse@ovh.us`, https://us.ovhcloud.com/abuse/ | ARIN RDAP (parent block) |
| BGP | `40.160.128.0/19` originated by **AS16276 OVH SAS**, continuously since 2025-10-27 | Team Cymru, RIPEstat |
| PTR | **`vps-4e3a719f.vps.ovh.us`**, OVH's default VPS name (`vps-<service id>`), never customized | DNS |
| Forward-confirmed | `vps-4e3a719f.vps.ovh.us` A gives `40.160.138.207`, AAAA gives `2604:2dc0:222::125f` (TTL 43200) | DNS |
| IPv6 of the same VPS | `2604:2dc0:222::125f` in `NET6-2604-2DC0-222-1` `VPS-US-WEST-OR-2`, PTR back to the same name | ARIN RDAP, DNS |
| OS (inferred) | **Windows Server**: `Server: Microsoft-IIS/10.0` on :80 | HTTP |
| CT | no certificate has ever been logged for this IP | crt.sh |
| Our domains pointing at it | none. Neither zone has a record for this IP. It sits behind Cloudflare as the `revive-client.com` origin and is used as a bare IP for GridFly | DNS (sections 2.1, 2.2) |

`vps-4e3a719f` is the OVH **service name** of the rented VPS. OVH can tie it to the customer
account, which makes it the identifier to quote in an abuse report or legal request.

---

## 5. Proxy, CDN and origin analysis

### 5.1 Summary

| Host | Front | Origin visible? | Origin stack | Evidence |
|---|---|---|---|---|
| `revive-client.com` | **Cloudflare reverse proxy** | **Yes, by fingerprint: `40.160.138.207:80`** ([5.5](#55-origin-match-40160138207-is-the-revive-clientcom-origin)) | **IIS 10 on Windows** (OVH VPS), static HTML | `Server: cloudflare`, `CF-RAY`, `cf-cache-status: DYNAMIC`, NEL to `a.nel.cloudflare.com`, CF anycast IPs, CF HTTPS RR with ECH. Body byte-identical to the IIS site on `40.160.138.207` |
| `www.revive-client.com` | **Cloudflare** | **Yes**, same as apex | Same static origin (identical `Last-Modified`) | as above |
| `dl.revive-client.com` | **Cloudflare** | **No** | **Node.js / Express** | `x-powered-by: Express`, Express default 404 page `Cannot GET /`, `content-security-policy: default-src 'none'` (Express finalhandler), CORS `access-control-allow-origin: https://revive-client.com` + `vary: Origin` |
| `emotes.revive-client.com` | **Cloudflare** | **No** | JSON API, same app as `team.reviveon.top` | 82-byte JSON 404 identical to `team.reviveon.top`, `Cache-Control: no-store` |
| `team.revive-client.com` | n/a | n/a | n/a | NXDOMAIN |
| **`team.reviveon.top`** | **None (direct)** | **Yes, `137.184.22.254`** | **Caddy** (auto-HTTPS, reverse proxy) in front of the JSON app, on a **DigitalOcean droplet** | `:80` gives `308` to `https://` with `Server: Caddy`. `:443` gives `Via: 1.1 Caddy` and `Alt-Svc: h3=":443"; ma=2592000` (Caddy default). LE cert served directly by the origin |
| **`reviveon.top`** / `www.` | **None (direct)** | **Yes, `141.11.34.107`** | Unknown, nothing answers | TCP connect to :80 and :443 **timed out** (15 s) |
| **`40.160.138.207:25600`** (GridFly) | **None (direct, bare IP)** | **Yes** | **Java**, TooTallNate **Java-WebSocket** library (`gridfly-server`), plain `ws://` (no TLS) | `HTTP/1.1 404 WebSocket Upgrade Failure`, `Server: TooTallNate Java-WebSocket`, `Content-Length: 77` (the library's stock rejection for a non-Upgrade request). A TLS ClientHello gets no ServerHello |
| **`40.160.138.207:80`** | **None (direct, bare IP)** | **Yes** | **Microsoft-IIS/10.0** default site = `revive-client.com` | `200`, `Server: Microsoft-IIS/10.0`, `ETag: "67b394e27f4cdd1:0"`, `Last-Modified: Thu, 24 Sep 2026 23:53:23 GMT`, `Content-Length: 30629` |
| `40.160.138.207:443` | n/a | n/a | n/a | TCP connect **timed out** (12 s) |

### 5.2 HTTP response fingerprints

One `HEAD /` and one `GET /` per host.

| Host | Status | `Server` | Other notable headers | Body |
|---|---|---|---|---|
| `https://revive-client.com/` | 200 | `cloudflare` | `Content-Type: text/html`, `Accept-Ranges: bytes`, **`last-modified: Thu, 24 Sep 2026 23:53:23 GMT`**, `cf-cache-status: DYNAMIC`, `CF-RAY: a4f21bd84b1f4369-EWR`, `alt-svc: h3=":443"; ma=86400` | Landing page |
| `https://www.revive-client.com/` | 200 | `cloudflare` | same `last-modified`, `vary: Accept-Encoding`, `CF-RAY: …-EWR` | Landing page |
| `https://dl.revive-client.com/` | 404 | `cloudflare` | **`x-powered-by: Express`**, `access-control-allow-origin: https://revive-client.com`, `vary: Origin`, `content-security-policy: default-src 'none'`, `x-content-type-options: nosniff`, `Content-Type: text/html; charset=utf-8` | `<pre>Cannot GET /</pre>` |
| `https://emotes.revive-client.com/` | 404 | `cloudflare` | `Content-Type: application/json`, `Content-Length: 82`, `Cache-Control: no-store` | `{"success":false,"type":"error","title":"Not found","message":"Unknown endpoint."}` |
| `http://team.reviveon.top/` | 308 | **`Caddy`** | `Location: https://team.reviveon.top/`, `Connection: close` | none |
| `https://team.reviveon.top/` | 404 | (none) | **`Via: 1.1 Caddy`**, `Alt-Svc: h3=":443"; ma=2592000`, `Content-Type: application/json`, `Content-Length: 82`, `Cache-Control: no-store` | `{"success":false,"type":"error","title":"Not found","message":"Unknown endpoint."}` |
| `http(s)://reviveon.top/` | timeout | n/a | n/a | n/a |
| `https://www.reviveon.top/` | timeout | n/a | n/a | n/a |
| `http://40.160.138.207:25600/` | **404 WebSocket Upgrade Failure** | **`TooTallNate Java-WebSocket`** | `Content-Type: text/html`, `Content-Length: 77` | Java-WebSocket's stock rejection page. The server does not serve the dashboard at `/` |
| `http://40.160.138.207/` | 200 | **`Microsoft-IIS/10.0`** | `ETag: "67b394e27f4cdd1:0"`, `Last-Modified: Thu, 24 Sep 2026 23:53:23 GMT`, `Accept-Ranges: bytes`, `Content-Length: 30629` | **`revive-client.com` landing page** (SHA-256 `ffbc4683…`) |
| `https://40.160.138.207/` | timeout | n/a | n/a | n/a |

Cloudflare edge trace (`/cdn-cgi/trace`, served by Cloudflare itself): `colo=EWR`, `http=http/1.1`,
`tls=TLSv1.3`, `kex=X25519`, `sni=plaintext`, `warp=off`. `EWR` is the collector's nearest PoP,
not the origin's.

### 5.3 TLS certificates served live

| Host | Subject | SANs | Issuer | Valid | Serial | SHA-256 |
|---|---|---|---|---|---|---|
| `revive-client.com`, `dl.`, `emotes.` | `CN=revive-client.com` | `revive-client.com`, `*.revive-client.com` | Google Trust Services **WE1** (ECDSA P-256) | 2026-09-03 03:58:29 to 2026-12-02 04:55:46 | `EFF0693502857A75139C547DDC4D2A76` | `51:FB:DE:83:5A:E4:97:6E:7D:77:06:E7:13:1B:6E:69:6C:AC:4A:A3:D8:CF:9A:99:6E:47:D3:00:C1:42:E7:98` |
| `team.reviveon.top` | `CN=team.reviveon.top` | `team.reviveon.top` | Let's Encrypt **YE1** (ECDSA P-256) | 2026-10-09 22:56:02 to 2027-01-07 22:56:01 | `052EFC7EF51BBF81C396578788478802FF2D` | `92:51:D1:AC:89:B5:2B:BC:B9:AA:4E:E1:9B:A7:8F:31:44:59:2A:93:44:77:24:5F:5C:A7:8C:4F:C3:E1:32:A3` |
| `reviveon.top` | n/a | n/a | n/a | no TLS listener answering | n/a | n/a |
| `40.160.138.207:443` | n/a | n/a | n/a | no TLS listener answering | n/a | n/a |
| `40.160.138.207:25600` | n/a | n/a | n/a | **plaintext**: TCP accepts, no ServerHello. GridFly runs as `ws://`, so the operator token in `ui_hello` crosses the internet in clear text | n/a | n/a |

The `revive-client.com` cert is a **Cloudflare Universal SSL** edge cert (apex + wildcard, GTS).
It says nothing about the origin. The `team.reviveon.top` cert was obtained by **Caddy on the
droplet itself** via ACME (single-name, LE). The droplet is therefore the TLS endpoint.

### 5.4 Backend correlation: `emotes.` vs `team.`

| Feature | `emotes.revive-client.com` | `team.reviveon.top` |
|---|---|---|
| Status on `/` | 404 | 404 |
| `Content-Type` | `application/json` | `application/json` |
| `Content-Length` | 82 | 82 |
| `Cache-Control` | `no-store` | `no-store` |
| Body | `{"success":false,"type":"error","title":"Not found","message":"Unknown endpoint."}` | **byte-identical** |
| `Via: 1.1 Caddy` | absent | present |
| Front | Cloudflare | none |
| WebSocket path in client source | `wss://emotes.revive-client.com/ws` | (TeamSync default was `wss://team.revive-client.com/ws`, now NXDOMAIN) |

**Conclusion:** both hostnames run **the same relay application**. Whether `emotes.` is proxied to
the same droplet (`137.184.22.254`) is **unverified**. The missing `Via: 1.1 Caddy` on `emotes.`
means either Cloudflare dropped the header or the app runs there without Caddy in front. No
origin-unmasking requests were sent to settle it.

### 5.5 Origin match: `40.160.138.207` is the `revive-client.com` origin

| Feature | `https://revive-client.com/` (via Cloudflare) | `http://40.160.138.207/` (direct) | Repo archive `01. Source Reconstruction/revive-www/index.html` |
|---|---|---|---|
| Status | 200 | 200 | n/a |
| `Last-Modified` | `Thu, 24 Sep 2026 23:53:23 GMT` | `Thu, 24 Sep 2026 23:53:23 GMT` | n/a |
| Size | 30629 bytes | 30629 bytes | 30629 bytes |
| SHA-256 | `ffbc468375e315eba08fef781de09fe57edee58b732a0c8b0efaabd890d3815e` | **identical** | **identical** |
| `<title>` | `Revive — Utility client for 2b2t` | identical | identical |
| `Server` | `cloudflare` (rewritten) | `Microsoft-IIS/10.0` | n/a |

**The IIS ETag is the file's mtime.** IIS builds `"<FILETIME as little-endian hex>:<change#>"`.
`67 b3 94 e2 7f 4c dd 1` read little-endian is `0x01DD4C7F_E294B367`, which is
**2026-09-24 23:53:23.69 UTC**. That matches `Last-Modified` to the sub-second, so it is one file on
one disk, not a coincidental copy.

**What follows:**

- **Cloudflare is bypassable.** The IIS default binding answers on the bare IP with no `Host`
  header, so Cloudflare's WAF, rate limiting and DDoS cover do nothing for anyone who has this
  address. The operator published the address themselves in the GridFly dashboard preset.
- **Likely Cloudflare "Flexible" SSL.** The origin's :443 does not answer from the internet while
  :80 serves the site. That points to Cloudflare talking to the origin in plain HTTP, so traffic
  between Cloudflare and the origin is unencrypted. It can't be ruled out that :443 is open only to
  Cloudflare's ranges.
- **The website and the bot-fleet controller share one Windows VPS.** That links the public
  "Revive" brand directly to the GridFly operation.
- **Not established:** whether `dl.` (Express), `emotes.` (relay JSON app) or the loader API paths
  are also served from this box. Only the static landing page was compared. No `Host`-header
  requests were sent to test the other names against this IP.

---

## 6. Certificate Transparency history

Unique certificates (precert and final cert merged). Source: crt.sh.

| # | crt.sh ID(s) | Issuer | Names | notBefore (UTC) | notAfter (UTC) | Serial | Reading |
|---|---|---|---|---|---|---|---|
| 1 | 17171311902, 17171313311 | DigiCert *Encryption Everywhere DV TLS CA - G2* | `www.reviveon.top` | 2025-03-17 00:00:00 | 2026-03-17 23:59:59 | `02b483be090ead132c006b73db515b6e` | 1-year DV the day after registration. This CA is sold through hosting and reseller panels. Expired |
| 2 | 17325354670, 17357186460 | Let's Encrypt **E6** | `reviveon.top` | 2025-03-20 23:03:22 | 2025-06-18 23:03:21 | `05a0dd676d80c903fd668e4497cd9e91c585` | Never renewed, so the apex web service stopped by mid-2025 |
| 3 | 29240518572 | Google Trust Services **WR1** (RSA) | `revive-client.com`, `*.revive-client.com` | 2026-09-03 03:57:55 | 2026-12-02 04:57:49 | `27b03de6cb02ac520efd3643376bb1a9` | Cloudflare Universal SSL, RSA leg |
| 4 | 29571484719, 29814181525 | Google Trust Services **WE1** | `revive-client.com`, `*.revive-client.com` | 2026-09-03 03:58:05 | 2026-12-02 04:58:01 | `01f0e4f0305dd57a133e0d11d61421b3` | Cloudflare Universal SSL, ECDSA leg |
| 5 | 29814227015 | Google Trust Services **WE1** | `revive-client.com`, `*.revive-client.com` | 2026-09-03 03:58:29 | 2026-12-02 04:55:46 | `00eff0693502857a75139c547ddc4d2a76` | **Currently served** at the edge |
| 6 | 29756885189 | Let's Encrypt **YE2** | `revive-client.com`, `*.revive-client.com` | 2026-09-05 09:04:16 | 2026-12-04 09:04:15 | `06677172e825869e5cbd1be121f46d5e44ba` | Cloudflare backup-CA cert pack |
| 7 | 29954611323 | Let's Encrypt **YE1** | `team.reviveon.top` | 2026-10-09 22:56:02 | 2027-01-07 22:56:01 | `052efc7ef51bbf81c396578788478802ff2d` | **Currently served** by Caddy on the droplet |

> **On timing:** the GTS certs' `notBefore` sits about 1 h *before* the 04:55:45 registration time,
> and Let's Encrypt backdates `notBefore` by 1 h too. Corrected for that, Cloudflare issued Universal
> SSL within minutes of the domain being registered (around 04:58 UTC). The `team.reviveon.top`
> cert was really issued around **23:56 UTC on 2026-10-09**, 3 minutes after the zone serial
> (23:53:00). So the A record was added and Caddy fetched a cert right away.

---

## 7. Timeline

| When (UTC) | Event | Source |
|---|---|---|
| 2025-03-16 12:52:31 | `reviveon.top` registered at Namecheap | RDAP |
| 2025-03-17 | DigiCert DV cert for `www.reviveon.top` | CT #1 |
| 2025-03-20 23:03 | Let's Encrypt cert for `reviveon.top` (never renewed) | CT #2 |
| 2025-06-18 | `reviveon.top` LE cert expires, apex HTTPS goes quiet | CT #2 |
| 2025-10-27 | OVH (AS16276) starts originating `40.160.128.0/19` | RIPEstat |
| 2026-02-12 | AS213496 starts originating `141.11.34.0/24` (RIPE inetnum registered 2026-02-18) | RIPEstat |
| 2026-06-11 | OVH registers IPv6 VPS block `2604:2dc0:222::/48` (`VPS-US-WEST-OR-2`) | ARIN RDAP |
| 2026-08-30 03:10 | OVH registers IPv4 VPS block `40.160.138.0/24` (`VPS-US-WEST-OR-2`) | ARIN RDAP |
| **2026-09-03 04:55:45** | **`revive-client.com` registered at Cloudflare Registrar** | RDAP |
| 2026-09-03 ~04:58 | Cloudflare Universal SSL (GTS WR1 + WE1) issued | CT #3 to #5 |
| 2026-09-05 09:04 | Cloudflare backup LE cert for `revive-client.com` | CT #6 |
| 2026-09-08 05:12:41 | `revive-client.com` last changed at registrar | Cloudflare RDAP |
| 2026-09-11 23:54:14 | `reviveon.top` last changed at registry (new registry contact handle 2026-09-12) | .top RDAP |
| 2026-09-24 23:53:23.69 | `revive-client.com` homepage file written on the IIS origin `40.160.138.207` (mtime from IIS ETag, matches `Last-Modified`) | HTTP |
| **2026-10-09 23:53:00** | **`reviveon.top` zone edited** (SOA serial) | DNS |
| **2026-10-09 ~23:56** | **LE cert for `team.reviveon.top` issued by Caddy on `137.184.22.254`** | CT #7 |
| 2026-10-19 15:35 | Loader and client jars retrieved from `revive-client.com/downloads/` (per [REPORT.md](REPORT.md)) | REPORT.md |
| 2026-10-23 16:25 to 16:32 | This capture: domains | n/a |
| 2026-10-23 16:33 to 16:40 | This capture: `40.160.138.207` (GridFly live on :25600, website live on :80) | n/a |

---

## 8. Abuse and reporting contacts

| Layer | Provider | Applies to | Contact |
|---|---|---|---|
| Registrar | Cloudflare Registrar (IANA 1910) | `revive-client.com` | `registrar-abuse@cloudflare.com`, +1.4153197517 |
| DNS / CDN / proxy | Cloudflare (AS13335) | `revive-client.com`, `www.`, `dl.`, `emotes.` | https://www.cloudflare.com/abuse, `abuse@cloudflare.com` |
| Serverless | Cloudflare Workers | `revive-auth.crownizzle.workers.dev` | https://www.cloudflare.com/abuse |
| Registrar / DNS / mail fwd | Namecheap (IANA 1068) | `reviveon.top`, `team.`, `www.` | `abuse@namecheap.com`, +1.9854014545 |
| Hosting | DigitalOcean (AS14061) | `137.184.22.254` (`team.reviveon.top`) | `abuse@digitalocean.com`, https://www.digitalocean.com/company/contact/#abuse |
| Hosting | **OVHcloud US** (AS16276), VPS **`vps-4e3a719f`** | `40.160.138.207` / `2604:2dc0:222::125f` (GridFly WS + `revive-client.com` origin) | `abuse@ovh.us`, https://us.ovhcloud.com/abuse/; NOC `noc@ovh.net`, +1-855-684-5463 |
| IP lease | IPXO-leased block, announced by AS213496 | `141.11.34.107` (`reviveon.top`) | `report@abuseradar.com` (RIPE `abuse-c` PC21160-RIPE) |
| Backend SaaS | Supabase | `yyvgdqzpyuwindemymrh.supabase.co` | not looked up |

---

## 9. Related hosts from the shipped code

| Host | Record | Value | Network |
|---|---|---|---|
| `yyvgdqzpyuwindemymrh.supabase.co` | A | `172.64.149.246`, `104.18.38.10` | Cloudflare (Supabase edge) |
| `yyvgdqzpyuwindemymrh.supabase.co` | AAAA | none | n/a |
| `revive-auth.crownizzle.workers.dev` | A | `104.21.12.16`, `172.67.151.72` | Cloudflare Workers |
| `revive-auth.crownizzle.workers.dev` | AAAA | `2606:4700:3030::6815:c10`, `2606:4700:3037::ac43:9748` | Cloudflare Workers |

`crownizzle` is the operator's **workers.dev account subdomain**. It is fixed per Cloudflare
account, so every Worker on that account is published under `*.crownizzle.workers.dev`.

### Passive pivots not pursued here

| Pivot | Why it's useful |
|---|---|
| NS pair `memphis` + `ullis` | Cloudflare gives one NS pair per account. A reverse-NS lookup in passive DNS lists other zones that may share the operator's account (noisy, since pairs are reused across many accounts) |
| `*.crownizzle.workers.dev` | Other Workers on the same Cloudflare account |
| `137.184.22.254` passive DNS | Other names that have pointed at the droplet |
| `40.160.138.207` / `2604:2dc0:222::125f` passive DNS | Names that pointed at the OVH VPS before Cloudflare was put in front |
| ETag `"67b394e27f4cdd1:0"` / body SHA-256 `ffbc4683…` | Internet-scan engines (Censys, Shodan) index both. Any other IP serving them is a mirror or a former origin |
| `Server: TooTallNate Java-WebSocket` on port 25600 | Scan-engine search for other GridFly servers (the port is unusual) |
| `141.11.34.107` passive DNS / March 2025 web archives | What `reviveon.top` hosted in 2025 |
| `785b30df…@withheldforprivacy.com` | Reverse WHOIS on the per-domain privacy alias |

---

## 10. Method, sources and limitations

### Sources

| Data | Tool / endpoint |
|---|---|
| DNS (recursive) | `Resolve-DnsName` against `1.1.1.1`; DoH JSON at `cloudflare-dns.com/dns-query` (CAA, HTTPS, SVCB, DS/DNSKEY with `do=1`) |
| DNS (authoritative) | `Resolve-DnsName` direct to `memphis`/`ullis.ns.cloudflare.com` and `dns1`/`dns2.registrar-servers.com` |
| Domain RDAP | `rdap.verisign.com` (.com registry), `rdap.cloudflare.com` (registrar), `rdap.zdnsgtld.com` via `rdap.org` (.top registry), `rdap.namecheap.com` (registrar) |
| IP RDAP | ARIN / RIPE via `rdap.org/ip/…` |
| ASN / prefix | Team Cymru (`origin.asn.cymru.com`, `asn.cymru.com` TXT), RIPEstat `prefix-overview`, `whois`, `routing-history` |
| Geo | `ipinfo.io` (free tier) |
| CT | `crt.sh` JSON (`%.domain` and apex) |
| HTTP / TLS | `curl` (`HEAD /`, `GET /`), `openssl s_client` + `x509`, Cloudflare `/cdn-cgi/trace` |

### What was sent to the operator's servers

Everything in sections 2 to 4 and 6 came from public registries, resolvers, the parents'
authoritative nameservers (Cloudflare and Namecheap), and CT logs. **Section 5 is the only part that
contacted hosts the operator controls.** That was one `HEAD /` and one `GET /` per hostname, plus
one TLS handshake per hostname.

For `40.160.138.207` it was:

- one `HEAD /` each on the three ports the operator publishes or that serve the web (:25600, :80,
  :443)
- one TLS handshake on :443 and on :25600
- one `GET /` on :80 and one on `https://revive-client.com/` for the hash comparison

In detail:

- no port scans beyond those three ports
- **no WebSocket upgrade, no `ui_hello`, and the operator token hardcoded in the dashboard was
  never sent**
- no requests to authenticated or download endpoints
- no origin-unmasking (`Host`-header) probes. The origin match in 5.5 came from the IP's own default
  site
- nothing downloaded or executed

### Limitations

- The four `revive-client.com` hostnames are Cloudflare-proxied. The **apex and `www.` origin is
  `40.160.138.207`** (5.5). **`dl.` and `emotes.` origins are still unknown.**
- Geolocation of `141.11.34.107` is unreliable (leased space, three sources disagree).
- CT only shows publicly trusted certs. A Cloudflare Origin CA cert, if one is used between Cloudflare
  and the origin, would not appear.
- Subdomain discovery used CT plus an 85-label wordlist, so it is not exhaustive. Cloudflare and
  Namecheap do not allow zone transfers.
- `team.reviveon.top` does not appear anywhere in the reconstructed source in this repo. Its tie to
  TeamSync is inferred from the shared backend fingerprint (5.4) and the dead
  `team.revive-client.com` default.

### Raw evidence ([`raw/`](raw/))

| File | Contents |
|---|---|
| [`dns_snapshot.json`](raw/dns_snapshot.json) | DoH answers for 10 names x 12 record types |
| [`http_heads.txt`](raw/http_heads.txt) | `HEAD /` response headers for every host |
| [`tls_certs_served.txt`](raw/tls_certs_served.txt) | Leaf certs presented on :443 |
| [`crt_revive-client.json`](raw/crt_revive-client.json), [`crt_revive-client_apex.json`](raw/crt_revive-client_apex.json) | crt.sh results, `revive-client.com` |
| [`crt_reviveon.json`](raw/crt_reviveon.json), [`crt_reviveon_apex.json`](raw/crt_reviveon_apex.json) | crt.sh results, `reviveon.top` |
| [`rdap_revive-client_registry.json`](raw/rdap_revive-client_registry.json), [`rdap_revive-client_registrar.json`](raw/rdap_revive-client_registrar.json) | Verisign and Cloudflare RDAP |
| [`rdap_reviveon.json`](raw/rdap_reviveon.json), [`rdap_reviveon_registrar.json`](raw/rdap_reviveon_registrar.json) | .top registry and Namecheap RDAP |
| `rdap_ip_*.json` | ARIN / RIPE RDAP for each resolved IP |
| [`ripestat_routing_history_141.11.34.0-24.json`](raw/ripestat_routing_history_141.11.34.0-24.json) | Origin-AS history of the leased `/24` |
| [`gridfly_40.160.138.207_probe.txt`](raw/gridfly_40.160.138.207_probe.txt) | Headers from :25600 / :80 / :443, TLS results, origin vs Cloudflare body hashes, ETag decode |
| [`rdap_ip_40_160_138_207.json`](raw/rdap_ip_40_160_138_207.json), [`rdap_ip_2604_2dc0_222__125f.json`](raw/rdap_ip_2604_2dc0_222__125f.json) | ARIN RDAP for the OVH VPS (IPv4 / IPv6) |
| [`ripestat_routing_history_40.160.138.0-24.json`](raw/ripestat_routing_history_40.160.138.0-24.json) | Origin-AS history covering the VPS block |
