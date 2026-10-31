# IPTV Whiz: Plan

**IPTV Whiz** is a lightweight, good-looking IPTV player in the spirit of TiviMate. The user adds a provider (an M3U URL or an Xtream Codes login), then watches, edits, hides, sorts and groups channels. It is one app for Android TV (remote) and phones/tablets (touch).

---

## 1. Product goals

| Goal | What it means in practice |
|---|---|
| **TV-first, phone-friendly** | Every screen works with only a D-pad + OK + Back. Every screen also works with only touch. Neither input depends on the other. |
| **Lightweight** | APK < 15 MB, cold start < 1.5 s on a cheap TV stick, smooth with 20k+ channels, low RAM on 1 GB devices. |
| **Easy for the public** | Setup in under a minute, no jargon on the main path, big readable UI from 3 m away. |
| **Customizations survive** | Renames, hidden channels, custom order and custom groups are kept when the provider playlist refreshes. **This is the core technical challenge.** |
| **Player only** | Ships with no channels or content. The user brings their own provider. (Required for store approval; see §10.) |

---

## 2. Tech stack (recommended)

| Area | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Standard |
| UI | **Jetpack Compose** + **Compose for TV** (`androidx.tv:tv-material`) on TV, **Material 3** on phone | One codebase; Compose-TV handles focus, scaling and TV-styled cards |
| Player | **AndroidX Media3 (ExoPlayer)** | HLS, DASH, MPEG-TS, progressive; good on TV hardware |
| Extra codecs | Media3 **FFmpeg audio extension** (AC3/E-AC3/DTS/MP2) | Many IPTV streams use audio codecs that cheap devices can't decode. Check LGPL/GPL terms. |
| Database | **Room** (SQLite) with FTS for search | Handles 50k rows easily, supports paging |
| Paging | **Paging 3** | Keeps big channel lists smooth |
| Settings | **DataStore** | |
| DI | **Koin** (lighter) or Hilt | Koin keeps the build simpler and the APK smaller |
| Networking | **OkHttp** + kotlinx.serialization | Xtream API is JSON |
| Images | **Coil** with a size-limited disk cache | Channel logos |
| Background | **WorkManager** | Scheduled playlist/EPG refresh |
| Parsing | Hand-written **streaming M3U parser** + **XmlPullParser** for XMLTV (gzip support) | EPG files can be 100+ MB; never load them fully into memory |

**minSdk 24 (Android 7.0)**, which covers almost all TV boxes still in use. **targetSdk:** latest.

A **single APK** declares both `LAUNCHER` and `LEANBACK_LAUNCHER`, has a TV banner, and sets `touchscreen required=false`. At runtime it detects TV vs phone (`UiModeManager`) and picks the layout.

---

## 3. Architecture

Today everything lives in the single `:app` module, split by package (`data`, `player`, `ui/state`, `ui/tv`, `ui/mobile`, `ui/common`). The modules below are the target once the code base is big enough to benefit.

```
app/                 → entry, navigation, device-type detection
core/model           → Channel, Group, Playlist, Program (pure Kotlin)
core/database        → Room entities, DAOs, migrations
core/network         → OkHttp, Xtream API client
core/parser          → M3U parser, XMLTV parser (streaming)
core/player          → Media3 wrapper, retry/reconnect logic, track selection
feature/onboarding   → add provider flow
feature/live         → channel list + player overlay (TV & phone variants)
feature/guide        → EPG grid
feature/manage       → edit / hide / sort / group channels
feature/settings
ui/tv, ui/mobile     → shared design tokens, focusable components
```

Pattern: **MVVM + unidirectional data flow** (ViewModel exposes `StateFlow<UiState>`). Repositories are the single source of truth over Room. The network only writes into the DB, and the UI only reads from it.

---

## 4. Data model: making edits survive refresh

The provider's playlist is **raw data**. The user's edits are an **overlay** stored separately and merged at read time.

```
Playlist        (id, name, type[M3U|XTREAM], url/credentials, epgUrl, lastRefreshed, refreshInterval)
ProviderChannel (playlistId, stableKey, name, streamUrl, logo, tvgId, groupTitle, providerOrder, catchupInfo)
ProviderGroup   (playlistId, name, providerOrder)

ChannelOverride (playlistId, stableKey, customName?, customLogo?, hidden, favorite, customGroupId?, sortOrder?, customEpgId?)
CustomGroup     (id, playlistId, name, sortOrder, hidden, icon?)
GroupOverride   (playlistId, providerGroupName, customName?, hidden, sortOrder?)
```

**Stable key** (identifies a channel across refreshes), in priority order:
1. Xtream `stream_id`
2. `tvg-id` + name
3. Hash of normalized name + group

The stream URL is *not* used as the key, because providers rotate tokens in URLs.

**On refresh:** upsert ProviderChannel rows and mark missing ones as `removed` (keep them for 7 days in case the provider glitches). Overrides are never touched. A **"New channels"** badge highlights additions.

**Display query** = ProviderChannel LEFT JOIN ChannelOverride, filtered by `hidden`, ordered by `COALESCE(sortOrder, providerOrder)`.

---

## 5. Features by phase

### Phase 1: MVP (≈ 5–7 weeks solo)
- Add provider: **M3U URL**, **M3U file**, **Xtream Codes** (server/user/pass)
- Optional **XMLTV EPG URL** (auto-filled from `url-tvg` or Xtream)
- Live TV: groups → channels → fullscreen playback
- TV player overlay: channel list on OK, now/next info, channel up/down with D-pad, **number keys to jump**
- Phone player: portrait (video on top, list below) and landscape fullscreen; swipe for volume/brightness; tap for controls
- **Favorites** and **Recently watched** as fixed groups at the top
- **Search** (Room FTS) with on-screen keyboard and voice on TV
- **Channel management:** rename, hide/unhide, change logo, move to group, reorder (see §7)
- **Group management:** rename, hide, reorder, create custom groups
- Auto-reconnect on stream drop, buffering indicator, audio/subtitle track picker
- Remember last channel and start there (optional, like TiviMate)

### Phase 2: TiviMate-class (≈ 4–6 weeks)
- **EPG grid guide** (timeline × channels, paged, focus-friendly)
- **Catch-up / timeshift** (Xtream `tv_archive`, M3U `catchup` attributes)
- **Multiple playlists** with a switcher
- **Backup & restore** of playlists + all customizations to a JSON file (and later to cloud)
- **Parental PIN** on hidden or locked groups
- **Picture-in-Picture** on phone; **mini preview** in the channel list on TV
- Program reminders (notification / on-screen toast)

### Phase 3: Nice-to-have
- VOD & Series sections (Xtream `get_vod_streams`, `get_series`)
- Multi-view (2–4 streams), only on powerful devices
- Recording to local storage (complex; storage permissions vary)
- Themes / accent colors, UI scale setting
- Stalker / MAC portal support (only if users ask; the protocol is messy)

---

## 6. Screens

**TV (landscape, 10-foot UI)**
1. **Welcome / Add provider**: big cards for "M3U link", "Xtream login", "Set up from phone" (QR, see §9)
2. **Home**: left nav rail (Live, Guide, Favorites, Search, Settings); content shows "Continue watching" + favorite channels row
3. **Live**: three columns: **Groups | Channels (logo, number, name, now-playing + progress bar) | Preview + program details**
4. **Player**: fullscreen with an overlay that slides in from the left on OK (TiviMate pattern); info banner on channel change; long-press OK opens a quick menu (favorite, audio track, aspect, EPG)
5. **Guide**: EPG grid
6. **Manage channels**: list with checkboxes + action bar
7. **Settings**: playlists, EPG, player (decoder, buffer size, aspect), appearance, parental, backup, about

**Phone (portrait-first)**
1. Onboarding (same choices, plus paste-from-clipboard)
2. Bottom nav: **Live · Guide · Favorites · Settings**
3. Live: group chips scrolling horizontally on top, channel list below
4. Player: portrait split view / landscape fullscreen; swipe up/down on the video changes channel
5. Manage: long-press for multi-select; drag handles to reorder

---

## 7. Editing UX for both remote and touch

This is where most IPTV apps get it wrong. Every operation needs a remote path and a touch path:

| Action | Touch | Remote |
|---|---|---|
| Open item menu | Long-press | Long-press OK **or** Menu key **or** a visible "⋮" button reachable with D-pad right |
| Multi-select | Long-press → tap others | "Select" mode from the menu → OK toggles checkboxes |
| Reorder | Drag handle | **Move mode:** select "Move" → item lifts (highlighted) → ▲▼ moves it → OK drops it, Back cancels. Also "Move to top/bottom" and "Move to position #" |
| Move to group | Menu → pick group | Same |
| Hide | Swipe or menu | Menu |
| Rename | Menu → text field | Menu → text field (or edit from phone via QR, §9) |
| Undo | Snackbar "Undo" | Snackbar with focusable "Undo" (auto-focus for 5 s) |

Other rules:
- **Sort presets:** Provider order · A–Z · By channel number · Custom. Switching to Custom starts from the current order.
- **Bulk actions on groups:** "Hide all channels in this group", "Hide group".
- A **"Show hidden"** toggle in Manage, so the user can always get hidden items back.

---

## 8. Design & usability guidelines

- **Focus is the UI on TV:** clear focus state (scale 1.05 + bright border + elevation). Never rely only on a color change. Focus must never get "lost": restore it to the last focused item when returning to a screen.
- **Overscan-safe:** 48 dp horizontal / 27 dp vertical margins on TV.
- **Readable at distance:** minimum 16 sp body on TV and 20 sp+ for channel names. High contrast dark theme by default (TV) and follow the system theme on phone.
- **Back always does the expected thing:** close overlay → exit player → previous screen → "press Back again to exit".
- **Number keys** on remotes jump to the channel number. **CH+/CH−** and **D-pad up/down** in the player change channel.
- **Big touch targets** (48 dp) on phone. All gestures have a visible-button equivalent.
- **Empty and error states in plain language:** "Couldn't load your playlist. Check the link or your internet." plus Retry and Edit buttons. No raw stack traces or HTTP codes on the main screen (show them under "Details").
- **Logos:** placeholder with channel initials + generated color when a logo is missing or broken.
- **Localization ready** from day one (all strings in `strings.xml`, RTL-safe layouts).

---

## 9. Suggestions and differentiators

1. **"Set up from your phone" (QR code)**: The TV shows a QR code; the phone opens a small local web page served by the TV app (e.g. NanoHTTPD on the LAN) to paste the URL/credentials and even rename or reorder channels. Typing long URLs with a remote is the #1 setup pain, so this is a big usability win.
2. **Instant channel switching:** keep the player instance warm, preload the next/previous channel's manifest, and show the last frame/logo while buffering.
3. **Smart cleanup on first import:** offer "Hide groups you don't need" with a checklist of groups (providers often ship 100+ country groups).
4. **Duplicate detection:** flag channels with the same name in different qualities (HD/FHD/4K) and let users keep one.
5. **Customization export/import:** share the overlay (renames, order, groups) separately from credentials, so users can move it between devices or providers.
6. **Health indicator:** optional background check that marks dead streams.
7. **Low-end mode:** auto-detect RAM < 1.5 GB and turn off previews and animations and shrink the image cache.

---

## 10. Legal & distribution notes

- **Google Play** is strict about IPTV apps. The app must ship **no content**, no preloaded or suggested playlists, and no screenshots showing copyrighted channels. Use clearly fake demo channels in store screenshots. Include a clear statement: "This app does not provide any content."
- Google Play for **Android TV** requires TV-specific review: a banner, a working D-pad everywhere, and no touch-only screens.
- Alternatives: Amazon Appstore (Fire TV is a big IPTV market), direct APK download, or a Downloader code.
- **FFmpeg extension:** use an LGPL-only build configuration if you don't want GPL obligations.
- Store credentials with **EncryptedSharedPreferences / Android Keystore**. Never log URLs that contain usernames or passwords.

---

## 11. Performance checklist

- Parse M3U as a **stream** and insert into Room in **batches of 500 inside one transaction**. 20k channels should import in a few seconds.
- Parse XMLTV with XmlPullParser and keep only programs from now − 1 day to now + 7 days, and only for channels that exist in the playlist.
- Use Paging 3 for channel lists and the EPG grid. Use stable `key` values in lazy lists to keep focus correct.
- Downsample logos to the display size (Coil `size()`), with a disk cache cap of about 100 MB.
- Use R8 full mode with resource shrinking. Avoid heavy libraries (no Firebase unless needed).
- Baseline profiles for faster startup on TV boxes.

---

## 12. Testing

- **Unit:** M3U parser (malformed lines, `#EXTVLCOPT`, attributes with quotes, UTF-8/BOM), Xtream client, merge/overlay logic (the most important tests).
- **UI:** Compose tests for focus traversal on TV screens (D-pad key events).
- **Devices:** a cheap Android TV stick (e.g. Chromecast w/ Google TV HD, Fire TV Stick Lite), Nvidia Shield, a mid-range phone, a tablet, and the Android TV emulator.
- **Streams:** HLS, MPEG-TS over HTTP, streams with AC3 audio, streams that drop or reconnect, geo-blocked/403 responses.

---

### Free, legal test sources (checked 2026-10-24)

| Source | URL | Notes |
|---|---|---|
| **Free-TV/IPTV** | `https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8` | About 2,050 channels. Only channels broadcasters offer for free (free-to-air), grouped by country. The header has **several comma-separated EPG URLs** in `x-tvg-url`. Names carry markers: Ⓖ geo-blocked, Ⓢ not 24/7, Ⓨ YouTube. |
| **iptv-org (all)** | `https://iptv-org.github.io/iptv/index.m3u` | About 11,300 channels: a good **stress test** for import speed and list performance. Publicly available streams; many are redirect links (`jmp2.uk`) |
| **iptv-org (news only)** | `https://iptv-org.github.io/iptv/categories/news.m3u` | About 1,000 channels. Other categories/countries: `iptv-org.github.io/iptv/categories/<name>.m3u`, `.../countries/<code>.m3u` |
| **Local test file** | `testdata/test-playlist.m3u` | Six entries: Apple/Mux official test streams, one live news stream, a deliberately broken URL, and parser edge cases (quotes, commas, `#EXTVLCOPT`, no attributes) |

Many public streams are geo-blocked or go offline at times, so expect some failures. That's useful for testing error states. There's no legitimate free **Xtream Codes** test server. Xtream support will be tested against a small fake server in unit tests.

---

## 13. Milestone roadmap

| # | Milestone | Done when |
|---|---|---|
| M0 ✅ | Project skeleton | App launches on TV emulator + phone, nav + theme in place. Done 2026-10-24: release APK 1.1 MB, verified on a phone emulator and an Android TV 14 emulator (D-pad only). |
| M1 ✅ | Import & play | Add M3U/Xtream, list groups/channels, play a stream. Done 2026-10-24, see notes below. |
| M2 ◐ | TV player UX | Overlay list, channel up/down, number entry and info banner were pulled into M1; quick menu (hold OK / Menu) done in 0.5.0. Still to do: audio/subtitle track picker, "inverse channel keys" setting. |
| M3 ✅ | Manage | Hide, rename, reorder (move mode + drag), custom groups; survives refresh. Done 2026-10-24, plus favorites. |
| M4 ✅ | EPG basics | XMLTV import, now/next in list and player. Done 2026-10-25 (plus a Now & next Guide tab). |
| M5 | Polish & release | Search ✅, watchlists ✅, channel health ✅ (0.5.0, 2026-10-26); viewing options ✅ (0.6.0, 2026-10-26); DRM playback ✅ (0.6.1, 2026-10-26); still to do: settings (player, appearance), error states, store assets → **v1.0** |
| M6+ | Phase 2 features | Guide grid, catch-up, PIN (backup file ✅ 2026-10-25, multi-playlist ✅ in M1) |

---

### M1 notes (2026-10-24)

- **Done:** M3U link, M3U file (where the device has a file picker), and Xtream login; import of 2,052 Free-TV channels in about 10 s; TV browser (groups | channels, group follows focus); full-screen TV player with ▲▼/CH± zapping, number entry, info banner, channel overlay and friendly error panel; phone home, add forms, player (portrait list / landscape full screen, swipe to zap); playlists screen (watch, refresh, delete); 19 JVM unit tests (M3U parser, stable keys, Xtream against a fake server).
- **Learned in testing:**
  - Many stream servers return **403 to unknown player names**. The default User-Agent is now VLC's; a per-playlist override is still to do.
  - Dead channels must fail fast: a never-started channel gets an 8 s connect timeout and one retry; one that was playing gets three retries with backoff.
  - Most TV boxes (including the emulator) have **no file picker**, so "Choose a file" only helps on phones. The "Set up from phone" page (§9) is the real fix for TV.
  - Focused tv-material buttons and list rows both use `inverseOnSurface`, so the theme's focus colours must work on both (light violet + dark text).
- **Deferred:** credentials still stored in plain Room (move to Keystore before Play); Paging 3 not needed yet (11k-channel lists stay smooth with lazy lists); no FFmpeg audio extension yet.

### M3 notes (2026-10-24)

- **Done:** overlay tables `channel_overrides` / `group_settings` and the `channel_view` that applies them, keyed by stable key, so **edits survive refresh** (verified: favorite + hidden channel kept after re-downloading). Hide/show, rename (blank = original), favorites, move to group, custom groups (create, rename, delete → channels go back), reorder channels and groups with fractional positions, "Sort A–Z" and "Use the provider's order".
- **TV:** long-press OK **or the Menu key** opens a channel's menu anywhere in Live; Manage screen with groups | channels, hidden items dimmed, move mode (▲▼ move, OK place, Back cancel). **Phone:** long-press opens a bottom sheet; Manage screens with ≡ drag handles, show/hide switches, heart toggles.
- **Favorites:** a Favorites group at the top of Live (TV and phone) and the Favorites tab.
- **Database:** first real migration (v1 → v2) generated from the exported schema; tested by upgrading both emulators with existing playlists (2,052 channels on TV).
- **Tests:** 10 organizer tests (hidden channels/groups, custom groups, ordering, favorites, positions). 29 JVM tests in total.
- **Behaviour to know:** "All channels" follows the group order, so after reordering groups the channel numbers in that list are no longer strictly ascending. Numbers stay the provider's, so number entry still finds the same channel.
- **Not yet:** a "Show hidden" toggle in Live (hidden items are only reachable from Manage, by design for now); drag auto-scroll on long phone lists; multi-select bulk actions.

### Backup & M4 notes (2026-10-25)

- **Backup & restore (§15 option A) done:** JSON file with playlists + all edits; playlists matched by a fingerprint (hash of type, address, username, never the password). Restore merges per item, newest change wins, and adds missing playlists only when the file includes logins (off by default). Phones use the system file picker. **Android TV has no real picker** (only a stub that returns nothing; now detected), so TV saves to `Download/IPTV Whiz` and restores from there or from `Android/data/com.iptvwhiz.app/files/backups` (USB). Files copied into Download by USB aren't visible to apps on Android 11+, which is why the app folder is also read. The phone→TV web page (§9) remains the friendly fix.
- **M4 done:** streaming XMLTV import (gzip detected by content), programmes kept for matched channels only, 2 h back to 3 days ahead. Matching by tvg-id (case-insensitive), then by normalised name when the tvg-id isn't in the guide. Old guide data is replaced only after a successful import. Now/next in TV and phone channel lists, the player banner and a Now & next Guide tab; Settings → TV guide shows status, lets you edit links and update now.
- **Updates:** WorkManager, daily; also right after adding/refreshing a playlist, and at app start when the guide is over a day old.
- **Big guide lists:** Free-TV's header lists **101 guide links (~600 MB)**. At most 10 links are used per update, preferring files whose name carries the country codes of your channels' tvg-ids; the settings screen says so and the list can be edited.
- **Database:** v3 (programs, channel_epg, epg_status). Migrations are generated from `app/schemas` by `tools/gen_migration.py` (additive steps only).
- **Tests:** 40 JVM tests (XMLTV times/offsets, CDATA, matching rules, now/next, link selection, backup merge).
- **Not yet:** full guide grid with scrolling timeline (M6), catch-up, programme reminders, per-playlist User-Agent.

---

## 14. Decisions (2026-10-24)

| Topic | Decision | What it means for the build |
|---|---|---|
| Name | **IPTV Whiz** | Package name **`com.iptvwhiz.app`** (fine to keep for Play later) |
| Distribution | **Direct APK now, Google Play later** | Follow Play rules from day one (no bundled content, TV quality checklist) so the switch is painless. Sign with a release keystore you keep safe, and plan to enroll it in Play App Signing later. |
| Price | **Free now, non-intrusive ads later** | Build an `AdSlot` abstraction now with a no-op implementation; see §16. |
| VOD / Series | **Not in v1** | Keep the Xtream client extensible, but ship live TV only. |
| Sync of edits | **Option A only: file export/import** | No backend. Same merge rules as §15, so cloud sync can be added later without changing the data model. |

---

## 15. Syncing edits across devices

### What gets synced
Only the **overlay** from §4 is synced: renames, hidden flags, favorites, custom groups, order and custom EPG mappings. That's a few KB even for big playlists. Channel lists and EPG are **not** synced; each device downloads them from the provider.

Each playlist is identified by a **playlist fingerprint**, a hash of the server address plus the playlist type, never the password. Each channel is matched by its stable key (§4). So when the phone and the TV both have the same provider, edits made on one apply to the other.

**Credentials are not synced by default.** An opt-in "Also sync my provider login" can upload them encrypted with a key derived from a passphrase only the user knows.

### How changes merge
- Every override record carries `updatedAt` and `deviceId`. On sync, the **newest change per record wins** (last-write-wins). Deleting leaves a "tombstone" record so the deletion syncs too.
- **Ordering uses fractional positions** (e.g. "a", "am", "b") instead of 1, 2, 3. Moving one channel changes only that channel's record, so moves made on two devices merge cleanly instead of overwriting each other's whole list.
- Sync runs on app start, after edits (debounced ~10 s), and on a WorkManager schedule. It also works offline: changes queue up locally and upload later.

### Options for where the data lives

| Option | How the user links devices | Works on Fire TV? | Cost | Effort |
|---|---|---|---|---|
| **A. File export/import** | Save a `.json` backup and open it on the other device | Yes | Free | Low |
| **B. Google Drive app folder** | Sign in with Google on each device | No (no Google account on Fire TV) | Free (uses the user's Drive) | Medium |
| **C. Firebase or Supabase + sync code** ⭐ | The TV shows a 6-digit code or QR, and the phone enters it. No account or password needed | Yes | Free tier is plenty for personal use and early public use | Medium |
| **D. Your own server** | Any | Yes | Hosting cost + maintenance | High |

**Decision: A only.** The backup is a single `iptvwhiz-backup-<date>.json` file containing playlists and all overlay records. Import **merges** it using the rules above (newest change wins per record) rather than wiping the device, so moving edits back and forth between phone and TV works. Credentials go in the file only if the user ticks "Include provider logins". Moving the file between devices: save to Downloads / share sheet on phone; on TV, import via USB, a file manager, or the "Set up from your phone" local web page (§9), which can also send and receive the backup file. Options B–D stay possible later because the records already carry timestamps, tombstones and fractional positions.

---

## 16. Ads (later, non-intrusive)

- **Never show ads during playback** or over the player overlay. Good places are the home screen, the bottom of Settings and Manage, and the phone's channel list (a small native or banner slot).
- **Android TV:** Google's mobile ad SDK (AdMob) has limited and restricted support on TV devices, and Play has extra ad rules for TV apps. Check the current policy before adding ads; TV may need to stay ad-free or use a TV-specific ad network.
- Ads bring a **consent screen** (GDPR/UMP) and a **Data safety** declaration on Play. Plan the privacy policy page then.
- Consider a one-time **"Remove ads"** purchase through Play Billing. Users usually accept that better than a subscription.
- Code now: an `AdSlot` composable that renders nothing, so ads can be added later without reworking layouts.

---

## 17. Remaining open questions

1. Which devices you'll test on first (TV box model + phone)?

---

## 18. Watchlists: your own channel lists across playlists (planned 2026-10-26, done in 0.5.0)

**Today:** the app shows one playlist at a time, and Favorites belong to that playlist. You can't combine channels from two providers into one list.

**Goal:** a *Watchlist* is a list you build yourself from channels in **any** of your playlists (for example "Sports" with channels from provider A and B). You open it, zap through it and see the TV guide like any group. It's called a Watchlist so it's clearly different from the provider's *playlists*.

### How it works for the user

- **Adding:** long-press (TV: hold OK or press Menu) any channel, wherever it appears, and choose **Add to watchlist**: pick a list, or "New watchlist…". This works in:
  - the Live TV channel lists and the Guide tab (already have a long-press menu: one new entry);
  - **the player itself** (new): with the video playing, hold OK / press Menu on TV, or long-press the video on a phone, to open a quick menu for the channel that's playing: *Add to watchlist*, *Add to / remove from favorites*, *Hide channel*, *Rename*;
  - the player's channel list overlay (TV) and the channel list under the video (phone portrait).
- **Watching:** watchlists appear at the top of Live TV, before Favorites and All channels, no matter which playlist is active, and in their own section. Zapping (▲▼ / swipe) moves through the watchlist, switching provider silently when needed. Each channel shows a small label with its playlist's name.
- **Managing:** Settings → Watchlists: create, rename, reorder, delete lists; inside a list, reorder (TV move mode / phone drag, as in Manage channels) and remove channels.
- A channel can be in several watchlists. Removing it from a watchlist never touches the provider's playlist.

### Design

| Piece | Plan |
|---|---|
| Data | `watchlists` (id, name, position, updatedAt) and `watchlist_items` (watchlistId, playlistId, stableKey, position, addedAt). Items point to the channel's **stable key**, so they survive playlist refreshes; if a channel disappears from its playlist the item stays, greyed out as "Not available right now", in case it comes back. Deleting a playlist removes its items. Database v4 (generated migration, new tables only). |
| Playing across playlists | The player currently assumes one playlist. It gets a "queue" source: either a group of one playlist (as today) or a watchlist. Each channel already carries its own stream link and user agent, so the player itself doesn't change; only the list it zaps through does. |
| TV guide | Now/next is per playlist today; for a watchlist the app merges the guides of the playlists involved. |
| Edits | A watchlist shows each channel with your edits applied (renames, logo). Hidden channels stay visible in a watchlist: if you put it there, you want it. |
| Backup | Watchlists go into the backup file, matched by playlist fingerprint + stable key, like other edits. Restoring merges lists by name. |
| Quick menu in player | TV: long-press OK / Menu while the overlay is closed (short OK still opens the channel list). Phone: long-press on the video. The menu is the same component as the Live list menu, plus *Add to watchlist*. |

### Decisions (2026-10-26)

1. **Favorites is the first, built-in watchlist** and works across playlists. Existing favorites move into it automatically.
2. **Many watchlists** are allowed.

### Search (added 2026-10-26)

- One search across **all playlists**: type part of a name, then narrow by **country** and **group** (the provider's category, e.g. News, Sports, or the country groups in Free-TV), and optionally by playlist.
- Country comes from the playlist's `tvg-country` when present, else from the tvg-id suffix (`Kanali7.al` → Albania); shown with full country names.
- Results show what's on now, the playlist label and the health dot; OK/tap plays and zapping moves through the results; long-press opens the usual channel menu (add to watchlist, hide…).

### Channel health: spot dead channels fast (added 2026-10-26)

- **Check channels** (in Manage channels for a group, and for search results / watchlists) probes each stream in the background: it requests the stream and reads the first bytes. Result per channel: **Working**, **Offline** (not found, no answer, timeout), **Blocked** (refused, often geo-blocking), or not checked yet. 12 at a time, 6 s timeout each; progress shown; can be stopped.
- Playing a channel also records its health for free (works → Working, fails → Offline/Blocked).
- Results are stored (with the time) and shown as a small dot on channel rows: green, red, amber. Results older than a week count as unknown.
- **Fast exclude:** after a check, one button: **Hide N offline channels** (and optionally the blocked ones). Undo is simply Show again in Manage channels.
- Limits: a stream can work from one place and not another (geo-blocking), and a channel can be off for a few minutes; the check reports what this device sees right now.

### Steps

1. Data: tables (watchlists, items, channel health), `country` on channels, migration v4 that also moves existing favorites into the built-in "Favorites" watchlist. Unit tests for ordering, refresh survival, missing channels.
2. Player queue that can span playlists; merged now/next for a watchlist.
3. Quick menu in the TV and phone player (long-press), and "Add to watchlist" in every channel menu, with "New watchlist…".
4. Watchlists at the top of Live TV and as their own section; playlist label on channels.
5. Settings → Watchlists: create/rename/delete/reorder lists; reorder/remove channels (TV move mode, phone drag).
6. Search screen (TV and phone) with name, country, group and playlist filters.
7. Health check with dots, "Check channels" and "Hide N offline channels".
8. Backup file with watchlists (older files still restore).
9. Test on TV and phone emulators with two playlists (Free-TV + the local test playlist), commit, send the APK.

### Notes (0.5.0, 2026-10-26)

- **Done:** all 9 steps. Checked on the phone emulator (migration of an old favorite, a watchlist mixing two playlists, zapping across them, long-press menus, search, Check channels → Hide offline, rename) and on the TV emulator (Menu / hold OK quick menu in the player, new watchlist from the player, Search with country filter and Check channels, Settings → Watchlists).
- **Database v4:** `watchlists`, `watchlist_items`, `channel_health`, `channels.country`. The old `favorite` flag is moved into the Favorites watchlist by the migration and no longer used.
- **Backup:** watchlists were added as an optional field, so the format stays version 1: older files restore (their favorites go into Favorites) and older app versions ignore the new field.
- **Fixed while testing on TV:** DASH (`.mpd`) channels crashed the app because the DASH player module was missing. It is now included, and any stream type the player can't handle shows the "format isn't supported" card instead. Also fixed: focus jumping to the side menu when Check channels finished, filter buttons squeezed off screen, and Down from the search field skipping Clear / Check channels.
- **Limits:** a health check can say Working when the playlist file answers but its video segments are blocked (seen on some geo-blocked channels). Hold OK on the player's error card presses Try again (Menu still opens the quick menu there).
- **Tests:** 44 JVM tests.

### Notes (0.5.1, 2026-10-26)

- A link that opens a web page (a blog post with a playlist pasted in) used to import as channels named `<html>`, and Check channels then crashed. The parser now only takes `scheme://` lines as links, and the probe can't crash on one bad channel.
- Phone video now uses a TextureView after a report of black video on a Xiaomi Redmi Note 10; not yet confirmed on that phone. TV keeps the SurfaceView.
- Next steps are kept in [CLAUDE.md](CLAUDE.md) ("Next steps") and the history in [CHANGELOG.md](CHANGELOG.md).

### Notes (0.6.0, 2026-10-26)

- **Viewing options (§19) done**, after a request for an in-player quality readout and for blocked channels to stay out of the way.
- **Quality overlay:** `StreamStats` lives in `player/LivePlayer.kt` and is filled by a once-a-second sampler that is only scheduled while the setting (or the player toggle) is on. Mbps comes from a Media3 `TransferListener` on the network data source; resolution and frames from `player.videoDecoderCounters`. `QualityBadge` (shared by TV and phone) formats it and shows "Measuring…" until the first sample.
  - Visible in the phone player's top bar (info button) and the TV quick menu (Menu / hold OK → Show quality info / Hide quality info).
  - The setting is `AppPrefs.showQuality`; it is remembered across restarts.
  - Verified on the phone emulator (`1280×720 · 26 fps · 0.6 Mbps`, later `16 fps · 5.1 Mbps`) and the TV emulator (`1280×720 · 3.2 Mbps`, no FPS reported by that decoder). The badge leaves out unknown parts by design.
- **Hide blocked channels:** `AppPrefs.hideBlocked`, applied by `LiveViewModel`, `SearchViewModel` and the zapping queue in `PlayerViewModel` through `OrganizedPlaylist.without(blocked)`. Watchlists and Manage channels deliberately keep blocked channels so they can be un-hidden/brought back. Verified with an injected Blocked result for CNA: Live 2,042 → 2,041 and Albania 7 → 6, no CNA in search or zapping, still listed with the Blocked dot in Manage; switching it off restored everything.
- **Phone full screen:** the player's top-bar button forces landscape with `requestedOrientation` even when auto-rotate is off, and restores `UNSPECIFIED` on exit. Verified: 2400×1080, `ROTATION_90`.
- **Database:** unchanged (v4); both switches are in SharedPreferences, so no migration.
- **Tests:** 50 JVM tests (3 new `QualityBadgeTest` cases for the chip text; the blocked filter test was already in the 0.5.1 tree at 47).
- **Limits to remember:** FPS/Mbps are device- and stream-dependent; the TV emulator reported no FPS for Ora News. Some Free-TV channels still fail with `parsing manifest malformed` on the emulator (channel-side, not this change). Verify the Redmi TextureView fix on the real phone when possible.

### Notes (0.6.1, 2026-10-26)

- **DRM playback (§20) done**, after the owner reported some channels showing only a blank black screen. Those playlists carry Kodi's `#KODIPROP` lines with a ClearKey key or a Widevine/PlayReady license link.
- **Parsing:** `data/drm/DrmInfo.kt` reads `inputstream.adaptive.license_type` / `license_key` per `#EXTINF` entry in `M3uParser`. (Since 0.6.2 the state survives `#EXTINF`, because some playlists put the block above it; it is cleared once a channel is emitted, so it still can't leak.) ClearKey accepts `<kid>:<key>` hex, a ready JSON key response, or a license URL. Widevine/PlayReady only accept a license URL. Headers after `|` use Kodi's `Name=Value&…` form.
- **Storage:** four nullable columns on `channels` (`drmType`, `drmKey`, `drmLicenseUrl`, `drmHeaders`), also exposed by `channel_view`/`Channel`; database v5 with `MIGRATION_4_5` (`SchemaMigrations.schema4to5`).
- **Playback:** `LivePlayer` sets a custom `DrmSessionManagerProvider` on `DefaultMediaSourceFactory`. ClearKey keys use `LocalMediaDrmCallback`; license links use `HttpMediaDrmCallback` plus the playlist's headers. Provider is `FrameworkMediaDrm.DEFAULT_PROVIDER`, so the device's own DRM modules do the work.
- **Failure path:** a DRM channel with no usable key/license shows `PlaybackProblem.Drm` and the friendly card. Media3's 6000-range DRM errors also map to it. A DRM failure is **not** recorded as Offline by the health recorder, because it says nothing about whether the stream is alive.
- **Verified** on the phone emulator with Google's public Shaka assets: ClearKey key, ClearKey license server, Widevine license server and a plain HLS channel all played; a broken ClearKey entry showed the message instead of a black screen. See CHANGELOG for the numbers.
- **Tests:** 61 JVM tests (9 `DrmInfoTest`, 2 `M3uParserTest` DRM cases).
- **Deliberate non-goals:** no key extraction from the device, no license-server bypass, no acceptance of raw Widevine/PlayReady keys. Only what the user's playlist provides is used.

### Notes (0.6.2, 2026-10-27)

- **Bug fixed, found on the owner's own playlist** (`https://bit.ly/fasgun`, TV3 SD). 45 of its 214 entries write the `#KODIPROP` block **above** `#EXTINF`, not between `#EXTINF` and the stream link. 0.6.1 cleared the DRM state at `#EXTINF`, so those channels lost the key, got no DRM session and stayed black with no error — exactly what the owner saw. `M3uParser` now keeps the state across `#EXTINF` (Kodi's rule: a `#KODIPROP` block belongs to the next stream link) and still clears it once a channel is emitted, so it cannot leak. `testdata/tv3-test.m3u` covers all three orders with the real TV3 SD stream.
- **New `PlaybackProblem.NoVideo`** after a 20 s first-frame watchdog in `LivePlayer`. Some encrypted streams fail without an error (no key in the playlist, nothing decodable), which used to be an unexplained black screen. The card says the channel isn't sending a picture and offers **Try again**. Cancelled by `onRenderedFirstFrame`, and skipped when the stream has no video track, so audio-only channels and normal channels never see it. Not recorded as Offline by the health recorder.
- **Tests:** 63 JVM tests (2 new `M3uParserTest` cases: DRM before `#EXTINF`, and no leak to later channels). Database unchanged at v5.
- **Verified** on the phone emulator: DRM before `#EXTINF` stored `clearkey` and played `720×540 · 23 fps · 0.6 Mbps`; the same stream with its DRM lines removed showed the new card after 20 s; a normal channel was unaffected.

### Notes (0.6.3, 2026-10-28)

- **The DRM card now explains itself.** `data/drm/DrmDiagnostics.kt` builds one line naming the DRM system and the reason; `DrmInfo.clearKeyKeyIds` / `DrmKeyIds` read key **IDs** from the playlist's ClearKey JSON and from the device's key request (never the key itself).
- **Key-ID comparison is the useful part:** Android's ClearKey module asks for a specific KID, so "playlist IDs ∩ requested IDs = ∅" means the provider re-keyed the stream and the playlist is stale. That is the classic "wrong provider key" case and it is now named.
- **Wiring:** `LivePlayer` wraps the ClearKey callback in `KeyRequestRecorder` to remember the requested IDs, and the 20 s no-picture watchdog prefers the key-ID message when it has one. `httpStatus()` now walks the cause chain, so a refused license reports `HTTP <status>`. `toProblem()` checks DRM error codes first, so a license 403 stays a DRM problem and is not mislabelled Blocked.
- **Tests:** 73 JVM tests (10 new in `DrmDiagnosticsTest`). Database unchanged at v5.
- **Verified** on the phone emulator with `testdata/drm-diagnostics-test.m3u`: stale key ID, license HTTP 405, missing key, and a working control channel. See CHANGELOG for the exact strings.

### Notes (0.6.4, 2026-10-28)

- **Phone UI polish, no behaviour change.** The phone app now uses the same dark, low-glare system as the TV app. Tokens live in `ui/theme/Brand.kt` and are shared by `MobileTheme.kt` and `TvTheme.kt`, so the two layouts cannot drift apart; `res/values/colors.xml` (launcher/splash) matches.
- **Palette:** midnight indigo brand (`#1E1B4B` deep, `#4338CA`, border `#312E81`), background `#0F0F23`, cards `#1B1B30`, muted `#27273B`, muted text `#94A3B8`, live/on-air green `#22C55E`, error `#EF4444`. M3 dark `primary` is indigo-300 `#A5B4FC` so text and icons clear 4.5:1 on the navy background; the deep tones fill buttons and containers.
- **Dark-only on phones, on purpose.** `IptvWhizMobileTheme(darkTheme = true)` keeps the light scheme compiled for a future Appearance setting, but the default no longer follows the system. `MainActivity` passes `SystemBarStyle.dark` so the status/nav icons stay light even when the phone itself is in light mode. The TV theme is unchanged in layout and still always dark.
- **Typography deviation:** the design system recommends Inter. A ~0.5 MB font is not bundled; `AppTypography` uses the system sans with tighter tracking and medium/semibold titles to get the same feel at no size cost.
- **What changed on screen:** Live TV header with the provider name, All channels first, count chips, loading and icon empty states, number badge + favourite heart + `HealthBadge` (Offline/Blocked only — a green dot on every working channel is noise), inset dividers, group subtitle only when the visible list mixes groups. Settings split into Setup / Playback / App & data with tinted icons, chevrons and **Soon** pills; About is an info row with the version. Player error card is compact enough for the portrait 16:9 area, with the diagnostics line and both actions; "Tuning in…" while buffering; LIVE pill and `#number · group`; current row accent bar. Search and Live TV share the same chip styling; the health summary stacks its actions instead of scrolling off-screen.
- **Not touched:** navigation, ViewModels, database (v5), player logic, DRM handling, TV layout.
- **Tests:** 73 JVM tests, unchanged.

### Notes (0.6.5, 2026-10-29)

- **Stale playlists now heal themselves.** The owner's TV3 SD opened to the no-picture card because the stored channel had no DRM columns: it was imported before 0.6.2 taught `M3uParser` to keep a `#KODIPROP` block written above `#EXTINF`, and the playlist had never been refreshed. Simulated exactly (cleared the four DRM columns) and confirmed.
- **Two recovery paths, because one is not enough:**
  - *After an update:* `AppPrefs.parserVersion` records which parser produced the stored channels (`PlaylistRepository.PARSER_VERSION`, currently 2). `IptvWhizApp.onCreate` launches `PlaylistRepository.refreshIfParserChanged()`, which re-imports every playlist once. The version is only recorded when every playlist refreshed, so an offline start tries again next time. User edits survive because they are overlay rows keyed by `stableKey`.
  - *While playing:* `PlayerViewModel` watches `LivePlayer.state`. When a channel reports `PlaybackProblem.Drm` and its stored `Channel.drmInfo` is null, it refreshes that playlist once, rebuilds its channel list with the same logic as `init` (now `loadChannels()`), and replays the refreshed channel at the same stable key. A failed refresh is retried on the next attempt; a playlist that genuinely has no key leaves the card up.
- **The watchdog can now tell "protected, no key" from "nothing arriving".** `LivePlayer`'s 20 s no-picture watchdog inspects `player.currentTracks` for `drmInitData`. If the stream is encrypted and the playlist says nothing, it reports `Drm` with `drmFailureDetail(null)` instead of `NoVideo`, which is also what triggers the play-time refresh. The old copy guessed at copy protection in the NoVideo text; the two cases are now separate.
- **Tests:** 76 JVM tests (3 new in `PlaylistParserVersionTest`). Database stays v5; the parser version is a `SharedPreferences` int.
- **Verified** on the phone emulator: stale DB + update self-healed on start and TV3 played; stale DB + current parser version healed on play and TV3 played; the no-key channel showed the copy-protection reason; the diagnostics playlist still produced its stale-key, HTTP-status and missing-key lines, and its control channel played. TV UI smoke test on the TV emulator.

### Notes (0.6.6, 2026-10-30)

- **0.6.5 broke the owner's FAST channels; 0.6.6 undoes that.** Their playlist puts `#KODIPROP` lines on channels that are clear, and some of those lines are unusable (a ClearKey type with a key that is not a key, or a Widevine type with no license link). `LivePlayer.load()` used to stop before loading when `drm != null && !drm.isValid`, so those channels never reached the network. The early return is gone: the stream is always tried, and `drmSessionManager()` still answers `DRM_UNSUPPORTED` for unusable DRM, so a clear stream plays with no session.
- **The card now only appears for streams that are really encrypted.** The watchdog's `drmInitData` check gained a second branch: `protected && drm != null && !drm.isValid` reports `Drm` with `drmMissingDetail(drm)` instead of falling through to `NoVideo`. `onTracksChanged` does the same check as soon as the tracks are known, so an encrypted channel with a broken key line shows the card in about two seconds rather than after the 20 s watchdog. Clear streams with a spurious line have no `drmInitData`, so nothing fires.
- **One wording decision, in one place.** `drmFailureDetail()` now returns `drmMissingDetail()` first when the playlist's DRM data is unusable, so every path (error handler, watchdog, tracks) says "clearkey · no key in the playlist" / "widevine · the playlist has no license link" instead of blaming the device or a license server. Existing messages are unchanged for usable DRM.
- **Tests:** 78 JVM tests (2 new). `M3uParserTest` parses `testdata/spurious-drm-test.m3u` and pins the shapes (five unusable, one no DRM, one unusable ClearKey on a real encrypted stream, one valid key on a clear stream) so a future parser change cannot quietly make them "valid"; `DrmDiagnosticsTest` pins the new wording precedence.
- **Verified** on the phone emulator: all seven clear channels played; the encrypted no-key channel showed the card in ~2 s; TV3 SD still played with its key (above and below `#EXTINF`); the no-DRM TV3 variant still showed the "no key for it" line; the diagnostics playlist was unchanged (stale key ID, HTTP 405 license, no key, control plays).

---

## 19. Viewing options: quality overlay and blocked channels (0.6.0, 2026-10-26)

**Why:** two small requests from the owner. First, "what quality am I actually getting?" (resolution, frames, bitrate) without opening another app. Second, after Check channels marks a channel **Blocked**, it should not keep cluttering Live TV, search or zapping, but it must never disappear for good.

**Show video quality**

- A chip over the video: `1920×1080 · 25 fps · 4.2 Mbps`. Parts that are not known yet are left out, so a stream with no FPS shows just `1280×720 · 3.2 Mbps`; before the first sample it says "Measuring…".
- Sampled once a second, and only while switched on. The numbers are measured, not read from the playlist: resolution and rendered frames from the decoder counters, Mbps from bytes actually received.
- Switched on in Settings → Viewing options, and from the player itself: phone top bar, TV quick menu. Both read/write the same `AppPrefs.showQuality` flag, so the setting stays in step.
- Off by default: it is a debugging/curiosity feature, not something to put over everyone's video.

**Hide blocked channels**

- Only **Blocked** results are hidden (not Offline or Working). The health check already distinguishes refused/geo-blocked links from dead ones; blocking is the one the user most often cannot fix.
- Applied in Live TV (group counts and list), search results and the zapping queue. It is not applied in watchlists (if you put a channel there, you want it) or Manage channels (where you need to see it to bring it back). Manage still shows the Blocked dot.
- Off by default. Turn it off and blocked channels come back everywhere immediately.

**Not done:** no per-channel "ignore this block" flag, and hiding does not delete a health result. A channel can come back on its own when a later check or playback records a different status.

---

## 20. DRM playback: ClearKey and Widevine/PlayReady from the playlist (0.6.1, 2026-10-26)

**Why:** the owner reported channels that open to a blank black screen. Those streams are encrypted (`cenc`), and the playlist already contains what Kodi uses to play them: `#KODIPROP` lines. Without a DRM session the player has nothing to decode, so it shows black instead of an error. The aim is to use the playlist's own DRM details, and to say so plainly when they don't work.

**What a playlist can say**

```m3u
#EXTINF:-1 group-title="...",Channel name
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=<kid-hex>:<key-hex>
https://server/stream.mpd

#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://server/license|User-Agent=Kodi/21
```

- `license_type` also accepts `org.w3.clearkey` and `com.microsoft.playready`.
- ClearKey `license_key` can be:
  - `<32 hex kid>:<32 hex key>` — turned into the W3C "temporary" JSON response Android's ClearKey module expects;
  - a JSON response the playlist already spells out;
  - a license URL, like Widevine/PlayReady.
- A `|` starts optional request headers in Kodi's `Name=Value&Name=Value` form.

**How it plays**

- `data/drm/DrmInfo.kt` holds the model and parsing. `M3uParser` attaches a `DrmInfo` to each entry. Since 0.6.2 the DRM state is not reset at `#EXTINF` (Kodi applies a `#KODIPROP` block to the next stream link, and some providers write it above `#EXTINF`); it is cleared after a channel is emitted, so it cannot leak to a later channel.
- The data is stored on the channel (`drmType`, `drmKey`, `drmLicenseUrl`, `drmHeaders`) so it survives refresh and works offline; `channel_view` exposes it as `Channel`. Database v5.
- `LivePlayer` builds a `DefaultDrmSessionManager` for the channel and installs it through `DefaultMediaSourceFactory.setDrmSessionManagerProvider`. The device's own DRM modules (`FrameworkMediaDrm.DEFAULT_PROVIDER`) do the decryption; the app only supplies keys/licenses.
- `PlaybackProblem.Drm` (and Media3's 6000-range error codes) show "This channel is copy-protected…" with a short hint and **Try again**. The health recorder ignores DRM failures so a channel isn't marked Offline just because its key is wrong.
- `PlaybackProblem.NoVideo` covers the silent case: encrypted but no error raised (e.g. the playlist has no key for that stream). `LivePlayer` starts a 20 s first-frame watchdog on every load, cancels it in `onRenderedFirstFrame`, and skips it for streams with no video track (audio-only). Since 0.6.5 the watchdog checks the tracks' `drmInitData`: if the stream is encrypted and the playlist has no DRM data, it reports `PlaybackProblem.Drm` (with "the stream is copy-protected, and the playlist has no key for it") and `PlayerViewModel` refreshes the playlist and replays the channel. Since 0.6.6 an *unusable* DRM line no longer stops the load at all: the stream is always tried, so a clear channel with a stray `#KODIPROP` plays; if the stream is encrypted, `onTracksChanged` reports `Drm` with `drmMissingDetail()` as soon as it sees `drmInitData`, and the watchdog repeats the check for streams that never reach that point. See the 0.6.2, 0.6.5 and 0.6.6 notes above.

**Test assets (public, for development only):** `testdata/drm-test.m3u` uses Google's Shaka demo library:
- ClearKey key and ClearKey license server: `angel-one-clearkey` with key `feedf00deedeadbeeff0baadf00dd00d:00112233445566778899aabbccddeeff` and license `https://cwip-shaka-proxy.appspot.com/clearkey?_u3wDe7erb7v8Lqt8A3QDQ=ABEiM0RVZneImaq7zN3u_w`.
- Widevine: `angel-one-widevine` with `https://proxy.uat.widevine.com/proxy`.
- Plus one broken ClearKey entry to check the error card, and a plain HLS channel as a no-DRM regression check.

---

## 21. In-app updates from GitHub releases (0.6.7, 2026-10-31)

**Why:** the app is sideloaded, so until now every update meant sending the owner an APK file by hand. With releases public on GitHub, the app can check for a newer version and fetch it itself — no accounts, no store.

**Prerequisite:** the GitHub repository `unclerahman/IPTVWhiz` is **public** (owner approved 2026-10-31). The anonymous releases API returns 404 for a private repo, so there is no way to keep the code private and still let the app check. The git history was scanned for secrets and the owner's playlist before the switch; only test fixtures mention words like "password". A GitHub token was never an option: anything embedded in the APK can be extracted by anyone.

**How it works**

- `data/update/UpdateChecker.kt` (pure, JVM-tested): parses the `releases/latest` JSON — `tag_name` without its `v` prefix is the version, the first `*.apk` asset is the download, `body` is the notes. `isNewer(latest, current)` compares numeric parts only and stops at the first non-numeric one, so a pre-release tag like `0.6.6-beta.1` never counts as newer than `0.6.6`.
- `ui/state/UpdateViewModel.kt`: Idle → Checking → UpToDate / Available / Failed; Available → Downloading (percent) → Installing → AwaitingInstall. The APK streams into `cacheDir/update.apk` and is shared with the **system installer** through `FileProvider` (`${applicationId}.updates`, `res/xml/update_paths.xml`) via `ACTION_VIEW` with `application/vnd.android.package-archive`. The app never runs or patches an APK. `Failed(fromDownload = true)` distinguishes a broken download from a failed check.
- `ui/common/UpdateScreen.kt`: one shared body. TV passes `Brand` button colors and an explicit light `textColor` — under the TV theme the shared mobile-MaterialTheme text defaults to invisible dark-on-dark — plus D-pad focus tracking on the one active button. Phone wraps it in `Scaffold` + `BackTopBar`.
- Entry point: Settings → "Check for updates" (App & data section, `Routes.UPDATE`, wired in both `TvApp` and `MobileApp`). The screen checks quietly on open; if the check fails it just offers the button.
- Manifest: `REQUEST_INSTALL_PACKAGES` (Android 8+ requires it to hand an APK to the installer). The user sees Android's "allow installs from IPTV Whiz" prompt once; installs themselves stay the system's job.

**Releasing with it:** build `assembleRelease`, put the APK on a GitHub release as an asset (name ending `.apk`), tag like `v0.6.8`. The next time any installed app checks, it offers the update with the release notes.

**Tests:** 4 new JVM tests in `UpdateCheckerTest` (asset pick, no-APK release, malformed JSON, version ordering incl. pre-release tags). Verified live on both emulators: the up-to-date path against the real repo, and — with the version faked to 0.6.0 on the TV emulator — Available with real release notes, a real download, and Android's installer prompt.

