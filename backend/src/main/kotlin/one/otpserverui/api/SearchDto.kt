package one.otpserverui.api

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val mode: String, // "park_and_ride" | "bring_bike"
    val timeMode: String, // "depart_at" | "arrive_by"
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null, // null = unlimited; the "number of connections" UI setting
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
data class SearchResponse(
    val itineraries: List<ItineraryDto>,
    val notice: String? = null,
)

@Serializable
data class SearchErrorResponse(val error: String)
