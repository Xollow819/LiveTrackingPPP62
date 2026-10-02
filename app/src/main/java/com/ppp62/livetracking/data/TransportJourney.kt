package com.ppp62.livetracking.data

import java.util.Locale
import java.util.UUID

enum class TransportPhase(val label: String) { START("Transport start"), END("Transport end") }

/** One start and one end record per student/session, including offline retries. */
object TransportJourney {
    fun recordId(sessionId: String, userId: String, phase: TransportPhase): String =
        UUID.nameUUIDFromBytes("ppp62:transport:$sessionId:$userId:${phase.name}".toByteArray(Charsets.UTF_8)).toString()

    fun phase(id: String?, sessionId: String, userId: String): TransportPhase? =
        TransportPhase.entries.firstOrNull { recordId(sessionId, userId, it) == id }

    fun ordered(checkpoints: List<CheckpointEntity>) = checkpoints.sortedWith(compareBy({ it.orderIndex }, { it.id }))
    fun endpoint(checkpoints: List<CheckpointEntity>, phase: TransportPhase): CheckpointEntity? =
        ordered(checkpoints).let { if (phase == TransportPhase.START) it.firstOrNull() else it.lastOrNull() }

    fun notes(phase: TransportPhase, notes: String) = listOf(phase.label, notes.trim()).filter { it.isNotEmpty() }.joinToString("\n")
}

/** Monotonic while booted; wall-clock fallback only after a device reboot. */
data class JourneyTiming(
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val accumulatedMillis: Long = 0,
    val runningSinceWall: Long = 0,
    val runningSinceElapsed: Long = 0,
    val bootCount: Int = -1,
) {
    val running get() = runningSinceWall > 0 && finishedAt == 0L
    fun elapsed(wall: Long, monotonic: Long, boot: Int): Long = accumulatedMillis + if (!running) 0 else {
        if (boot == bootCount && monotonic >= runningSinceElapsed) monotonic - runningSinceElapsed
        else (wall - runningSinceWall).coerceAtLeast(0)
    }
    fun resume(wall: Long, monotonic: Long, boot: Int): JourneyTiming =
        if (running || finishedAt > 0) this else copy(startedAt = startedAt.takeIf { it > 0 } ?: wall,
            runningSinceWall = wall, runningSinceElapsed = monotonic, bootCount = boot)
    fun pause(wall: Long, monotonic: Long, boot: Int) = copy(
        accumulatedMillis = elapsed(wall, monotonic, boot), runningSinceWall = 0, runningSinceElapsed = 0)
    fun finish(wall: Long, monotonic: Long, boot: Int): JourneyTiming =
        if (startedAt == 0L || finishedAt > 0) this else pause(wall, monotonic, boot).copy(finishedAt = wall)

    companion object {
        fun format(millis: Long): String {
            val seconds = millis.coerceAtLeast(0) / 1000
            return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
    }
}
