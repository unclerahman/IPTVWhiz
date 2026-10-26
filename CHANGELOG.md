# Changelog

All versions so far are sideloaded APKs (`dist/IPTVWhiz-<version>.apk`, not in git), signed with the debug key.
Dates are build dates. The milestone numbers (M0–M6) refer to the roadmap in [PLAN.md](PLAN.md) §13.

## 0.5.1 (2026-10-26)

**Fixed**
- **Crash when checking channels of a "playlist" that was really a web page.** A link that opens an HTML page (for example a blog post with a playlist pasted into it) was read line by line, giving channels named `<html>`, `<head>`… The health probe then crashed the app on them, because it built the request outside its `try`.
  - The M3U parser now only accepts lines that look like links (`scheme://…`). Such a page now reads as "not a playlist".
  - The probe treats any odd channel as Offline instead of failing the whole check.
- **Black video on some phones (reported on a Xiaomi Redmi Note 10) while channels showed a green dot.** The phone player now draws into a `TextureView` (`res/layout/player_view_texture.xml`). TV keeps the `SurfaceView`.
  - Verified on the phone emulator only; **not yet confirmed on the Redmi**.

**Tests:** 46 JVM tests (2 new parser tests).

## 0.5.0 (2026-10-26)

**Added**
- **Watchlists** (PLAN §18): your own channel lists built from any of your playlists.
  - Favorites is the built-in first watchlist; the v3→v4 migration moves old favorites into it.
  - Zapping crosses playlists, now/next is merged from each playlist's guide, and channels show their playlist's name.
  - Manage watchlists in Settings → Watchlists: create, rename, reorder, delete; reorder or remove channels.
- **Quick menu in the player**: TV hold OK or Menu, phone long-press the video or a channel in the list. It offers add to watchlist / New watchlist…, favorites, rename, move to group and hide.
- **Search** across all playlists by name, with country, group and playlist filters, capped at 500 results.
  - Country comes from `tvg-country`, else from the tvg-id suffix.
  - Zapping moves through the results.
- **Channel health**: "Check channels" probes streams (12 at a time, 6 s each, reads the first 512 bytes) and shows the result as a dot: Working (green), Offline (red), Blocked (amber).
  - "Hide N offline" gives a fast exclude.
  - Playback records health too.
  - Available in Search, Manage channels (per group) and inside a watchlist.
- Backup files include watchlists. The format stays version 1 (new optional field), so old files still restore.

**Fixed**
- DASH (`.mpd`) channels crashed the player: the `media3-exoplayer-dash` module was missing. It is now included, and any stream type without a player module shows the "format isn't supported" card.
- TV search:
  - The filter row no longer pushes the Clear button off screen.
  - Focus no longer jumps to the side menu when a check changes state.
  - Down from the field reaches Clear / Check channels.
  - Option dialogs open on the current choice.

**Database:** v4 (`watchlists`, `watchlist_items`, `channel_health`, `channels.country`).

## 0.4.0 (2026-10-25): M4, TV guide

- XMLTV import:
  - Streaming parser; gzip detected by content.
  - Keeps 2 h back to 3 days ahead.
  - Matches by tvg-id, then by normalised name.
- Now/next everywhere: TV and phone channel lists, the player banner, and a Now & next Guide tab.
- Settings → TV guide: status, edit links, update now.
- Daily WorkManager update, plus right after adding or refreshing a playlist.
- Big header lists (Free-TV has 101 links, about 600 MB) are cut to 10 links, chosen by your channels' country codes.
- **Database:** v3.

## 0.3.0 (2026-10-25): Backup & restore

- JSON backup of playlists and every edit; playlists are matched by fingerprint (never by password).
- Restore merges per item, newest edit wins. Logins are included only if you tick the option.
- Phone uses the system file picker. On TV, which has no working picker, backups go to `Download/IPTV Whiz` and restore from there or from `Android/data/com.iptvwhiz.app/files/backups`.

## 0.2.0 (2026-10-24): M0, M1 and M3

- **M0:** project skeleton. One APK for TV (Compose for TV, D-pad) and phone (Material 3).
- **M1 (import and play):**
  - M3U link, M3U file and Xtream login.
  - TV browser and full-screen player with zapping, number entry, banner and channel overlay.
  - Phone home and player.
  - Friendly playback errors, VLC user agent, fast failure on dead channels.
- **M3 (manage):**
  - Hide, rename, favorites, custom groups, reorder (TV move mode, phone drag).
  - Edits live in an overlay keyed by stable channel keys, so they survive playlist refreshes.
- **Database:** v2.
