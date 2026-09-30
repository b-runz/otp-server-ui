package one.otpserverui.domain

import one.otpserverui.model.Itinerary
import one.otpserverui.model.Leg
import org.opentripplanner.model.plan.leg.ScheduledTransitLeg
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.PolylineEncoder
import org.opentripplanner.model.plan.Itinerary as OtpItinerary
import org.opentripplanner.model.plan.Leg as OtpLeg

/**
 * Maps OTP's own [OtpItinerary]/[OtpLeg] (returned by the embedded routing engine) onto
 * this app's [Itinerary]/[Leg] model (what the UI renders) -- the in-process equivalent of
 * [one.otpserverui.domain.toItineraries]'s GraphQL-JSON-to-app-model mapping.
 */
fun OtpItinerary.toAppItinerary(): Itinerary =
    Itinerary(legs = dropTinyLegs(legs().map { it.toAppLeg() }))

private fun OtpLeg.toAppLeg(): Leg {
    val fromCoordinate = from().coordinate
    val toCoordinate = to().coordinate
    val route = route()
    return Leg(
        mode = legModeName(),
        distanceMeters = distanceMeters(),
        durationSeconds = duration().seconds.toDouble(),
        fromLat = fromCoordinate.latitude(),
        fromLon = fromCoordinate.longitude(),
        toLat = toCoordinate.latitude(),
        toLon = toCoordinate.longitude(),
        fromName = from().name?.toString(),
        toName = to().name?.toString(),
        routeShortName = route?.shortName,
        routeGtfsId = route?.id?.toString(),
        legGeometryPoints = legGeometry()?.let { PolylineEncoder.encodeGeometry(it).points },
        departureTime = startTime().toOffsetDateTime(),
        arrivalTime = endTime().toOffsetDateTime(),
    )
}

private fun OtpLeg.legModeName(): String = when {
    isTransitLeg -> (this as ScheduledTransitLeg).mode().name
    this is StreetLeg -> mode.name
    else -> error("Unsupported leg type for Park & Ride mapping: ${this::class.simpleName}")
}
