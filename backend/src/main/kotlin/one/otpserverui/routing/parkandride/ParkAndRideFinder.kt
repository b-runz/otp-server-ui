package one.otpserverui.routing.parkandride

import java.time.Duration
import java.time.Instant
import one.otpserverui.routing.ReachDirection
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.filter
import one.otpserverui.routing.streetReach
import one.otpserverui.routing.toItineraries
import one.otpserverui.routing.transitSearch
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.routing.api.response.RoutingErrorCode
import org.opentripplanner.routing.error.RoutingValidationException
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

/**
 * Park & Ride: bike from an origin to a stop, leave the bike, ride one zero-transfer transit
 * trip, then walk the rest of the way to the destination. Runs one bike-access street search, one
 * or two walk-egress street searches (see [FALLBACK_WALK_EGRESS]), and one transit search -- and
 * picks the shortest resulting itinerary.
 *
 * **Egress is two-tier, not a single fixed cap.** [MAX_WALK_EGRESS] runs first; only when that
 * finds zero reachable stops does [search] retry with the much larger [FALLBACK_WALK_EGRESS] --
 * a real address whose nearest stop is a longer walk away (confirmed on-device, see
 * `MorkeSkovstienRealDataTest`) now finds a route instead of a silent "no route," at the cost of
 * an extra street search only in that (rare) case. The returned [Itinerary] carries no separate
 * "long walk" flag: callers/UI derive it the same way [one.brj.bikebus.model.Itinerary
 * .exceedsBikeLimit] already flags an over-limit bike leg -- checking the final leg's own mode and
 * duration -- since which tier found the egress isn't itself meaningful once a real itinerary
 * comes back.
 */
object ParkAndRideFinder {
    private val MAX_BIKE_ACCESS: Duration = Duration.ofMinutes(15)
    private val MAX_WALK_EGRESS: Duration = Duration.ofMinutes(15)

    /**
     * Fallback egress cap, tried only when [MAX_WALK_EGRESS]'s own search finds nothing (see
     * [search]'s two-tier egress below): large enough that it is not, in practice, a real limit on
     * how far a genuinely reachable stop can be -- real cost is still bounded by
     * [one.otpserverui.routing.streetReach]'s own `maxStopCount` cutoff
     * (`AccessEgressRouter.findAccessEgresses`'s other, independent bound), not by this duration,
     * so this is safe to set generously rather than needing its own careful tuning.
     */
    private val FALLBACK_WALK_EGRESS: Duration = Duration.ofHours(2)

    /**
     * The best itinerary found, or `null` if no bikeable-then-transit path qualifies.
     *
     * A coordinate the street graph cannot link to at all (entirely outside the loaded map data,
     * or too remote from any street/path within it) makes OTP's own
     * `LinkingContextFactory.checkIfVerticesFound` throw a [RoutingValidationException] from
     * inside [one.otpserverui.routing.streetReach] -- it does not return an empty access/egress
     * collection the way "linked fine, but nothing reachable within [MAX_BIKE_ACCESS]/
     * [MAX_WALK_EGRESS]" does. `checkIfVerticesFound` (`routing/linking/LinkingContextFactory.java`)
     * can throw for three distinct reasons, and only two of them mean "unreachable":
     * [RoutingErrorCode.OUTSIDE_BOUNDS] (the location is outside the graph's convex hull) and
     * [RoutingErrorCode.LOCATION_NOT_FOUND] (linked, but disconnected from the street network) are
     * both mapped to this function's contract ("no bikeable-then-transit path qualifies" ->
     * `null`). The third, [RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT], is thrown when `from`
     * and `to` resolve to the *same* street vertex -- since [streetReach] links against the
     * *whole* request's from/to (only overriding the one side [from]/[direction] is about -- see
     * its own kdoc), a sufficiently-close origin/destination pair can trigger this on the very
     * first (access) call. That is not "unreachable," it is "reachable trivially by walking,
     * don't bother searching transit" -- a materially different outcome this function does not
     * have a `null`-shaped answer for, so it is deliberately rethrown rather than swallowed into
     * the same `null` as a real dead end.
     *
     * [maxTransfers] is the user-facing "number of connections" setting shared across every search
     * mode (the spec's own Park & Ride constraint used to hardcode this at 0 -- it is now just this
     * search's default caller-chosen value like any other mode). `null` means unlimited: no
     * `withMaxTransfers` call at all, same as [one.otpserverui.routing.bringBike]'s own unconstrained
     * default. A non-null value is translated to raptor's own round-count parameter as
     * `maxTransfers + 1`: raptor's round counting (otp-raptor's RoundTracker/SearchContext.nRounds())
     * treats round 0 as access-only and round 1 as the *first* transit boarding (0 transfers); the
     * number of rounds it runs is `maxNumberOfTransfers + 1`, used as an *exclusive* limit
     * (hasMoreRounds(): round + 1 < roundMaxLimit). So `withMaxTransfers(0)` yields exactly one round
     * (round 0) and no transit itinerary is ever found, on any network, while `withMaxTransfers(1)`
     * yields two rounds (round 0, then round 1) -- exactly the zero-transfer search this function
     * used to hardcode. Confirmed empirically against this project's own fixture (originally in
     * Approach A's own file, before it was deleted): raw raptor value 0 -> 0 paths every time; raw
     * raptor value 1 -> 11 paths, all with numberOfTransfers() == 0.
     *
     * [accessMode]/[accessMaxDuration] default to the real Park & Ride access leg (bike, 15
     * minutes), but [one.otpserverui.api.parkAndRideWithHubPreference] also calls this with
     * [StreetMode.WALK] for the hub-continuation leg of a hub-preferred trip: once the bike is
     * parked at the first stop, the rider never bikes again, so "ride transit onward from the hub"
     * is the same bike-access-then-transit-then-walk-egress shape with a trivial walk access
     * instead of a bike one (the hub coordinate is already at or next to a real stop).
     */
    fun search(
        engine: RoutingEngine,
        origin: WgsCoordinate,
        destination: WgsCoordinate,
        departureTime: Instant,
        maxTransfers: Int?,
        accessMode: StreetMode = StreetMode.BIKE,
        accessMaxDuration: Duration = MAX_BIKE_ACCESS,
    ): Itinerary? {
        val request = engine.requestBuilder()
            .withFrom(GenericLocation.fromCoordinate(origin))
            .withTo(GenericLocation.fromCoordinate(destination))
            .withDateTime(departureTime)
            .withPreferences { preferences ->
                if (maxTransfers != null) {
                    preferences.withTransfer { it.withMaxTransfers(maxTransfers + 1) }
                }
            }
            .buildRequest()

        val access = try {
            engine.streetReach(origin, accessMode, accessMaxDuration, ReachDirection.ACCESS, request)
        } catch (e: RoutingValidationException) {
            if (e.routingErrors.any { it.code == RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT }) throw e
            return null
        }
        if (access.isEmpty()) return null

        val strictEgress = try {
            engine.streetReach(destination, StreetMode.WALK, MAX_WALK_EGRESS, ReachDirection.EGRESS, request)
        } catch (e: RoutingValidationException) {
            if (e.routingErrors.any { it.code == RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT }) throw e
            return null
        }
        // Two-tier egress: only widen the search when the strict, common-case cap finds nothing --
        // this keeps today's ranking/behavior unchanged for every already-working query, and pays
        // the cost of the wider search only for the real, reachable case a strict cap alone
        // silently turned into "no route" (a real address near Mørke, confirmed to have its
        // nearest stop ~3km away, well past MAX_WALK_EGRESS but well within FALLBACK_WALK_EGRESS).
        val egress = if (strictEgress.isNotEmpty()) {
            strictEgress
        } else {
            try {
                engine.streetReach(destination, StreetMode.WALK, FALLBACK_WALK_EGRESS, ReachDirection.EGRESS, request)
            } catch (e: RoutingValidationException) {
                if (e.routingErrors.any { it.code == RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT }) throw e
                return null
            }
        }
        if (egress.isEmpty()) return null

        val paths = engine.transitSearch(access, egress, request)
        val itineraries = engine.filter(engine.toItineraries(paths, request), request)
        return itineraries.minByOrNull { it.totalDuration() }
    }
}
