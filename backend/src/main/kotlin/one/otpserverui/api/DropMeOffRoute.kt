package one.otpserverui.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import kotlinx.serialization.Serializable
import one.otpserverui.model.Itinerary
import one.otpserverui.model.TimeMode
import one.otpserverui.model.TransitHub
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.connectToRoute
import one.otpserverui.routing.nearbyRoutes
import org.opentripplanner.street.geometry.WgsCoordinate

@Serializable
data class NearbyRouteDto(val routeGtfsId: String, val routeShortName: String?, val stopIds: List<String>, val distanceMeters: Double)

@Serializable
data class NearbyRoutesResponse(val routes: List<NearbyRouteDto>)

@Serializable
data class ConnectRequest(
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val routeGtfsId: String,
    val routeStopIds: List<String>,
    val timeMode: String,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null, // null = unlimited; the "number of connections" UI setting
)

@Serializable
data class FlagStopInfoDto(
    val flagLat: Double,
    val flagLon: Double,
    val officialFinalLegDistanceMeters: Double,
    val officialFinalLegDurationSeconds: Double,
    val flagStopDistanceMeters: Double,
    val flagStopDurationSeconds: Double,
)

@Serializable
data class ConnectResponse(val itinerary: ItineraryDto, val flagStopInfo: FlagStopInfoDto?, val extraRideSeconds: Double?, val hubName: String?)

/**
 * Drop-me-off's two HTTP endpoints: `GET /nearby-routes` (browse routes whose own path passes
 * near the destination -- each [NearbyRouteDto] now carries its own `stopIds`, Task 19's fix for
 * the gap the whole-branch review found: a client needs those ids to feed straight into a later
 * `/connect` call's `routeStopIds`, not just a route's bare gtfsId) and `POST /connect` (the real
 * connect-to-route flow: [connectToRoute], Task 19 -- replaces the old client-given-flag-point walk
 * entirely). Reuses [nearbyRoutes]/[connectToRoute] for the domain logic and
 * [SearchErrorResponse]/[ItineraryDto]/[LegDto] (Task 13's `SearchDto.kt`) for the response shapes
 * shared with Park & Ride/Bring Bike's own `/search` endpoint.
 *
 * Kept as a genuinely two-step flow (unlike `/search`'s single call) because Drop-me-off really
 * is browse-then-connect: see this task's own brief for why it isn't folded into
 * `SearchRequest.mode`.
 *
 * `timeMode` is validated the same way `/search` does (Task 13): `"arrive_by"` vs. anything else
 * defaults to `depart_at` -- Task 20 (the `/search` `timeMode`-validation fix wave) had not landed
 * yet when this was written (confirmed: no task-20-report.md / ledger entry at the time), so there
 * was no shared helper yet to reuse here.
 */
fun Routing.dropMeOffRoutes(engine: RoutingEngine, hubs: List<TransitHub>) {
    get("/nearby-routes") {
        val lat = checkNotNull(call.request.queryParameters["lat"]).toDouble()
        val lon = checkNotNull(call.request.queryParameters["lon"]).toDouble()
        val radiusMeters = call.request.queryParameters["radiusMeters"]?.toDouble() ?: 500.0
        val routes = nearbyRoutes(engine, WgsCoordinate(lat, lon), radiusMeters)
        call.respond(
            NearbyRoutesResponse(
                routes.map { NearbyRouteDto(it.routeGtfsId, it.routeShortName, it.stopIds, it.distanceMeters) }
            )
        )
    }

    post("/connect") {
        val request = call.receive<ConnectRequest>()
        val origin = WgsCoordinate(request.originLat, request.originLon)
        val destination = WgsCoordinate(request.destinationLat, request.destinationLon)
        val timeMode = if (request.timeMode == "arrive_by") TimeMode.ARRIVE_BY else TimeMode.DEPART_AT

        val result = connectToRoute(
            engine, hubs, origin, destination, request.routeGtfsId, request.routeStopIds,
            timeMode, Instant.parse(request.dateTimeIso), request.preferHubs, request.maxTransfers,
        )
        if (result == null) {
            call.respond(HttpStatusCode.UnprocessableEntity, SearchErrorResponse("unreachable"))
            return@post
        }

        val app = Itinerary(legs = result.legs)
        call.respond(
            ConnectResponse(
                itinerary = ItineraryDto(
                    legs = app.legs.map {
                        LegDto(it.mode, it.distanceMeters, it.durationSeconds, it.fromLat, it.fromLon, it.toLat, it.toLon, it.fromName, it.toName, it.routeShortName, it.departureTime.toEpochSecond())
                    },
                    exceedsBikeLimit = app.exceedsBikeLimit,
                    hasLongWalkEgress = app.hasLongWalkEgress,
                ),
                flagStopInfo = result.flagStopInfo?.let {
                    FlagStopInfoDto(
                        flagLat = it.flagLat,
                        flagLon = it.flagLon,
                        officialFinalLegDistanceMeters = it.officialFinalLegDistanceMeters,
                        officialFinalLegDurationSeconds = it.officialFinalLegDurationSeconds,
                        flagStopDistanceMeters = it.flagStopDistanceMeters,
                        flagStopDurationSeconds = it.flagStopDurationSeconds,
                    )
                },
                extraRideSeconds = result.extraRideSeconds,
                hubName = result.hubName,
            )
        )
    }
}
