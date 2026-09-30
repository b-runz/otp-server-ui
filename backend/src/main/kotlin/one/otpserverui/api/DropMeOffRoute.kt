package one.otpserverui.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import kotlinx.serialization.Serializable
import one.otpserverui.domain.toAppItinerary
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.connectByFlaggingABus
import one.otpserverui.routing.nearbyRoutes
import org.opentripplanner.street.geometry.WgsCoordinate

@Serializable
data class NearbyRouteDto(val routeGtfsId: String, val routeShortName: String?, val distanceMeters: Double)

@Serializable
data class NearbyRoutesResponse(val routes: List<NearbyRouteDto>)

@Serializable
data class ConnectRequest(
    val flagLat: Double,
    val flagLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val dateTimeIso: String,
)

/**
 * Drop-me-off's two HTTP endpoints: `GET /nearby-routes` (browse routes whose own path passes
 * near the destination) and `POST /connect` (the direct, non-transit leg from a chosen flag
 * point to the destination). Reuses [nearbyRoutes]/[connectByFlaggingABus] (Task 11) for the
 * domain logic and [SearchErrorResponse]/[ItineraryDto]/[LegDto] (Task 13's `SearchDto.kt`) for
 * the response shapes shared with Park & Ride/Bring Bike's own `/search` endpoint.
 *
 * Kept as a genuinely two-step flow (unlike `/search`'s single call) because Drop-me-off really
 * is browse-then-connect: see this task's own brief for why it isn't folded into
 * `SearchRequest.mode`. Not wired into the real production application here -- Task 16 registers
 * this alongside `searchRoute`/`geocodeRoute` in `Main.kt`'s own `module()`.
 */
fun Routing.dropMeOffRoutes(engine: RoutingEngine) {
    get("/nearby-routes") {
        val lat = checkNotNull(call.request.queryParameters["lat"]).toDouble()
        val lon = checkNotNull(call.request.queryParameters["lon"]).toDouble()
        val radiusMeters = call.request.queryParameters["radiusMeters"]?.toDouble() ?: 500.0
        val routes = nearbyRoutes(engine, WgsCoordinate(lat, lon), radiusMeters)
        call.respond(
            NearbyRoutesResponse(
                routes.map { NearbyRouteDto(it.routeGtfsId, it.routeShortName, it.distanceMeters) }
            )
        )
    }

    post("/connect") {
        val request = call.receive<ConnectRequest>()
        val flagPoint = WgsCoordinate(request.flagLat, request.flagLon)
        val destination = WgsCoordinate(request.destinationLat, request.destinationLon)
        val itinerary = connectByFlaggingABus(engine, flagPoint, destination, Instant.parse(request.dateTimeIso))
        if (itinerary == null) {
            call.respond(HttpStatusCode.UnprocessableEntity, SearchErrorResponse("unreachable"))
            return@post
        }
        val app = itinerary.toAppItinerary()
        call.respond(
            ItineraryDto(
                legs = app.legs.map {
                    LegDto(it.mode, it.distanceMeters, it.durationSeconds, it.fromLat, it.fromLon, it.toLat, it.toLon, it.routeShortName, it.departureTime.toEpochSecond())
                },
                exceedsBikeLimit = app.exceedsBikeLimit,
                hasLongWalkEgress = app.hasLongWalkEgress,
            )
        )
    }
}
