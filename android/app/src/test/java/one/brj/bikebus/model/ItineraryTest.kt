package one.brj.bikebus.model

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ItineraryTest {
    private val baseTime = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)

    private fun leg(mode: String, distanceMeters: Double, durationSeconds: Double, departureTime: OffsetDateTime) = Leg(
        mode = mode, distanceMeters = distanceMeters, durationSeconds = durationSeconds,
        fromLat = 0.0, fromLon = 0.0, toLat = 0.0, toLon = 0.0,
        fromName = "A", toName = "B", routeShortName = null, departureTime = departureTime,
    )

    @Test
    fun `leg arrivalTime is departureTime plus durationSeconds`() {
        val l = leg("WALK", 100.0, 90.0, baseTime)
        assertEquals(baseTime.plusSeconds(90), l.arrivalTime)
    }

    @Test
    fun `itinerary departureTime and arrivalTime come from first and last leg`() {
        val legA = leg("BICYCLE", 500.0, 120.0, baseTime)
        val legB = leg("BUS", 0.0, 600.0, legA.arrivalTime)
        val itinerary = Itinerary(legs = listOf(legA, legB), exceedsBikeLimit = false, hasLongWalkEgress = false)
        assertEquals(baseTime, itinerary.departureTime)
        assertEquals(legB.arrivalTime, itinerary.arrivalTime)
        assertEquals(720.0, itinerary.totalDurationSeconds, 0.0)
    }

    @Test
    fun `totalBikeDistanceMeters sums only BICYCLE legs`() {
        val legs = listOf(
            leg("BICYCLE", 3000.0, 600.0, baseTime),
            leg("WALK", 200.0, 120.0, baseTime.plusMinutes(10)),
            leg("BICYCLE", 1500.0, 300.0, baseTime.plusMinutes(12)),
        )
        val itinerary = Itinerary(legs = legs, exceedsBikeLimit = false, hasLongWalkEgress = false)
        assertEquals(4500.0, itinerary.totalBikeDistanceMeters, 0.0)
    }

    @Test
    fun `exceedsBikeLimit and hasLongWalkEgress are passed through, not recomputed`() {
        val legs = listOf(leg("BICYCLE", 50_000.0, 600.0, baseTime))
        // A huge bike distance would exceed the client's old hardcoded 10km limit, but the
        // server is now the sole authority -- a false flag here must stay false.
        val itinerary = Itinerary(legs = legs, exceedsBikeLimit = false, hasLongWalkEgress = false)
        assertFalse(itinerary.exceedsBikeLimit)
    }
}
