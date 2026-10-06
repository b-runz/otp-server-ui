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
        assertThat(fastest!!.totalDuration()).isLessThan(itineraries.first().totalDuration())
        assertThat(itineraries.all { it.totalDuration() >= fastest.totalDuration() }).isTrue()
    }

    @Test
    fun `fastest returns null for an empty list`() {
        assertThat(emptyList<org.opentripplanner.model.plan.Itinerary>().fastest()).isNull()
    }
}
