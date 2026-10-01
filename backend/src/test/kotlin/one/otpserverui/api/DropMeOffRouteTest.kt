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
import one.otpserverui.routing.HubCatalog
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
                routing { dropMeOffRoutes(testEngine(), HubCatalog.load()) }
            }
            val response = client.get("/nearby-routes?lat=56.171798&lon=10.172087&radiusMeters=500")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<NearbyRoutesResponse>(response.bodyAsText())
            assertThat(body.routes).isNotEmpty()
        }
    }

    // Real end-to-end chain, not a hand-guessed route id: GET /nearby-routes near destination
    // (56.1456, 10.2290) -- the same destination BringBikeViaTest's own query already confirmed is
    // really reached, via a real transit leg, from origin (56.1507, 10.2037) "Aarhus
    // Banegårdsplads" -- at a 1500m radius (confirmed directly, throwaway diagnostic code, to
    // include real route 1:24004_3/shortName "17" among its candidates, ~1364m from this
    // destination), then feeds that route's own gtfsId/stopIds straight from the response into
    // POST /connect.
    @Test
    fun `POST connect returns a real connected itinerary for a route found via nearby-routes`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { dropMeOffRoutes(testEngine(), HubCatalog.load()) }
            }

            val nearbyResponse = client.get("/nearby-routes?lat=56.1456&lon=10.2290&radiusMeters=1500")
            assertThat(nearbyResponse.status).isEqualTo(HttpStatusCode.OK)
            val nearbyBody = Json.decodeFromString<NearbyRoutesResponse>(nearbyResponse.bodyAsText())
            val route = nearbyBody.routes.first { it.routeGtfsId == "1:24004_3" }
            assertThat(route.stopIds).isNotEmpty()

            val connectResponse = client.post("/connect") {
                contentType(ContentType.Application.Json)
                setBody(
                    Json.encodeToString(
                        ConnectRequest.serializer(),
                        ConnectRequest(
                            originLat = 56.1507,
                            originLon = 10.2037,
                            destinationLat = 56.1456,
                            destinationLon = 10.2290,
                            routeGtfsId = route.routeGtfsId,
                            routeStopIds = route.stopIds,
                            timeMode = "depart_at",
                            dateTimeIso = "2026-09-13T06:00:00Z",
                        )
                    )
                )
            }
            assertThat(connectResponse.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<ConnectResponse>(connectResponse.bodyAsText())
            assertThat(body.itinerary.legs).isNotEmpty()
        }
    }
}
