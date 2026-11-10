# Afterglow


## Version 0.5.1 — queue editing

Removing a queued song now leaves its slot empty. Reordering, shuffling, and library edits do not automatically pull another song from the playlist backlog. Playback advancement still prepares the next portion of a long playlist.

**Clear queue** in the queue sheet clears both upcoming songs and the deferred playlist backlog, cancels preparation for removed queue items, and releases the prepared next player. It keeps the current song playing and preserves the library, favourites, playlists and cached audio. Repeat-all turns off so the cleared queue cannot rebuild itself; repeat-one is unchanged.


## Version 0.5 — library collections and normal transitions

- **Favourites:** use a song's menu to add/remove a favourite, then open the Favourites filter.
- **Sort:** added/playlist order, title A–Z or Z–A, artist A–Z, or shuffled order. Sorting changes the visible list without changing the currently playing song.
- **Play list / Shuffle play:** play the current library, favourite, playlist, or search-filtered view. Only the next ten songs enter the download queue; longer lists refill it as playback advances. Queue also has a Shuffle queue action.
- **Playlists:** create named playlists, select songs from the library, add individual songs from their menu, rename, edit membership, play/shuffle, or delete a playlist. Deleting a playlist keeps its songs and audio. Stable track IDs retain membership when online titles resolve to audio URLs.
- **Repeat:** tap to cycle Off → All → One → Off. All cycles the current playback queue/list, including the currently playing song; One loops only the current song.
- **Transitions:** crossfade/gapless overlap is removed. The outgoing song fades over its final three seconds (shorter for short songs); the next song starts after completion at normal volume. Downloads that are not ready can still cause a wait.
- Favourites, playlists, repeat mode, sorting and pending playback order are saved on the phone.

Install over an existing copy to retain app data. Uninstalling removes private settings, cached audio and encrypted keys. A previously backed-up library can be restored, but the search API key must be entered again after uninstalling.

Earlier version notes below describe previous implementations and test history.


## Version 0.5 — library collections and normal transitions

- **Favourites:** use a song's menu to add/remove a favourite, then open the Favourites filter.
- **Sort:** added/playlist order, title A–Z or Z–A, artist A–Z, or shuffled order. Sorting changes the visible list without changing the currently playing song.
- **Play list / Shuffle play:** play the current library, favourite, playlist, or search-filtered view. Only the next ten songs enter the download queue; longer lists refill it as playback advances. Queue also has a Shuffle queue action.
- **Playlists:** create named playlists, select songs from the library, add individual songs from their menu, rename, edit membership, play/shuffle, or delete a playlist. Deleting a playlist keeps its songs and audio. Stable track IDs retain membership when online titles resolve to audio URLs.
- **Repeat:** tap to cycle Off → All → One → Off. All cycles the current playback queue/list, including the currently playing song; One loops only the current song.
- **Transitions:** crossfade/gapless overlap is removed. The outgoing song fades over its final three seconds (shorter for short songs); the next song starts after completion at normal volume. Downloads that are not ready can still cause a wait.
- Favourites, playlists, repeat mode, sorting and pending playback order are saved on the phone.

Install over an existing copy to retain app data. Uninstalling removes private settings, cached audio and encrypted keys. A previously exported/backed-up library can be restored, but the search API key must be entered again after uninstalling.

Earlier version notes below describe the previous implementations and test history.

An Android music player combining a Spotify-inspired interface with classic animated music visualizations. The app uses native Android screens and audio playback, with no Afterglow account or sign-in required.

The current app is in **[`afterglow-android/`](afterglow-android/README.md)**. The `afterglow/` directory contains the earlier web prototype; it is not required to run the Android app.

## Version 0.4 improvements

- Search the library by title or artist. Library size is independent of the queue.
- Up next holds at most 10 tracks, all prepared in the background, one download at a time. Adding/importing to the library alone does not download every song.
- Swipe right to queue; swipe left to open a deletion confirmation. The menu provides the same actions. Deleting removes the library/queue entry and its downloaded cache, never the original local file.
- Reorder or remove upcoming tracks, and clear the song list in Settings. CSV/TXT imports skip duplicate URLs or normalized title/artist pairs, including duplicates within the same import.
- Repeat lives in the now-playing controls. Prepared tracks overlap with a 12-second crossfade; short tracks or late downloads may use a shorter fade or immediate handoff. Slow downloads can still cause a wait.
- Saved API key, separate EQ section, roomier song cards, and two visual modes. Record art uses embedded local artwork or YouTube thumbnails; unavailable art shows an original label.

## Features

- Local music playback and a persistent on-device queue.
- YouTube title search using a user-provided YouTube Data API key.
- On-device yt-dlp and FFmpeg audio preparation, followed by native playback—no embedded video player.
- A 3 GiB audio cache (displayed as 3 GB), download progress, cancellation, and cache cleanup.
- CSV and TXT song-list imports with duplicate skipping.
- Studio AI stem separation on your computer, with four WAV stems and ZIP export.
- A 10-band equalizer, presets, custom tuning, preamp, stereo balance, and peak limiter.
- A smooth spinning record with cover art and a smoothed Spectrum visualization.
- Afterglow, Spotify, Cyberpunk, and Arctic themes, plus a custom accent color.
- Background audio and media notification controls.

## Try the app

The development APK is generated at `afterglow-android/build/afterglow-debug.apk`. The current build supports **ARM64 Android devices running Android 9 or newer**.

1. Install the APK and open **Afterglow**.
2. Press play to try the bundled original demo, or select **Add local music** to choose audio files.
3. For title search, open **Settings**, enter a YouTube Data API v3 key, and use **Search**. Keys are encrypted on this phone and retained across restarts and updates until you delete them.
4. Select a search result to download and cache its audio. Playback begins when the download finishes. A pasted YouTube video URL does not require a search API key.
5. Open **EQ** to adjust sound, **Studio** for server-based AI stems, or **Themes** for appearance.

Only download content you are authorized to save. Availability depends on the source; some tracks may be unavailable or require verification. The app does not bypass those restrictions.

## Build and install

Required on Windows:

- JDK 17 and Node.js available on `PATH`.
- Android SDK platform 36 and build-tools 36.0.0 under `%LOCALAPPDATA%/Android/Sdk`.
- Android Debug Bridge (`adb`) on `PATH` for USB installation and tests.
- Internet access for the initial native runtime downloads.

From PowerShell:

```powershell
cd afterglow-android
./prepare-audio-engine.ps1
./build.ps1
adb devices -l
adb install -r build/afterglow-debug.apk
adb shell am start -n com.afterglow.player/.MainActivity
```

Enable USB debugging on the phone and authorize the computer when prompted. If multiple devices are connected, add `-s <device-serial>` after `adb`.

The project builds with the Android SDK command-line tools directly; Gradle is not required. Preserve `.local/debug.keystore` to install subsequent development updates over the existing app. This is a development build, not a Play Store release.

## Playlist formats

TXT accepts one song title or YouTube video URL per line:

```text
Artist - Song title
Another artist - Another song
```

CSV supports optional `title` and `artist` headers, including quoted commas:

```csv
title,artist
Song title,Artist
"Song, with a comma",Another artist
```

Imports are limited to 1 MB and 500 tracks. Imported titles are matched when played or queued and require a search API key.

## Device tests

```powershell
cd afterglow-android
./test-device.ps1
./test-device.ps1 -Runner DownloadChecks
```

The test script currently targets the connected Infinix X663 serial configured in `test-device.ps1`. Update that serial before using a different device. Tests may play audible demo audio; download checks need a network connection and use a short public test clip.

Tests passed on the Infinix X663 for native launch, playback, seeking, EQ gain changes, screen rendering, background playback, audio downloading, and cached replay. See the [native app README](afterglow-android/README.md) for detailed verification and untested cases.

## Privacy and limitations

- Local playback stays on the phone. Explicit stem separation uploads the selected audio to your configured computer/server.
- Search queries and the supplied API key are sent to Google. The key is encrypted on this phone with Android Keystore and is never passed to the downloader.
- Downloads contact YouTube and its media servers and are stored in the app's private cache.
- Audio-reactive visuals use Android's Visualizer API, which requires the microphone permission. The app captures its own playback session and does not record the microphone. Ambient visuals work without that permission.
- Android can evict cached audio under storage pressure. The cache is not a permanent music backup.
- First playback waits for downloading to finish. Each download is limited to 150 MB, and only one runs at a time.
- Extractor updates currently require rebuilding the APK. The bundled version is yt-dlp 2026.10.21.

## Project layout

| Path | Purpose |
| --- | --- |
| `afterglow-android/src/com/afterglow/player/` | Native screens, playback, visualizations, and download/cache implementation |
| `afterglow-android/AndroidManifest.xml` | App components and permissions |
| `afterglow-android/prepare-audio-engine.ps1` | Download and prepare pinned native runtimes |
| `afterglow-android/build.ps1` | Compile, package, and sign the development APK |
| `afterglow-android/tests/` | Android instrumentation checks |
| `afterglow-android/THIRD_PARTY.md` | Third-party runtime provenance and distribution considerations |
| `afterglow/` | Earlier web prototype |

Review [third-party licensing and provenance](afterglow-android/THIRD_PARTY.md) before distributing the APK beyond development testing.

## Studio: separate and export stems

Studio uses **Demucs htdemucs** on your Windows computer to produce **vocals, drums, bass, and other instruments**. No phone model download or account is required. From the project root, with Python 3.12 and ADB available:

```powershell
cd stem-server
./setup.ps1
./start-server.ps1
```

Setup is needed once. Connect the phone with USB debugging authorized before starting the server. The server runs hidden on localhost; the script configures USB forwarding and provisions a token that the app encrypts with Android Keystore.

1. Open Studio and test the server connection.
2. Select local audio or an already cached song. Prepare uncached songs through playback or the queue first.
3. Press **Separate selected track** and wait for processing and ZIP download.
4. Export through Android's file picker. Unzip the result to access the four WAV stems.

Keep the computer awake and USB connected. Rerun `start-server.ps1` after reconnecting; use `-Device <adb-serial>` for another phone. Run `stop-server.ps1` from the server folder to stop it. The first job downloads model weights to the computer; later jobs reuse them. CPU inference uses up to four threads and may take longer than playback.

Limits: one active job, 10-minute tracks, 200 MB input, and at least 1 GiB free phone storage. Audio is converted to stereo 44.1 kHz PCM16 WAV. Export before the next successful separation replaces the private phone ZIP. Server input/intermediate audio is cleaned after processing; results are deleted after download or expire unclaimed after an hour. Model weights remain on the computer. Separation can contain bleed and artifacts.

See the [server guide](stem-server/README.md) for details. This is a local development service; remote endpoints require HTTPS.

## Latest verification and test-data issue

On Infinix X663, checks passed queue limits, crossfade, imports, saved keys, background downloading, and UI rendering. Record rendering measured 179 frames over three seconds with 100 sample songs. StemChecks completed a real 12-second original-demo upload, Demucs inference, and four-WAV ZIP download. This validates the pipeline rather than subjective quality for every song.

From `afterglow-android`, the current runners are:

```powershell
./test-device.ps1 -Runner UpgradeChecks
./test-device.ps1 -Runner QueueDownloadChecks
./test-device.ps1 -Runner StemChecks
./test-device.ps1 -Runner VisualChecks
```

StemChecks requires the running server. QueueDownloadChecks requires network access. **VisualChecks left sample entries such as `Library song 78` in the personal library despite reporting a pass. The list was subsequently restored from the user-provided CSV.** Avoid running these fixture-based tests on personal data without a durable backup. Cached audio has not been removed as part of investigating this issue.

The test runner now backs up player preferences to device-private files/pre-test-player.xml before instrumentation and restores them afterward. It refuses to overwrite an outstanding backup. The safeguard was subsequently exercised in device tests; the 438-entry library matched its pre-test snapshot exactly after restoration.



## Playback controls update — 2026-10-25

- EQ now includes a saved 0–12 dB volume boost for quiet recordings. Peak limiting remains enabled while boost is above zero.
- The now-playing Previous button restarts the current song after five seconds; within the first five seconds it returns to the last played song when available. Notification and media-session Previous use the same behavior.
- Seeking after natural completion restarts playback from the chosen position. Dragging the seeker no longer competes with periodic progress updates; seeking is committed on release.
- Hold the queue's Reorder handle and drag onto another queued song to change its position. Remove remains a separate action.
- Repeat updates its label without rebuilding the page or restarting the visualization.
- Prepared-track crossfades use Android VolumeShaper equal-power envelopes in the audio engine, replacing UI-thread volume steps. Handoff retains the shared DSP session. Perceptual transition quality still needs listening checks on the target output device.

The personal song library was restored from the supplied CSV after the earlier visualization-test issue. Subsequent tests use a durable preference backup and restore; never discard an outstanding test backup before checking it.

Queue sheet update: compact artwork/title/artist rows in a bottom-aligned panel. Drag the three-line handle on the right directly to reorder; neighbouring rows animate and the list scrolls near its edges. Order commits on release; cancelling preserves it. Hold a song row to remove it. QueueSheetChecks verifies drag preview, commit, and cancellation with preference backup/restore.
