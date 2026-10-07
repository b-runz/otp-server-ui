package one.brj.bikebus.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import one.brj.bikebus.model.FlagStopConnectResult
import one.brj.bikebus.model.Itinerary
import one.brj.bikebus.model.NearbyRoute

sealed class SearchResult {
    data class Success(val itineraries: List<Itinerary>, val notice: String?) : SearchResult()
    data class Error(val code: String) : SearchResult()
}

sealed class ConnectOutcome {
    data class Success(val result: FlagStopConnectResult) : ConnectOutcome()
    data class Error(val code: String) : ConnectOutcome()
}

private val json = Json { ignoreUnknownKeys = true }
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

class OtpServerApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val authToken: String,
) {
    private fun errorCode(body: String): String =
        runCatching { json.decodeFromString(SearchErrorResponseDto.serializer(), body).error }
            .getOrDefault("unknown_error")

    suspend fun search(request: SearchRequestDto): SearchResult = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("$baseUrl/search")
            .header("X-Auth-Token", authToken)
            .post(json.encodeToString(SearchRequestDto.serializer(), request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val parsed = json.decodeFromString(SearchResponseDto.serializer(), body)
                SearchResult.Success(parsed.itineraries.map { it.toUiModel() }, parsed.notice)
            } else {
                SearchResult.Error(errorCode(body))
            }
        }
    }

    suspend fun nearbyRoutes(lat: Double, lon: Double, radiusMeters: Double = 500.0): List<NearbyRoute> =
        withContext(Dispatchers.IO) {
            val httpRequest = Request.Builder()
                .url("$baseUrl/nearby-routes?lat=$lat&lon=$lon&radiusMeters=$radiusMeters")
                .header("X-Auth-Token", authToken)
                .get()
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                json.decodeFromString(NearbyRoutesResponseDto.serializer(), body).routes.map { it.toUiModel() }
            }
        }

    suspend fun connect(request: ConnectRequestDto): ConnectOutcome = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("$baseUrl/connect")
            .header("X-Auth-Token", authToken)
            .post(json.encodeToString(ConnectRequestDto.serializer(), request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val parsed = json.decodeFromString(ConnectResponseDto.serializer(), body)
                ConnectOutcome.Success(
                    FlagStopConnectResult(
                        legs = parsed.itinerary.toUiModel().legs,
                        flagStopInfo = parsed.flagStopInfo?.toUiModel(),
                        extraRideSeconds = parsed.extraRideSeconds,
                        hubName = parsed.hubName,
                    )
                )
            } else {
                ConnectOutcome.Error(errorCode(body))
            }
        }
    }
}
