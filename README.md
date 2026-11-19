# Bondhu Bench (বন্ধু বেঞ্চ)

A small Android app that checks how good a phone is for the future **Bondhu** app: a quick phone test, an on-device language model benchmark (Bengali/Hindi), and in-app updates. It makes a report you can share as text.

License: AGPL-3.0, see the LICENSE file.

## The three tabs (version 0.2.0)

### Test
The original quick test (about 10 seconds). Tap **Run test**, then **Share report** or **Copy**.
- **Device**: brand, model, Android version, chip (SoC), CPU cores, RAM, free storage, battery level and temperature, heat status.
- **Brand warning flag**: some brands (Xiaomi, Oppo, vivo, realme, OnePlus, Samsung, Huawei, Honor, Infinix, Tecno, Itel) close apps in the background very aggressively.
- **Speech**: is speech recognition available? Is a voice installed for Bengali, Hindi and Indian English?
- **CPU speed**: a simple 2 second maths test, with battery temperature before and after.
- **Model bench**: results saved from the Models tab (including the model replies) are added to the report.
- **Failure log**: if something fails, a short event is saved on the phone and added to the report.

### Models
1. Pick a model and tap **Download**. The real size is read from the server. The app checks free space first, warns you before using mobile data, can resume after a break, and checks the final size. **Cancel download** keeps the partial file so you can continue later. Keep the app open while downloading.
2. Tap **Run bench** on a downloaded model. It loads the model, asks 9 fixed questions (3 Bengali, 3 Hindi, 2 English, 1 "answer only with JSON") and measures: load time, time to first token, speed (tokens per second), memory, battery temperature and heat status, before and after. The screen stays on during the bench.
3. Default is CPU. The **GPU switch is experimental** and may fail or crash the app.
4. If the model file is bigger than 90% of the free RAM you get a warning and must tap "Continue anyway".
5. **Show replies** lets you read what the model wrote, to judge Bengali and Hindi quality. **Share** sends the result as text.

Models are big (about 0.3 GB to 2.6 GB). Use WiFi if you can.

### Update
Shows the installed version. **Check for update** reads the public GitHub release `bench-latest`. If a newer build exists, **Download and install** downloads it, checks its SHA-256, and opens Android's installer. Android always asks you to confirm; that is normal. The first time, Android may send you to a settings page to allow "Install unknown apps" for this app.

## Install and update

- **First time**: install the APK once by hand (download `bondhu-bench-debug.apk` from the GitHub Release `bench-latest`, tap it, allow unknown sources if asked). If you have an older Bondhu Bench (0.1.0) installed, uninstall it first: it was signed with a different key.
- **Later updates**: use the Update tab. This only works when every build is signed with the same permanent key (see "Signing" below); otherwise Android says the signatures do not match.

## Privacy

- Internet is used only for: model downloads from Hugging Face, and update checks/downloads from GitHub. Nothing about you or your phone is sent.
- No microphone permission. It never records audio.
- The failure log has events only (time, code, short message, phone model). Never messages, contacts or audio.
- The report is shared only when **you** tap Share. You can read it first.
- The fixed bench questions contain no personal data.

## How to build (for the developer)

You need Android Studio (or JDK 17 plus the Android SDK).

```
./gradlew :app:assembleDebug
```

The APK appears in `app/build/outputs/apk/debug/`.

### GitHub Actions

`.github/workflows/build-bench-apk.yml` builds the APK on every push and publishes it, together with `latest.json`, to the GitHub Release `bench-latest` (the app's Update tab reads that release). The version code is the CI run number.

### Signing

Updates need every build signed with the same key. Create one key once (outside this repo, keep a backup), then add these GitHub Secrets (Settings, Secrets and variables, Actions):
`BONDHU_KEYSTORE_B64` (the keystore file as base64 text), `BONDHU_KEYSTORE_PASSWORD`, `BONDHU_KEY_ALIAS`, `BONDHU_KEY_PASSWORD`.
Without the secrets the build still works but uses a throw-away debug key (so in-app updates will fail). The CI log prints the certificate SHA-256 of each APK so you can check that it stays the same. Never commit a keystore.

If `gradlew` is ever missing, CI falls back to its own Gradle 8.7. You can re-create the wrapper with `gradle wrapper --gradle-version 8.7`.

## Code map (`app/src/main/java/in/bynq/bondhu/bench/`)

- `MainActivity.kt` - title and the three tabs.
- `TestScreen.kt`, `ModelsScreen.kt`, `UpdateScreen.kt` - the screens.
- `Report.kt` - runs every check and builds the JSON text.
- `DeviceProfile.kt`, `SpeechCheck.kt`, `CpuBench.kt` - the phone checks.
- `FailureLog.kt` - the privacy-safe event log.
- `ModelCatalog.kt` - the hard-coded model list and file locations.
- `HttpDownload.kt` - download helper (redirects, resume, progress).
- `ModelsViewModel.kt` - model downloads (space check, data warning, cancel, delete).
- `ModelBench.kt`, `ModelBenchViewModel.kt`, `ModelBenchStore.kt` - the model benchmark (LiteRT-LM 0.16.1) and saved results.
- `Updater.kt`, `UpdateViewModel.kt`, `InstallResultReceiver.kt` - in-app update with PackageInstaller.
- `NetworkUtil.kt`, `ShareUtil.kt` - small helpers.

## Notes

- Speech recognition availability is reported once for the phone; Android does not tell us per language without actually listening, and we never listen.
- Version 0.2.0 is BUILT by CI but not yet field-tested. See `docs/ROADMAP.md`.
