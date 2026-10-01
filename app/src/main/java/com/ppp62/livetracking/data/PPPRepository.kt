package com.ppp62.livetracking.data

import com.ppp62.livetracking.util.LocationUtils
import kotlinx.coroutines.*
import java.util.UUID

class PPPRepository(private val dao: PPPDao) {
    val sessions = dao.observeSessions()
    val checkpoints = dao.observeCheckpoints(LOCAL_SESSION_ID)
    val checkIns = dao.observeCheckIns(LOCAL_SESSION_ID)
    val locations = dao.observeLocations(LOCAL_SESSION_ID)

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
        dao.upsertCheckpoint(CheckpointEntity(UUID.randomUUID().toString(), LOCAL_SESSION_ID, name.trim(), latitude, longitude, radius, order, instructions.trim()))
    }

    suspend fun setTrackingState(participantId: String, state: TrackingState) = dao.setTrackingState(participantId, state, System.currentTimeMillis())

    companion object { const val LOCAL_SESSION_ID = "local-session"; const val DEVICE_PARTICIPANT_ID = "this-device" }
}
