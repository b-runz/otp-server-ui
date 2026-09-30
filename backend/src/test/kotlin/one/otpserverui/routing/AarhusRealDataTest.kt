package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import org.junit.jupiter.api.Test
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

class AarhusRealDataTest {
    @Test
    fun `directRoute finds the known real Aarhus bike route`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val request = engine.requestBuilder()
            .withFrom(org.opentripplanner.model.GenericLocation.fromCoordinate(origin))
            .withTo(org.opentripplanner.model.GenericLocation.fromCoordinate(destination))
            .withDateTime(java.time.ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, java.time.ZoneId.of("Europe/Copenhagen")).toInstant())
            .buildRequest()

        val itineraries = engine.directRoute(origin, destination, StreetMode.BIKE, request)

        assertThat(itineraries).hasSize(1)
        val leg = itineraries.single().legs().single() as StreetLeg
        assertThat(leg.mode.name).isEqualTo("BICYCLE")
        // Measured directly against this project's own fixture (not bikebus's 1820.95m/474s):
        // this graph was built by a different toolchain (real standalone OTP's own GraphBuilder,
        // vs. bikebus's custom Python pipeline), so a slightly different real shortest path over
        // the same real Aarhus OSM data is expected, not a bug.
        assertThat(leg.distanceMeters()).isWithin(1.0).of(1816.58)
        assertThat(leg.duration().seconds).isEqualTo(425L)
    }
}
