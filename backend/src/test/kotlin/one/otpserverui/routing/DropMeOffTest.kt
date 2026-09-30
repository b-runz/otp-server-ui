package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import org.junit.jupiter.api.Test
import org.opentripplanner.street.geometry.WgsCoordinate

class DropMeOffTest {
    @Test
    fun `nearbyRoutes finds real routes near a known Aarhus stop`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val destination = WgsCoordinate(56.171798, 10.172087)
        val routes = nearbyRoutes(engine, destination, radiusMeters = 500.0)

        assertThat(routes).isNotEmpty()
    }

    @Test
    fun `connectByFlaggingABus finds a real direct route to the flag point`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val flagPoint = WgsCoordinate(56.171798, 10.172087)
        val destination = WgsCoordinate(56.165518, 10.185262)
        val itinerary = connectByFlaggingABus(engine, flagPoint, destination, java.time.Instant.parse("2026-09-13T14:00:00Z"))

        assertThat(itinerary).isNotNull()
    }
}
