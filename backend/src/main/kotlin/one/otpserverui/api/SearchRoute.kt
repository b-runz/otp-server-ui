package one.otpserverui.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
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
): Pair<List<Itinerary>, String?> {
    val baseline = bringBike(engine, origin, destination, timeMode, dateTime)
    if (!preferHubs) return baseline to null
    val bestBaseline = baseline.firstOrNull() ?: return baseline to null
    val hub = HubRouting.findHubSplit(hubs, bestBaseline) ?: return baseline to null
    val hubCoordinate = WgsCoordinate(hub.lat, hub.lon)

    val stitched = runCatching {
        when (timeMode) {
            TimeMode.DEPART_AT -> {
                val legA = bringBike(engine, origin, hubCoordinate, TimeMode.DEPART_AT, dateTime).firstOrNull() ?: return@runCatching null
                val legB = bringBike(engine, hubCoordinate, destination, TimeMode.DEPART_AT, legA.endTimeAsInstant()).firstOrNull() ?: return@runCatching null
                stitchItineraries(legA, legB)
            }
            TimeMode.ARRIVE_BY -> {
                val legB = bringBike(engine, hubCoordinate, destination, TimeMode.ARRIVE_BY, dateTime).firstOrNull() ?: return@runCatching null
                val legA = bringBike(engine, origin, hubCoordinate, TimeMode.ARRIVE_BY, legB.startTimeAsInstant()).firstOrNull() ?: return@runCatching null
                stitchItineraries(legA, legB)
            }
        }
    }.getOrNull()

    return HubRouting.choose(baseline, stitched, hub.name)
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
                "park_and_ride" -> listOfNotNull(ParkAndRideFinder.search(engine, origin, destination, dateTime)) to null
                else -> bringBikeWithHubPreference(engine, hubs, origin, destination, timeMode, dateTime, request.preferHubs)
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
