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
    fun `nearbyRoutes widens the search when the strict radius finds nothing`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        // Same real long-walk-egress point ParkAndRideFinderTest already confirmed: its nearest
        // stop is beyond a 15-minute walk, so a strict, small nearby-routes radius finds nothing --
        // confirmed directly (throwaway diagnostic code, not committed): 500m and 1000m both find
        // zero routes against this fixture, while 2000m+ finds real ones. A real rural destination
        // (Skovstien 5, Mørke) hit this exact gap against the full production graph: its nearest
        // route's own path is ~3.7km away, well past the UI's default 500m radius, and
        // nearbyRoutes had no fallback at all -- it just returned an empty list.
        val destination = WgsCoordinate(56.245, 10.18)

        val routes = nearbyRoutes(engine, destination, radiusMeters = 500.0)

        assertThat(routes).isNotEmpty()
    }

    @Test
    fun `nearbyRoutes finds a route whose path passes close by even when every one of its own stops is far away`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        // Real point confirmed directly (throwaway diagnostic code, not committed) against this
        // project's own fixture: route "BLÅ" passes just 88m away here, but stop-based discovery
        // (stopsNear, this function's old candidate-discovery mechanism) finds zero stops within
        // 500m -- the route has no stop anywhere near this exact stretch of its own path, so the
        // old mechanism could never have found it regardless of how far the radius was widened.
        val destination = WgsCoordinate(56.21, 10.13)

        // Confirms this test isn't vacuous: the old stop-based mechanism really does fail here.
        assertThat(engine.stopsNear(destination, 500.0)).isEmpty()

        val routes = nearbyRoutes(engine, destination, radiusMeters = 500.0)

        assertThat(routes.any { it.routeShortName == "BLÅ" }).isTrue()
    }
}
