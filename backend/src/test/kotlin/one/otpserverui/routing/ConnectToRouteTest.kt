package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
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
