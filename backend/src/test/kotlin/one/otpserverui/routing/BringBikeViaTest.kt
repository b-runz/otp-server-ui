package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.model.TimeMode
import org.junit.jupiter.api.Test
import org.opentripplanner.model.plan.leg.ScheduledTransitLeg
import org.opentripplanner.street.geometry.WgsCoordinate

/**
 * [bringBike]'s new via-stop-constraint/`numItineraries` behavior (Task 18).
 *
 * The origin/destination/departure below (Aarhus Banegårdsplads -> a real point further east) is a
 * fresh query, confirmed directly (throwaway diagnostic code, never committed) to make `bringBike`'s
 * own baseline (no via constraint) ride a real transit route -- unlike [BringBikeTest]'s own known
 * Aarhus query, whose own comment already documents that its transit itineraries are all dropped by
 * the filter chain in favor of the ~7-minute direct bike route. That diagnostic run found route
 * `1:24004_3` (short name "17") riding from stop `1:000751300702` to stop `1:000751414201` in one of
 * this query's own baseline itineraries -- both real gtfsIds read directly off that itinerary's own
 * legs, not guessed.
 */
class BringBikeViaTest {
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
    fun `baseline (no via constraint) really does ride the target route`() {
        val engine = loadEngine()

        val itineraries = bringBike(engine, origin, destination, TimeMode.DEPART_AT, departure)

        val ridesTargetRoute = itineraries.any { itinerary ->
            itinerary.legs().any { leg ->
                leg.isTransitLeg && leg is ScheduledTransitLeg && leg.trip()!!.route.id.toString() == targetRouteGtfsId
            }
        }
        assertThat(ridesTargetRoute).isTrue()
    }

    @Test
    fun `a via-stop constraint on the target route's own stops still returns an itinerary riding it`() {
        val engine = loadEngine()

        val itineraries = bringBike(
            engine, origin, destination, TimeMode.DEPART_AT, departure,
            viaStopIds = targetRouteStopIds, numItineraries = 20,
        )

        assertThat(itineraries).isNotEmpty()
        val ridesTargetRoute = itineraries.any { itinerary ->
            itinerary.legs().any { leg ->
                leg.isTransitLeg && leg is ScheduledTransitLeg && leg.trip()!!.route.id.toString() == targetRouteGtfsId
            }
        }
        assertThat(ridesTargetRoute).isTrue()
    }

    @Test
    fun `a via-stop constraint must never produce a direct (non-transit) itinerary`() {
        val engine = loadEngine()

        val itineraries = bringBike(
            engine, origin, destination, TimeMode.DEPART_AT, departure,
            viaStopIds = targetRouteStopIds, numItineraries = 20,
        )

        assertThat(itineraries.none { it.legs().none { leg -> leg.isTransitLeg } }).isTrue()
    }

    @Test
    fun `an obviously-wrong nonexistent via stop id returns an empty list, not a crash`() {
        val engine = loadEngine()

        // A well-formed ("feedId:id") but genuinely nonexistent stop id -- confirmed directly
        // (throwaway diagnostic code) that without this function's own unresolvable-via-stop guard,
        // real raptor's own RaptorRequestMapper throws IllegalArgumentException("At least one
        // connection must exist!") for this exact id against this fixture.
        val itineraries = bringBike(
            engine, origin, destination, TimeMode.DEPART_AT, departure,
            viaStopIds = listOf("1:999999999999"), numItineraries = 20,
        )

        assertThat(itineraries).isEmpty()
    }
}
