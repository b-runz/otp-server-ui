package one.otpserverui.routing

import org.locationtech.jts.geom.Envelope
import org.opentripplanner.street.geometry.PolylineEncoder
import org.opentripplanner.street.geometry.SphericalDistanceLibrary
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.transit.model.network.TripPattern

/**
 * One transit route/pattern serving a stop found by [stopsNear], shaped to mirror `PatternDto`
 * closely enough to map 1:1 onto it (this project's own `one.otpserverui.domain.NearbyPatternMapper`).
 * [patternGeometryPoints] is the pattern's shape, polyline-encoded the same way
 * bikebus's own `one.brj.bikebus.domain.OtpItineraryMapper` already encodes a leg's geometry.
 * [patternGeometryPointCount] is the point count [PolylineEncoder.encodeGeometry] already reports
 * for that same encoding, carried alongside it so callers don't need to decode
 * [patternGeometryPoints] a second time just to learn how many points it has.
 */
data class NearbyPattern(
    val routeGtfsId: String,
    val routeShortName: String?,
    val routeMode: String,
    val routeGtfsType: Int?,
    val patternGeometryPoints: String,
    val patternGeometryPointCount: Int,
    val stopGtfsIds: List<String>,
)

/**
 * Every distinct transit pattern serving a `RegularStop` within [radiusMeters] of [coordinate].
 * Wraps `DefaultTransitService.findRegularStopsByBoundingBox`/`.findPatterns` the same way OTP's
 * own `StraightLineNearbyStopFinder`
 * (`place/nearbystopfinder/StraightLineNearbyStopFinder.java`) builds its bounding-box query: a
 * plain lat/lon box centered on [coordinate] and expanded by [radiusMeters] converted to degrees
 * via `SphericalDistanceLibrary`, not a precise circular radius filter (see this task's brief -
 * a bounding box is sufficient for a nearby-stop lookup).
 *
 * Patterns are deduplicated by `TripPattern.id`: two stops within [radiusMeters] that are both
 * served by the same pattern contribute it to the result only once.
 */
fun RoutingEngine.stopsNear(coordinate: WgsCoordinate, radiusMeters: Double): List<NearbyPattern> {
    val center = coordinate.asJtsCoordinate()
    val envelope = Envelope(center)
    envelope.expandBy(
        SphericalDistanceLibrary.metersToLonDegrees(radiusMeters, center.y),
        SphericalDistanceLibrary.metersToDegrees(radiusMeters),
    )

    val nearbyStops = services.transitService.findRegularStopsByBoundingBox(envelope)
    return nearbyStops
        .flatMap { services.transitService.findPatterns(it) }
        .distinctBy { it.id }
        .map { it.toNearbyPattern() }
}

/**
 * Every distinct transit pattern in the whole graph, regardless of where its stops are -- see
 * [one.otpserverui.routing.nearbyRoutes]'s own KDoc for why candidate discovery needs this instead
 * of [stopsNear]: a route whose real path passes close to a destination can have every one of its
 * own stops further away than any reasonable search radius (confirmed directly: a real rural
 * destination near Mørke found its closest route, "L1", this way -- at 3.2km via real path
 * distance -- while stop-proximity discovery had no way to find it without widening the stop
 * search radius past where its own stops happen to sit). Confirmed fast enough to run per-request:
 * checking all 7,810 patterns in the real production graph against a destination point takes
 * ~220ms (throwaway diagnostic code, not committed).
 */
fun RoutingEngine.allPatterns(): List<NearbyPattern> =
    services.transitService.listTripPatterns().map { it.toNearbyPattern() }

private fun TripPattern.toNearbyPattern(): NearbyPattern {
    val encodedGeometry = PolylineEncoder.encodeGeometry(geometry)
    return NearbyPattern(
        routeGtfsId = route.id.toString(),
        routeShortName = route.shortName,
        routeMode = route.mode.name,
        routeGtfsType = route.gtfsType,
        patternGeometryPoints = encodedGeometry.points,
        patternGeometryPointCount = encodedGeometry.length,
        stopGtfsIds = stops.map { it.id.toString() },
    )
}
