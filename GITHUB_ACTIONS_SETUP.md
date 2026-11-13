# Build Afterglow APKs with GitHub Actions

This guide is for `ZwelithiniMphanza/after-glow` and its existing `afterglow-android` folder. It builds an ARM64 Android 9+ development APK whenever you push to **android-build**. Change that branch name in the YAML if you prefer `main` or another branch.

The project uses PowerShell and Android SDK tools directly, **not Gradle**. The workflow below uses a Windows runner, Java 17, Node 22, Android platform 36 and build-tools 36.0.0. It prepares the bundled audio engine before building.

## 1. Add the signing-key secret once

Use the same key as the APK already installed on your phone. Otherwise Android will reject an in-place update due to a signature mismatch. Keep the original key backed up privately.

From the project root on your Windows computer, run this in PowerShell. It copies the encoded key to your clipboard without printing it:

```powershell
$signingFile = Join-Path (Get-Location) 'afterglow-android/.local/debug.keystore'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($signingFile)) | Set-Clipboard
```

In your GitHub repository:

1. Open **Settings → Secrets and variables → Actions**.
2. Select **New repository secret**.
3. Name it **ANDROID_DEBUG_KEYSTORE_BASE64**.
4. Paste the clipboard value and save it. Clear your clipboard afterward.

This uses the current build script's development key alias `androiddebugkey` and password `android`. The workflow deliberately fails if the secret is missing, rather than generating a different key on each run. Do not commit the keystore or its Base64 value. No YouTube API key or stem-server token is needed for compilation.

## 2. Create the workflow file

At the repository root create:

```text
.github/workflows/android-apk.yml
```

Copy the following YAML into it. Use spaces, not tabs. The SDK step accepts Android SDK licenses on the runner; review those licenses before enabling the workflow.

```yaml
name: Build Android APK

on:
  push:
    branches:
      - android-build
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: android-apk-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: windows-2025
    timeout-minutes: 45
    defaults:
      run:
        shell: pwsh

    steps:
      - name: Check out source
        uses: actions/checkout@v7
        with:
          persist-credentials: false

      - name: Set up Java
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Node
        uses: actions/setup-node@v7
        with:
          node-version: '22'
          package-manager-cache: false

      - name: Install Android SDK packages where build.ps1 expects them
        run: |
          $ErrorActionPreference = 'Stop'
          $sdkManager = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/sdkmanager.bat'
          if (-not (Test-Path -LiteralPath $sdkManager)) {
            throw "SDK manager was not found at $sdkManager"
          }
          $sdkTarget = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
          New-Item -ItemType Directory -Force -Path $sdkTarget | Out-Null
          (1..100 | ForEach-Object { 'y' }) | & $sdkManager "--sdk_root=$sdkTarget" --licenses
          if ($LASTEXITCODE -ne 0) { throw 'SDK license acceptance failed' }
          & $sdkManager "--sdk_root=$sdkTarget" 'platforms;android-36' 'build-tools;36.0.0'
          if ($LASTEXITCODE -ne 0) { throw 'SDK package installation failed' }

      - name: Restore the existing development signing key
        env:
          SIGNING_KEY_BASE64: ${{ secrets.ANDROID_DEBUG_KEYSTORE_BASE64 }}
        run: |
          $ErrorActionPreference = 'Stop'
          if ([string]::IsNullOrWhiteSpace($env:SIGNING_KEY_BASE64)) {
            throw 'Add the ANDROID_DEBUG_KEYSTORE_BASE64 repository secret first.'
          }
          $keyDir = Join-Path $env:GITHUB_WORKSPACE 'afterglow-android/.local'
          New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
          [IO.File]::WriteAllBytes(
            (Join-Path $keyDir 'debug.keystore'),
            [Convert]::FromBase64String($env:SIGNING_KEY_BASE64)
          )

      - name: Prepare native audio dependencies
        run: '& ./afterglow-android/prepare-audio-engine.ps1'

      - name: Build and verify APK signature
        run: '& ./afterglow-android/build.ps1'

      - name: Upload APK
        uses: actions/upload-artifact@v7
        with:
          name: afterglow-apk-${{ github.run_number }}
          path: afterglow-android/build/afterglow-debug.apk
          if-no-files-found: error
          retention-days: 14
          compression-level: 0

      - name: Remove temporary signing key
        if: always()
        run: |
          $keyFile = Join-Path $env:GITHUB_WORKSPACE 'afterglow-android/.local/debug.keystore'
          if (Test-Path -LiteralPath $keyFile) {
            Remove-Item -LiteralPath $keyFile -Force
          }
```

The action major versions above follow their official repositories at the time this guide was prepared. For stricter dependency control, replace action tags with verified full commit SHAs and maintain them through Dependabot.

## 3. Commit and push to the build branch

If the branch does not exist locally, create it from the version you want to build:

```powershell
git switch -c android-build
git add .github/workflows/android-apk.yml GITHUB_ACTIONS_SETUP.md
git commit -m "Add Android APK build workflow"
git push -u origin android-build
```

If the branch already exists, use `git switch android-build` instead. Commit the application changes you want built as well: GitHub builds the pushed source, not uncommitted files on your computer. The repository needs the Java source, resources, manifest, `make-demo.cjs`, and both PowerShell build scripts. Generated native binaries, demo audio, dependencies and APKs can stay ignored; CI recreates them.

Every later push to `android-build` automatically starts a build. A newer push cancels an unfinished build for the same branch. Pushes to other branches do not trigger this workflow.

To use another branch, change `on.push.branches` and ensure the workflow exists on that branch. For example:

```yaml
  push:
    branches:
      - main
```

## 4. Download the latest successful APK

1. Open [the repository's Actions page](https://github.com/ZwelithiniMphanza/after-glow/actions).
2. Choose **Build Android APK**.
3. Select the newest **successful** run for your build branch. Check its commit matches your push.
4. In **Artifacts**, download `afterglow-apk-<run number>`.
5. Extract the ZIP to get `afterglow-debug.apk` and install it on your phone.

From a computer with ADB and the phone connected:

```powershell
adb install -r ./afterglow-debug.apk
```

The artifact is retained for 14 days, subject to repository policy. Each successful run has its own artifact; this does not create a GitHub Release or a permanent “latest APK” URL. Downloading Actions artifacts requires GitHub access. Artifact behavior and retention are documented in [GitHub's artifact guide](https://docs.github.com/en/actions/tutorials/store-and-share-data).

For a manual **Run workflow** button, also put this workflow on the repository's default branch. Then open Actions, select the workflow, click **Run workflow**, and select the branch. Its presence on the default branch does not change the configured push branch filter. See [manual workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

## 5. What this does and does not verify

`build.ps1` compiles the app, packages the audio engine, signs the APK, and verifies its signature. This workflow does not run phone instrumentation tests: the existing test runner targets your physical phone, which is not attached to GitHub's hosted runner. It also does not start or deploy the stem-separation server; that remains a separate computer service.

This is the existing **development/debug build**, not a Play Store release. For store distribution, add a release build configuration and release signing separately. Increase `android:versionCode` in `afterglow-android/AndroidManifest.xml` when publishing newer app versions, and update `versionName` for the displayed version. The workflow run number only names the artifact; it does not change the app version.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| No build appears | Actions enabled in repository settings; exact branch spelling; workflow committed on the pushed branch. |
| Missing secret | Add `ANDROID_DEBUG_KEYSTORE_BASE64` under repository Actions secrets, not as an ordinary variable. |
| Keystore signing fails | Use the original development keystore expected by `build.ps1`, with its existing alias and password. |
| SDK manager not found | Check the Windows runner's `ANDROID_HOME` and installed command-line tools. The script must install platform 36 and build-tools 36.0.0 under `%LOCALAPPDATA%/Android/Sdk`. |
| Dependency download or checksum failure | Read the Prepare step log; retry transient network failures. For unavailable pinned versions, update `prepare-audio-engine.ps1` and the verified extractor hash together. Do not bypass checksum verification. |
| APK cannot update installed app | Check matching signing certificates and a non-decreasing version code. Restore the correct key rather than uninstalling and losing app data. |
| Artifact missing | Open a successful run; check Upload APK completed and the artifact has not expired. |

The workflow has been checked against this project's local build scripts, but has not been run on GitHub yet. Its first pushed run is the hosted-environment validation.

## References

- [GitHub workflow syntax and branch filters](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [Windows 2025 runner software and Android paths](https://github.com/actions/runner-images/blob/main/images/windows/Windows2025-Readme.md)
- [Checkout action](https://github.com/actions/checkout)
- [Java setup action](https://github.com/actions/setup-java)
- [Node setup action](https://github.com/actions/setup-node)
- [Upload artifact action](https://github.com/actions/upload-artifact)
