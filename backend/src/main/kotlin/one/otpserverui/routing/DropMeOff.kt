package one.otpserverui.routing

import java.time.Instant
import one.otpserverui.domain.NearbyRoutesFinder
import one.otpserverui.domain.closestPointOnPolyline
import one.otpserverui.domain.decodePolyline
import one.otpserverui.domain.toAppItinerary
import one.otpserverui.domain.toPatternDto
import one.otpserverui.model.FlagStopConnectResult
import one.otpserverui.model.FlagStopInfo
import one.otpserverui.model.Itinerary
import one.otpserverui.model.Leg
import one.otpserverui.model.NearbyRoute
import one.otpserverui.model.TimeMode
import one.otpserverui.model.TransitHub
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

// Fallback radius, tried only when the caller's own [radiusMeters] finds nothing (see
// [nearbyRoutes]'s own two-tier search below) -- mirrors [one.otpserverui.routing.parkandride
// .ParkAndRideFinder]'s own MAX_WALK_EGRESS/FALLBACK_WALK_EGRESS two-tier egress search, for the
// same reason: a real rural destination (Skovstien 5, Mørke) confirmed this gap directly against
// the production graph -- its nearest route's own path is ~3.7km away, well past the UI's default
// 500m radius, and this function had no fallback at all, just a silent empty list. [stopsNear]'s
// own bounding-box lookup (NearbyStops.kt) is a cheap spatial-index query, not a flood fill, so a
// much larger fallback box costs nothing extra in the common case where the strict radius succeeds.
private val FALLBACK_NEARBY_ROUTES_RADIUS_METERS = 10_000.0

/**
 * Drop-me-off's "nearby routes" lookup: every bus route serving a stop within [radiusMeters] of
 * [destination] (or, if that finds nothing, within [FALLBACK_NEARBY_ROUTES_RADIUS_METERS] -- see
 * that constant's own KDoc), ranked by how close that route's own path (not just a stop) comes to
 * [destination]. Composes this task's own [stopsNear]/`toPatternDto`/
 * [NearbyRoutesFinder.rankCandidates] -- ported from bikebus's own `TripViewModel.findNearbyRoutes`
 * embedded-engine path, minus any network/GraphQL step (there is none on this path either way).
 */
fun nearbyRoutes(engine: RoutingEngine, destination: WgsCoordinate, radiusMeters: Double): List<NearbyRoute> {
    val strict = engine.stopsNear(destination, radiusMeters)
    val patterns = if (strict.isNotEmpty()) {
        strict
    } else {
        engine.stopsNear(destination, FALLBACK_NEARBY_ROUTES_RADIUS_METERS)
    }.map { it.toPatternDto() }
    return NearbyRoutesFinder.rankCandidates(patterns, destination.latitude(), destination.longitude())
}

// The real CONNECT_BATCH_SIZE value from bikebus's own TripViewModel.connectViaRoute/
// buildStitchedNearbyItinerary -- this project has no equivalent shared constants file (Task 19
// brief), so it's hardcoded here rather than imported.
private const val CONNECT_BATCH_SIZE = 20

// Mirrors HubRouting.MAX_ACCEPTABLE_DETOUR_SECONDS (10 minutes) -- duplicated rather than reused
// for the same reason NearbyRoutesFinder.kt already duplicates HubRouting's NEAR_STOP_RADIUS_METERS:
// see this file's own [chooseConnection] KDoc for why HubRouting.choose's real OTP-`Itinerary`-typed
// function can't be reused directly here.
private const val MAX_ACCEPTABLE_DETOUR_SECONDS = 600.0

// Mirrors HubRouting.CONNECTOR_LEG_MAX_METERS -- see [trimAppHubConnector]'s own KDoc.
private const val CONNECTOR_LEG_MAX_METERS = 50.0

/**
 * Drop-me-off's real "connect to route" flow -- the one bikebus's own `TripViewModel.connectToRoute`
 * implements (`bikebus/app/src/main/java/one/brj/bikebus/TripViewModel.kt`, lines 570-698): a
 * via-constrained origin -> destination search restricted to [routeGtfsId]'s own [routeStopIds]
 * (see [connectViaRoute]), optionally re-run through a hub-stitched alternative when [preferHubs]
 * is set and a cataloged hub sits at one of the plain result's own transfer points (see
 * [buildStitchedNearbyItinerary] and [NearbyRoutesFinder.findSplitForRoute]), then a flag-stop
 * decision comparing the bus's own path against the official final stop (see [confirmFlagStop]).
 *
 * Replaces `connectByFlaggingABus` entirely (deleted by this task): that function only ever walked
 * from a client-given point, never actually searched for or rode the chosen route.
 *
 * **Stateless-HTTP adaptation (this task's own ruling).** bikebus's real `connectToRoute` reads
 * `state.itineraries` -- a baseline itinerary list the screen's own prior `/search` call already
 * fetched -- to compute `extraRideSeconds`. This project's `/connect` endpoint has no session state,
 * so it re-runs a fresh, unconstrained [bringBike] call (same origin/destination/timeMode/dateTime)
 * to get a comparable baseline instead. One extra real search per `/connect` call; `/connect` is
 * already an expensive, low-frequency, user-triggered endpoint, so this is negligible.
 */
fun connectToRoute(
    engine: RoutingEngine,
    hubs: List<TransitHub>,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    routeGtfsId: String,
    routeStopIds: List<String>,
    timeMode: TimeMode,
    dateTime: Instant,
    preferHubs: Boolean,
    maxTransfers: Int? = null,
): FlagStopConnectResult? {
    val plainLegs = connectViaRoute(engine, origin, destination, routeGtfsId, routeStopIds, timeMode, dateTime, maxTransfers) ?: return null

    var finalLegs = plainLegs
    var hubName: String? = null
    if (preferHubs) {
        val split = NearbyRoutesFinder.findSplitForRoute(plainLegs, hubs, routeGtfsId)
        if (split != null) {
            val stitched = runCatching {
                buildStitchedNearbyItinerary(
                    engine, origin, destination, split.hub, routeGtfsId, routeStopIds,
                    split.routeOnOriginSide, timeMode, dateTime, maxTransfers,
                )
            }.getOrNull()
            if (stitched != null && stitched.any { it.routeGtfsId == routeGtfsId }) {
                val (chosen, chosenHubName) = chooseConnection(plainLegs, stitched, split.hub.name)
                finalLegs = chosen
                hubName = chosenHubName
            }
        }
    }

    // Same maxTransfers cap as the constrained search above, so extraRideSeconds compares two
    // itineraries under the same transfer budget -- an unconstrained baseline here would make the
    // comparison meaningless whenever the user has actually chosen a finite cap.
    val baseline = bringBike(engine, origin, destination, timeMode, dateTime, maxTransfers = maxTransfers)
    val extraRideSeconds = baseline.minOfOrNull { it.totalDuration().seconds.toDouble() }
        ?.let { baselineCost -> Itinerary(legs = finalLegs).totalDurationSeconds - baselineCost }

    val busLeg = finalLegs.firstOrNull { it.routeGtfsId == routeGtfsId }
    val flagStopInfo = busLeg?.let { confirmFlagStop(engine, it, finalLegs.last(), destination, dateTime) }

    return FlagStopConnectResult(legs = finalLegs, flagStopInfo = flagStopInfo, extraRideSeconds = extraRideSeconds, hubName = hubName)
}

// Hardcodes BRING_BIKE's request shape (via bringBike) regardless of any caller search mode --
// mirrors bikebus's own connectViaRoute comment: Park & Ride has no "connect point-to-point via a
// specific route" search concept, and BRING_BIKE is the only non-Park&Ride access mode this project
// has. A `via` stop-list constraint only guarantees the itinerary passes through a stop belonging to
// the route, not that it rides that route -- shared stops (interchanges) can be satisfied by a
// different, faster route -- so this explicitly scans a wide batch ([CONNECT_BATCH_SIZE]) for the
// cheapest itinerary that actually contains a leg on [routeGtfsId].
private fun connectViaRoute(
    engine: RoutingEngine,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    routeGtfsId: String,
    routeStopIds: List<String>,
    timeMode: TimeMode,
    dateTime: Instant,
    maxTransfers: Int?,
): List<Leg>? {
    val itineraries = bringBike(
        engine, origin, destination, timeMode, dateTime,
        viaStopIds = routeStopIds, numItineraries = CONNECT_BATCH_SIZE, maxTransfers = maxTransfers,
    ).map { it.toAppItinerary().legs }
    return NearbyRoutesFinder.pickCheapestQualifying(itineraries, routeGtfsId)
}

// Mirrors bikebus's own buildStitchedNearbyItinerary / SearchRoute.kt's bringBikeWithHubPreference
// DEPART_AT/ARRIVE_BY chaining (Task 13), but one side of the split must stay via-constrained to
// [routeStopIds] (exactly which side depends on [routeOnOriginSide]) while the other is an ordinary
// [bringBike] query -- see NearbyRoutesFinder.findSplitForRoute's own KDoc for how that side is
// determined.
private fun buildStitchedNearbyItinerary(
    engine: RoutingEngine,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    hub: TransitHub,
    routeGtfsId: String,
    routeStopIds: List<String>,
    routeOnOriginSide: Boolean,
    timeMode: TimeMode,
    dateTime: Instant,
    maxTransfers: Int?,
): List<Leg>? {
    val hubCoordinate = WgsCoordinate(hub.lat, hub.lon)

    fun plain(from: WgsCoordinate, to: WgsCoordinate, at: Instant): List<Leg>? =
        bringBike(engine, from, to, timeMode, at, maxTransfers = maxTransfers).fastest()?.toAppItinerary()?.legs

    fun viaRoute(from: WgsCoordinate, to: WgsCoordinate, at: Instant): List<Leg>? {
        val itineraries = bringBike(
            engine, from, to, timeMode, at,
            viaStopIds = routeStopIds, numItineraries = CONNECT_BATCH_SIZE, maxTransfers = maxTransfers,
        ).map { it.toAppItinerary().legs }
        return NearbyRoutesFinder.pickCheapestQualifying(itineraries, routeGtfsId)
    }

    val legA: List<Leg>?
    val legB: List<Leg>?
    when (timeMode) {
        TimeMode.DEPART_AT -> {
            legA = if (routeOnOriginSide) viaRoute(origin, hubCoordinate, dateTime) else plain(origin, hubCoordinate, dateTime)
            if (legA == null) return null
            val arrivalInstant = legA.last().arrivalTime.toInstant()
            legB = if (routeOnOriginSide) plain(hubCoordinate, destination, arrivalInstant) else viaRoute(hubCoordinate, destination, arrivalInstant)
        }
        TimeMode.ARRIVE_BY -> {
            legB = if (routeOnOriginSide) plain(hubCoordinate, destination, dateTime) else viaRoute(hubCoordinate, destination, dateTime)
            if (legB == null) return null
            val departureInstant = legB.first().departureTime.toInstant()
            legA = if (routeOnOriginSide) viaRoute(origin, hubCoordinate, departureInstant) else plain(origin, hubCoordinate, departureInstant)
        }
    }
    if (legA == null || legB == null) return null
    return trimAppHubConnector(legA, fromEnd = true) + trimAppHubConnector(legB, fromEnd = false)
}

/**
 * Drop-me-off's "flag a bus" decision: projects [destination] onto [busLeg]'s own path (its real
 * `legGeometryPoints`, not just the official stop it alights at) to find the closest point along
 * the route the rider could actually be dropped at, then plans a short direct route from that flag
 * point to [destination] using [finalLeg]'s own real mode, so the UI can compare it against the
 * official final leg ([NearbyRoutesFinder.buildFlagStopInfo]).
 *
 * Ported from bikebus's own `confirmFlagStop` (`TripViewModel.kt` lines 673-698): that function
 * never used `timeMode`/arrive-by semantics for this short direct query either (its own
 * `OtpQueryBuilder.buildDirectRequest` only ever sets `earliestDeparture`), so this doesn't take a
 * `timeMode` parameter despite Task 19's own brief sketch including one -- confirmed directly
 * against bikebus's real source before omitting it; see this task's report for detail.
 */
private fun confirmFlagStop(
    engine: RoutingEngine,
    busLeg: Leg,
    finalLeg: Leg,
    destination: WgsCoordinate,
    dateTime: Instant,
): FlagStopInfo? {
    // Mirrors bikebus's own guard: the direct flag-point route only ever makes sense for the two
    // street modes Bring Bike's own final leg can actually be.
    val mode = when (finalLeg.mode) {
        "WALK" -> StreetMode.WALK
        "BICYCLE" -> StreetMode.BIKE
        else -> return null
    }
    val points = busLeg.legGeometryPoints?.let { decodePolyline(it) } ?: return null
    val flagPoint = closestPointOnPolyline(points, destination.latitude(), destination.longitude()) ?: return null
    val flagCoordinate = WgsCoordinate(flagPoint.lat, flagPoint.lon)

    val request = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(flagCoordinate))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(dateTime)
        .buildRequest()
    val directLeg = engine.directRoute(flagCoordinate, destination, mode, request)
        .firstOrNull()?.toAppItinerary()?.legs?.firstOrNull() ?: return null

    return NearbyRoutesFinder.buildFlagStopInfo(
        flagLat = flagPoint.lat,
        flagLon = flagPoint.lon,
        officialLeg = finalLeg,
        directLeg = directLeg,
    )
}

/**
 * **This task's own resolved design question** (see the brief's Step 1): [HubRouting.choose]
 * (Task 10) is typed for real OTP `org.opentripplanner.model.plan.Itinerary`, because it runs
 * inside the routing-engine tier before any app-level conversion happens. This flow's
 * hub-stitching comparison, by contrast, already operates on post-`toAppItinerary()` app-level
 * [Leg] lists -- the same type [NearbyRoutesFinder.pickCheapestQualifying]/[findSplitForRoute]
 * (Task 18) use -- so forcing [baselineLegs]/[stitchedLegs] through [HubRouting.choose] would mean
 * either reconstructing a fake OTP `Itinerary` around app-level legs (which OTP's own
 * `Itinerary`/`ItineraryBuilder` isn't meant to wrap) or an unsafe cast; both strictly worse than
 * this short, type-correct duplicate of the same comparison.
 *
 * This reuses this project's own [Itinerary] (app-level) and its existing
 * [Itinerary.totalDurationSeconds] (wall-clock departure-to-arrival span, not a sum of individual
 * leg durations -- matching real OTP `Itinerary.totalDuration()`'s own semantics, which
 * [HubRouting.choose] compares) rather than `legs.sumOf { it.durationSeconds }`, so the comparison
 * stays apples-to-apples with [HubRouting.choose]'s real logic: keep the stitched result only if it
 * doesn't cost more than [MAX_ACCEPTABLE_DETOUR_SECONDS] over the baseline (mirroring
 * [HubRouting.MAX_ACCEPTABLE_DETOUR_SECONDS] exactly, same 10-minute value, duplicated here since
 * that constant is private to [HubRouting]). Unlike [HubRouting.choose] (which takes a *list* of
 * baseline itineraries, because [one.otpserverui.api.bringBikeWithHubPreference]'s own baseline is
 * every result from one [bringBike] call), there is exactly one baseline itinerary here --
 * [connectViaRoute]'s own single cheapest-qualifying pick -- matching bikebus's own real
 * `connectToRoute`, which also wraps its single `plainLegs` baseline in a one-element list before
 * calling its own `HubRouting.choose`.
 */
internal fun chooseConnection(baselineLegs: List<Leg>, stitchedLegs: List<Leg>?, hubName: String): Pair<List<Leg>, String?> {
    if (stitchedLegs == null) return baselineLegs to null
    // Unlike HubRouting.choose, there's no `?: return listOf(stitched) to hubName` empty-baseline
    // fallback here: baselineLegs comes from connectViaRoute's own non-null return (connectToRoute
    // returns null before ever calling this function otherwise), so it's guaranteed non-empty by
    // construction -- that branch genuinely doesn't apply to this call site.
    val baselineCost = Itinerary(legs = baselineLegs).totalDurationSeconds
    val stitchedCost = Itinerary(legs = stitchedLegs).totalDurationSeconds
    return if (stitchedCost - baselineCost <= MAX_ACCEPTABLE_DETOUR_SECONDS) {
        stitchedLegs to hubName
    } else {
        baselineLegs to null
    }
}

/**
 * App-level equivalent of [HubRouting.trimHubConnector], operating on this project's own app-level
 * [Leg] instead of OTP's native `Leg`. [buildStitchedNearbyItinerary] (this function's only caller)
 * already works with post-`toAppItinerary()` data throughout (matching
 * [NearbyRoutesFinder.pickCheapestQualifying]/[findSplitForRoute]'s own Task 18 app-level design),
 * so routing it back through the OTP-native-typed [HubRouting.trimHubConnector] would mean either
 * reconstructing a fake OTP `Leg` or an unsafe cast -- the same type-boundary call this file's
 * [chooseConnection] already makes, for the same reason. Logic and the 50m threshold
 * ([CONNECTOR_LEG_MAX_METERS]) are copied verbatim from [HubRouting.trimHubConnector]; "transit"
 * here means "not WALK/BICYCLE", matching [NearbyRoutesFinder]'s own private `isTransit` helper.
 */
internal fun trimAppHubConnector(legs: List<Leg>, fromEnd: Boolean): List<Leg> {
    val edge = if (fromEnd) legs.lastOrNull() else legs.firstOrNull()
    val isConnector = edge != null && isAppStreetLeg(edge) && edge.distanceMeters < CONNECTOR_LEG_MAX_METERS
    if (!isConnector) return legs
    return if (fromEnd) legs.dropLast(1) else legs.drop(1)
}

// "Transit" here means "not WALK/BICYCLE", matching NearbyRoutesFinder's own private `isTransit`.
internal fun isAppStreetLeg(leg: Leg): Boolean = leg.mode == "WALK" || leg.mode == "BICYCLE"
