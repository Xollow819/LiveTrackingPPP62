package com.ppp62.livetracking.data

data class ParticipantMarker(val id: String, val label: String, val emoji: String)

object ParticipantMarkers {
    val options = listOf(
        ParticipantMarker("motorcycle", "Motorcycle", "🏍️"),
        ParticipantMarker("car", "Car", "🚗"),
        ParticipantMarker("horse", "Horse", "🐎"),
        ParticipantMarker("bicycle", "Bicycle", "🚲"),
        ParticipantMarker("bus", "Bus", "🚌"),
        ParticipantMarker("walking", "Walking", "🚶")
    )
    val default = options.first().id
    fun preferenceKey(sessionId: String, userId: String) = "$sessionId:$userId"
    fun get(id: String) = options.firstOrNull { it.id == id } ?: options.first()
}
