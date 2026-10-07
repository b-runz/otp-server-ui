package one.brj.bikebus.model

import java.time.Duration
import java.time.OffsetDateTime

data class Leg(
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
    val departureTime: OffsetDateTime,
) {
    val arrivalTime: OffsetDateTime get() = departureTime.plusSeconds(durationSeconds.toLong())
}

data class Itinerary(
    val legs: List<Leg>,
    val exceedsBikeLimit: Boolean,
    val hasLongWalkEgress: Boolean,
) {
    val departureTime: OffsetDateTime get() = legs.first().departureTime
    val arrivalTime: OffsetDateTime get() = legs.last().arrivalTime
    val totalDurationSeconds: Double get() = Duration.between(departureTime, arrivalTime).seconds.toDouble()
    val totalBikeDistanceMeters: Double get() = legs.filter { it.mode == "BICYCLE" }.sumOf { it.distanceMeters }
}
