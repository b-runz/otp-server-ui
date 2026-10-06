package one.otpserverui.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import java.time.Duration
import java.time.Instant
import one.otpserverui.domain.toAppItinerary
import one.otpserverui.model.TimeMode
import one.otpserverui.model.TransitHub
import one.otpserverui.routing.HubRouting
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.bringBike
import one.otpserverui.routing.parkandride.ParkAndRideFinder
import org.opentripplanner.core.model.basic.Cost
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.routing.error.RoutingValidationException
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

// Leg B of a Park & Ride hub-preference stitch (see parkAndRideWithHubPreference) starts walking
// from the hub coordinate itself, which is already at or next to a real stop -- this cap only
// needs to be big enough to actually reach that nearby stop, not a real access-distance limit.
private val HUB_CONTINUATION_WALK_ACCESS: Duration = Duration.ofMinutes(15)

/**
 * Stitches two independently-planned [Itinerary]s (origin -> hub, hub -> destination) into one
 * combined itinerary, trimming the near-zero hub connector leg off each side first (see
 * [HubRouting.trimHubConnector]'s own kdoc for why that connector exists).
 *
 * [Itinerary.ofScheduledTransit] is used (not [Itinerary.ofDirect]) because a hub split is only
 * ever found on a transit-to-transit transfer ([HubRouting.findHubSplit] only matches those), so
 * the combined legs always include real transit legs on at least one side.
 *
 * [org.opentripplanner.model.plan.ItineraryBuilder.build] computes the itinerary's generalized
 * cost from its builder's own `generalizedCost` field, which defaults to `null` -- calling
 * `build()` without first calling `withGeneralizedCost(...)` NPEs (confirmed by reading
 * `ItineraryBuilder.java`'s `calculateGeneralizedCostWithoutPenalty()`), so the combined cost
 * (the two source itineraries' own [Itinerary.generalizedCost] added together) is supplied here.
 */
internal fun stitchItineraries(legA: Itinerary, legB: Itinerary): Itinerary {
    val trimmedA = HubRouting.trimHubConnector(legA.legs(), fromEnd = true)
    val trimmedB = HubRouting.trimHubConnector(legB.legs(), fromEnd = false)
    val combinedCost = Cost.costOfSeconds(legA.generalizedCost() + legB.generalizedCost())
    return Itinerary.ofScheduledTransit(trimmedA + trimmedB)
        .withGeneralizedCost(combinedCost)
        .build()
}

private fun bringBikeWithHubPreference(
    engine: RoutingEngine,
    hubs: List<TransitHub>,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    timeMode: TimeMode,
    dateTime: Instant,
    preferHubs: Boolean,
    maxTransfers: Int?,
): Pair<List<Itinerary>, String?> {
    val baseline = bringBike(engine, origin, destination, timeMode, dateTime, maxTransfers = maxTransfers)
    if (!preferHubs) return baseline to null
    val bestBaseline = baseline.firstOrNull() ?: return baseline to null
    val hub = HubRouting.findHubSplit(hubs, bestBaseline) ?: return baseline to null
    val hubCoordinate = WgsCoordinate(hub.lat, hub.lon)

    val stitched = runCatching {
        when (timeMode) {
            TimeMode.DEPART_AT -> {
                val legA = bringBike(engine, origin, hubCoordinate, TimeMode.DEPART_AT, dateTime, maxTransfers = maxTransfers).firstOrNull() ?: return@runCatching null
                val legB = bringBike(engine, hubCoordinate, destination, TimeMode.DEPART_AT, legA.endTimeAsInstant(), maxTransfers = maxTransfers).firstOrNull() ?: return@runCatching null
                stitchItineraries(legA, legB)
            }
            TimeMode.ARRIVE_BY -> {
                val legB = bringBike(engine, hubCoordinate, destination, TimeMode.ARRIVE_BY, dateTime, maxTransfers = maxTransfers).firstOrNull() ?: return@runCatching null
                val legA = bringBike(engine, origin, hubCoordinate, TimeMode.ARRIVE_BY, legB.startTimeAsInstant(), maxTransfers = maxTransfers).firstOrNull() ?: return@runCatching null
                stitchItineraries(legA, legB)
            }
        }
    }.getOrNull()

    return HubRouting.choose(baseline, stitched, hub.name)
}

/**
 * Park & Ride's own hub-preference composition (see [bringBikeWithHubPreference]'s own kdoc for
 * the shared "split the trip at the hub" idea). The original bikebus app never applied
 * hub-stitching to Park & Ride at all -- its own `fetchItineraries` returned immediately for that
 * mode before reaching the hub-preference check, because Park & Ride was hardcoded to zero
 * transfers there, so a transit-to-transit transfer (what [HubRouting.findHubSplit] matches
 * against) could never exist on a Park & Ride itinerary in the first place. Once Park & Ride's own
 * transfer cap became a caller-chosen [maxTransfers] setting instead of a hardcoded zero, that
 * precondition stopped holding -- a Park & Ride itinerary really can have a transit-to-transit
 * transfer now, so hub preference is a real, applicable feature for this mode too.
 *
 * Leg A (origin -> hub) reuses [ParkAndRideFinder.search] unchanged: "bike to a stop, ride
 * transit, end at the hub" is exactly what that function already does when the hub coordinate is
 * passed as the destination. Leg B (hub -> destination) reuses the same function with
 * [StreetMode.WALK] as the access mode instead of the default bike: once the bike is parked at leg
 * A's own first stop, the rider never bikes again, so "ride transit onward from the hub" is the
 * same bike-access-then-transit-then-walk-egress shape with a trivial walk access leg (the hub
 * coordinate is already at or next to a real stop) rather than a bike one.
 */
internal fun parkAndRideWithHubPreference(
    engine: RoutingEngine,
    hubs: List<TransitHub>,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    dateTime: Instant,
    preferHubs: Boolean,
    maxTransfers: Int?,
): Pair<List<Itinerary>, String?> {
    val baseline = ParkAndRideFinder.search(engine, origin, destination, dateTime, maxTransfers)
    val baselineList = listOfNotNull(baseline)
    if (!preferHubs || baseline == null) return baselineList to null
    val hub = HubRouting.findHubSplit(hubs, baseline) ?: return baselineList to null
    val hubCoordinate = WgsCoordinate(hub.lat, hub.lon)

    val stitched = runCatching {
        val legA = ParkAndRideFinder.search(engine, origin, hubCoordinate, dateTime, maxTransfers)
            ?: return@runCatching null
        val legB = ParkAndRideFinder.search(
            engine, hubCoordinate, destination, legA.endTimeAsInstant(), maxTransfers,
            accessMode = StreetMode.WALK, accessMaxDuration = HUB_CONTINUATION_WALK_ACCESS,
        ) ?: return@runCatching null
        stitchItineraries(legA, legB)
    }.getOrNull()

    return HubRouting.choose(baselineList, stitched, hub.name)
}

fun Routing.searchRoute(engine: RoutingEngine, hubs: List<TransitHub>) {
    post("/search") {
        val request = call.receive<SearchRequest>()

        val dateTime = try {
            Instant.parse(request.dateTimeIso)
        } catch (e: java.time.format.DateTimeParseException) {
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("invalid_request"))
            return@post
        }
        if (request.mode != "park_and_ride" && request.mode != "bring_bike") {
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("unknown_mode"))
            return@post
        }
        if (request.timeMode != "depart_at" && request.timeMode != "arrive_by") {
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("invalid_time_mode"))
            return@post
        }
        if (request.mode == "park_and_ride" && request.timeMode == "arrive_by") {
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("unsupported_time_mode"))
            return@post
        }

        val origin = WgsCoordinate(request.originLat, request.originLon)
        val destination = WgsCoordinate(request.destinationLat, request.destinationLon)
        val timeMode = if (request.timeMode == "arrive_by") TimeMode.ARRIVE_BY else TimeMode.DEPART_AT

        val (itineraries, notice) = try {
            when (request.mode) {
                "park_and_ride" -> parkAndRideWithHubPreference(engine, hubs, origin, destination, dateTime, request.preferHubs, request.maxTransfers)
                else -> bringBikeWithHubPreference(engine, hubs, origin, destination, timeMode, dateTime, request.preferHubs, request.maxTransfers)
            }
        } catch (e: RoutingValidationException) {
            call.respond(HttpStatusCode.UnprocessableEntity, SearchErrorResponse("no_coverage"))
            return@post
        }

        val dtos = itineraries.map { it.toAppItinerary() }.map { app ->
            ItineraryDto(
                legs = app.legs.map {
                    LegDto(it.mode, it.distanceMeters, it.durationSeconds, it.fromLat, it.fromLon, it.toLat, it.toLon, it.fromName, it.toName, it.routeShortName, it.departureTime.toEpochSecond())
                },
                exceedsBikeLimit = app.exceedsBikeLimit,
                hasLongWalkEgress = app.hasLongWalkEgress,
            )
        }
        call.respond(SearchResponse(dtos, notice))
    }
}
