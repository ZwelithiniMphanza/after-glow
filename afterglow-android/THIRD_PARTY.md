# Native audio runtime

The ARM64 development APK includes native binary payloads and the yt-dlp Python bundle from Maven Central artifacts:

- `io.github.junkfood02.youtubedl-android:library:0.18.1`
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`

The bundled extractor is replaced with official yt-dlp 2026.10.21, verified SHA-256 `b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14`, from https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.21. The preparation script generates a symbolic-link manifest from ZIP metadata; Android recreates those links inside the validated private runtime directory.

Upstream: https://github.com/yausername/youtubedl-android (GPL-3.0). Build recipes and component provenance are in `BUILD_PYTHON.md` and `BUILD_FFMPEG.md` in that repository. FFmpeg source and licensing: https://ffmpeg.org/legal.html. yt-dlp source: https://github.com/yt-dlp/yt-dlp. Python: https://www.python.org/psf/license/. QuickJS: https://bellard.org/quickjs/.

Only ARM64 payloads are packaged in this development build. Java runtime integration is implemented by Afterglow; no upstream Java/Kotlin wrapper classes are included. Runtime archives are unpacked into the app's private no-backup directory with canonical-path validation. Native executable files are installed by Android from the APK. No shell commands, browser cookies, user API keys, or authentication tokens are passed to the downloader.

This build is for device testing. Before wider distribution, bundle all applicable component license notices and corresponding source/build materials required by the binary licenses, and review platform/provider distribution requirements. The preparation script pins Maven versions and prints archive SHA-256 hashes for provenance.

## Server AI separation

The Android APK does not include an AI runtime/model. The local `stem-server` installs Demucs 4.0.1 (MIT), CPU PyTorch/torchaudio 2.5.1 and SoundFile 0.13.1. Demucs downloads its `htdemucs` pretrained model on the computer on the first job. Sources: https://github.com/facebookresearch/demucs, https://github.com/pytorch/pytorch, https://github.com/pytorch/audio, https://github.com/bastibe/python-soundfile. See their component notices before redistribution.
