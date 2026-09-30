package one.otpserverui

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.otpserverui.api.GeocodeCandidate
import one.otpserverui.api.GeocodeClient
import one.otpserverui.api.NearbyRoutesResponse
import one.otpserverui.api.SearchResponse
import one.otpserverui.model.TransitHub
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test
import org.opentripplanner.street.geometry.WgsCoordinate

private class NoOpGeocodeClient : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = emptyList()
}

/**
 * End-to-end smoke test for the real, complete `module(engine, hubs, geocodeClient)` from Task 16
 * (the same function `main()` calls) -- one real HTTP request per search mode, via Ktor's
 * `testApplication`, against the real engine/hubs and a no-op fake geocode client (this test
 * doesn't exercise `/geocode`, so a real Google API key isn't needed here).
 *
 * The `bring_bike`/`park_and_ride` origin and destination reuse the exact same real Aarhus query
 * already proven against this project's own fixture graph by [one.otpserverui.routing
 * .parkandride.ParkAndRideFinderTest] (a real bike-then-transit itinerary) and
 * [one.otpserverui.routing.BringBikeTest] (a real, non-empty -- though direct-bike-only --
 * result), computed here the same way those tests compute it (`WgsCoordinate(...)
 * .moveEastMeters(...).moveNorthMeters(...)`) rather than as separately hand-copied decimal
 * literals, so there's no risk of transcribing the real offset math wrong. This is NOT the task
 * brief's own `destinationLat:56.102216,destinationLon:10.17293` pair -- Task 7's and Task 9's own
 * implementers each already determined that pair doesn't correspond to any verified real query
 * against this project's fixture graph.
 *
 * The `nearby-routes` query below (`lat=56.171798&lon=10.172087&radiusMeters=500`) matches the
 * brief's own sample as-is -- it's the same real Aarhus stop [one.otpserverui.routing
 * .DropMeOffTest] already proved yields real nearby routes, no correction needed.
 */
class BackendSmokeTest {
    private fun realEngine(): RoutingEngine {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
    }

    private fun realHubs(): List<TransitHub> = HubCatalog.load()

    // Same real, already-proven-working query used throughout this repo (AarhusRealDataTest,
    // ParkAndRideFinderTest, BringBikeTest, HubRoutingTest, DropMeOffTest, SearchRouteTest).
    private val realOrigin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
    private val realDestination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)

    @Test
    fun `bring_bike search returns a real result over HTTP`() = runTest {
        testApplication {
            application { module(realEngine(), realHubs(), NoOpGeocodeClient()) }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"mode":"bring_bike","timeMode":"depart_at",""" +
                        """"originLat":${realOrigin.latitude()},"originLon":${realOrigin.longitude()},""" +
                        """"destinationLat":${realDestination.latitude()},"destinationLon":${realDestination.longitude()},""" +
                        """"dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}"""
                )
            }
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            assertThat(body.itineraries).isNotEmpty()
        }
    }

    @Test
    fun `park_and_ride search returns a real result over HTTP`() = runTest {
        testApplication {
            application { module(realEngine(), realHubs(), NoOpGeocodeClient()) }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"mode":"park_and_ride","timeMode":"depart_at",""" +
                        """"originLat":${realOrigin.latitude()},"originLon":${realOrigin.longitude()},""" +
                        """"destinationLat":${realDestination.latitude()},"destinationLon":${realDestination.longitude()},""" +
                        """"dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}"""
                )
            }
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            assertThat(body.itineraries).isNotEmpty()
        }
    }

    @Test
    fun `drop_me_off nearby-routes returns real routes over HTTP`() = runTest {
        testApplication {
            application { module(realEngine(), realHubs(), NoOpGeocodeClient()) }
            val response = client.get("/nearby-routes?lat=56.171798&lon=10.172087&radiusMeters=500")
            val body = Json.decodeFromString<NearbyRoutesResponse>(response.bodyAsText())
            assertThat(body.routes).isNotEmpty()
        }
    }
}
