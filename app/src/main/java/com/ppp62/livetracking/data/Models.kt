package com.ppp62.livetracking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole { STUDENT, LECTURER }
enum class SessionStatus { DRAFT, ACTIVE, PAUSED, COMPLETED }
enum class TrackingState { LIVE, STALE, PAUSED, FINISHED }
enum class SyncState { PENDING, SYNCED, FLAGGED }
enum class FishCondition { GOOD, STRESSED, MORTALITY }

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val joinCode: String,
    val lecturerName: String,
    val status: SessionStatus,
    val startsAt: Long,
    val endsAt: Long?
)

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 75.0,
    val orderIndex: Int,
    val instructions: String,
    val requiresPhoto: Boolean = true,
    val requiresTemperature: Boolean = true,
    val requiresWeight: Boolean = true
)

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val id: String,
    val checkpointId: String,
    val sessionId: String,
    val studentName: String,
    val team: String,
    val temperatureC: Double,
    val weightKg: Double,
    val condition: FishCondition,
    val notes: String,
    val photoUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val distanceMeters: Double?,
    val createdAt: Long,
    val syncState: SyncState,
    val exceptionReason: String? = null
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val participantId: String,
    val sessionId: String,
    val participantName: String,
    val team: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val heading: Float,
    val recordedAt: Long,
    val trackingState: TrackingState,
    val batteryPercent: Int
)
