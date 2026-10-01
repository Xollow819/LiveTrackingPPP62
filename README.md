# Live Tracking PPP62

Native Android pilot for supervised student practice in fish transportation and distribution. Built with Kotlin, Jetpack Compose, Material 3, Android foreground location services, and OpenStreetMap via osmdroid.

## Included
- Student and lecturer role flows
- OpenStreetMap route with checkpoints (no API key)
- Android 14/15-compatible foreground location service
- Student temperature, weight, condition, notes, and evidence UI
- Lecturer live/stale/paused status dashboard
- 75 m proximity and 90 s staleness utilities with tests
- Local-first repository hooks for a future authenticated backend

## Run
1. Open this directory in Android Studio Ladybug or newer.
2. Use JDK 17 and let Android Studio sync Gradle.
3. Run on Android 8.0+ (physical device recommended for GPS).
4. Grant location and notification permissions. Background location must be enabled separately in system settings for full screen-off tracking.

## Free API choice
This pilot uses **OpenStreetMap raster tiles through osmdroid**, so no Google Maps billing account or API key is needed. Respect the [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/) and use a dedicated tile provider or self-hosted tiles before a high-volume production release.

## Production integration still required
The app intentionally does not ship with shared cloud credentials. For multi-device real-time tracking, connect the service and repository hooks to one of:
- Firebase Firestore/Storage on the Spark free tier for a pilot.
- Supabase free tier with Row Level Security.
- An institution-owned API/WebSocket service.

Add authenticated roles, team/session authorization, encrypted transport, retention rules, consent, CameraX capture, durable Room queues, WorkManager retry, and server-side CSV/report generation before production use.
