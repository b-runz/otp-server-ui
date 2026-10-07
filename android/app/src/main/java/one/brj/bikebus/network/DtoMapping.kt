package one.brj.bikebus.network

import one.brj.bikebus.model.FlagStopInfo
import one.brj.bikebus.model.Itinerary
import one.brj.bikebus.model.Leg
import one.brj.bikebus.model.NearbyRoute
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

fun LegDto.toUiModel(): Leg = Leg(
    mode = mode,
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    fromLat = fromLat,
    fromLon = fromLon,
    toLat = toLat,
    toLon = toLon,
    fromName = fromName,
    toName = toName,
    routeShortName = routeShortName,
    departureTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(departureEpochSecond), ZoneId.systemDefault()),
)

fun ItineraryDto.toUiModel(): Itinerary = Itinerary(
    legs = legs.map { it.toUiModel() },
    exceedsBikeLimit = exceedsBikeLimit,
    hasLongWalkEgress = hasLongWalkEgress,
)

fun NearbyRouteDto.toUiModel(): NearbyRoute = NearbyRoute(
    routeGtfsId = routeGtfsId,
    routeShortName = routeShortName,
    stopIds = stopIds,
    distanceMeters = distanceMeters,
)

fun FlagStopInfoDto.toUiModel(): FlagStopInfo = FlagStopInfo(
    flagLat = flagLat,
    flagLon = flagLon,
    officialFinalLegDistanceMeters = officialFinalLegDistanceMeters,
    officialFinalLegDurationSeconds = officialFinalLegDurationSeconds,
    flagStopDistanceMeters = flagStopDistanceMeters,
    flagStopDurationSeconds = flagStopDurationSeconds,
)
