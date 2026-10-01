package com.ppp62.livetracking.data

import com.ppp62.livetracking.util.LocationUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class PPPRepository(private val dao: PPPDao) {
    val sessions = dao.observeSessions()
    private val session = MutableStateFlow("")
    @OptIn(ExperimentalCoroutinesApi::class) val checkpoints = session.flatMapLatest { dao.observeCheckpoints(it) }
    @OptIn(ExperimentalCoroutinesApi::class) val checkIns = session.flatMapLatest { dao.observeCheckIns(it) }
    @OptIn(ExperimentalCoroutinesApi::class) val locations = session.flatMapLatest { dao.observeLocations(it) }
    fun selectSession(id: String?) { session.value = id.orEmpty() }

    suspend fun submitCheckIn(
        checkpoint: CheckpointEntity,
        studentName: String,
        team: String,
        temperatureC: Double?,
        weightKg: Double?,
        condition: FishCondition,
        notes: String,
        photoUri: String?,
        latitude: Double?,
        longitude: Double?,
        userId: String
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
                syncState = SyncState.PENDING, exceptionReason = exception, userId = userId
            )
        )
    }

}
