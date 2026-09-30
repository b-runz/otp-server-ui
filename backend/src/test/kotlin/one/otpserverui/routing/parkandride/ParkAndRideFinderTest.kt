package one.otpserverui.routing.parkandride

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.routing.ReachDirection
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.streetReach
import org.junit.jupiter.api.Test
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode
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

    @Test
    fun `a destination whose nearest stop is a long walk still returns a route`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        // A real coordinate inside this project's own fixture bbox (min_lon=10.05 min_lat=56.08
        // max_lon=10.30 max_lat=56.25, per docs/graph-build.md) -- open countryside near the
        // fixture's northern edge, well outside Aarhus's own dense stop coverage. Confirmed
        // directly (throwaway diagnostic code, not committed, run against this exact fixture):
        // its egress-side streetReach is empty through the strict 15-minute cap, but non-empty
        // by 60 minutes -- so ParkAndRideFinder's real two-tier fallback (widening all the way to
        // FALLBACK_WALK_EGRESS = 2 hours) is what actually finds this route, the same shape of
        // case bikebus's own MorkeSkovstienRealDataTest covers against its full-Denmark data.
        val longWalkDestination = WgsCoordinate(56.245, 10.18)
        val departure = java.time.Instant.parse("2026-09-13T14:00:00Z")

        val request = engine.requestBuilder()
            .withFrom(GenericLocation.fromCoordinate(origin))
            .withTo(GenericLocation.fromCoordinate(longWalkDestination))
            .withDateTime(departure)
            .buildRequest()

        // The egress side genuinely has no stop within ParkAndRideFinder's strict 15-minute cap --
        // confirms this test does not pass vacuously (a destination that already resolved within
        // the strict cap would never touch the fallback branch at all).
        val strictEgress = engine.streetReach(
            longWalkDestination, StreetMode.WALK, Duration.ofMinutes(15), ReachDirection.EGRESS, request,
        )
        assertThat(strictEgress).isEmpty()

        val itinerary = ParkAndRideFinder.search(engine, origin, longWalkDestination, departure)

        assertThat(itinerary).isNotNull()
        assertThat(itinerary!!.legs().last().isTransitLeg).isFalse()
        // The final egress leg's own duration exceeds the strict cap -- direct evidence the
        // fallback-tier egress search (not the strict one) is what produced this leg.
        assertThat(itinerary.legs().last().duration()).isGreaterThan(Duration.ofMinutes(15))
    }

    @Test
    fun `honest empty result when no bikeable stop is within reach`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val departure = java.time.ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, java.time.ZoneId.of("Europe/Copenhagen")).toInstant()

        // Far outside this project's own fixture area (bbox min_lon=10.05 min_lat=56.08
        // max_lon=10.30 max_lat=56.25) -- no street data at all here, so no bikeable stop can ever
        // be found. A correct implementation returns null, not a fabricated walk-only result (the
        // exact bug bikebus's own 2026-07-31 spec redesign fixed, ported here verbatim).
        val unreachableOrigin = WgsCoordinate(59.9, 10.7) // Oslo
        val itinerary = ParkAndRideFinder.search(engine, unreachableOrigin, destination, departure)

        assertThat(itinerary).isNull()
    }
}
