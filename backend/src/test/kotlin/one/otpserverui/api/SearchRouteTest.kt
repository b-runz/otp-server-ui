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
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.otpserverui.GraphLoader
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test

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
}
