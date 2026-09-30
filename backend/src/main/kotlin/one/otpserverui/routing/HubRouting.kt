package one.otpserverui.routing

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import one.otpserverui.model.TransitHub
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.model.plan.Leg

/**
 * Backs the "prefer transit hubs" toggle.
 *
 * OTP's transfer-priority config (GTFS transfers.txt / NeTEx interchange
 * weighting) only re-ranks stops within a route pair Raptor already picked --
 * it can't bias which trips get chosen at all. The `via` GraphQL parameter
 * looked like it should do better (it's meant to force the itinerary through
 * a given stop), but in practice OTP satisfies it however is cheapest, which
 * for a stop the bus already passes on its way to a nearby non-hub stop is
 * just riding on -- the actual route-to-route transfer still happens at the
 * non-hub stop. Neither lever can distinguish two transfer points that are
 * genuinely cost-tied (same road, same buses), because there's no real cost
 * gap left to exploit.
 *
 * So instead of asking OTP's optimizer to prefer a hub, this splits the trip
 * in two and asks two well-posed questions it can't dodge: "get me to the
 * hub" and, separately, "get me from the hub onward." Neither sub-search has
 * the option to ride past the hub, because in each one the hub *is* the
 * requested endpoint.
 *
 * Ported (adapted, not verbatim) from bikebus's own
 * `one.brj.bikebus.domain.HubRouting`: that source operates on bikebus's own
 * app-level `Itinerary`/`Leg`/`TransitHub` model types
 * (`one.brj.bikebus.model`), which this project has no equivalent of -- here
 * this operates directly on OTP's own `org.opentripplanner.model.plan.Itinerary`/
 * `Leg` (the same types [one.otpserverui.routing.RoutingEngine]'s whole
 * building-block tier already uses), with `isTransit(mode: String)` replaced by
 * `Leg.isTransitLeg`, the real OTP-native equivalent check. The core
 * split-finding/cost-comparison logic and its constants are unchanged.
 */
object HubRouting {
    // How close a transfer point in the baseline itinerary needs to be to a
    // cataloged hub to treat that hub as a substitute for it.
    private const val NEAR_STOP_RADIUS_METERS = 1_000.0
    private const val MAX_ACCEPTABLE_DETOUR_SECONDS = 600.0 // 10 minutes
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Finds the first transit-to-transit transfer in the itinerary that happens near a
     * cataloged hub, and returns that hub -- the substitution target for splitting the trip. */
    fun findHubSplit(hubs: List<TransitHub>, itinerary: Itinerary): TransitHub? {
        val legs = itinerary.legs()
        for (i in 0 until legs.size - 1) {
            val alight = legs[i]
            val board = legs[i + 1]
            if (!alight.isTransitLeg || !board.isTransitLeg) continue
            val alightCoordinate = alight.to().coordinate
            val nearest = hubs.minByOrNull {
                haversineMeters(it.lat, it.lon, alightCoordinate.latitude(), alightCoordinate.longitude())
            } ?: continue
            if (haversineMeters(nearest.lat, nearest.lon, alightCoordinate.latitude(), alightCoordinate.longitude()) <= NEAR_STOP_RADIUS_METERS) {
                return nearest
            }
        }
        return null
    }

    // A hub's platforms are looked up by its centroid coordinate, not the exact platform, so
    // OTP inserts a near-zero bike/walk hop between "the hub" and the actual stop at whichever
    // end of a sub-search lands there. That hop is an artifact of the split, not a real part of
    // the trip -- drop it so the stitched itinerary reads as a clean bike-bus-bus-bike route
    // instead of showing a spurious near-0km leg at the join.
    //
    // (bikebus's own version of this function has a much longer comment here tracing exactly why
    // its app-layer `dropTinyLegs` post-processing step usually already removes this connector
    // leg before this function ever runs, making this check a frequent no-op there. This project
    // has no equivalent app-layer itinerary post-processing pipeline -- there is no
    // `ItineraryPostProcessing.kt`/`dropTinyLegs` here -- so that reasoning doesn't carry over;
    // this function is this project's only line of defense against a spurious connector leg.)
    private const val CONNECTOR_LEG_MAX_METERS = 50.0

    fun trimHubConnector(legs: List<Leg>, fromEnd: Boolean): List<Leg> {
        val edge = if (fromEnd) legs.lastOrNull() else legs.firstOrNull()
        val isConnector = edge != null && !edge.isTransitLeg && edge.distanceMeters() < CONNECTOR_LEG_MAX_METERS
        if (!isConnector) return legs
        return if (fromEnd) legs.dropLast(1) else legs.drop(1)
    }

    /** Returns the itineraries to show, and the hub name if the stitched route was used. */
    fun choose(baseline: List<Itinerary>, stitched: Itinerary?, hubName: String): Pair<List<Itinerary>, String?> {
        if (stitched == null) return baseline to null
        val baselineCost = baseline.minOfOrNull { it.totalDuration().seconds.toDouble() } ?: return listOf(stitched) to hubName
        return if (stitched.totalDuration().seconds.toDouble() - baselineCost <= MAX_ACCEPTABLE_DETOUR_SECONDS) {
            listOf(stitched) to hubName
        } else {
            baseline to null
        }
    }

    // Copied verbatim from bikebus's own `one.brj.bikebus.domain.haversineMeters`
    // (`app/src/main/java/one/brj/bikebus/domain/Geo.kt`), which this function's bikebus-side
    // counterpart relied on via same-package visibility -- inlined here as a private function
    // since this project has no equivalent shared `Geo.kt` utility to import it from.
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(p1) * cos(p2) * sin(dLambda / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }
}
