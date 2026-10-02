package com.ppp62.livetracking.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row in public.tracking_sessions */
@Serializable
data class SessionRow(
    val id: String,
    val code: String,
    val title: String = "",
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

/** Row in public.session_participants */
@Serializable
data class ParticipantRow(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    val role: String = "student",
    val team: String = "",
    @SerialName("joined_at") val joinedAt: String? = null
)

/** Row in public.live_positions (one per student per session, upserted). */
@Serializable
data class LivePositionRow(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
    val team: String = "",
    @SerialName("tracking_state") val trackingState: String = "LIVE",
    @SerialName("marker_type") val markerType: String = "motorcycle",
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("event_at") val eventAt: String? = null
)

/** Row in public.submissions */
@Serializable
data class SubmissionRow(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("checkpoint_id") val checkpointId: String? = null,
    @SerialName("exception_reason") val exceptionReason: String? = null,
    val team: String = "",
    @SerialName("checkpoint_name") val checkpointName: String = "",
    val note: String = "",
    @SerialName("photo_path") val photoPath: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("temperature_c") val temperatureC: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("total_fish") val totalFish: Int? = null,
    @SerialName("ph") val ph: Double? = null,
    @SerialName("dissolved_oxygen") val dissolvedOxygen: Double? = null,
    val condition: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** Row in public.checkpoints */
@Serializable
data class CheckpointRow(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    @SerialName("radius_m") val radiusM: Double = 50.0,
    @SerialName("order_index") val orderIndex: Int = 0,
    val instructions: String = "",
    @SerialName("requires_photo") val requiresPhoto: Boolean = true,
    @SerialName("requires_temperature") val requiresTemperature: Boolean = true,
    @SerialName("requires_weight") val requiresWeight: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)
