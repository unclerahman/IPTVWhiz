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
| M2 ◐ | TV player UX | Overlay list, channel up/down, number entry and info banner were pulled into M1. Still to do: long-press OK quick menu, audio/subtitle track picker, "inverse channel keys" setting. |
| M3 ✅ | Manage | Hide, rename, reorder (move mode + drag), custom groups; survives refresh. Done 2026-10-24, plus favorites. |
| M4 ✅ | EPG basics | XMLTV import, now/next in list and player. Done 2026-10-25 (plus a Now & next Guide tab). |
| M5 | Polish & release | Search, favorites, settings, error states, store assets → **v1.0** |
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
