package one.otpserverui.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class GeocodeCandidate(val label: String, val lat: Double, val lon: Double)

@Serializable
data class GeocodeResponse(val candidates: List<GeocodeCandidate>)

interface GeocodeClient {
    suspend fun search(query: String): List<GeocodeCandidate>
}

@kotlinx.serialization.Serializable
private data class AutocompleteRequest(val input: String, val includedRegionCodes: List<String> = listOf("dk"), val languageCode: String = "da")

@kotlinx.serialization.Serializable
private data class AutocompleteResponse(val suggestions: List<SuggestionDto> = emptyList())

@kotlinx.serialization.Serializable
private data class SuggestionDto(val placePrediction: PlacePredictionDto? = null)

@kotlinx.serialization.Serializable
private data class PlacePredictionDto(val placeId: String, val text: FormattedTextDto)

@kotlinx.serialization.Serializable
private data class FormattedTextDto(val text: String)

@kotlinx.serialization.Serializable
private data class PlaceDetailsResponse(val location: LatLngDto)

@kotlinx.serialization.Serializable
private data class LatLngDto(val latitude: Double, val longitude: Double)

/**
 * Real Google Places (New) API — same endpoints/headers bikebus's own
 * `PlacesApi.kt` already calls (Retrofit there, plain Ktor HttpClient here;
 * same wire contract): `POST /v1/places:autocomplete` for suggestions, then
 * `GET /v1/places/{placeId}` with `X-Goog-FieldMask: location` to resolve
 * one candidate's real lat/lon.
 */
class GooglePlacesGeocodeClient(private val httpClient: HttpClient, private val apiKey: String) : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> {
        val autocomplete = httpClient.post("https://places.googleapis.com/v1/places:autocomplete") {
            header("X-Goog-Api-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(AutocompleteRequest(input = query))
        }.body<AutocompleteResponse>()

        return autocomplete.suggestions.mapNotNull { it.placePrediction }.map { prediction ->
            val details = httpClient.get("https://places.googleapis.com/v1/places/${prediction.placeId}") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", "location")
            }.body<PlaceDetailsResponse>()
            GeocodeCandidate(prediction.text.text, details.location.latitude, details.location.longitude)
        }
    }
}

fun Routing.geocodeRoute(client: GeocodeClient) {
    get("/geocode") {
        val query = call.request.queryParameters["q"] ?: ""
        call.respond(GeocodeResponse(client.search(query)))
    }
}
