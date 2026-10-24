# Afterglow

Android-first installable web music player. Serve `dist/` over HTTPS, or run `node server.cjs` for localhost development.

## Features
- Local audio selection, original synthesized WAV demo, queue, seek, repeat, Media Session controls.
- Web Audio 10-band peaking equalizer (±12 dB), preamp, stereo balance, compressor, presets and saved custom tuning.
- Audio-reactive nebula, spectrum and oscilloscope. YouTube visualization is ambient, not audio-reactive.
- Spotify, Cyberpunk, Arctic and Afterglow themes plus custom accent colors.
- TXT: one title or YouTube URL per line. CSV: optional title and artist headers, quoted commas and multiline fields. Up to 500 rows, 1 MB.
- YouTube URL playback through the visible official iframe. Title search and imported title matching require a user-provided YouTube Data API v3 key. Matching happens when a queued title is played. Keys are session-only; restrict browser keys by referrer and API.
- PWA manifest and offline app shell. Imported audio is never uploaded; files need reselection on reload.

## Limits
This is a PWA, not a native APK. Android device installation and background playback need physical-device validation. YouTube availability depends on embedding permissions, connectivity and API quota. EQ cannot process the cross-origin YouTube iframe. No audio extraction/downloading is implemented. Offline cache covers app assets, not YouTube or selected local files. Browser or Android power management can suspend playback.

## Verification
JavaScript syntax checked; browser demo playback and interactive EQ/theme controls checked. No YouTube API credential was supplied, so live search and matching were not end-to-end verified.
