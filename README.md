# Live Tracking PPP62 / PPPVenza

Native Android app for supervised fish transportation practicals. Lecturers create field sessions and routes; students join with a code, share location, and capture checkpoint evidence.

## Current app

- Liquid Glass inspired Compose interface with light/dark appearance, Instrument Sans, floating map controls and an opaque-panel accessibility preference.
- Standard OpenStreetMap and keyless EOX Sentinel-2 satellite layers, remembered across launches. Search sits above GPS in the upper-right corner. Checkpoint help retracts to an information control.
- Lecturer email accounts, session history, route creation/editing, checkpoint instructions and evidence requirements, student/team roster, submission review and CSV export.
- Student session join, real foreground-service sharing controls, checkpoint evidence/camera capture, queued uploads and retry status. Optional measurements remain optional; exceptions upload with their own review flag.
- Supabase authentication, realtime session snapshots and private evidence storage. Room caches routes/evidence; WorkManager retries stable-ID uploads and queued positions. Server policies enforce session membership and lecturer ownership.
- Explicit Room migration preserves installed data. Existing legacy sessions require administrator ownership review when upgrading Supabase.

## Run

Open the repository in an Android Studio version supporting AGP 9.1.1, use JDK 17, and install Android SDK 37. Run on Android 8.0 or newer. Location sharing starts from the visible app and uses a foreground notification; grant location, camera and notification permissions when prompted.

Configure the existing Supabase project and apply its migration using [SETUP.md](backend/supabase/SETUP.md). There is no seeded demonstration join code. Debug builds without project configuration show an unavailable state; release builds reject missing configuration. Never bundle a service-role key.

For public downloads, use the signed `LiveTrackingPPP62-v1.2.3.apk` from [GitHub Releases](https://github.com/Xollow819/LiveTrackingPPP62/releases), rather than a debug APK. It supports Android 8.0 and newer. The production package is `com.ppp62.livetracking`; older debug builds use a separate `.debug` package and keep their own local data.

Build a distributable APK with `python3 scripts/build_distribution.py` after configuring Supabase. The script creates a persistent private signing key once in `~/.android/pppvenza-release`, reuses it for subsequent builds, and runs assembly, unit tests, and release lint. Securely back up that entire directory: future updates require the same key. Never commit or share its contents. Native libraries are compressed and extracted during installation for installer compatibility. Unsigned release builds are rejected. CI debug artifacts are for development, not public distribution.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Satellite imagery needs no API key. EOX imagery is licensed for academic/noncommercial use under CC BY-NC-SA 4.0, with linked attribution retained in the app. It provides landscape detail. OpenStreetMap usage must follow its [tile policy](https://operations.osmfoundation.org/policies/tiles/).

[Design direction](docs/DESIGN.md) · [Verification and remaining acceptance](docs/VERIFICATION.md) · [Interface screenshots](docs/screenshots)

Local checks pass. Live Supabase deployment and multi-device acceptance require project configuration/access; real GPS, camera lifecycle, background battery behavior and glass performance still require physical-device verification.
