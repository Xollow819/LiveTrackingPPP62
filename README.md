# Live Tracking PPP62

Android application for supervised student practice in fish transportation and distribution logistics. Students join a practical session, share one live beacon per team vehicle, and record evidence at route checkpoints. Lecturers monitor progress, review exceptions, configure checkpoints, and export check-in reports.

## V1 capabilities

### Student
- Join an active session using the demo code `PPP6201`.
- Start, pause, resume, or finish Android foreground GPS tracking.
- Continue tracking with the screen off, subject to Android background-location settings.
- View OpenStreetMap checkpoints without a paid API key.
- Check in with water temperature, consignment weight, fish condition, notes, GPS distance, and camera evidence.
- Save evidence offline with Pending or Flagged status.

### Lecturer
- Monitor team vehicle locations and Live, Stale, Paused, or Finished states.
- Filter participants by team.
- See session metrics and exceptions.
- Add ordered checkpoints with coordinates and a 20–500 m arrival radius.
- Review check-ins and export a CSV report through Android sharing.

### Engineering
- Kotlin, Jetpack Compose, Material 3, and Navigation Compose.
- Room database for sessions, checkpoints, evidence, and latest locations.
- Foreground location service compatible with Android 14/15 declarations.
- WorkManager synchronization hook with network constraints.
- Camera evidence stored in private app storage through FileProvider.
- OpenStreetMap through osmdroid.
- Unit tests for proximity and 90-second staleness rules.
- GitHub Actions build, lint, test, and debug-APK artifact.

## Run

1. Install Android Studio Ladybug or newer and JDK 17.
2. Clone the repository and open its root directory.
3. Let Android Studio install Android SDK 35 and sync Gradle.
4. Run the `app` configuration on an Android 8.0+ device.
5. Grant camera, precise location, and notification permissions. Enable **Allow all the time** location separately in Android settings for uninterrupted screen-off tracking.
6. Select Student and use join code `PPP6201`, or select Lecturer to inspect the seeded demonstration session.

Command-line verification:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Map types

The app uses OpenStreetMap raster tiles through osmdroid, so it has no Google Maps key or billing dependency. The public OSM tile service is appropriate for development and small pilots only. Follow the [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) and use a dedicated provider or self-hosted tiles for a high-volume deployment.

The layers button on each map switches between **Standard** and **Satellite**, and remembers your choice. Satellite imagery uses MapTiler; Standard remains available without a provider key.

To enable satellite imagery, add a MapTiler client API key to gitignored `local.properties`, then rebuild:

```properties
MAPTILER_API_KEY=your_maptiler_client_key
```

The app reads zoom limits and attribution from MapTiler's satellite TileJSON. Satellite loading failures offer Retry and Standard actions. See the [MapTiler Tiles API](https://docs.maptiler.com/cloud/api/tiles/) for provider configuration and account requirements.

## Cloud synchronization boundary

V1 is fully functional on one device and deliberately does not embed shared cloud credentials. `PPP62_SYNC_ENDPOINT` can be supplied as a Gradle property and `SyncWorker` is the integration boundary. A production multi-device deployment still needs an authenticated Firebase/Supabase/institution backend, access rules, consent and retention policies, and server acknowledgement before Pending records are marked Synced.

Do not place administrator secrets in `local.properties`, Gradle files, or the APK. Mobile clients must use short-lived user authentication and server-enforced role/session authorization.

## Privacy checklist

- Obtain informed location and photo consent before each practical session.
- Track only while an active session is visible through the foreground notification.
- Set a retention period for precise location history and evidence.
- Restrict lecturers to their own sessions and students to their assigned team.
- Document manual-override and out-of-radius review procedures.
