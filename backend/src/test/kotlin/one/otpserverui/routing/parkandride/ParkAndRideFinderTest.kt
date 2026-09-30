package one.otpserverui.routing.parkandride

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.search.TraverseMode

/**
 * [ParkAndRideFinder]'s behavioral acceptance test, ported from bikebus's own
 * `ParkAndRideFinderTest` -- reuses the same real Aarhus origin/destination/service-date query
 * bikebus's `ParkAndRideFixture` uses (also already confirmed, in this project's own
 * `AarhusRealDataTest`, to yield a real bike route against *this* project's fixture graph).
 */
class ParkAndRideFinderTest {
    @Test
    fun `real bike-then-transit itinerary for the known Aarhus trip`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val departure = java.time.ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, java.time.ZoneId.of("Europe/Copenhagen")).toInstant()

        val itinerary = ParkAndRideFinder.search(engine, origin, destination, departure)

        assertThat(itinerary).isNotNull()
        assertThat(itinerary!!.legs().any { it.isTransitLeg }).isTrue()

        // Assert the actual mode of the access leg, not just "any non-transit leg" -- that looser
        // assertion would also pass if bike access silently degraded to walking.
        val accessLeg = itinerary.legs().first()
        assertThat(accessLeg.isTransitLeg).isFalse()
        assertThat((accessLeg as StreetLeg).mode).isEqualTo(TraverseMode.BICYCLE)
    }
}
