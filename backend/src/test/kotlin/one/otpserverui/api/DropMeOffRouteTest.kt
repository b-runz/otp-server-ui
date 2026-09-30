package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
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
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test

private fun testEngine(): RoutingEngine {
    val fixturePath = checkNotNull(object {}.javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
}

class DropMeOffRouteTest {
    @Test
    fun `GET nearby-routes returns real routes near a known Aarhus stop`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { dropMeOffRoutes(testEngine()) }
            }
            val response = client.get("/nearby-routes?lat=56.171798&lon=10.172087&radiusMeters=500")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<NearbyRoutesResponse>(response.bodyAsText())
            assertThat(body.routes).isNotEmpty()
        }
    }

    @Test
    fun `POST connect returns a real itinerary to the flag point`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { dropMeOffRoutes(testEngine()) }
            }
            val response = client.post("/connect") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"flagLat":56.171798,"flagLon":10.172087,
                     "destinationLat":56.165518,"destinationLon":10.185262,
                     "dateTimeIso":"2026-09-13T14:00:00Z"}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<ItineraryDto>(response.bodyAsText())
            assertThat(body.legs).isNotEmpty()
        }
    }
}
