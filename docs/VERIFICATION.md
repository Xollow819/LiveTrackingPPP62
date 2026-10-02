# Implementation verification — 2 October 2026

The app uses the Anthropic frontend-design direction in [DESIGN.md](DESIGN.md), adapted to native Compose. [Welcome](screenshots/welcome.png) and [satellite map](screenshots/student-map-satellite.png) show the finished light appearance. Interface screenshots use explicit test fixtures; these are not claims of a live Supabase session.

## Passed locally

- Debug APK and Android test APK assembly; nine JVM unit tests; Android lint with zero errors.
- Eight interface tests also passed in dark mode at 130% text size; forms remain readable and the map dock/navigation fit. The screenshot review prompted automatic system-bar contrast for dark mode.
- Twelve tests on the Pixel 8a Android 37.2 emulator: account/join/setup/route-editing/navigation/forms/preferences, actual installed Room v1-to-v2 migration, start/pause/resume/finish service state, EXIF-oriented photo compression, and an HTTPS satellite download decoded on Android.
- SQLite migration comparison preserves existing evidence and checkpoint instructions, isolates participant locations, and prevents an old upload acknowledgement from deleting a newer queued position. Versioned Room schemas are included for reproducible migration checks.
- PostgreSQL-compatible PGlite security checks cover atomic session creation, account ownership, forged roles, cross-session access, private photos, monotonic GPS events, flagged uploads, duplicate retries, closed sessions, and repeated migration application. PGlite supplies minimal auth/storage stubs; it does not replace acceptance against Supabase.
- Screenshot review caught and corrected a transparent light-theme surface, panel rendering defects, unreadable map headers, and stale tile error notices. GPS is below search; map help retracts to an information control. Satellite tiles display on Android.

- Configured-app cold launch on Pixel 8a passed after fixing the startup provider in v1.2.1. Only WorkManager's initializer is disabled; Supabase's SettingsInitializer is retained. A new native regression test for default auth/session and PKCE storage passes. Nine unit tests and lint pass for this patch.

## Remaining external acceptance

The Supabase URL/public client key are now configured in gitignored local.properties. Read-only live checks confirmed that the project responds and anonymous sign-in/email authentication are enabled. The user applied the upgrade in Supabase SQL Editor. Subsequent read-only API checks confirmed the new ownership, checkpoint requirements, evidence and tracking columns on all four affected tables. Authentication email redirects, realtime subscriptions, authenticated policy behavior and multi-device evidence sync remain **unverified against the existing project**. Follow [backend setup](../backend/supabase/SETUP.md). Release builds reject missing configuration.

A physical Android device is still required for real GPS movement, permission revocation, camera lifecycle, background tracking/battery behavior and sustained glass rendering performance. Emulator service-state and image tests do not establish these results.

EOX Sentinel imagery is available without a key for academic/noncommercial use under CC BY-NC-SA 4.0; the app retains linked provider/data/license attribution. It provides landscape imagery rather than street-level aerial detail.

## Reproduce

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
python3 scripts/verify_room_migration.py
cd backend/supabase/tests
npm install
npm test
```

Public UiAutomation APIs are used for Android UI tests because older Espresso input injection references a hidden API removed in Android 17. The satellite acceptance test requires Internet access.
