package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.otpserverui.GraphLoader
import one.otpserverui.model.TransitHub
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.parkandride.ParkAndRideFinder
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.LineString
import org.opentripplanner.core.model.basic.Cost
import org.opentripplanner.core.model.i18n.NonLocalizedString
import org.opentripplanner.model.fare.FareOffer
import org.opentripplanner.model.plan.Emission
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.model.plan.Leg
import org.opentripplanner.model.plan.Place
import org.opentripplanner.model.plan.leg.LegCallTime
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.routing.alertpatch.TransitAlert
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.search.TraverseMode

private fun testEngine(): RoutingEngine {
    val fixturePath = checkNotNull(object {}.javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
}

class SearchRouteTest {
    @Test
    fun `POST search bring_bike returns a real itinerary`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            assertThat(body.itineraries).isNotEmpty()
        }
    }

    @Test
    fun `POST search with an out-of-coverage destination returns the typed no_coverage error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":59.9,"destinationLon":10.7,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("no_coverage")
        }
    }

    @Test
    fun `POST search with an unknown mode returns a typed 400 error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"teleport","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("unknown_mode")
        }
    }

    @Test
    fun `POST search with park_and_ride and arrive_by returns a typed 400 error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"park_and_ride","timeMode":"arrive_by",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("unsupported_time_mode")
        }
    }

    @Test
    fun `POST search with an invalid timeMode returns a typed 400 error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"nonsense",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("invalid_time_mode")
        }
    }

    @Test
    fun `POST search with an unparseable datetime returns a typed 400 error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"not-a-real-datetime","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("invalid_request")
        }
    }

    // Synthetic legs built with OTP's own vendored `StreetLegBuilder`, same technique
    // `HubRoutingTest.kt`'s `streetLeg` helper uses for `trimHubConnector`'s own tests --
    // `stitchItineraries` only feeds these legs through `HubRouting.trimHubConnector` (which reads
    // `isTransitLeg`, always false for a `StreetLeg`, and `distanceMeters()`) before concatenating
    // and rebuilding, so no other field on the leg needs to be realistic.
    private fun streetLeg(distanceMeters: Double): Leg {
        val start = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        return StreetLeg.of()
            .withMode(TraverseMode.WALK)
            .withStartTime(start)
            .withEndTime(start.plusMinutes(1))
            .withDistanceMeters(distanceMeters)
            .build()
    }

    // `Itinerary.build()` NPEs unless `withGeneralizedCost(...)` is called first (the bug
    // `stitchItineraries` itself works around) -- so every synthetic itinerary built for these
    // tests must supply one explicitly.
    private fun syntheticItinerary(legs: List<Leg>, generalizedCostSeconds: Int): Itinerary =
        Itinerary.ofDirect(legs).withGeneralizedCost(Cost.costOfSeconds(generalizedCostSeconds)).build()

    // Real origin/destination confirmed directly (throwaway diagnostic code, not committed) against
    // this project's own tiny fixture graph: an unconstrained (maxTransfers = null) Park & Ride
    // search finds a real two-transfer itinerary whose first transfer happens at "Park Allé/
    // Rådhuset (Aarhus Kom)", a stop within HubRouting's own 1000m radius of this project's real
    // "Aarhus Banegårdsplads" hub (hubs.json) -- a genuine hub-split case, not a fabricated one.
    // This exact scenario did not exist before this task: Park & Ride was previously hardcoded to
    // zero transfers, so a transit-to-transit transfer (what HubRouting.findHubSplit matches
    // against) could never occur on a Park & Ride itinerary at all.
    private val parkAndRideHubOrigin = WgsCoordinate(56.08, 10.2)
    private val parkAndRideHubDestination = WgsCoordinate(56.24, 10.1)
    private val parkAndRideHubDeparture =
        ZonedDateTime.of(2026, 9, 13, 8, 0, 0, 0, ZoneId.of("Europe/Copenhagen")).toInstant()

    @Test
    fun `parkAndRideWithHubPreference leaves the baseline untouched when preferHubs is false`() {
        val engine = testEngine()
        val hubs = HubCatalog.load()

        val (itineraries, notice) = parkAndRideWithHubPreference(
            engine, hubs, parkAndRideHubOrigin, parkAndRideHubDestination, parkAndRideHubDeparture,
            preferHubs = false, maxTransfers = null,
        )

        assertThat(notice).isNull()
        // Itinerary has no overridden equals() (confirmed directly: two separately-run searches of
        // the identical real query produce "non-equal instance[s] with same string representation"),
        // so this compares the departure/arrival times and leg count instead of object identity.
        val baseline = ParkAndRideFinder.search(engine, parkAndRideHubOrigin, parkAndRideHubDestination, parkAndRideHubDeparture, maxTransfers = null)
        assertThat(itineraries).hasSize(1)
        val result = itineraries.single()
        assertThat(result.startTimeAsInstant()).isEqualTo(baseline!!.startTimeAsInstant())
        assertThat(result.endTimeAsInstant()).isEqualTo(baseline.endTimeAsInstant())
        assertThat(result.legs().size).isEqualTo(baseline.legs().size)
    }

    @Test
    fun `parkAndRideWithHubPreference re-routes the transfer through the real cataloged hub when preferHubs is true`() {
        val engine = testEngine()
        val hubs = HubCatalog.load()

        val (itineraries, notice) = parkAndRideWithHubPreference(
            engine, hubs, parkAndRideHubOrigin, parkAndRideHubDestination, parkAndRideHubDeparture,
            preferHubs = true, maxTransfers = null,
        )

        assertThat(notice).isEqualTo("Aarhus Banegårdsplads")
        assertThat(itineraries).hasSize(1)
        val stopNames = itineraries.single().legs().flatMap { listOf(it.from().name.toString(), it.to().name.toString()) }
        assertThat(stopNames).contains("Park Allé/Rådhuset (Aarhus Kom)")
    }

    // A minimal `Leg` test double -- same technique and same reasoning as HubRoutingTest.kt's own
    // FakeTransitLeg: findHubSplit only ever reads isTransitLeg/startTime/endTime/to().coordinate,
    // which is far cheaper to implement directly than wiring up OTP's real TripTimes/TripPattern
    // machinery just to construct a ScheduledTransitLeg.
    private class FakeTransitLeg(
        private val legStartTime: ZonedDateTime,
        private val legEndTime: ZonedDateTime,
        private val toPlace: Place,
    ) : Leg {
        override fun isTransitLeg() = true
        override fun hasSameMode(other: Leg) = other.isTransitLeg
        override fun start(): LegCallTime? = null
        override fun end(): LegCallTime? = null
        override fun startTime(): ZonedDateTime = legStartTime
        override fun endTime(): ZonedDateTime = legEndTime
        override fun distanceMeters(): Double = 5_000.0
        override fun from(): Place = toPlace
        override fun to(): Place = toPlace
        override fun legGeometry(): LineString? = null
        override fun listTransitAlerts(): Set<TransitAlert> = emptySet()
        override fun emissionPerPerson(): Emission? = null
        override fun withEmissionPerPerson(emissionPerPerson: Emission?): Leg = this
        override fun generalizedCost(): Int = 0
        override fun fareOffers(): List<FareOffer> = emptyList()
    }

    @Test
    fun `findHubSplitAcrossBaseline finds a hub split on a later itinerary when the first is a direct route`() {
        val hub = TransitHub("Test Hub", 56.15, 10.20, stopIds = listOf("1:test"))
        val directItinerary = syntheticItinerary(listOf(streetLeg(5_000.0)), generalizedCostSeconds = 500)

        val start = ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        val alightLeg = FakeTransitLeg(start, start.plusMinutes(10), Place.normal(hub.lat, hub.lon, NonLocalizedString(hub.name)))
        val boardLeg = FakeTransitLeg(start.plusMinutes(11), start.plusMinutes(20), Place.normal(hub.lat + 0.05, hub.lon + 0.05, NonLocalizedString("Elsewhere")))
        val itineraryWithSplit = Itinerary.ofScheduledTransit(listOf(alightLeg, boardLeg)).withGeneralizedCost(Cost.costOfSeconds(600)).build()

        val result = findHubSplitAcrossBaseline(listOf(hub), listOf(directItinerary, itineraryWithSplit))

        assertThat(result).isNotNull()
        assertThat(result!!.second).isEqualTo(hub)
        assertThat(result.first).isSameInstanceAs(itineraryWithSplit)
    }

    @Test
    fun `findHubSplitAcrossBaseline returns null when nothing in baseline has a hub split`() {
        val hub = TransitHub("Test Hub", 56.15, 10.20, stopIds = listOf("1:test"))
        val directItinerary = syntheticItinerary(listOf(streetLeg(5_000.0)), generalizedCostSeconds = 500)

        val result = findHubSplitAcrossBaseline(listOf(hub), listOf(directItinerary))

        assertThat(result).isNull()
    }

    @Test
    fun `stitchItineraries trims the hub connector legs and concatenates in order without NPE`() {
        val keptA = streetLeg(200.0)
        val connectorAtEndOfA = streetLeg(49.0)
        val legA = syntheticItinerary(listOf(keptA, connectorAtEndOfA), generalizedCostSeconds = 100)

        val connectorAtStartOfB = streetLeg(49.0)
        val keptB = streetLeg(300.0)
        val legB = syntheticItinerary(listOf(connectorAtStartOfB, keptB), generalizedCostSeconds = 150)

        val stitched = stitchItineraries(legA, legB)

        assertThat(stitched.legs()).containsExactly(keptA, keptB).inOrder()
    }

    @Test
    fun `stitchItineraries sums the two source itineraries' generalized costs`() {
        val legA = syntheticItinerary(listOf(streetLeg(200.0)), generalizedCostSeconds = 100)
        val legB = syntheticItinerary(listOf(streetLeg(300.0)), generalizedCostSeconds = 150)

        val stitched = stitchItineraries(legA, legB)

        assertThat(stitched.generalizedCost()).isEqualTo(250)
    }

    @Test
    fun `POST search itinerary legs carry human-readable endpoint names`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            val firstLeg = body.itineraries.first().legs.first()
            assertThat(firstLeg.fromName).isNotNull()
            assertThat(firstLeg.toName).isNotNull()
        }
    }
}
