# Bondhu Bench (বন্ধু বেঞ্চ)

A small Android app that checks how good a phone is for the future **Bondhu** app.
You tap one button. It makes a short report about the phone. You can share that report as text.

License: AGPL-3.0, see the LICENSE file.

## What it checks

- **Device**: brand, model, Android version, chip (SoC), CPU cores, RAM, free storage, battery level and temperature, heat status.
- **Brand warning flag**: some brands (Xiaomi, Oppo, vivo, realme, OnePlus, Samsung, Huawei, Honor, Infinix, Tecno, Itel) close apps in the background very aggressively. The flag tells us to show the right battery guide later.
- **Speech**: is speech recognition available? Is a voice installed for Bengali, Hindi and Indian English?
- **CPU speed**: a simple 2 second maths test, with battery temperature before and after.
- **Failure log**: if a check fails, a short event is saved on the phone and added to the report.

## Privacy

- No internet permission. The app cannot send anything by itself.
- No microphone permission. It never records audio.
- The failure log has events only (time, code, short message, phone model). Never messages, contacts or audio.
- The report is shared only when **you** tap "Share report". You can read it first.

## How to install (for friends)

1. Download the APK file (`app-debug.apk`) that was sent to you.
2. Tap the file. If Android asks, allow "Install from unknown sources" for the app you opened it with (Chrome, Files, WhatsApp...).
3. Open **Bondhu Bench**.
4. Tap **Run test**. Wait about 10 seconds.
5. Tap **Share report** and send it to the developer.

## How to build (for the developer)

You need Android Studio (or JDK 17 plus the Android SDK).

```
./gradlew :app:assembleDebug
```

The APK appears in `app/build/outputs/apk/debug/`.

### GitHub Actions

`.github/workflows/build-bench-apk.yml` builds the APK on every push and uploads it as the artifact `bondhu-bench-debug-apk`. Open the run in the Actions tab and download it.

If `gradlew` is ever missing, CI falls back to its own Gradle 8.7. You can re-create the wrapper with `gradle wrapper --gradle-version 8.7`.

## Code map (`app/src/main/java/in/bynq/bondhu/bench/`)

- `MainActivity.kt` - the screen (Run test, results, Share, Copy).
- `Report.kt` - runs every check and builds the JSON text.
- `DeviceProfile.kt` - phone facts, battery, heat.
- `SpeechCheck.kt` - speech recognition and text-to-speech checks.
- `CpuBench.kt` - the 2 second CPU test.
- `FailureLog.kt` - the privacy-safe event log.

## Notes

- Speech recognition availability is reported once for the phone; Android does not tell us per language without actually listening, and we never listen.
- Stage 1 has no AI model yet. See `docs/ROADMAP.md`.
