package one.otpserverui.model

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test

/**
 * [Itinerary.hasLongWalkEgress]'s own regression coverage -- the UI-facing flag
 * [one.otpserverui.ui.ResultsList]'s `LongWalkNotice` uses to surface a real, correct itinerary
 * [one.otpserverui.routing.parkandride.ParkAndRideFinder]'s two-tier egress search now finds
 * instead of silently returning no route (see `MorkeSkovstienRealDataTest` for the real,
 * full-Denmark-data proof this is reachable in practice).
 */
class ItineraryTest {
    private val baseTime: OffsetDateTime = OffsetDateTime.parse("2026-09-30T10:00:00+02:00")

    private fun leg(mode: String, durationSeconds: Double, startOffsetSeconds: Long): Leg = Leg(
        mode = mode,
        distanceMeters = 0.0,
        durationSeconds = durationSeconds,
        fromLat = 0.0,
        fromLon = 0.0,
        toLat = 0.0,
        toLon = 0.0,
        fromName = null,
        toName = null,
        routeShortName = null,
        routeGtfsId = null,
        legGeometryPoints = null,
        departureTime = baseTime.plusSeconds(startOffsetSeconds),
        arrivalTime = baseTime.plusSeconds(startOffsetSeconds + durationSeconds.toLong()),
    )

    @Test
    fun `final walk leg longer than the strict egress cap is flagged`() {
        val itinerary = Itinerary(
            legs = listOf(
                leg("BICYCLE", durationSeconds = 300.0, startOffsetSeconds = 0),
                leg("BUS", durationSeconds = 600.0, startOffsetSeconds = 300),
                leg("WALK", durationSeconds = Itinerary.LONG_WALK_EGRESS_SECONDS + 1.0, startOffsetSeconds = 900),
            ),
        )

        assertThat(itinerary.hasLongWalkEgress).isTrue()
    }

    @Test
    fun `final walk leg at or under the strict egress cap is not flagged`() {
        val itinerary = Itinerary(
            legs = listOf(
                leg("BICYCLE", durationSeconds = 300.0, startOffsetSeconds = 0),
                leg("BUS", durationSeconds = 600.0, startOffsetSeconds = 300),
                leg("WALK", durationSeconds = Itinerary.LONG_WALK_EGRESS_SECONDS, startOffsetSeconds = 900),
            ),
        )

        assertThat(itinerary.hasLongWalkEgress).isFalse()
    }

    @Test
    fun `a long final leg that is not a walk is not flagged`() {
        // A long final BICYCLE leg (e.g. Bring Bike's own direct route) is not a Park & Ride
        // egress walk -- hasLongWalkEgress must only react to the WALK case it exists for.
        val itinerary = Itinerary(
            legs = listOf(
                leg("BICYCLE", durationSeconds = Itinerary.LONG_WALK_EGRESS_SECONDS + 1000.0, startOffsetSeconds = 0),
            ),
        )

        assertThat(itinerary.hasLongWalkEgress).isFalse()
    }
}
