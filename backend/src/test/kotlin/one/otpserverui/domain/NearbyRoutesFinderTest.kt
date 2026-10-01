package one.otpserverui.domain

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import one.otpserverui.model.Leg
import one.otpserverui.model.TransitHub
import org.junit.jupiter.api.Test

/**
 * [NearbyRoutesFinder]'s three Task 18 additions (`pickCheapestQualifying`/`findSplitForRoute`/
 * `buildFlagStopInfo`), ported adapted from bikebus's own `NearbyRoutesFinderTest`-equivalent logic
 * against this project's own app-level [Leg] (Task 12) -- a plain data class, so these are
 * synthetic, hand-built instances, not a live fixture search, the same way [one.otpserverui.routing
 * .HubRoutingTest] builds synthetic OTP `StreetLeg`s for `HubRouting.trimHubConnector`.
 */
class NearbyRoutesFinderTest {

    private val t0: OffsetDateTime = OffsetDateTime.parse("2026-09-13T16:00:00+02:00")

    private fun leg(
        mode: String,
        routeGtfsId: String? = null,
        durationSeconds: Double = 60.0,
        distanceMeters: Double = 100.0,
        fromLat: Double = 56.15,
        fromLon: Double = 10.20,
        toLat: Double = 56.16,
        toLon: Double = 10.21,
    ): Leg = Leg(
        mode = mode,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        fromLat = fromLat,
        fromLon = fromLon,
        toLat = toLat,
        toLon = toLon,
        fromName = null,
        toName = null,
        routeShortName = null,
        routeGtfsId = routeGtfsId,
        legGeometryPoints = null,
        departureTime = t0,
        arrivalTime = t0.plusSeconds(durationSeconds.toLong()),
    )

    @Test
    fun `pickCheapestQualifying returns null when no itinerary rides the target route`() {
        val itineraries = listOf(
            listOf(leg("BUS", routeGtfsId = "1:other")),
            listOf(leg("BICYCLE")),
        )

        val result = NearbyRoutesFinder.pickCheapestQualifying(itineraries, "1:target")

        assertThat(result).isNull()
    }

    @Test
    fun `pickCheapestQualifying picks the cheapest among itineraries that do ride the target route`() {
        val cheap = listOf(leg("BUS", routeGtfsId = "1:target", durationSeconds = 100.0))
        val expensive = listOf(
            leg("BUS", routeGtfsId = "1:target", durationSeconds = 500.0),
            leg("WALK", durationSeconds = 200.0),
        )
        val notQualifying = listOf(leg("BUS", routeGtfsId = "1:other", durationSeconds = 1.0))

        val result = NearbyRoutesFinder.pickCheapestQualifying(listOf(expensive, cheap, notQualifying), "1:target")

        assertThat(result).isEqualTo(cheap)
    }

    @Test
    fun `findSplitForRoute returns null when the target route is not in the legs`() {
        val legs = listOf(leg("BICYCLE"), leg("BUS", routeGtfsId = "1:other"))

        val result = NearbyRoutesFinder.findSplitForRoute(legs, hubs = emptyList(), targetRouteGtfsId = "1:target")

        assertThat(result).isNull()
    }

    @Test
    fun `findSplitForRoute returns null when no transit-to-transit transfer is near a hub`() {
        // Single transit leg -- no adjacent transit-transit pair to split at all.
        val legs = listOf(leg("BICYCLE"), leg("BUS", routeGtfsId = "1:target"))
        val farHub = TransitHub("Far Hub", lat = 60.0, lon = 20.0, stopIds = listOf("1:far"))

        val result = NearbyRoutesFinder.findSplitForRoute(legs, hubs = listOf(farHub), targetRouteGtfsId = "1:target")

        assertThat(result).isNull()
    }

    @Test
    fun `findSplitForRoute finds the hub split and marks the route on the origin side`() {
        // alight (leg index 0, the target route) -> board (leg index 1) at a point very close to the
        // cataloged hub's own coordinate -- well within the 1km NEAR_STOP_RADIUS_METERS threshold.
        val alight = leg("BUS", routeGtfsId = "1:target", toLat = 56.1507, toLon = 10.2037)
        val board = leg("BUS", routeGtfsId = "1:other", fromLat = 56.1507, fromLon = 10.2037)
        val hub = TransitHub("Aarhus Banegårdsplads", lat = 56.1507, lon = 10.2037, stopIds = listOf("1:hub"))

        val result = NearbyRoutesFinder.findSplitForRoute(listOf(alight, board), hubs = listOf(hub), targetRouteGtfsId = "1:target")

        assertThat(result).isNotNull()
        assertThat(result!!.hub).isEqualTo(hub)
        assertThat(result.routeOnOriginSide).isTrue()
    }

    @Test
    fun `findSplitForRoute marks the route on the destination side when it rides after the split`() {
        val alight = leg("BUS", routeGtfsId = "1:other", toLat = 56.1507, toLon = 10.2037)
        val board = leg("BUS", routeGtfsId = "1:target", fromLat = 56.1507, fromLon = 10.2037)
        val hub = TransitHub("Aarhus Banegårdsplads", lat = 56.1507, lon = 10.2037, stopIds = listOf("1:hub"))

        val result = NearbyRoutesFinder.findSplitForRoute(listOf(alight, board), hubs = listOf(hub), targetRouteGtfsId = "1:target")

        assertThat(result).isNotNull()
        assertThat(result!!.routeOnOriginSide).isFalse()
    }

    @Test
    fun `buildFlagStopInfo combines the official and direct legs' own numbers`() {
        val officialLeg = leg("BUS", distanceMeters = 500.0, durationSeconds = 300.0)
        val directLeg = leg("BICYCLE", distanceMeters = 200.0, durationSeconds = 120.0)

        val info = NearbyRoutesFinder.buildFlagStopInfo(
            flagLat = 56.1, flagLon = 10.2, officialLeg = officialLeg, directLeg = directLeg,
        )

        assertThat(info.flagLat).isEqualTo(56.1)
        assertThat(info.flagLon).isEqualTo(10.2)
        assertThat(info.officialFinalLegDistanceMeters).isEqualTo(500.0)
        assertThat(info.officialFinalLegDurationSeconds).isEqualTo(300.0)
        assertThat(info.flagStopDistanceMeters).isEqualTo(200.0)
        assertThat(info.flagStopDurationSeconds).isEqualTo(120.0)
    }
}
