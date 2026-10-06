package one.otpserverui.routing

import java.time.Duration
import java.time.Instant
import one.otpserverui.model.TimeMode
import org.opentripplanner.core.model.id.FeedScopedId
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.routing.api.request.via.VisitViaLocation
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
 *
 * [viaStopIds] (Task 18) mirrors Drop-me-off's original via-stop constraint, applied directly
 * against the embedded engine the same way bikebus's own `EmbeddedRequestBuilder.buildEmbeddedRequest`
 * applies it: when non-empty, every id is parsed (`"feedId:id"` format) into one [VisitViaLocation]
 * wrapping the whole list as alternatives ("visit any one of these stops"), via
 * `RouteRequestBuilder.withViaLocations`. [numItineraries] mirrors the GraphQL path's `first` param,
 * applied via `withNumItineraries` when set. A via-constrained search must never fall back to a
 * direct (non-transit) route -- bikebus's own `planEmbeddedItineraries` does the same
 * (`directItineraries` forced empty whenever `viaStopIds` is non-empty), since "ride via this stop"
 * has no meaning for a direct route.
 *
 * **Unresolvable via-stop guard (Task 18, not present in bikebus's own source).** When every id in
 * [viaStopIds] parses as a syntactically valid `FeedScopedId` but none of them names a real stop in
 * this graph, real raptor's own `RaptorRequestMapper.mapViaLocation` throws `IllegalArgumentException
 * ("At least one connection must exist!")` deep inside `transitSearch` -- confirmed directly against
 * this project's own fixture graph (a well-formed but nonexistent stop id, e.g. "1:999999999999").
 * bikebus's own `planEmbeddedItineraries`/`buildEmbeddedRequest` has no guard against this either
 * (read directly: it would crash the same way) -- this project adds one since an unresolvable via
 * constraint is exactly as "real and reachable" as an empty access/egress reach above, and this
 * function's own contract is to return an empty list for a query with no valid result, not to
 * crash.
 *
 * [maxTransfers] is the user-facing "number of connections" setting shared across every search
 * mode -- `null` (the default) means unlimited, preserving this function's original unconstrained
 * behavior. A non-null value is translated to raptor's own round-count parameter as
 * `maxTransfers + 1`; see [one.otpserverui.routing.parkandride.ParkAndRideFinder.search]'s own KDoc
 * for the full raptor round-counting explanation this mirrors.
 */
fun bringBike(
    engine: RoutingEngine,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    timeMode: TimeMode,
    dateTime: Instant,
    viaStopIds: List<String> = emptyList(),
    numItineraries: Int? = null,
    maxTransfers: Int? = null,
): List<Itinerary> {
    val requestBuilder = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(origin))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(dateTime)
        .apply { if (timeMode == TimeMode.ARRIVE_BY) withArriveBy(true) }
        // Bring Bike's own real semantics (bikebus's `EmbeddedRequestBuilder`, SearchMode.BRING_BIKE
        // branch): bike through transfers too, not just access/egress -- biking through a transfer
        // is expensive, so it's paired with a steeper transfer cost below, not the generic default
        // (see RoutingEngine.requestBuilder) -- plus a widened search window.
        .withJourney { it.withAllModes(StreetMode.BIKE) }
        .withSearchWindow(Duration.ofHours(12))
        .withPreferences { preferences ->
            preferences.withTransfer { transfer ->
                transfer.withCost(3600)
                if (maxTransfers != null) transfer.withMaxTransfers(maxTransfers + 1)
            }
        }
    if (viaStopIds.isNotEmpty()) {
        requestBuilder.withViaLocations(listOf(VisitViaLocation(null, null, FeedScopedId.parse(viaStopIds), null)))
    }
    if (numItineraries != null) {
        requestBuilder.withNumItineraries(numItineraries)
    }
    val request = requestBuilder.buildRequest()

    val accessEgressDuration = request.preferences().street().accessEgress().maxDuration().valueOf(StreetMode.BIKE)
    val access = engine.streetReach(origin, StreetMode.BIKE, accessEgressDuration, ReachDirection.ACCESS, request)
    val egress = engine.streetReach(destination, StreetMode.BIKE, accessEgressDuration, ReachDirection.EGRESS, request)

    val transitItineraries: List<Itinerary> = if (access.isEmpty() || egress.isEmpty()) {
        emptyList()
    } else {
        try {
            val paths = engine.transitSearch(access, egress, request)
            engine.toItineraries(paths, request)
        } catch (e: IllegalArgumentException) {
            // See this function's own "Unresolvable via-stop guard" KDoc above: only reachable when
            // viaStopIds is non-empty and none of its ids resolve to a real stop in this graph.
            if (viaStopIds.isEmpty()) throw e
            emptyList()
        }
    }

    // A via-constrained search must never fall back to a direct (non-transit) route -- see this
    // function's own KDoc above.
    val directItineraries: List<Itinerary> = if (viaStopIds.isEmpty()) {
        engine.directRoute(origin, destination, StreetMode.BIKE, request)
    } else {
        emptyList()
    }

    return engine.filter(directItineraries + transitItineraries, request)
}
