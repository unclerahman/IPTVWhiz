# Changelog

All versions so far are sideloaded APKs (`dist/IPTVWhiz-<version>.apk`, not in git), signed with the debug key.
Dates are build dates. The milestone numbers (M0–M6) refer to the roadmap in [PLAN.md](PLAN.md) §13.

## 0.6.5 (2026-10-29)

**Fixed**
- **A playlist imported by an older version is re-imported once, automatically.** Some channels were stored without their DRM details (playlists that write the `#KODIPROP` lines *before* the channel line were only handled from 0.6.2), so a protected channel could open to a black screen even though the playlist had its key. After updating, the app re-downloads each playlist once in the background; renames, hidden channels, groups and order are kept because they live separately from the provider's rows. A playlist that cannot be downloaded is tried again on the next start.
- **A protected channel with no key in the playlist now heals itself while you watch.** If a channel is encrypted but the stored playlist has no DRM data for it, the player refreshes that playlist once and replays the channel with the key it finds. This also covers the case where the app was already open when the update was installed.
- **The "isn't sending a picture" card no longer guesses.** When the stream is encrypted but the playlist says nothing about a key, the copy-protection card now names that reason ("the stream is copy-protected, and the playlist has no key for it") instead of suggesting the channel may be down. The card's wording was shortened; it no longer asks people to check a key by hand, because the app now refreshes the playlist for them.

**Notes**
- Database stays at v5: the parser version is a single value in the app's preferences, not a per-playlist column.
- Nothing about keys changed: the app still only uses DRM data from the user's own playlist and never extracts or guesses a key.

**Tests:** 76 JVM tests (3 new for the upgrade decision).

**Verified on the phone emulator (Android 15, 2026-10-28):**
- Simulated the owner's phone by clearing the DRM columns of a stored TV3 SD channel and updating from 0.6.4: the app re-imported the playlist on start and TV3 SD played (ClearKey session opened, LIVE, picture).
- Repeated with the parser version already current, so no start-up refresh: opening the stale channel refreshed the playlist while playing and TV3 SD played.
- The no-key channel now shows the copy-protection reason above instead of the no-picture card.
- `testdata/drm-diagnostics-test.m3u` still reports `clearkey · the playlist key doesn't match this stream's key ID`, the license server's HTTP status, and `clearkey · no key in the playlist`; its control channel played.

## 0.6.4 (2026-10-28)

**Improved**
- **A new look for the phone app.** The palette, type and spacing were reworked around a dark, low-glare design (deep navy background, indigo accents, a green "live" accent). The phone app is now dark-only, like the TV app: the video is the brightest thing on screen, and the status bar icons stay readable either way.
- **Live TV is calmer and easier to scan.** A "Live TV" header with your provider's name underneath, **All channels** first in the chip row with the number of channels on every chip, a proper loading state, and friendly icon empty states. Each row shows the channel number, a heart for favourites and, after a check, an **Offline** or **Blocked** label. Dividers start after the logo, and the group name under each channel is hidden when the whole list is from one group.
- **Settings is grouped and easier to read.** Three sections (**Setup**, **Playback**, **App & data**), tinted icons, chevrons on the rows that open something, and a **Soon** pill on Player and Appearance instead of a toast. About is now a plain information row that shows the version, with no dead tap.
- **The player explains failures without covering the picture.** The error card is compact enough to sit inside the portrait video area: a warning icon, the reason, the small diagnostics line, and **Try again** / **Next channel** side by side. While a channel is tuning it says "Tuning in…". The now-playing panel adds a **LIVE** pill and `#number · group`, the playing row in the channel list has an accent bar, and the quality badge and side zap buttons step aside when an error is showing.
- **Watchlists, Playlists and Search match the new style.** The filter chips look the same in Search and Live TV, playlist titles line up whether or not a playlist is active, and the "Check channels" summary puts its actions on their own line instead of running off the screen.
- **Logo tiles read better:** initials skip a leading number ("1 TV3…" now shows **TS**, not **1T**), and the fallback colours are deeper so a screen full of them stays quiet.
- The TV app shares the new colours and stays dark, as before; its layout is unchanged.

**Notes**
- The design tool suggested the Inter font. It is not bundled (about 0.5 MB): the system font is used with a tuned size/weight/tracking scale instead, which keeps the APK small and native.
- No behaviour, settings or data changed. Database stays at v5.

**Tests:** 73 JVM tests, unchanged (this release has no logic changes).

**Verified on the phone emulator (Android 15, 2026-10-28):** Live TV with counts and the Offline badge, Guide empty state, Settings (all three sections, Soon pills, About version), Playlists, Watchlists and the watchlist contents, Manage channels and a group, Search results, the add-playlist screen, and the player's tuning and error states.

## 0.6.3 (2026-10-28)

**Improved**
- **The copy-protection card now says *why*.** Instead of a vague "its key didn't work", the small print names the DRM system the playlist asked for and the actual reason:
  - `clearkey · the playlist key doesn't match this stream's key ID` — the provider changed the key; refreshing the playlist usually fixes it.
  - `clearkey · no key in the playlist` — the channel has DRM lines but no usable key.
  - `widevine · the license server said no (HTTP 403)` — the license server answered and refused; the status is shown.
  - `widevine · this device has no widevine module` — the stream needs a DRM system this device does not have.
  - `playready · the stream doesn't offer playready` — the playlist names a system the stream doesn't use.
- **The "isn't sending a picture" watchdog now gives the better reason when it has one.** If a protected channel never shows a picture because the device asked for a key ID the playlist doesn't contain, you get the key-ID message above, not the generic one.
- Nothing is extracted or guessed: the card only compares key *IDs* (never keys) and reports the server's own answer.

**Tests:** 73 JVM tests (10 new for the diagnostics text and key-ID reading).

**Verified on the phone emulator (Android 15, 2026-10-28)** with `testdata/drm-diagnostics-test.m3u`:
- Real TV3 SD stream with a deliberately stale key ID: `clearkey · the playlist key doesn't match this stream's key ID`.
- Widevine stream pointed at a license URL that refuses: `widevine · the license server said no (HTTP 405)`.
- DRM lines with a broken key: `clearkey · no key in the playlist`.
- Control channel with a working ClearKey key: played, `768×576 · 8 fps · 1.8 Mbps`.

## 0.6.2 (2026-10-27)

**Fixed**
- **Channels whose playlist writes the DRM lines *before* the channel line now play.** Some providers (including the list behind `https://bit.ly/fasgun`) put the `#KODIPROP` block above `#EXTINF` instead of between `#EXTINF` and the stream link. 0.6.1 threw those details away when it read `#EXTINF`, so the channel lost its key and stayed black. Kodi applies a `#KODIPROP` block to the next stream link, and the app now does the same. 45 of the 214 channels in that playlist were written this way, TV3 SD among them.
- **A channel that never shows a picture now says so.** If nothing has been drawn after 20 seconds and the player has not reported an error, it shows "This channel isn't sending a picture..." with **Try again**, instead of a silent black screen. That is what a copy-protected stream looks like when the playlist carries no key for it. Audio-only channels are left alone, and a channel that starts normally never sees the message.
- Refreshing a playlist is enough to pick up the fix for channels you already imported; there is no need to delete and re-add it.

**Tests:** 63 JVM tests (2 new: DRM lines before `#EXTINF` attach to the next channel, and a DRM block does not leak to later channels).

**Verified on the phone emulator (Android 15, 2026-10-27)** with `testdata/tv3-test.m3u` (the real TV3 SD stream, three line orders):
- DRM lines before `#EXTINF`: the key was stored (`clearkey`) and the channel played, `720×540 · 23 fps · 0.6 Mbps`.
- DRM lines after `#EXTINF`: played as in 0.6.1.
- The same stream with its DRM lines removed: after 20 seconds the new "isn't sending a picture" card appeared with **Try again**, instead of a black screen that never explains itself.

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
