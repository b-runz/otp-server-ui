package one.otpserverui.domain

import one.otpserverui.model.NearbyRoute
import one.otpserverui.model.PatternDto

private const val FLEX_ROUTE_TYPE = 715 // GTFS extended route type: Demand and Response Bus Service

/**
 * Pure decision logic for the "nearby routes" flag-stop tool -- no network I/O here.
 *
 * Ported (adapted, not verbatim) from bikebus's own
 * `one.brj.bikebus.domain.NearbyRoutesFinder`: that source is a larger object with three more
 * functions -- `pickCheapestQualifying`, `findSplitForRoute`, `buildFlagStopInfo` (plus the
 * `RouteHubSplit` data class they use) -- all of which operate on bikebus's own app-level
 * `one.brj.bikebus.model.Leg`, a hand-rolled itinerary-leg shape this project has no equivalent
 * of (this project's whole routing tier operates on real OTP
 * `org.opentripplanner.model.plan.Itinerary`/`Leg` directly, as `HubRouting.kt`'s own KDoc
 * explains). Those three functions back bikebus's `TripViewModel` hub-stitching/leg-comparison
 * orchestration for the flag-stop *connect* flow, which is out of scope here: this task's
 * `DropMeOff.kt` only needs `rankCandidates` (`nearbyRoutes`'s own ranking step) --
 * `connectByFlaggingABus` calls `RoutingEngine.directRoute` directly instead (see this task's
 * brief's "no GraphQL dependency" requirement), so it never needs `pickCheapestQualifying`/
 * `findSplitForRoute`/`buildFlagStopInfo`'s hub-splitting/leg-comparison logic at all. Only
 * `rankCandidates` is ported here; the rest is deliberately left behind rather than adapted to a
 * model type that doesn't exist in this project.
 */
object NearbyRoutesFinder {

    // Step 1: rank candidate routes by real path proximity to the destination,
    // not by stop proximity -- patterns are only discovered via nearby stops,
    // but ranked by their own patternGeometry.
    fun rankCandidates(patterns: List<PatternDto>, destinationLat: Double, destinationLon: Double): List<NearbyRoute> {
        val distinctPatterns = patterns.distinctBy { it.route.gtfsId to it.patternGeometry.points }
        val bestPerRoute = mutableMapOf<String, NearbyRoute>()
        for (pattern in distinctPatterns) {
            if (pattern.route.mode != "BUS") continue
            if (pattern.route.type == FLEX_ROUTE_TYPE) continue
            val points = decodePolyline(pattern.patternGeometry.points)
            val closest = closestPointOnPolyline(points, destinationLat, destinationLon) ?: continue
            val existing = bestPerRoute[pattern.route.gtfsId]
            if (existing == null || closest.distanceMeters < existing.distanceMeters) {
                bestPerRoute[pattern.route.gtfsId] = NearbyRoute(
                    routeGtfsId = pattern.route.gtfsId,
                    routeShortName = pattern.route.shortName,
                    stopIds = pattern.stops.map { it.gtfsId },
                    distanceMeters = closest.distanceMeters
                )
            }
        }
        return bestPerRoute.values.sortedBy { it.distanceMeters }
    }
}
