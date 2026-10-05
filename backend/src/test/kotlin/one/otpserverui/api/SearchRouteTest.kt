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
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test
import org.opentripplanner.core.model.basic.Cost
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.model.plan.Leg
import org.opentripplanner.model.plan.leg.StreetLeg
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
