package one.otpserverui.domain

import one.otpserverui.model.FlagStopInfo
import one.otpserverui.model.Leg
import one.otpserverui.model.NearbyRoute
import one.otpserverui.model.PatternDto
import one.otpserverui.model.TransitHub

private const val FLEX_ROUTE_TYPE = 715 // GTFS extended route type: Demand and Response Bus Service

// Mirrors HubRouting's NEAR_STOP_RADIUS_METERS constant, duplicated locally rather than changing
// HubRouting.kt: findSplitForRoute needs to know which leg-pair index the hub split happens at (to
// tell which side of the split carries the target route), which HubRouting.findHubSplit doesn't
// expose. Ported from bikebus's own NearbyRoutesFinder.kt, same rationale.
private const val NEAR_STOP_RADIUS_METERS = 1_000.0

data class RouteHubSplit(val hub: TransitHub, val routeOnOriginSide: Boolean)

/**
 * Pure decision logic for the "nearby routes" flag-stop tool -- no network I/O here.
 *
 * Ported (adapted, not verbatim) from bikebus's own `one.brj.bikebus.domain.NearbyRoutesFinder`.
 * Task 11 ported only `rankCandidates`, deliberately leaving `pickCheapestQualifying`/
 * `findSplitForRoute`/`buildFlagStopInfo` (plus the `RouteHubSplit` data class they use) behind:
 * those three operate on bikebus's own app-level `one.brj.bikebus.model.Leg`, a hand-rolled
 * itinerary-leg shape this project had no equivalent of at the time. Task 12 added this project's
 * own app-level `one.otpserverui.model.Leg`/`Itinerary` (same field shape as bikebus's), so Task 18
 * ports the remaining three here, adapted to **this project's own app-level `Leg`** (not real OTP's
 * `Leg` -- unlike `HubRouting.kt` (Task 10), which runs inside the routing-engine tier before any
 * app-level conversion happens, these three already operate on post-`toAppItinerary()` data in
 * bikebus, same as here).
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

    // Step 2: a `via` stop-list constraint only guarantees the itinerary passes
    // through a stop belonging to the route, not that it rides that route --
    // shared stops (interchanges) can be satisfied by a different, faster
    // route. This explicitly scans a wide batch for the cheapest itinerary
    // that actually contains a leg on the target route.
    fun pickCheapestQualifying(itineraries: List<List<Leg>>, targetRouteGtfsId: String): List<Leg>? =
        itineraries
            .filter { legs -> legs.any { it.routeGtfsId == targetRouteGtfsId } }
            .minByOrNull { legs -> legs.sumOf { it.durationSeconds } }

    // Hub-preference reconciliation: given a plain connected itinerary's legs,
    // find whether one of its own transit-to-transit transfer points sits near
    // a cataloged hub (same logic as HubRouting.findHubSplit), and if so,
    // which side of that split (before or after the hub) carries the leg that
    // uses the target route -- that side needs to be re-fetched with the same
    // via constraint when the caller rebuilds the stitched alternative.
    fun findSplitForRoute(legs: List<Leg>, hubs: List<TransitHub>, targetRouteGtfsId: String): RouteHubSplit? {
        val routeLegIndex = legs.indexOfFirst { it.routeGtfsId == targetRouteGtfsId }
        if (routeLegIndex == -1) return null
        for (i in 0 until legs.size - 1) {
            val alight = legs[i]
            val board = legs[i + 1]
            if (!isTransit(alight.mode) || !isTransit(board.mode)) continue
            val nearest = hubs.minByOrNull { haversineMeters(it.lat, it.lon, alight.toLat, alight.toLon) } ?: continue
            if (haversineMeters(nearest.lat, nearest.lon, alight.toLat, alight.toLon) <= NEAR_STOP_RADIUS_METERS) {
                return RouteHubSplit(hub = nearest, routeOnOriginSide = routeLegIndex <= i)
            }
        }
        return null
    }

    // Step 3: combine the official stop's own final-leg numbers with the
    // confirmed direct-query numbers from the projected flag point -- no
    // accept/reject decision, just the comparison data for the UI to show.
    fun buildFlagStopInfo(flagLat: Double, flagLon: Double, officialLeg: Leg, directLeg: Leg): FlagStopInfo =
        FlagStopInfo(
            flagLat = flagLat,
            flagLon = flagLon,
            officialFinalLegDistanceMeters = officialLeg.distanceMeters,
            officialFinalLegDurationSeconds = officialLeg.durationSeconds,
            flagStopDistanceMeters = directLeg.distanceMeters,
            flagStopDurationSeconds = directLeg.durationSeconds
        )

    private fun isTransit(mode: String) = mode != "BICYCLE" && mode != "WALK"
}
