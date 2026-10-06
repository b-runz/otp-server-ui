package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.model.TimeMode
import org.junit.jupiter.api.Test
import org.opentripplanner.core.model.basic.Cost
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.search.TraverseMode

/**
 * [List<Itinerary>.fastest]'s behavioral test, using a real query confirmed directly (throwaway
 * diagnostic code, not committed) against this project's own fixture graph where
 * [bringBike]'s own result list places its direct (non-transit) itinerary first (5519s) even
 * though a transit alternative (3970s) is faster -- the exact bug `.firstOrNull()` call sites used
 * to fall into (see [fastest]'s own KDoc).
 */
class FastestTest {
    @Test
    fun `fastest picks the quicker transit itinerary over a slower direct route that happens to sort first`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.08, 10.2)
        val destination = WgsCoordinate(56.24, 10.1)
        val departure = ZonedDateTime.of(2026, 9, 13, 8, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toInstant()

        val itineraries = bringBike(engine, origin, destination, TimeMode.DEPART_AT, departure, maxTransfers = null)

        // Confirms this test isn't vacuous: the direct route really is first and really is slower.
        assertThat(itineraries.first().legs().none { it.isTransitLeg }).isTrue()
        assertThat(itineraries.size).isAtLeast(2)

        val fastest = itineraries.fastest()

        assertThat(fastest).isNotNull()
        assertThat(fastest!!.endTimeAsInstant()).isLessThan(itineraries.first().endTimeAsInstant())
        assertThat(itineraries.all { it.endTimeAsInstant() >= fastest.endTimeAsInstant() }).isTrue()
    }

    // Synthetic legs (same technique as SearchRouteTest.kt's own streetLeg/syntheticItinerary
    // helpers) -- reproduces the real bug directly: a later-departing itinerary with a shorter own
    // duration must never be picked over an earlier-departing one that arrives sooner overall. This
    // is exactly the shape that made a real hub-preferred Bring Bike search skip a real, well-timed
    // bus 121 connection at Rønde Busterminal in favor of a bus over an hour later (see fastest's
    // own KDoc) -- confirmed by reverting this function to `minByOrNull { totalDuration() }` and
    // watching this test fail.
    @Test
    fun `fastest picks the itinerary that arrives soonest, not the one with the shortest own duration`() {
        val start = ZonedDateTime.of(2026, 9, 13, 8, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))

        // Departs now, a 90-minute ride, arrives at 09:30.
        val soonerButLonger = syntheticItinerary(start, start.plusMinutes(90))
        // Departs an hour later, only a 60-minute ride, but still arrives later overall (10:00).
        val laterButShorter = syntheticItinerary(start.plusMinutes(60), start.plusMinutes(120))

        val fastest = listOf(laterButShorter, soonerButLonger).fastest()

        assertThat(fastest).isSameInstanceAs(soonerButLonger)
    }

    @Test
    fun `fastest returns null for an empty list`() {
        assertThat(emptyList<Itinerary>().fastest()).isNull()
    }

    private fun syntheticItinerary(start: ZonedDateTime, end: ZonedDateTime): Itinerary {
        val leg = StreetLeg.of()
            .withMode(TraverseMode.WALK)
            .withStartTime(start)
            .withEndTime(end)
            .withDistanceMeters(1_000.0)
            .build()
        return Itinerary.ofDirect(listOf(leg)).withGeneralizedCost(Cost.costOfSeconds(1)).build()
    }
}
