# Afterglow stem server

Runs **Demucs htdemucs** on this Windows computer and returns four PCM WAV stems: vocals, drums, bass and other instruments. No cloud account is required. The Android app uploads a selected local/cached track only when you press **Separate selected track**.

## Setup and use

Python 3.12 and ADB are required. The setup script detects Codex's bundled Python when available.

```powershell
./setup.ps1
./start-server.ps1
```

The server runs hidden in the background, listens only on `127.0.0.1:8765`, and connects to the development APK through `adb reverse`. The start script provisions a random authentication token directly into private app storage; the app then encrypts it with Android Keystore. No token is printed. In **Studio**, press **Test server connection**, select a song, and press **Separate selected track**. When ready, export the ZIP using Android's file picker.

Keep this computer awake and the USB cable connected. Rerun `start-server.ps1` after reconnecting USB or restarting the computer. Use `-Device <adb-serial>` for another phone. This script requires the debug APK because provisioning uses `run-as`.

```powershell
./stop-server.ps1
```

The first separation downloads the model weights on the computer into `.models/`. Subsequent jobs use the saved model. CPU mode uses up to four threads; processing time depends on track length and computer performance. GPU setup is not required.

## Limits and privacy

- Authenticated requests only; one active job, up to 10 minutes / 200 MB PCM WAV input. A second job receives a busy response.
- Input/output paths are server-generated IDs. No shell execution or remote executable downloads are used.
- The phone converts supported source formats to stereo 44.1 kHz PCM WAV. The server returns four WAVs in a ZIP.
- Cancelling requests termination of the server worker. Jobs time out after one hour. After a successful download, the phone requests deletion of the server job.
- Uploaded WAVs and intermediate stems are removed when processing finishes. Unclaimed results/logs expire after one hour; stale job directories are removed on server startup. `.token` and model weights remain local.
- The last successful ZIP remains in private phone storage until replaced. Export it before creating another set.
- Files may contain separation artifacts and bleed. This uses open-source Demucs, not VirtualDJ's proprietary algorithm.
- This is a local development service, not an internet-facing hosted deployment. For another server, the app accepts HTTPS URLs and a token in Studio's server settings. Production hosting needs a TLS reverse proxy and appropriate deployment controls.

## Files

`server.py`: authenticated job API; `worker.py`: isolated Demucs process; `.token`: secret server credential; `.data/`: temporary jobs; `.models/`: downloaded model; `server.log` / `server-error.log`: service logs. These local runtime files are gitignored.

Model/code: [Demucs](https://github.com/facebookresearch/demucs) (MIT), [PyTorch](https://github.com/pytorch/pytorch), [SoundFile](https://github.com/bastibe/python-soundfile). Dependencies are pinned in `setup.ps1`.

## Troubleshooting and verification

Run setup/start commands from this `stem-server` folder. If Studio cannot connect, keep the computer awake, authorize USB debugging, and rerun `start-server.ps1`. Provisioning requires the debug APK. If audio is unavailable, choose a local or already cached song. A slow first job may be downloading model weights. Wait or cancel the active job if the server reports busy. Startup diagnostics are in `server-error.log` and `server.log`; keep `.token` private.

With the server running and idle:

```powershell
./.venv/Scripts/python.exe test_api.py
```

Authentication, malformed WAV rejection, one-job enforcement, cancellation, and cleanup passed on 2026-10-24. Android StemChecks completed real Demucs inference on a 12-second original demo and verified the four-WAV ZIP. Keep at least 1 GiB free on the phone and export the ZIP before separating another song.
