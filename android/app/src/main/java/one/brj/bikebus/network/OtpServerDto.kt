package one.brj.bikebus.network

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequestDto(
    val mode: String,
    val timeMode: String,
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null,
)

@Serializable
data class LegDto(
    val mode: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val fromName: String?,
    val toName: String?,
    val routeShortName: String?,
    val departureEpochSecond: Long,
)

@Serializable
data class ItineraryDto(
    val legs: List<LegDto>,
    val exceedsBikeLimit: Boolean,
    val hasLongWalkEgress: Boolean,
)

@Serializable
data class SearchResponseDto(val itineraries: List<ItineraryDto>, val notice: String? = null)

@Serializable
data class SearchErrorResponseDto(val error: String)

@Serializable
data class NearbyRouteDto(
    val routeGtfsId: String,
    val routeShortName: String?,
    val stopIds: List<String>,
    val distanceMeters: Double,
)

@Serializable
data class NearbyRoutesResponseDto(val routes: List<NearbyRouteDto>)

@Serializable
data class ConnectRequestDto(
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val routeGtfsId: String,
    val routeStopIds: List<String>,
    val timeMode: String,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null,
)

@Serializable
data class FlagStopInfoDto(
    val flagLat: Double,
    val flagLon: Double,
    val officialFinalLegDistanceMeters: Double,
    val officialFinalLegDurationSeconds: Double,
    val flagStopDistanceMeters: Double,
    val flagStopDurationSeconds: Double,
)

@Serializable
data class ConnectResponseDto(
    val itinerary: ItineraryDto,
    val flagStopInfo: FlagStopInfoDto?,
    val extraRideSeconds: Double?,
    val hubName: String?,
)
