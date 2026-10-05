package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.model.Leg
import one.otpserverui.model.TimeMode
import org.junit.jupiter.api.Test
import org.opentripplanner.street.geometry.WgsCoordinate

/**
 * [connectToRoute]'s behavioral acceptance test, direct (not over HTTP -- see
 * [one.otpserverui.api.DropMeOffRouteTest] for the HTTP-layer test).
 *
 * Reuses [BringBikeViaTest]'s own already-confirmed-real query: origin Aarhus Banegårdsplads
 * (56.1507, 10.2037) to destination (56.1456, 10.2290), departing 2026-09-13T08:00
 * Europe/Copenhagen, riding real route `1:24004_3` (shortName "17") via its own real stops
 * `1:000751300702`/`1:000751414201` -- that test already confirmed directly (throwaway diagnostic
 * code, same technique used here) that a via-constraint on these exact stop ids returns an
 * itinerary that really rides this route, so [connectToRoute]'s own `connectViaRoute` step (the
 * same [bringBike] + [one.otpserverui.domain.NearbyRoutesFinder.pickCheapestQualifying]
 * composition) has a real, non-fabricated case to succeed on here.
 */
class ConnectToRouteTest {
    private fun loadEngine(): RoutingEngine {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
    }

    private val origin = WgsCoordinate(56.1507, 10.2037) // Aarhus Banegårdsplads
    private val destination = WgsCoordinate(56.1456, 10.2290)
    private val departure = ZonedDateTime.of(2026, 9, 13, 8, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toInstant()

    private val targetRouteGtfsId = "1:24004_3" // shortName "17"
    private val targetRouteStopIds = listOf("1:000751300702", "1:000751414201")

    @Test
    fun `a real reachable route returns a non-null result that really rides it, with flag-stop info`() {
        val engine = loadEngine()
        val hubs = HubCatalog.load()

        val result = connectToRoute(
            engine, hubs, origin, destination, targetRouteGtfsId, targetRouteStopIds,
            TimeMode.DEPART_AT, departure, preferHubs = false,
        )

        assertThat(result).isNotNull()
        assertThat(result!!.legs.any { it.routeGtfsId == targetRouteGtfsId }).isTrue()
        // The final leg of this real query is a street leg (WALK or BICYCLE) all the way to the
        // destination -- confirmFlagStop's own guard, ported verbatim from bikebus -- so a real flag
        // point really is found along the bus leg's own path and a real direct comparison query
        // succeeds from it.
        assertThat(result.flagStopInfo).isNotNull()
    }

    @Test
    fun `a routeGtfsId that no reachable itinerary rides returns null, not a crash`() {
        val engine = loadEngine()
        val hubs = HubCatalog.load()

        // A well-formed but genuinely nonexistent route id -- the real target route's own stop ids
        // are still used as the via constraint (so the search itself resolves normally), but no
        // itinerary in the batch ever rides this route, so pickCheapestQualifying's own filter
        // leaves nothing -- the real, honest "nothing qualifies" case, not a fabricated one.
        val result = connectToRoute(
            engine, hubs, origin, destination, "1:does_not_exist_999", targetRouteStopIds,
            TimeMode.DEPART_AT, departure, preferHubs = false,
        )

        assertThat(result).isNull()
    }
}

/**
 * Synthetic, graph-free unit tests for [chooseConnection]/[trimAppHubConnector]/[isAppStreetLeg] --
 * the fix-round addition requested by Task 19's review (the full `connectToRoute(preferHubs=true)`
 * flow against a live fixture-graph hub split stays out of scope, same as before; see this task's
 * report). Mirrors [HubRoutingTest]'s own synthetic-leg approach for
 * [HubRouting.choose]/[HubRouting.trimHubConnector] -- the OTP-native-typed logic these two
 * functions are a verbatim port of across the app-level [Leg] type boundary (see [chooseConnection]
 * and [trimAppHubConnector]'s own KDoc in DropMeOff.kt) -- except [Leg] here is this project's own
 * plain data class (`one.otpserverui.model.Leg`), so fixtures are built directly via its
 * constructor instead of OTP's `StreetLeg.of()` builder.
 */
class ChooseConnectionTest {
    private val baseTime: OffsetDateTime = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toOffsetDateTime()

    // Placeholder from/to coordinates and names: chooseConnection/trimAppHubConnector/isAppStreetLeg
    // only ever read mode, distanceMeters, departureTime, and arrivalTime (via Itinerary's own
    // wall-clock totalDurationSeconds), so every other field just needs a well-formed, non-null
    // placeholder value.
    private fun leg(
        mode: String,
        distanceMeters: Double,
        departureTime: OffsetDateTime,
        arrivalTime: OffsetDateTime,
        routeGtfsId: String? = null,
    ): Leg = Leg(
        mode = mode,
        distanceMeters = distanceMeters,
        durationSeconds = java.time.Duration.between(departureTime, arrivalTime).seconds.toDouble(),
        fromLat = 56.15,
        fromLon = 10.20,
        toLat = 56.16,
        toLon = 10.21,
        fromName = "Somewhere",
        toName = "Somewhere else",
        routeShortName = routeGtfsId?.let { "X" },
        routeGtfsId = routeGtfsId,
        legGeometryPoints = null,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
    )

    @Test
    fun `chooseConnection keeps the stitched legs when their cost is within the 600s budget over baseline`() {
        val baseline = listOf(leg("BUS", 5000.0, baseTime, baseTime.plusSeconds(1000)))
        // 500s more than baseline -- within MAX_ACCEPTABLE_DETOUR_SECONDS (600s).
        val stitched = listOf(leg("BUS", 5000.0, baseTime, baseTime.plusSeconds(1500)))

        val (chosen, hubName) = chooseConnection(baseline, stitched, "Aarhus Banegårdsplads")

        assertThat(hubName).isEqualTo("Aarhus Banegårdsplads")
        assertThat(chosen).isEqualTo(stitched)
    }

    @Test
    fun `chooseConnection falls back to baseline when stitched costs more than 600s over baseline`() {
        val baseline = listOf(leg("BUS", 5000.0, baseTime, baseTime.plusSeconds(1000)))
        // 700s more than baseline -- over MAX_ACCEPTABLE_DETOUR_SECONDS (600s).
        val stitched = listOf(leg("BUS", 5000.0, baseTime, baseTime.plusSeconds(1700)))

        val (chosen, hubName) = chooseConnection(baseline, stitched, "Aarhus Banegårdsplads")

        assertThat(hubName).isNull()
        assertThat(chosen).isEqualTo(baseline)
    }

    @Test
    fun `chooseConnection falls back to baseline when stitchedLegs is null`() {
        val baseline = listOf(leg("BUS", 5000.0, baseTime, baseTime.plusSeconds(1000)))

        val (chosen, hubName) = chooseConnection(baseline, null, "unused")

        assertThat(hubName).isNull()
        assertThat(chosen).isEqualTo(baseline)
    }

    @Test
    fun `trimAppHubConnector drops a sub-50m street leg at the start when fromEnd is false`() {
        val connector = leg("WALK", 49.0, baseTime, baseTime.plusSeconds(60))
        val kept = leg("BICYCLE", 200.0, baseTime.plusSeconds(60), baseTime.plusSeconds(300))

        val trimmed = trimAppHubConnector(listOf(connector, kept), fromEnd = false)

        assertThat(trimmed).containsExactly(kept)
    }

    @Test
    fun `trimAppHubConnector drops a sub-50m street leg at the end when fromEnd is true`() {
        val kept = leg("BICYCLE", 200.0, baseTime, baseTime.plusSeconds(240))
        val connector = leg("WALK", 49.0, baseTime.plusSeconds(240), baseTime.plusSeconds(300))

        val trimmed = trimAppHubConnector(listOf(kept, connector), fromEnd = true)

        assertThat(trimmed).containsExactly(kept)
    }

    @Test
    fun `trimAppHubConnector leaves the legs unchanged when the edge leg is at least 50m`() {
        val notAConnector = leg("WALK", 50.0, baseTime, baseTime.plusSeconds(60))
        val kept = leg("BICYCLE", 200.0, baseTime.plusSeconds(60), baseTime.plusSeconds(300))
        val legs = listOf(notAConnector, kept)

        val trimmed = trimAppHubConnector(legs, fromEnd = false)

        assertThat(trimmed).isEqualTo(legs)
    }

    @Test
    fun `trimAppHubConnector leaves a short transit-mode edge leg unchanged`() {
        // Same sub-50m distance as a real connector, but isAppStreetLeg only matches WALK/BICYCLE --
        // a short transit leg (e.g. a one-stop bus hop) is never treated as a hub connector.
        val kept = leg("BICYCLE", 200.0, baseTime, baseTime.plusSeconds(60))
        val shortTransit = leg("BUS", 10.0, baseTime.plusSeconds(60), baseTime.plusSeconds(120), routeGtfsId = "1:fake")
        val legs = listOf(kept, shortTransit)

        val trimmed = trimAppHubConnector(legs, fromEnd = true)

        assertThat(trimmed).isEqualTo(legs)
    }

    @Test
    fun `isAppStreetLeg is true for WALK and BICYCLE and false for a transit mode`() {
        assertThat(isAppStreetLeg(leg("WALK", 10.0, baseTime, baseTime.plusSeconds(10)))).isTrue()
        assertThat(isAppStreetLeg(leg("BICYCLE", 10.0, baseTime, baseTime.plusSeconds(10)))).isTrue()
        assertThat(isAppStreetLeg(leg("BUS", 10.0, baseTime, baseTime.plusSeconds(10), routeGtfsId = "1:fake"))).isFalse()
    }
}
