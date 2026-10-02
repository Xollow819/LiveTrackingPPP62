package com.ppp62.livetracking.data

import com.ppp62.livetracking.util.RoutePlanner
import org.junit.Assert.*
import org.junit.Test

class TransportJourneyTest {
    private fun checkpoint(id: String, order: Int, latitude: Double) =
        CheckpointEntity(id, "session", id, latitude, 106.8, orderIndex = order, instructions = "")

    @Test fun selectsDepartureAndArrivalInLecturerOrderIncludingOnePinRoutes() {
        val checkpoints = listOf(checkpoint("end", 3, -6.3), checkpoint("start", 1, -6.1), checkpoint("middle", 2, -6.2))
        assertEquals("start", TransportJourney.endpoint(checkpoints, TransportPhase.START)?.id)
        assertEquals("end", TransportJourney.endpoint(checkpoints, TransportPhase.END)?.id)
        assertEquals(listOf(-6.1, -6.2, -6.3), RoutePlanner.coordinates(checkpoints).map { it.latitude })
        val single = checkpoints.take(1)
        assertEquals(TransportJourney.endpoint(single, TransportPhase.START), TransportJourney.endpoint(single, TransportPhase.END))
        assertNull(TransportJourney.endpoint(emptyList(), TransportPhase.START))
    }

    @Test fun recordsRemainDistinctAndIdempotentAcrossRetriesAndStudents() {
        val start = TransportJourney.recordId("session", "student", TransportPhase.START)
        assertEquals(start, TransportJourney.recordId("session", "student", TransportPhase.START))
        assertNotEquals(start, TransportJourney.recordId("session", "student", TransportPhase.END))
        assertNotEquals(start, TransportJourney.recordId("session", "other", TransportPhase.START))
        assertNotEquals(start, TransportJourney.recordId("other", "student", TransportPhase.START))
        assertEquals(TransportPhase.START, TransportJourney.phase(start, "session", "student"))
        assertNull(TransportJourney.phase(start, "session", "other"))
    }

    @Test fun timerSurvivesRecreationAndWallClockChangesAndExcludesPausedTime() {
        val start = JourneyTiming().resume(10_000, 1000, 1)
        assertEquals(5000, start.elapsed(99_000, 6000, 1))
        val restored = start.copy()
        assertEquals(5000, restored.elapsed(15_000, 6000, 1))
        val paused = start.pause(15_000, 6000, 1)
        assertEquals(5000, paused.elapsed(25_000, 16_000, 1))
        val resumed = paused.resume(25_000, 16_000, 1)
        assertEquals(7000, resumed.elapsed(27_000, 18_000, 1))
        val finished = resumed.finish(27_000, 18_000, 1)
        assertEquals(7000, finished.elapsed(77_000, 68_000, 1))
        assertEquals(finished, finished.resume(77_000, 68_000, 1))
        assertEquals("00:00:07", JourneyTiming.format(finished.accumulatedMillis))
        assertEquals("25:01:01", JourneyTiming.format(90_061_000))
    }

    @Test fun timerRecoversAcrossRebootWithoutNegativeElapsedTime() {
        val start = JourneyTiming().resume(10_000, 50_000, 1)
        assertEquals(6000, start.elapsed(16_000, 1000, 2))
        assertEquals(0, start.elapsed(9000, 1000, 2))
        assertEquals(JourneyTiming(), JourneyTiming().finish(10_000, 1000, 1))
    }

    @Test fun routeDecoderPreservesLongitudeLatitudeAndRejectsInvalidServerGeometry() {
        val route = RoutePlanner.parseResponse("""{"code":"Ok","routes":[{"distance":1500,"geometry":{"coordinates":[[106.8,-6.1],[106.81,-6.2]]}}]}""")
        assertTrue(route.road)
        assertEquals(-6.1, route.points.first().latitude, .0001)
        assertEquals(106.8, route.points.first().longitude, .0001)
        assertEquals(1500.0, route.distanceMeters, .01)
        for (body in listOf("""{"code":"NoRoute"}""", """{"code":"Ok","routes":[{"distance":1,"geometry":{"coordinates":[[106,91],[107,92]]}}]}""")) {
            assertThrows(Exception::class.java) { RoutePlanner.parseResponse(body) }
        }
    }
}
