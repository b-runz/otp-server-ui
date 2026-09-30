package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
}

private class FakeGeocodeClient(private val results: List<GeocodeCandidate>) : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = results
}
