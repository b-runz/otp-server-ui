package one.otpserverui.routing

import java.time.Instant
import one.otpserverui.domain.NearbyRoutesFinder
import one.otpserverui.domain.toPatternDto
import one.otpserverui.model.NearbyRoute
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

/**
 * Drop-me-off's "nearby routes" lookup: every bus route serving a stop within [radiusMeters] of
 * [destination], ranked by how close that route's own path (not just a stop) comes to
 * [destination]. Composes this task's own [stopsNear]/`toPatternDto`/
 * [NearbyRoutesFinder.rankCandidates] -- ported from bikebus's own `TripViewModel.findNearbyRoutes`
 * embedded-engine path, minus any network/GraphQL step (there is none on this path either way).
 */
fun nearbyRoutes(engine: RoutingEngine, destination: WgsCoordinate, radiusMeters: Double): List<NearbyRoute> {
    val patterns = engine.stopsNear(destination, radiusMeters).map { it.toPatternDto() }
    return NearbyRoutesFinder.rankCandidates(patterns, destination.latitude(), destination.longitude())
}

/**
 * Drop-me-off's "flag a bus" connect step: the direct (non-transit) route from [flagPoint] (a
 * point along a chosen bus route, not necessarily an official stop) to [destination].
 *
 * Replaces bikebus's own `OtpQueryBuilder`/`otpApi` GraphQL round-trip (a `plan` query from the
 * flag point to the destination) with a direct call to [RoutingEngine.directRoute] -- this
 * project has no GraphQL dependency (see this task's brief), and `directRoute` is the same
 * building block [one.otpserverui.routing.parkandride.ParkAndRideFinder] and `HubRouting`'s own
 * callers already use for a non-transit point-to-point search.
 */
fun connectByFlaggingABus(
    engine: RoutingEngine,
    flagPoint: WgsCoordinate,
    destination: WgsCoordinate,
    departureTime: Instant,
): Itinerary? {
    val request = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(flagPoint))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(departureTime)
        .buildRequest()
    return engine.directRoute(flagPoint, destination, StreetMode.WALK, request).firstOrNull()
}
