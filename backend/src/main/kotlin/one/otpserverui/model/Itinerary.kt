package one.otpserverui.model

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
    val routeGtfsId: String?,
    val legGeometryPoints: String?,
    val departureTime: OffsetDateTime,
    val arrivalTime: OffsetDateTime
)

data class Itinerary(val legs: List<Leg>) {
    val departureTime: OffsetDateTime get() = legs.first().departureTime
    val arrivalTime: OffsetDateTime get() = legs.last().arrivalTime
    val totalDurationSeconds: Double get() = Duration.between(departureTime, arrivalTime).seconds.toDouble()
    val totalBikeDistanceMeters: Double get() = legs.filter { it.mode == "BICYCLE" }.sumOf { it.distanceMeters }
    val exceedsBikeLimit: Boolean get() = totalBikeDistanceMeters > BIKE_LIMIT_METERS

    /**
     * Whether this itinerary's final leg is a walk longer than
     * [one.otpserverui.routing.parkandride.ParkAndRideFinder]'s own strict egress cap (that
     * class's `MAX_WALK_EGRESS`, duplicated here as [LONG_WALK_EGRESS_SECONDS] since the app model
     * doesn't depend on the routing module's internals) -- a real, reachable result now that
     * [ParkAndRideFinder.search]'s own egress search is two-tier, not a sign of a wrong route.
     */
    val hasLongWalkEgress: Boolean
        get() = legs.last().let { it.mode == "WALK" && it.durationSeconds > LONG_WALK_EGRESS_SECONDS }

    companion object {
        const val BIKE_LIMIT_METERS = 10_000.0
        const val LONG_WALK_EGRESS_SECONDS = 15.0 * 60.0
    }
}
