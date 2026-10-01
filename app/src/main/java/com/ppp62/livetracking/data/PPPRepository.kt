package com.ppp62.livetracking.data

import com.ppp62.livetracking.util.LocationUtils
import kotlinx.coroutines.*
import java.util.UUID

class PPPRepository(private val dao: PPPDao) {
    val sessions = dao.observeSessions()
    val checkpoints = dao.observeCheckpoints(DEMO_SESSION_ID)
    val checkIns = dao.observeCheckIns(DEMO_SESSION_ID)
    val locations = dao.observeLocations(DEMO_SESSION_ID)

    fun scheduleSeed() = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { seedDemoIfEmpty() }

    suspend fun joinSession(code: String): SessionEntity? = dao.sessionByCode(code.trim().uppercase())

    suspend fun submitCheckIn(
        checkpoint: CheckpointEntity,
        studentName: String,
        team: String,
        temperatureC: Double,
        weightKg: Double,
        condition: FishCondition,
        notes: String,
        photoUri: String?,
        latitude: Double?,
        longitude: Double?
    ) {
        val distance = if (latitude != null && longitude != null) LocationUtils.distanceMeters(latitude, longitude, checkpoint.latitude, checkpoint.longitude) else null
        val exception = when {
            distance == null -> "GPS unavailable"
            distance > checkpoint.radiusMeters -> "Outside checkpoint radius (${distance.toInt()} m)"
            checkpoint.requiresPhoto && photoUri.isNullOrBlank() -> "Photo evidence missing"
            else -> null
        }
        dao.upsertCheckIn(
            CheckInEntity(
                id = UUID.randomUUID().toString(), checkpointId = checkpoint.id, sessionId = checkpoint.sessionId,
                studentName = studentName.trim(), team = team.trim(), temperatureC = temperatureC, weightKg = weightKg,
                condition = condition, notes = notes.trim(), photoUri = photoUri, latitude = latitude, longitude = longitude,
                distanceMeters = distance, createdAt = System.currentTimeMillis(),
                syncState = if (exception == null) SyncState.PENDING else SyncState.FLAGGED, exceptionReason = exception
            )
        )
    }

    suspend fun addCheckpoint(name: String, latitude: Double, longitude: Double, radius: Double, instructions: String, order: Int) {
        dao.upsertCheckpoint(CheckpointEntity(UUID.randomUUID().toString(), DEMO_SESSION_ID, name.trim(), latitude, longitude, radius, order, instructions.trim()))
    }

    suspend fun setTrackingState(participantId: String, state: TrackingState) = dao.setTrackingState(participantId, state, System.currentTimeMillis())

    private suspend fun seedDemoIfEmpty() {
        if (dao.sessionCount() > 0) return
        val now = System.currentTimeMillis()
        dao.upsertSession(SessionEntity(DEMO_SESSION_ID, "PPP62 Fish Distribution Practice", "PPP6201", "Dr. Supervisor", SessionStatus.ACTIVE, now, null))
        dao.upsertCheckpoints(listOf(
            CheckpointEntity("cp-loading", DEMO_SESSION_ID, "Hatchery loading", -6.9147, 107.6098, 75.0, 1, "Record loading temperature, total weight, and cargo condition."),
            CheckpointEntity("cp-water", DEMO_SESSION_ID, "Water-quality stop", -6.9270, 107.6170, 75.0, 2, "Inspect water temperature and fish stress indicators."),
            CheckpointEntity("cp-delivery", DEMO_SESSION_ID, "Distribution point", -6.9440, 107.6400, 100.0, 3, "Record arrival, unloading condition, and final weight.")
        ))
        dao.upsertLocation(LocationEntity("team-a", DEMO_SESSION_ID, "Alya", "Team A", -6.9160, 107.6110, 12f, 4f, 120f, now, TrackingState.LIVE, 84))
        dao.upsertLocation(LocationEntity("team-b", DEMO_SESSION_ID, "Bima", "Team B", -6.9290, 107.6190, 18f, 0f, 0f, now - 125_000, TrackingState.LIVE, 51))
        dao.upsertLocation(LocationEntity("team-c", DEMO_SESSION_ID, "Citra", "Team C", -6.9400, 107.6320, 10f, 0f, 0f, now, TrackingState.PAUSED, 73))
    }

    companion object { const val DEMO_SESSION_ID = "demo-session"; const val DEVICE_PARTICIPANT_ID = "this-device" }
}
