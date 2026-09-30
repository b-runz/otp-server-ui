package one.otpserverui.routing

import java.time.Instant
import one.otpserverui.model.TimeMode
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

/**
 * Bring Bike's own composition of the building-block tier -- `streetReach` (access + egress, both
 * [StreetMode.BIKE]) -> `transitSearch` -> `toItineraries`, plus the direct (non-transit) bike
 * route, then `filter` -- extracted (not copied verbatim) from bikebus's own
 * `TripViewModel.planEmbeddedItineraries` (`bikebus/app/src/main/java/one/brj/bikebus/
 * TripViewModel.kt`), which composed the same building-block tier over an
 * `AndroidViewModel`/`Application`-backed embedded engine this project has no equivalent of.
 * Unlike that source function, this one has no `mode`/`viaStopIds`/`numItineraries` parameters:
 * bikebus's own Park & Ride mode never went through `planEmbeddedItineraries` at all (it errored
 * out -- see [one.otpserverui.routing.parkandride.ParkAndRideFinder] for that composition
 * instead), and Drop-me-off's via-stop-constrained searches are this project's own later concern,
 * not part of this extraction.
 *
 * **Empty-reach guard.** Real raptor's own `RaptorRequestMapper` requires at least one access AND
 * one egress path (throws `IllegalArgumentException` otherwise) -- an empty reach on either side
 * (a real, reachable case: an origin/destination with no transit stop within
 * `accessEgressDuration`) must skip the transit search entirely, not let that exception propagate
 * and lose the direct (non-transit) itinerary below along with it.
 * [one.otpserverui.routing.parkandride.ParkAndRideFinder.search] guards this same gap the same
 * way, for the same reason.
 */
fun bringBike(
    engine: RoutingEngine,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    timeMode: TimeMode,
    dateTime: Instant,
): List<Itinerary> {
    val request = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(origin))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(dateTime)
        .apply { if (timeMode == TimeMode.ARRIVE_BY) withArriveBy(true) }
        .buildRequest()

    val accessEgressDuration = request.preferences().street().accessEgress().maxDuration().valueOf(StreetMode.BIKE)
    val access = engine.streetReach(origin, StreetMode.BIKE, accessEgressDuration, ReachDirection.ACCESS, request)
    val egress = engine.streetReach(destination, StreetMode.BIKE, accessEgressDuration, ReachDirection.EGRESS, request)

    val transitItineraries: List<Itinerary> = if (access.isEmpty() || egress.isEmpty()) {
        emptyList()
    } else {
        val paths = engine.transitSearch(access, egress, request)
        engine.toItineraries(paths, request)
    }

    val directItineraries = engine.directRoute(origin, destination, StreetMode.BIKE, request)

    return engine.filter(directItineraries + transitItineraries, request)
}
