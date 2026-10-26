# IPTV Whiz

Lightweight IPTV player for Android TV and phones. See [PLAN.md](PLAN.md) for the full plan and roadmap.

## Build

Uses the toolchain in `F:\Android` (JDK 17, SDK 35, Gradle 8.11.1).

```bash
export JAVA_HOME="F:/Android/jdk17" GRADLE_USER_HOME="F:/Android/gradle-home"
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk` (signed with the debug key for sideloading).

## Install on a TV box

```bash
adb connect <tv-ip-address>
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Layout

All code is in the `:app` module for now, split by package. It can be broken into the modules in PLAN.md §3 once there is enough code to justify it.

- `data`: Room database, M3U parser, Xtream client, playlist repository
- `player`: Media3 ExoPlayer set up for live IPTV (retries, HLS fallback, friendly errors)
- `ui/state`: ViewModels shared by the TV and phone UIs
- `ui/tv`: 10-foot UI (Compose for TV)
- `ui/mobile`: phone/tablet UI (Material 3)
- `ui/common`: shared pieces (logos, messages, video surface, file picker)
- `ui/theme`: brand tokens and both themes

## Database changes

After changing an entity, bump the version in `AppDatabase`, build once (Room writes `app/schemas/<version>.json`), then run `python tools/gen_migration.py .` to regenerate `SchemaMigrations.kt`. It handles new tables, indices, nullable columns and views; wire the step (plus any data moves) into the hand-written `Migrations.kt`.

## Test

```bash
./gradlew testReleaseUnitTest
```

## More

- [CHANGELOG.md](CHANGELOG.md): what each version changed.
- [CLAUDE.md](CLAUDE.md): working notes for agents (environment, emulators, conventions, next steps).

Free, legal playlists for manual testing are listed in PLAN.md ("Free, legal test sources"). To try `testdata/test-playlist.m3u` on an emulator, serve the folder (`python -m http.server 8765` inside `testdata`) and add `http://10.0.2.2:8765/test-playlist.m3u` as an M3U link.
