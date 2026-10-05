package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class GeocodeRouteTest {
    @Test
    fun `GET geocode returns real candidates from the injected client`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { geocodeRoute(FakeGeocodeClient(listOf(GeocodeCandidate("Langelandsgade, Aarhus, Danmark", 56.1638, 10.1979)))) }
            }
            val response = client.get("/geocode?q=Langelandsg")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<GeocodeResponse>(response.bodyAsText())
            assertThat(body.candidates).hasSize(1)
            assertThat(body.candidates.first().label).isEqualTo("Langelandsgade, Aarhus, Danmark")
        }
    }

    @Test
    fun `GET geocode with no results returns an empty candidates array`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { geocodeRoute(FakeGeocodeClient(emptyList())) }
            }
            val response = client.get("/geocode?q=zzzznonsense")
            val body = Json.decodeFromString<GeocodeResponse>(response.bodyAsText())
            assertThat(body.candidates).isEmpty()
        }
    }

    @Test
    fun `GET geocode with an empty q returns an empty candidates array with zero calls to the client`() = runTest {
        val countingClient = CountingGeocodeClient(listOf(GeocodeCandidate("unused", 0.0, 0.0)))
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { geocodeRoute(countingClient) }
            }
            val response = client.get("/geocode?q=")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<GeocodeResponse>(response.bodyAsText())
            assertThat(body.candidates).isEmpty()
        }
        assertThat(countingClient.callCount).isEqualTo(0)
    }

    @Test
    fun `GET geocode with a missing q returns an empty candidates array with zero calls to the client`() = runTest {
        val countingClient = CountingGeocodeClient(listOf(GeocodeCandidate("unused", 0.0, 0.0)))
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { geocodeRoute(countingClient) }
            }
            val response = client.get("/geocode")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<GeocodeResponse>(response.bodyAsText())
            assertThat(body.candidates).isEmpty()
        }
        assertThat(countingClient.callCount).isEqualTo(0)
    }

    @Test
    fun `GET geocode with a client that throws produces a typed error response, not an unhandled exception`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                // Mirrors Main.kt's real `StatusPages` install (Task 20) -- this is the catch-all
                // that turns a failed/timed-out/4xx-5xx Google call into a typed error instead of
                // a raw 500/an exception escaping the test.
                install(StatusPages) {
                    exception<Throwable> { call, _ ->
                        call.respond(HttpStatusCode.BadGateway, SearchErrorResponse("geocode_unavailable"))
                    }
                }
                routing { geocodeRoute(ThrowingGeocodeClient()) }
            }
            val response = client.get("/geocode?q=Langelandsg")
            assertThat(response.status).isEqualTo(HttpStatusCode.BadGateway)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("geocode_unavailable")
        }
    }
}

private class FakeGeocodeClient(private val results: List<GeocodeCandidate>) : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = results
}

private class CountingGeocodeClient(private val results: List<GeocodeCandidate>) : GeocodeClient {
    var callCount: Int = 0
        private set

    override suspend fun search(query: String): List<GeocodeCandidate> {
        callCount++
        return results
    }
}

// A distinct exception type, deliberately not one of the other, more specific types `Main.kt`'s
// real `StatusPages` config maps to "invalid_request" (`NumberFormatException`,
// `IllegalStateException`, `DateTimeParseException`) -- this is meant to fall through to the
// generic `Throwable` catch-all both here and in the real app, the same way an actual Google
// HTTP-client failure (a `ResponseException`, an `IOException`, a `SerializationException`, none
// of which are those three types either) would.
private class SimulatedGoogleFailure : RuntimeException("simulated Google Places failure")

private class ThrowingGeocodeClient : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> {
        throw SimulatedGoogleFailure()
    }
}
