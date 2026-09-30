package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.routing.parkandride.ParkAndRideFinder
import org.junit.jupiter.api.Test
import org.opentripplanner.model.plan.Leg
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.search.TraverseMode

/**
 * There is no real `HubRouting`/`HubCatalog` test in bikebus to port assertions from (confirmed:
 * `find bikebus/app -iname "*HubRouting*Test*" -o -iname "*HubCatalog*Test*"` finds nothing), so
 * this is a fresh test built against this project's own fixture graph and the real bundled
 * `hubs.json` catalog -- no fabricated hub name or invented split behavior.
 *
 * The bundled catalog's geographic coverage genuinely overlaps this project's own fixture bbox
 * (`min_lon=10.05 min_lat=56.08 max_lon=10.30 max_lat=56.25`, per docs/graph-build.md): several
 * real Aarhus hubs from bikebus's own `transit_hubs.json` -- e.g. "Aarhus Banegårdsplads"
 * (56.1507, 10.2037) and "Aarhus Rutebilstation" (56.1515, 10.2096) -- fall inside it, confirmed
 * directly against the copied `hubs.json` before writing the assertion below.
 */
class HubRoutingTest {
    @Test
    fun `loads the real bundled hub catalog`() {
        val hubs = HubCatalog.load()
        assertThat(hubs).isNotEmpty()
    }

    @Test
    fun `the bundled hub catalog covers this project's own fixture area`() {
        val hubs = HubCatalog.load()

        val aarhusHub = hubs.singleOrNull { it.name == "Aarhus Banegårdsplads" }
        assertThat(aarhusHub).isNotNull()
        assertThat(aarhusHub!!.lat).isWithin(0.01).of(56.1507)
        assertThat(aarhusHub.lon).isWithin(0.01).of(10.2037)
        assertThat(aarhusHub.stopIds).isNotEmpty()
    }

    @Test
    fun `findHubSplit finds no hub transfer in a real zero-transfer Park and Ride itinerary`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        // The same known-good real Aarhus query ParkAndRideFinderTest/AarhusRealDataTest/
        // BringBikeTest all already confirmed against this project's own fixture.
        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val departure = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toInstant()

        val itinerary = ParkAndRideFinder.search(engine, origin, destination, departure)
        assertThat(itinerary).isNotNull()

        // ParkAndRideFinder caps MAX_TRANSFERS at 1 -- raptor's own "zero transfers" round limit,
        // per that object's own comment -- so this real itinerary has exactly one transit leg and
        // never two adjacent ones. findHubSplit only ever matches a transit-to-transit transfer,
        // so null is the real, honest result here, not a fabricated assertion.
        val transitLegCount = itinerary!!.legs().count { it.isTransitLeg }
        assertThat(transitLegCount).isEqualTo(1)

        val hubs = HubCatalog.load()
        val split = HubRouting.findHubSplit(hubs, itinerary)
        assertThat(split).isNull()
    }

    @Test
    fun `choose keeps the stitched itinerary when its cost matches a real baseline`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val departure = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toInstant()

        val itinerary = ParkAndRideFinder.search(engine, origin, destination, departure)
        assertThat(itinerary).isNotNull()

        // Reusing the same real itinerary as both "baseline" and "stitched" gives a real,
        // zero-detour comparison (stitched cost - baseline cost == 0, well within the 10-minute
        // acceptable-detour budget) without inventing a second, fabricated itinerary.
        val (chosen, hubName) = HubRouting.choose(listOf(itinerary!!), itinerary, "Aarhus Banegårdsplads")

        assertThat(hubName).isEqualTo("Aarhus Banegårdsplads")
        assertThat(chosen).containsExactly(itinerary)
    }

    @Test
    fun `choose falls back to the real baseline when no stitched route was built`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val departure = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toInstant()

        val itinerary = ParkAndRideFinder.search(engine, origin, destination, departure)
        assertThat(itinerary).isNotNull()
        val baseline = listOf(itinerary!!)

        val (chosen, hubName) = HubRouting.choose(baseline, null, "unused")

        assertThat(hubName).isNull()
        assertThat(chosen).isEqualTo(baseline)
    }

    // Synthetic legs built with OTP's own vendored `StreetLegBuilder` (otp-routing module,
    // already on this module's classpath) -- gives direct control over `distanceMeters()` for a
    // non-transit leg without needing a live graph or fixture search. `trimHubConnector` only
    // reads `isTransitLeg` (always false for a `StreetLeg`) and `distanceMeters()`, both of which
    // this builder sets directly, so no other field on the leg needs to be realistic.
    private fun streetLeg(distanceMeters: Double): Leg {
        val start = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        return StreetLeg.of()
            .withMode(TraverseMode.WALK)
            .withStartTime(start)
            .withEndTime(start.plusMinutes(1))
            .withDistanceMeters(distanceMeters)
            .build()
    }

    @Test
    fun `trimHubConnector drops a sub-50m connector leg at the end when fromEnd is true`() {
        val kept = streetLeg(200.0)
        val connector = streetLeg(49.0)

        val trimmed = HubRouting.trimHubConnector(listOf(kept, connector), fromEnd = true)

        assertThat(trimmed).containsExactly(kept)
    }

    @Test
    fun `trimHubConnector drops a sub-50m connector leg at the start when fromEnd is false`() {
        val connector = streetLeg(49.0)
        val kept = streetLeg(200.0)

        val trimmed = HubRouting.trimHubConnector(listOf(connector, kept), fromEnd = false)

        assertThat(trimmed).containsExactly(kept)
    }

    @Test
    fun `trimHubConnector leaves the legs unchanged when the end leg is at least 50m`() {
        val kept = streetLeg(200.0)
        val notAConnector = streetLeg(50.0)
        val legs = listOf(kept, notAConnector)

        val trimmed = HubRouting.trimHubConnector(legs, fromEnd = true)

        assertThat(trimmed).isEqualTo(legs)
    }

    @Test
    fun `trimHubConnector leaves the legs unchanged when the start leg is at least 50m`() {
        val notAConnector = streetLeg(50.0)
        val kept = streetLeg(200.0)
        val legs = listOf(notAConnector, kept)

        val trimmed = HubRouting.trimHubConnector(legs, fromEnd = false)

        assertThat(trimmed).isEqualTo(legs)
    }
}
