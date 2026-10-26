# Changelog

All versions so far are sideloaded APKs (`dist/IPTVWhiz-<version>.apk`, not in git), signed with the debug key.
Dates are build dates. The milestone numbers (M0–M6) refer to the roadmap in [PLAN.md](PLAN.md) §13.

## 0.6.1 (2026-10-26)

**Added**
- **DRM-protected channels now play** instead of showing a blank black screen, when your playlist carries the key details Kodi uses (`#KODIPROP` lines). This is exactly what the playlist says — the app never tries to find, guess or extract a key, and it does not bypass a license server.
  - **ClearKey with the key in the playlist:** a `kid:key` pair in hex, or a ready-made JSON key response. The picture plays straight away.
  - **ClearKey, Widevine and PlayReady with a license link:** the player asks that server for the key. A link can carry extra headers in Kodi's form, for example `https://server/license|User-Agent=Kodi/21&Referer=https://example/`.
- **A plain message instead of a black screen** when a protected channel can't be unlocked (no key in the playlist, a key the device rejects, or a license server that says no). The card says the channel is copy-protected and suggests checking the key in the playlist.
- DRM details are kept with each channel in the database, so they survive a refresh and are used every time you play that channel.

**Notes and limits**
- Only data from **your own playlist** is used. A raw Widevine/PlayReady key in a playlist is not accepted: those systems always need a license link, because only the license server can issue a usable key.
- If a channel has DRM lines but no usable key or license link, it is treated as unplayable and shows the message above, not a silent black screen.
- The DRM columns are nullable and empty for ordinary channels, so nothing changes for the rest of your list.

**Tests:** 61 JVM tests (9 new for DRM parsing and keys, 2 new for `#KODIPROP` in the M3U parser).

**Verified on the phone emulator (Android 15, 2026-10-26)** with Google's public Shaka demo streams (`testdata/drm-test.m3u`):
- ClearKey key in the playlist: played, chip showed `768×576 · 25 fps · 2.3 Mbps`.
- ClearKey license server: played, `768×576 · 25 fps · 1.5 Mbps`.
- Widevine license server: played on this emulator, `768×576 · 25 fps · 2.8 Mbps`. A device without a usable Widevine module would show the copy-protection message instead.
- Plain HLS with no DRM still played, `768×432 · 30 fps · 2.4 Mbps`.
- A deliberately broken ClearKey entry showed "This channel is copy-protected…" with the detail `no ClearKey key in playlist` and a **Try again** button — no black screen.

**Database:** v5. The v4 → v5 migration adds four nullable DRM columns to `channels` and rebuilds `channel_view`. Verified on the phone emulator that an existing 0.6.0 database (2 playlists, 2,058 channels) upgraded without losing anything.

## 0.6.0 (2026-10-26)

**Added**
- **Settings → Viewing options**, with two switches:
  - **Show video quality:** a small chip over the video with what is actually arriving: resolution, frames per second and Mbps. It updates about once a second and is sampled only while it is switched on, so it costs nothing when off. You can also switch it on and off from the player: the top bar on a phone, the quick menu (hold OK / Menu) on TV. The chip says "Measuring…" until the first numbers arrive and leaves out a part it doesn't know yet.
  - **Hide blocked channels:** channels the last Check channels run marked **Blocked** are left out of Live TV, search and channel zapping. They stay in your watchlists and in Manage channels (still shown with the amber **Blocked** dot), so you can always bring them back. Off by default.
- **Full screen button in the phone player.** It forces landscape even when the phone's auto-rotate is off, and puts the normal rotation back when you leave full screen.

**Tests:** 50 JVM tests (3 new for the quality chip text; a blocked-channel filter test came with the feature).

**Verified on the emulators (2026-10-26)**
- Phone (Android 15): quality chip showed `1280×720 · 26 fps · 0.6 Mbps`, then `1280×720 · 16 fps · 5.1 Mbps` on the same channel; the info button toggled it off and on. Full screen rotated the display to 2400×1080 (`ROTATION_90`) and the top bar offered "Exit full screen"; leaving restored portrait.
- Phone, hide blocked ON (CNA was given a Blocked result for the test): Live dropped from 2,042 to 2,041 channels and Albania from 7 to 6, CNA was gone from Live and from the player's zapping list, searching CNA said "No channels match.", and Manage channels still listed it with the Blocked dot (`7 shown · 10 hidden`). Turning the switch off restored 2,042 and CNA.
- TV (Android 14): the quick menu item read "Show quality info", the chip showed `1280×720 · 3.2 Mbps`, and the item changed to "Hide quality info" and removed the chip. The TV emulator did not report FPS for that stream, and the chip simply left it out.
- **Known limits:** FPS and Mbps are what the device's decoder is doing, so they vary with the emulator/box and some streams report no FPS at all. Some Free-TV channels still fail on the TV emulator with `parsing manifest malformed` (a channel/decoder limit, not new in 0.6.0); use another channel. The Redmi black-screen fix from 0.5.1 is still unconfirmed on the real phone.

**Database:** unchanged, still v4.

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
