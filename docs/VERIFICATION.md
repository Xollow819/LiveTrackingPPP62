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

## Map loading optimisation

Physical-device map-only checks passed on the connected I2407 phone. Session/evidence records were not modified by the test. Native overlay identities remain unchanged after unrelated UI updates; repeated GPS requests to the same coordinates recenter correctly; returning to the map preserves its camera.

The app now starts directly with the selected layer, avoids resetting an unchanged tile source, separates route geometry from live marker updates, remembers per-session camera position, uses bounded 64/128-tile memory caches and a 256 MB disk cache, and requests the visible center tile first. Satellite requests allow four concurrent visible downloads and disable speculative prefetch; OpenStreetMap retains its own two-request concurrency policy.

Measured cached re-entry was 203–209 ms (including a deliberate 200 ms test wait), with no additional wait for the cached center tile. A later cached center tile was available within the test's initial 506 ms observation window. These are device observations, not a controlled before/after benchmark or a claim that every tile completes in that time.

Uncached imagery remains dependent on the provider/network: one satellite run exceeded the test's 30-second tile wait, and a direct uncached EOX request from the host took 13 seconds. The subsequent cached device run passed. No forced cache expiry or bulk/offscreen downloads were added.

## Public APK installation — v1.2.3

The public download now uses a signed, non-debuggable production APK (`com.ppp62.livetracking`, version code 7). Its persistent private signing key is stored outside the repository and reused by `scripts/build_distribution.py`; release builds reject missing signing settings. APK verification checks its signature, package ID, absence of debug/test-only flags, both ARM architectures, and compressed native libraries extracted at installation. Twelve JVM tests and release lint with zero errors passed.

On a clean Android 12/API 31 ARM64 emulator, opening the APK from Files → Download and using the standard Android package installer completed with **App installed**. A subsequent cold launch displayed the configured app's welcome screen successfully. This checks the user-facing installation flow, rather than just an ADB install.

The original v1.2.2 also installed through ADB on this Android 12 emulator. Consequently the reported OPPO CPH2461 rejection is not reproduced, and its underlying installer error remains unconfirmed. The new production APK must still be tried on that physical OPPO; the emulator is not a substitute for ColorOS testing. Older debug builds keep their separate package and local data.

## Student transport workflow — v1.2.4

Transport condition records now belong to departure and arrival only, with stable per-session/student/phase IDs that prevent duplicate saves from replacing an existing record. Intermediate lecturer pins remain route stops. Saving departure starts the foreground service and stopwatch; Finish immediately stops sharing and freezes time before opening the arrival form. Cancelling arrival leaves a pending action. Pausing sharing pauses time. Timing survives process recreation in local preferences and uses a monotonic clock on the same boot, with a nonnegative wall-clock fallback after reboot. Arrival notes include the final duration for lecturer review.

The student map fits the ordered lecturer route, keeps its native map across dashboard tabs, updates route geometry independently of GPS markers, and isolates stopwatch ticks from map recomposition. It avoids downloading a placeholder map before lecturer pins exist. Road routes use the [FOSSGIS OSRM service](https://routing.openstreetmap.de/about.html), with bounded requests, at least 1.1 seconds between requests, a small seven-day disk cache, and a dashed direct-path fallback. Only lecturer checkpoint coordinates are submitted to the routing provider; live student GPS is not submitted. Provider attribution and its map correction link remain visible.

No new Supabase migration or Room schema change is required: existing submissions and notes carry phase and duration. Anonymous sign-in is serialized, and cancellation propagates instead of returning an empty identity.

Seventeen JVM tests passed before the final immediate-completion UI update. On the Android 12/API 31 ARM64 emulator, service start/pause/resume/finish, startup auth storage, Room migration, and photo orientation tests passed. The synthetic student dashboard exercise displayed the ordered road route and advancing timer, kept the map across tabs, showed that intermediate stops need no forms, and opened the arrival form after Finish. Its final check initially exposed that the test inserted the arrival directly through the repository, bypassing the ViewModel’s normal form-save callback; the dashboard now also records successful phase saves directly in ViewModel state. That final UI redraw was not rerun on Android. Real moving-GPS behavior, that final UI redraw on a physical phone, and cross-device backend acceptance remain subject to the limitations below.

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
