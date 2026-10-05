package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

// A trimmed-but-real capture of Google Places Autocomplete (New)'s actual response shape for the
// query "Aarhus H" -- includes fields this backend's DTOs never declare (`place`, `structuredFormat`,
// `text.matches`), which is exactly what a strict (default) Json decoder rejects.
private const val REAL_AUTOCOMPLETE_RESPONSE = """
{
  "suggestions": [
    {
      "placePrediction": {
        "place": "places/ChIJiRsduMY_TEYR73zivGORBXQ",
        "placeId": "ChIJiRsduMY_TEYR73zivGORBXQ",
        "text": {
          "text": "Aarhus H, Banegårdspladsen, Aarhus, Danmark",
          "matches": [{"endOffset": 8}]
        },
        "structuredFormat": {
          "mainText": {"text": "Aarhus H", "matches": [{"endOffset": 8}]},
          "secondaryText": {"text": "Banegårdspladsen, Aarhus, Danmark"}
        },
        "types": ["establishment", "point_of_interest"]
      }
    }
  ]
}
"""

private const val REAL_PLACE_DETAILS_RESPONSE = """
{ "location": { "latitude": 56.1503094, "longitude": 10.2045245 } }
"""

private fun mockHttpClient(json: Json): HttpClient {
    val engine = MockEngine { request ->
        val body = if (request.url.encodedPath.endsWith(":autocomplete")) {
            REAL_AUTOCOMPLETE_RESPONSE
        } else {
            REAL_PLACE_DETAILS_RESPONSE
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }
}

class GooglePlacesGeocodeClientTest {
    @Test
    fun `search parses a real Google Places response shape, including fields this backend doesn't declare`() = runTest {
        // The exact client configuration Main.kt must use: default Json() is strict about unknown
        // keys, which real Google responses always contain extras of (place, structuredFormat, ...).
        val httpClient = mockHttpClient(Json { ignoreUnknownKeys = true })
        val client = GooglePlacesGeocodeClient(httpClient, apiKey = "test-key")

        val candidates = client.search("Aarhus H")

        assertThat(candidates).hasSize(1)
        val candidate = candidates.first()
        assertThat(candidate.placeId).isEqualTo("ChIJiRsduMY_TEYR73zivGORBXQ")
        assertThat(candidate.label).isEqualTo("Aarhus H, Banegårdspladsen, Aarhus, Danmark")
        assertThat(candidate.lat).isEqualTo(56.1503094)
        assertThat(candidate.lon).isEqualTo(10.2045245)
        assertThat(candidate.isStreet).isFalse()
    }
}
