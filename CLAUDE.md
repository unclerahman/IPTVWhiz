# IPTV Whiz: guide for agents

A lightweight, TiviMate-style IPTV **player** for Android TV (D-pad first) and phones (touch). The user adds a provider link (M3U or Xtream login), then watches, edits, hides, sorts and groups channels, and builds watchlists across playlists. No content is bundled.

Read these first:
- [PLAN.md](PLAN.md): goals, architecture, decisions (§14), roadmap (§13), per-milestone notes, and the watchlists/search/health design (§18).
- [CHANGELOG.md](CHANGELOG.md): what each version shipped and fixed.
- [README.md](README.md): build, install, test.

## Owner's decisions (don't relitigate)

- **Name and package:** IPTV Whiz, `com.iptvwhiz.app`.
- **Distribution:** direct APK now, Play Store later, so follow Play rules already.
- **Price:** free now, non-intrusive ads later. Never show ads during playback, and leave `AdSlot` as a no-op.
- **Scope:** live TV only; no VOD/series yet.
- **Sync:** backup file only (option A, PLAN §15).
- **Favorites:** the built-in first watchlist (id 1). Many watchlists are allowed.
- **Legal:** test only with legal free sources (PLAN §12 "Free, legal test sources": Free-TV/IPTV, iptv-org, Apple/Mux test streams, `testdata/test-playlist.m3u`).
  - Don't fetch, test against, or add features for pirate lists.
  - In particular, no scraping playlists out of web pages: the owner once sent a Blogger page with paid channels pasted in, and it was declined.

## Build and run (Windows, Git Bash)

```bash
export JAVA_HOME="F:/Android/jdk17" GRADLE_USER_HOME="F:/Android/gradle-home"
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk (R8 on, signed with debug key)
./gradlew testReleaseUnitTest      # JVM tests (78 at 0.6.6)
```

- **Toolchain:** `F:\Android` (jdk17, sdk with platform 35, gradle 8.11.1). minSdk 24, targetSdk 35.
- **Shipping a build:**
  1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
  2. Copy the APK to `dist/IPTVWhiz-<version>.apk` (gitignored).
  3. Add a CHANGELOG entry.
- **adb from Git Bash:** prefix with `MSYS_NO_PATHCONV=1`. adb is `/f/Android/sdk/platform-tools/adb.exe`.
- **Emulators** (AVDs in `F:/Android/avd`; set `ANDROID_AVD_HOME=F:/Android/avd ANDROID_SDK_ROOT=F:/Android/sdk`):
  - TV: `emulator -avd IPTVWhiz_TV_API34 -port 5556 -gpu host -no-snapshot-save -no-audio`
  - Phone: `emulator -avd IPTVWhiz_Phone_API35 -port 5558 -gpu host -memory 2048 -no-snapshot-save -no-audio`. It needs the extra memory; below that it ANRs.
  - **`PocketTavern_API35` (emulator-5554) belongs to another project. Never use or stop it.** Stop ours with `adb -s emulator-5556 emu kill`.
  - `-no-snapshot-save` boots from the old snapshot every time, so **app data you change in a session is lost on the next boot**. Don't mistake that for a persistence bug.
- **Driving the TV:** `adb shell input keyevent KEYCODE_DPAD_*`. Hold OK with `--longpress KEYCODE_DPAD_CENTER`; `KEYCODE_MENU` opens the menu. Screenshot with `adb exec-out screencap -p > file.png`.
- **Local test playlist:** run `python -m http.server 8765` in `testdata/`, then use `http://10.0.2.2:8765/test-playlist.m3u` on the emulator.

## Architecture in one screen

- **One `:app` module, one APK:** the launcher picks the TV UI (`ui/tv`, androidx.tv-material 1.0) or the phone UI (`ui/mobile`, Material 3) via `DeviceType`. ViewModels in `ui/state` are shared.
- **Themes:** `ui/theme/Brand.kt` holds the shared tokens; `TvTheme.kt` (always dark, tv-material) and `MobileTheme.kt` (dark-only by default, light scheme kept for a future Appearance setting) both read them, so the two layouts stay in step. `res/values/colors.xml` mirrors the brand colours for the launcher and splash.
- **Hand-wired DI:** `AppContainer` holds the db, http, repositories, `playQueue` and `USER_AGENT` (VLC's; many servers 403 other agents). ViewModels are created with `appViewModel { container, savedState -> … }`.
- **Data (`data/`):**
  - Room 2.6.1 with KSP; schemas are exported to `app/schemas`. Currently **v5**.
  - **The overlay model is the core idea.** Provider data (`channels`) is replaced on every refresh. User edits live in `channel_overrides` / `group_settings`, keyed by a **stable key** (`StableKeys.kt`), and are joined in the `channel_view` DatabaseView (`Overlay.kt`). That is why edits survive refreshes. `ChannelOrganizer` turns the view into groups and order.
  - Order uses fractional positions (`Positions.between`), so a move touches one row and merges cleanly.
  - Watchlists: `watchlists` + `watchlist_items` (playlistId, stableKey), using `ChannelRef` and `WatchlistRepository`. `FAVORITES_ID = 1` is built in.
  - Health: `channel_health` and `HealthRepository` (probe + passive recording); `HealthProbe.classify` is pure and tested.
  - Search: `ChannelSearch` (pure, tested); `Channel.effectiveCountry`.
  - EPG: `data/epg`. `XmltvParser` (streaming), `EpgRepository` (max 10 links, `selectLinks`), daily `EpgWork`.
  - Backup: `data/backup`. JSON file, playlists matched by fingerprint, newest edit wins, format version 1 with optional fields.
  - DRM: `data/drm/DrmInfo.kt` (pure, tested) parses Kodi `#KODIPROP` `license_type`/`license_key`; `M3uParser` attaches a `DrmInfo` to the next stream link, whether the block sits above or below `#EXTINF` (Kodi semantics; 0.6.2 fixed the above-`#EXTINF` case). Four nullable `drm*` columns on `channels`, exposed by `channel_view`. `PlaylistRepository.PARSER_VERSION` + `AppPrefs.parserVersion` trigger a one-time re-import after a parser change (0.6.5), so old rows cannot stay without their DRM data.
  - Parsers: `M3uParser` (only accepts `scheme://` lines as links) and `XtreamClient`.
- **Player (`player/LivePlayer.kt`):** Media3 1.5.1 ExoPlayer with OkHttp, HLS and DASH.
  - Per-channel user agent and referrer, short timeouts, retries with backoff, HLS fallback for links without `.m3u8`.
  - Errors are mapped to `PlaybackProblem`.
  - DRM: a custom `DrmSessionManagerProvider` on `DefaultMediaSourceFactory`. ClearKey keys from the playlist use `LocalMediaDrmCallback`; license links use `HttpMediaDrmCallback` with the playlist's headers; provider is `FrameworkMediaDrm.DEFAULT_PROVIDER`. `PlaybackProblem.Drm` covers the missing-key case and Media3's 6000-range errors; DRM failures are not recorded as Offline. `PlaybackProblem.NoVideo` is the silent case: a 20 s first-frame watchdog (`noVideoWatchdog`), cancelled by `onRenderedFirstFrame`, skipped for streams with no video track. Since 0.6.5 the watchdog also checks the tracks' `drmInitData`: an encrypted stream with no DRM data in the playlist is reported as `Drm` with `drmFailureDetail(null)`, and `PlayerViewModel.recoverMissingKey()` refreshes the playlist and replays the channel. Since 0.6.6 an *unusable* DRM line (`drm != null && !drm.isValid`) never blocks the load: the stream is always tried, so a clear channel with a stray `#KODIPROP` plays, and `onTracksChanged` reports `Drm` with `drmMissingDetail()` as soon as encrypted tracks appear (`drmFailureDetail()` returns that first for unusable data). The app never extracts or guesses keys (see the DrmInfo doc comment).
  - Video goes through `ui/common/PlayerSurface.kt`: SurfaceView on TV, TextureView on phone (black-screen fix for Xiaomi).
  - `StreamStats` + `setStatsEnabled()` feed the optional quality chip (`ui/common/QualityBadge.kt`): resolution/FPS from decoder counters, Mbps from a `TransferListener`, sampled once a second only while switched on.
- **Viewing options:** `AppPrefs.showQuality` / `AppPrefs.hideBlocked`, edited in Settings → Viewing options (`ui/tv/TvViewingScreen.kt`, `ui/mobile/MobileViewingScreen.kt`, `ViewingViewModel`). Hide blocked is applied in `LiveViewModel`, `SearchViewModel` and `PlayerViewModel` (zapping) by `OrganizedPlaylist.without(blocked)`; watchlists and Manage keep blocked channels.
- **Player sources:** a route `player?src=&playlistId=&group=&key=` with `SOURCE_GROUP`, `SOURCE_WATCHLIST` and `SOURCE_QUEUE`. The queue holds search results via `container.playQueue`. See `ui/Destinations.kt`.

## Conventions and gotchas

- **Database change:**
  1. Edit the entity and bump `AppDatabase.version`.
  2. Build once; Room writes `app/schemas/…/<n>.json`.
  3. Run `python tools/gen_migration.py .`, which regenerates `data/db/SchemaMigrations.kt`. It supports new tables and indices, nullable ADD COLUMN and view changes, and aborts on anything else.
  4. Wire the new step in the hand-written `data/db/Migrations.kt` (data moves go there) and add it to `Migrations.ALL`.
- **strings.xml:** apostrophes must be escaped. After editing, run `python tools/escape_apostrophes.py app/src/main/res/values/strings.xml`. Counts use `plurals`.
- **Scripts:** Git Bash heredocs mangle backslashes and nested quotes (`\n`, `"""`). Write Python scripts to a file with the Write tool, or use the Edit tool.
- **TV focus:** use `requestFocusWhenReady()` (`ui/common/Focus.kt`), `focusRestorer`, and explicit `FocusRequester`s where spatial search skips a row (see the search screen).
  - When a button disappears, move focus on deliberately, or it falls to the side rail (see `TvHealthBar`).
  - Rows that move use `Modifier.moveKeys` / `movingBorder` (`TvWatchlistsScreen.kt`).
- **TV dialogs and inputs:** `TvDialog`, `TvMenuDialog` and `TvChannelEditor`. `TvTextField` needs OK to start editing.
- **Android TV has no real document picker**, only a stub. `hasDocumentPicker()` filters it out (a `<queries>` element is in the manifest).
- **Writing style for UI text and docs:** plain, short, friendly. The owner is non-technical, so describe things in user terms.

## Current status (0.6.6)

| Milestone | Status |
|---|---|
| M0 skeleton | ✅ Done |
| M1 import & play | ✅ Done |
| M2 TV player UX | ◐ Overlay, zapping, number entry and quick menu are done. Still missing: audio/subtitle track picker and the "inverse channel keys" setting. |
| M3 manage | ✅ Done |
| M4 EPG basics | ✅ Done |
| Backup | ✅ Done |
| M5 polish | ◐ Search, watchlists, health, the viewing options (quality chip, hide blocked, phone full screen) and DRM playback are done. Still missing: Settings → Player and Appearance (still "coming soon"), store assets, release signing. |

**0.6.6 (2026-10-30):** fixes the FAST channels that 0.6.5 broke. Their playlist writes `#KODIPROP` lines on clear channels, and some of those lines are unusable (ClearKey type with a key that is not a key, Widevine type with no license link). 0.6.5's `LivePlayer.load()` returned early when `drm != null && !drm.isValid`, so those channels showed the copy-protection card and never touched the network. The early return is gone: the stream is always tried (`drmSessionManager()` still returns `DRM_UNSUPPORTED` for unusable data), so a clear channel plays. The card only appears for streams that really are encrypted: `onTracksChanged` checks `drmInitData` and reports `Drm` with `drmMissingDetail()` at once, and the watchdog has the same `protected && !drm.isValid` branch; `drmFailureDetail()` now returns `drmMissingDetail()` first for unusable data, so the small line blames the playlist, not the device or a license server. New `testdata/spurious-drm-test.m3u` (7 clear channels with stray DRM + 1 real encrypted no-key) is pinned by `M3uParserTest`. 78 JVM tests. Verified on the phone emulator: all clear channels played, the encrypted one explained itself in ~2 s, TV3 SD still played, the diagnostics playlist was unchanged. See CHANGELOG and PLAN §20 notes.

**0.6.5 (2026-10-29):** fixes the owner's TV3 SD. Their stored channel had no DRM columns (imported before 0.6.2 and never refreshed), so a protected stream reached the no-picture card. Two recovery paths: (1) `AppPrefs.parserVersion` + `PlaylistRepository.PARSER_VERSION` (2) + `refreshIfParserChanged()` called from `IptvWhizApp.onCreate` re-imports every playlist once after a parser change; (2) `PlayerViewModel.recoverMissingKey()` watches for `PlaybackProblem.Drm` on a channel whose `drmInfo` is null, refreshes that playlist once and replays the channel from the rebuilt `loadChannels()` list. `LivePlayer`'s 20 s watchdog now checks `currentTracks` for `drmInitData`, so "encrypted but the playlist has no key" is `Drm` with `drmFailureDetail(null)`, not `NoVideo`; the NoVideo copy no longer guesses at copy protection. User edits survive (overlay rows by `stableKey`). Database stays v5; parser version is a `SharedPreferences` int. 76 JVM tests. Verified on the phone emulator by clearing the DRM columns (both the update path and the play-time path healed and TV3 played), plus the diagnostics playlist and a TV smoke test. See CHANGELOG and PLAN §20 notes.

**0.6.4 (2026-10-28):** phone UI polish only — no behaviour, settings or database changes. New shared design tokens in `ui/theme/Brand.kt` (midnight indigo `#1E1B4B`/`#4338CA`, background `#0F0F23`, live green `#22C55E`), used by both `MobileTheme.kt` and `TvTheme.kt` so the two layouts stay in step; `colors.xml` matches. Phones are now **dark-only** (`IptvWhizMobileTheme(darkTheme = true)`, `SystemBarStyle.dark`), like TV. Live TV gained a provider header, count chips with All channels first, loading/empty states, a number badge, favourite heart and `HealthBadge` (Offline/Blocked only); Settings is grouped into Setup / Playback / App & data with icon tiles, chevrons and Soon pills, and About shows the version; the player's error card is compact for the portrait video area, with "Tuning in…", a LIVE pill, `#number · group` and a current-row accent bar. Inter is not bundled (system font with tuned tracking/weights instead). 73 JVM tests, unchanged. See CHANGELOG and PLAN notes for 0.6.4.

**0.6.3 (2026-10-28):** the copy-protection card now says why: it names the DRM system the playlist asked for and the actual reason (`clearkey · the playlist key doesn't match this stream's key ID`, `clearkey · no key in the playlist`, `widevine · the license server said no (HTTP 405)`, `widevine · this device has no widevine module`). `DrmDiagnostics.kt` builds the text; `DrmKeyIds` reads key *IDs* (never keys) from the playlist's ClearKey JSON and the device's key request, so a re-keyed provider is named instead of guessed. The no-picture watchdog prefers the key-ID message when it has one; `httpStatus()` walks the cause chain and `toProblem()` checks DRM codes first. Verified on the phone emulator with `testdata/drm-diagnostics-test.m3u` (stale key, license 405, missing key, working control). Database unchanged at v5. 73 JVM tests. See CHANGELOG and PLAN §20.

**0.6.2 (2026-10-27):** fixed the remaining black-screen channels. Some playlists (the owner's own list among them: 45 of 214 entries) put the `#KODIPROP` DRM block *above* `#EXTINF`; 0.6.1 cleared the DRM state at `#EXTINF`, so those channels lost their key and stayed black with no error. `M3uParser` now keeps the block for the next stream link (Kodi's rule) and clears it after the channel is emitted. Also added `PlaybackProblem.NoVideo`: a 20 s first-frame watchdog shows "This channel isn't sending a picture…" with **Try again** instead of a silent black screen; cancelled by `onRenderedFirstFrame` and skipped for audio-only streams. Verified on the phone emulator with the real TV3 SD stream in all three line orders (`testdata/tv3-test.m3u`): before `#EXTINF` played `720×540 · 23 fps · 0.6 Mbps`, no DRM lines showed the new card. Database unchanged at v5. 63 JVM tests. See CHANGELOG and PLAN §20.

**0.6.1 (2026-10-26):** DRM playback for Kodi-style ClearKey and Widevine/PlayReady playlists (`#KODIPROP`), so protected channels play instead of a blank black screen; a friendly copy-protection card when the key/license doesn't work. Database v5 (four nullable DRM columns; v4 → v5 migration verified with an existing 0.6.0 database). Verified on the phone emulator with Google's Shaka demo assets — ClearKey key, ClearKey license server, Widevine license server and plain HLS all played. 61 JVM tests. See CHANGELOG and PLAN §20.

**0.6.0 (2026-10-26):** quality overlay (phone top bar, TV quick menu), Settings → Viewing options with "Show video quality" and "Hide blocked channels", and a phone full-screen button that forces landscape. Verified on both emulators; see CHANGELOG for the numbers and PLAN §19 for the design. Database unchanged at v4. 50 JVM tests.

## Next steps (in rough priority)

1. **Confirm the Redmi black-screen fix** with the owner.
   - If video is still black, ask whether sound plays and whether the spinner shows.
   - The new quality chip is a first diagnostic: if it shows numbers while the screen is black, the decoder is running and the problem is the surface.
   - Consider adding the quality chip to the "Playback info" idea in the quick menu (state, codec, decoder, last error) to diagnose devices remotely.
2. **Ask the owner about 0.6.4:** whether the dark-only phone UI and the grouped Settings feel right, and whether a light theme is wanted (Appearance is still "Soon"; the light colour scheme is already written in `MobileTheme.kt`).
3. **Ask the owner about 0.6.6:** whether the FAST channels that showed "This channel is copy-protected…" play again, and whether TV3 SD still plays. The fix only changes what happens when the playlist's DRM line is unusable; no refresh or action should be needed. If a channel still fails, ask for the small line under the message (it names the DRM system and reason) and the channel name.
4. **Ask the owner about 0.6.0:** whether the quality chip shows what they expect on real hardware (some boxes report no FPS) and whether hide-blocked should also apply to watchlists.
5. **"Check channels" in Live TV:** add it to the group long-press menu. The owner expected it there; it was offered but not built yet.
6. **TV player Back:** on the error card, Back sometimes needs an extra press.
7. **Settings:**
   - Player: buffer size, decoder preference, aspect ratio, inverse channel keys.
   - Appearance: theme, text size.
   - Per-playlist user agent.
8. **M2 leftovers:** audio and subtitle track picker.
9. **DRM later, if asked:** show the DRM scheme (and that a channel is protected) in Manage channels, and let the user repair a license link for one channel. Do not add key extraction.
10. **Before Play Store:**
    - Release keystore (keep it out of git; `*.keystore` and `keystore.properties` are ignored).
    - Move provider passwords from plain Room to Keystore-backed encryption.
    - Privacy policy; ads consent (UMP) when ads arrive; TV checklist.
11. **Phase 2 (M6):** full guide grid, catch-up, PIN lock, a "set up from your phone" local web page for TV (PLAN §9).
12. **Known limit:** the health probe can say Working when the playlist file answers but its segments are geo-blocked.
