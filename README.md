# Parabolic for Android

Full Android port of Parabolic (Steps 1–4 complete), combining the `yt-dlp` + `FFmpeg` + `aria2c` native backend with an Expressive Material 3 adaptive Jetpack Compose application for phones, tablets, foldables, and Android Laptop / desktop windows.

## Build

Use JDK 17, Android SDK platform 36, and the checked-in Gradle 8.13 wrapper. Set `ANDROID_HOME` or put `sdk.dir=/your/android/sdk` in an untracked `local.properties`.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The supported baseline is Android 8 (API 26), targeting API 36. Dependencies are pinned in `gradle/libs.versions.toml`: Kotlin 2.2.21, AGP 8.13.2, Material 3 1.4.0 (`MaterialExpressiveTheme` + `MotionScheme.expressive()`), Adaptive 1.2.0 (`NavigationSuiteScaffold` + `NavigableListDetailPaneScaffold`), Hilt, Room, Preferences DataStore, WorkManager, and `youtubedl-android` 0.18.1 with FFmpeg and aria2. Native executables are extracted through `jniLibs.useLegacyPackaging`; no broad storage permission is requested. All four upstream ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) are enabled.

## Architecture & Modules

- `core`: Android-independent domain contracts, validated download options, `yt-dlp` argument generation, full metadata and structured progress parsing, log redaction, and JVM tests.
- `app/.../engine`: Hilt-bound `AndroidYtDlpRepository`, lazy native initialization, execution/cancellation, credential snapshots, and serialized binary updates.
- `app/.../data`: Room queue/history/log/export records (`DownloadDatabase`), DataStore settings (`PreferencesRepository`), and persistent WorkManager scheduling (`QueueRepository`).
- `app/.../work`: Foreground download worker (`DownloadWorker`), progress notifications, pause/cancel recovery, and configurable 1–4 concurrent jobs (`DownloadGate`).
- `app/.../storage`: Private staging (`StagingStore`), Netscape cookie import and WebView session formatting (`CookieStore`, `NetscapeCookieFormatter`), SAF folder grants, and MediaStore/SAF publishing with per-file URI journals (`OutputPublisher`).
- `app/.../ui`:
  - `ParabolicTheme`: Material 3 Expressive theme with dynamic color support, `MotionScheme.expressive()`, custom vector iconography (`ParabolicIcons`), and edge-to-edge layout.
  - `ParabolicAppShell`: Adaptive `NavigationSuiteScaffold` switching automatically between bottom bar and navigation rail/drawer, desktop keyboard shortcuts (`Ctrl+N` to add a download, `Esc` to dismiss), multi-window drag-and-drop URL intake (`Modifier.dragAndDropTarget`), and API 33+ notification permission handling.
  - `DownloadsWorkspace` & `AddDownloadSheet`: `SharedTransitionLayout` hero transition between the queue header card and the Add Download sheet, coupled with `NavigableListDetailPaneScaffold` for side-by-side queue list and job detail + live sanitized log viewing.
  - `AuthScreen` & `SettingsScreen`: Netscape `cookies.txt` picker, isolated HTTPS WebView sign-in with automatic Netscape cookie capture, concurrency (1–4 jobs), speed limit / proxy / `aria2c` configuration, SAF folder selection, and `yt-dlp` engine updater.

## Features

- **URL Inspection & Playlist Control**: `getInfo` runs `--dump-single-json --flat-playlist` with process-ID cancellation support. Single items expose all video/audio stream formats, audio languages, chapters, and subtitle tracks. Flat playlists/collections allow selecting individual entries to enqueue or inspecting a single entry's formats.
- **Format & Post-Processing Options**: Supports `MP4`, `WEBM`, `MKV` (merged via MKV and recoded) and `MP3`, `OPUS`, `FLAC`, `WAV`, `M4A`, resolution caps or explicit stream IDs, audio track language, time-range clipping (`--download-sections` with `--force-keyframes-at-cuts` and one-tap chapter bounds), subtitle languages/format/embedding, metadata/chapters, cover art, chapter splitting, and SponsorBlock segment removal.
- **Scoped Storage & Credentials**: Native tools stage files in `noBackupFilesDir/downloads/<UUID>` and publish finished files to `Downloads/Parabolic` via MediaStore (API 29+) or a user-selected SAF tree (API 26+). Cookies live strictly in `noBackupFilesDir/cookies.txt` and are snapshotted per job.

This is an independent Android port, not an official NickvisionApps release.
