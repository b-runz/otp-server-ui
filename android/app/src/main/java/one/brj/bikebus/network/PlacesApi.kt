package one.brj.bikebus.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface PlacesApi {
    @POST("v1/places:autocomplete")
    suspend fun autocomplete(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Body request: AutocompleteRequest
    ): AutocompleteResponse

    @GET("v1/places/{placeId}")
    suspend fun placeDetails(
        @Path("placeId") placeId: String,
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "location"
    ): PlaceDetailsResponse
}
