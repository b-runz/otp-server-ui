@file:OptIn(ExperimentalSerializationApi::class)

package one.brj.bikebus.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class AutocompleteRequest(
    val input: String,
    @EncodeDefault val includedRegionCodes: List<String> = listOf("dk"),
    @EncodeDefault val languageCode: String = "da",
    val locationBias: LocationBias? = null
)

@Serializable
data class AutocompleteResponse(val suggestions: List<SuggestionDto> = emptyList())

@Serializable
data class SuggestionDto(val placePrediction: PlacePredictionDto? = null)

@Serializable
data class PlacePredictionDto(
    val placeId: String,
    val text: FormattedTextDto,
    val structuredFormat: StructuredFormatDto? = null,
    val types: List<String> = emptyList()
)

@Serializable
data class StructuredFormatDto(val mainText: FormattedTextDto, val secondaryText: FormattedTextDto? = null)

@Serializable
data class FormattedTextDto(val text: String)

@Serializable
data class PlaceDetailsResponse(val location: LatLngDto)

@Serializable
data class LatLngDto(val latitude: Double, val longitude: Double)

@Serializable
data class LocationBias(val circle: Circle)

@Serializable
data class Circle(val center: LatLngDto, val radius: Double)
