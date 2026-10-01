> Historical initial proposal. Current implementation, Supabase setup, and verification are documented in [README.md](README.md) and [docs/VERIFICATION.md](docs/VERIFICATION.md).

# Live Tracking PPP62 - Supervised Student Practice in Fish Transportation & Distribution

An Android application designed for supervised field practice in aquaculture fish transportation and distribution logistics. Students share live GPS location during active practical sessions and log checkpoint arrival, water/fish temperature, transport conditions, weight, and photographic evidence. Lecturers monitor student progress, review submissions and exceptions, and manage customizable checkpoints in real time.

## User Review Required

> [!IMPORTANT]
> **Tech Stack Choice: Native Android (Kotlin + Jetpack Compose) vs. Flutter**
> - The prompt mentions both *"Build one Android application"* and *"Mobile app: Flutter (shared Android/iOS codebase)"*.
> - **Recommendation:** If the pilot only requires Android devices, **Native Android (Kotlin + Jetpack Compose + Material 3)** offers superior reliability for Android 14+ background/foreground location services (`FOREGROUND_SERVICE_LOCATION`), battery-efficient GPS fusing, CameraX capture, and offline Room DB queuing.
> - If an iOS build is required for students using iPhones in the same pilot, **Flutter** (with `flutter_map` or `google_maps_flutter`, `geolocator`, `workmanager`) is recommended.

> [!WARNING]
> **Tracking Scope: Individual vs. Team Vehicle**
> - In practical fish transport, multiple students often travel together in a single distribution truck/vehicle.
> - Tracking one designated device per vehicle/team drastically reduces GPS battery drain, Cloud Firestore read/write costs, and map clutter, while all team members can still submit checkpoint evidence and handling logs.

---

## Open Questions

> [!IMPORTANT]
> 1. **Framework Confirmation:** Should we bootstrap the project as **Native Android (Kotlin + Jetpack Compose)** or **Flutter (Dart)**?
> 2. **Team vs. Individual Location:** Will tracking be per individual student, or one active beacon device per distribution vehicle/team?
> 3. **Firebase Configuration:** Do you have an existing Firebase project to link (`google-services.json`), or should we build with mock/repository interfaces ready for Firebase integration?
> 4. **Compulsory Fields:** Which fish handling fields are strictly required before check-in can be submitted (e.g. water temperature, species/batch ID, mortality count, weight, cargo photo)?

---

## Proposed Changes

The project will be scaffolded from scratch in `/Users/ojan/Documents/Live Tracking PPP62`. Below is the architectural design for the recommended **Native Android (Jetpack Compose + Clean Architecture)** stack.

### 1. Build & Core Architecture Setup

Configure Gradle build files with Version Catalog (`libs.versions.toml`), Android 14/15 target SDK, Jetpack Compose, Material 3, Coroutines, Flow, Hilt (or Koin) for DI, Navigation Compose, Room, Google Maps Compose, and Firebase SDKs.

#### [NEW] [build.gradle.kts](file:///Users/ojan/Documents/Live Tracking PPP62/build.gradle.kts)
Root Gradle build configuration.

#### [NEW] [settings.gradle.kts](file:///Users/ojan/Documents/Live Tracking PPP62/settings.gradle.kts)
Project settings and repository resolution.

#### [NEW] [libs.versions.toml](file:///Users/ojan/Documents/Live Tracking PPP62/gradle/libs.versions.toml)
Dependency version catalog for Compose, AndroidX, Play Services Location, Maps Compose, Room, and Firebase.

#### [NEW] [app/build.gradle.kts](file:///Users/ojan/Documents/Live Tracking PPP62/app/build.gradle.kts)
App-level build configuration, plugins, dependencies, and build types.

---

### 2. Domain Models & Contracts (`core/model`, `domain`)

Data structures for role-based access, active sessions, checkpoints, check-in evidence, live location packets, and fish transport metrics.

#### [NEW] [User.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/domain/model/User.kt)
`User` entity with `Role` (`LECTURER`, `STUDENT`), `teamId`, and profile details.

#### [NEW] [Session.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/domain/model/Session.kt)
Session state model (`DRAFT`, `ACTIVE`, `PAUSED`, `COMPLETED`), start/end times, join code, and lecturer ID.

#### [NEW] [Checkpoint.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/domain/model/Checkpoint.kt)
Customizable checkpoint with lat/lng coordinates, arrival radius (default 75m), order index, instructions, assigned teams, and required evidence toggles.

#### [NEW] [LiveLocation.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/domain/model/LiveLocation.kt)
Location packet: latitude, longitude, accuracy, speed, heading, timestamp, staleness status (`LIVE`, `STALE`, `PAUSED`, `FINISHED`), and battery status.

#### [NEW] [CheckInRecord.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/domain/model/CheckInRecord.kt)
Evidence record with checkpoint ID, session ID, student ID, timestamp, temperature (°C), fish quantity/weight (kg), photo URL/local path, notes, sync status (`PENDING_SYNC`, `SYNCED`, `FLAGGED_EXCEPTION`).

---

### 3. Location Tracking & Foreground Service (`service/location`)

Reliable location sharing that functions smoothly with screen off, respecting Android 14 `foregroundServiceType="location"`.

#### [NEW] [LocationTrackingService.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/service/LocationTrackingService.kt)
Foreground service managing continuous location updates with FusedLocationProviderClient (10–15s moving interval, stationary back-off), notification controls (Pause / Resume / Finish), and session auto-cutoff.

#### [NEW] [LocationManager.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/data/location/DefaultLocationClient.kt)
Kotlin Flow-based location updates with accuracy validation and distance filtering.

---

### 4. Data Layer & Offline Sync (`data`)

Room database for local offline queueing and Firestore/Storage synchronization engine.

#### [NEW] [AppDatabase.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/data/local/AppDatabase.kt)
Room database definitions for offline checkpoints, pending check-in evidence, and offline location logs.

#### [NEW] [CheckInDao.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/data/local/dao/CheckInDao.kt)
DAO for storing, querying pending uploads, and marking items synced.

#### [NEW] [SyncRepository.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/data/repository/SyncRepositoryImpl.kt)
Handles two-way synchronization: streams live locations to Firestore, queues evidence locally when offline, and retries uploads with exponential back-off via WorkManager upon network reconnection.

---

### 5. UI Layer & Compose Screens (`ui`)

Clean, role-tailored Jetpack Compose UI with Material 3.

#### [NEW] [NavGraph.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/ui/navigation/NavGraph.kt)
Navigation structure routing between Student and Lecturer roles.

#### [NEW] [LiveMapScreen.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/ui/map/LiveMapScreen.kt)
Google Maps Compose screen rendering:
- Student markers with status chips (Live = Green, Stale > 90s = Amber, Paused = Grey, Offline = Red).
- Checkpoint pins with configurable circular geofence boundary rings.
- Team and participant filter bottom sheet.
- Tracking control bar (Start / Pause / Finish).

#### [NEW] [CheckInScreen.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/ui/checkin/CheckInScreen.kt)
Student evidence entry form:
- GPS distance indicator to target checkpoint.
- Camera capture preview for cargo/fish conditions.
- Number fields for water temperature (°C), consignment weight (kg), fish condition checklist, and notes.
- Exception/Manual Override button when GPS is degraded.

#### [NEW] [LecturerDashboardScreen.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/ui/lecturer/LecturerDashboardScreen.kt)
Lecturer monitoring panel:
- Session overview, active students/teams, checkpoint completion progress.
- Submission review list with flagged exceptions (e.g. out-of-radius check-ins or delayed uploads).
- CSV / Report export trigger.

#### [NEW] [CheckpointEditorScreen.kt](file:///Users/ojan/Documents/Live Tracking PPP62/app/src/main/java/com/ppp62/livetracking/ui/lecturer/CheckpointEditorScreen.kt)
Interactive map-based checkpoint creation and editing: reorder stops, drag pins, adjust arrival radius, and configure required evidence.

---

## Verification Plan

### Automated Tests
- Unit tests for proximity calculation (`LocationUtilsTest`) verifying 75m threshold and edge cases.
- Unit tests for `SyncRepositoryTest` ensuring offline check-ins queue correctly and do not duplicate upon sync.
- Staleness detector tests verifying that timestamps >90 seconds transition marker state from `LIVE` to `STALE`.
- Run: `./gradlew test`

### Manual Verification
- **Location sharing lifecycle:** Launch app, join session, grant location permission (`ACCESS_FINE_LOCATION` + `FOREGROUND_SERVICE_LOCATION`), turn screen off, and verify continuous updates in logcat.
- **Offline resilience:** Enable airplane mode, tap **Check in**, submit temperature and photo; confirm entry is saved with `PENDING_SYNC` badge. Disable airplane mode and verify automatic sync to cloud.
- **Lecturer monitoring:** Verify that moving student positions update dynamically on the map and stale status is displayed if updates cease.
