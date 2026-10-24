# Afterglow native Android

Native Java Android Views/Canvas, MediaPlayer playback, foreground downloads, MediaSession controls and a 10-band DynamicsProcessing equalizer. Android 9+ / ARM64. No account or embedded player.

## Version 0.4

- A searchable song library, separate from the **10-track maximum Up next queue**. Every uncached queued track is prepared in the background, one download at a time. Library imports alone do not trigger bulk downloads.
- Play now, Play next, Add to queue, reorder, remove from queue, and confirmed library deletion. Swipe right to queue; swipe left to confirm deletion. Cards reveal color-coded action labels during dragging, provide haptic feedback at the threshold, and animate back into place. Deletion removes cached audio but never deletes the original local media file. Settings can clear the library and queue.
- CSV/TXT import skips matching URLs and normalized title/artist duplicates, including duplicates within the imported file. Quoted CSV fields supported; 1 MB / 500-row import limits.
- **3 GiB music cache** (displayed as 3 GB), with oldest-file eviction protecting current/queued tracks. Individual downloads are capped at 150 MB.
- **12-second crossfade** between prepared tracks. Short tracks, late preparation and repeat mode can use immediate handoff or a shorter fade. Insufficient network speed can still cause a wait for uncached audio. Repeat is in the now-playing card.
- Roomier song cards and single-line, smaller navigation labels. EQ has its own section.
- Two visualizations: smoothed logarithmic Spectrum with peak markers and a 33⅓-RPM record with rotating cover art. Record artwork/grooves are cached once and rotation follows display refresh; library status updates do not rebuild song rows or scan cache files on the UI thread. Local files use embedded artwork; YouTube entries use video thumbnails. Missing art gets an original record label.
- Search API key is encrypted with Android Keystore and survives restarts/updates until deleted in Settings. A key lost by an older build must be entered once. It is sent only to Google for search, never to the downloader.
- Studio uses a **server-based four-stem Demucs model**: vocals, drums, bass, other instruments. The computer performs AI processing; the phone uploads the selected track and downloads and exports the WAV ZIP. See [server setup](../stem-server/README.md).

## Build and install

Requires JDK 17, Node.js, Android SDK platform 36 and build-tools 36.0.0. Run from PowerShell:

```powershell
./prepare-audio-engine.ps1
./build.ps1
adb install -r build/afterglow-debug.apk
```

The build uses Android SDK tools directly, without Gradle. Preserve `.local/debug.keystore` for compatible updates. Output is an ARM64 development APK; this is not a store release. Source is in `src/com/afterglow/player/`. Native dependency provenance is in [THIRD_PARTY.md](THIRD_PARTY.md).

## Studio server

Run `../stem-server/setup.ps1` once, then `../stem-server/start-server.ps1` with the phone connected. It starts a loopback-only server and configures USB reverse forwarding plus an encrypted app token. No account is needed. Keep the computer awake and USB connected; rerun the start script after reconnecting. Other endpoints can be configured in Studio and require HTTPS.

The server downloads the model on its first job. One track at a time, up to 10 minutes and 200 MB source, with 1 GiB free on the phone for temporary and export files. Selected audio is uploaded only for explicit separation. Successful server results are deleted after download; abandoned results expire after an hour. The most recent successful export stays in private phone storage until replaced. Export it before preparing another song. AI separation may contain bleed/artifacts.

## Tests

```powershell
./test-device.ps1 -Runner UpgradeChecks
./test-device.ps1 -Runner QueueDownloadChecks
./test-device.ps1 -Runner StemChecks
```

The test script targets Infinix X663 serial `083172524K003610`. Upgrade checks attempt to restore library/API-key state and use original demo audio to exercise queue cap/reordering, native search, duplicate skipping, crossfade, key persistence/deletion, deletion/cache removal, navigation, swipe confirmation and library clearing. Queue-download checks use the downloader project's public fixture. Stem checks require the local server and exercise upload, real AI inference, downloaded four-WAV ZIP output.

Earlier 0.2 device checks passed native playback, seeking, DSP gain readback, background playback, on-device audio downloading and cache reuse. Current test outcomes are recorded under `build/test/`. Long-duration screen-off behavior, every content-provider restriction and all Bluetooth devices are not covered.

## Privacy and operational notes

Local media uses document-picker URI grants. Search contacts Google; downloads contact YouTube and media servers; thumbnail art contacts YouTube's image CDN. Download only audio you are authorized to save. No verification/DRM bypass is implemented. Android may evict cached audio under storage pressure. The bundled extractor is yt-dlp 2026.10.21; updating it requires rebuilding.

Reactive visualizations require Android's RECORD_AUDIO permission because of the Visualizer API; only this player's audio session is captured, not the microphone. Ambient visuals work without permission. EQ includes preamp, balance, limiter, presets and saved custom settings. Playback responds to audio focus and headphone disconnection. Themes include Afterglow, Spotify, Cyberpunk, Arctic and custom accents.

## Verified on Infinix X663 — 2026-10-24

- UpgradeChecks: 3 GiB cache cap, 10-track queue, ordering, library search, duplicate handling, 12-second crossfade, DSP session continuity, encrypted key reload/deletion, song/cache deletion, all five screens, swipe-to-queue, confirmed swipe deletion, and one-line Themes label.
- VisualChecks: **179 record frames over 3 seconds (~60 FPS)** with 100 library songs. Record/queue screenshots are in `build/test/`. This short measurement is not a guarantee under all battery/thermal conditions.
- QueueDownloadChecks: actual public-fixture audio downloaded in the background while the original demo continued playing; next-track preload and cache deletion passed.
- StemChecks: the phone uploaded a 12-second original demo excerpt over USB, the computer ran real Demucs inference, and the phone downloaded and verified a ZIP containing vocals/drums/bass/other WAVs. This tests the pipeline, not subjective separation quality for every song.
- Server API checks: missing-token rejection, malformed WAV rejection, one-job limit, cancellation, and temporary-file cleanup passed.

The server is a hidden local process; restart with `../stem-server/start-server.ps1` after reconnecting USB. The installed development APK is debuggable to support private USB provisioning; use a non-debuggable release build and explicit settings provisioning for distribution.

## Test-data issue

VisualChecks left its sample library on the connected phone despite reporting a pass. The original list has not been recovered. Do not run fixture-based checks on personal data without a durable backup. See the [root README](../README.md) for current status.

