package one.otpserverui.domain

import one.otpserverui.model.Leg

// OTP sometimes returns a spurious near-zero leg -- a few meters of walking to/from a stop
// that's effectively at the origin/destination, or a near-zero hop at a transfer point --
// that isn't a real part of the trip. Drop any leg under this distance, regardless of its
// mode or position (start, middle, or end), unless doing so would empty the itinerary
// entirely (a genuine short trip). Shared between the GraphQL mapper (ItineraryMapper.kt)
// and the embedded-engine mapper (OtpItineraryMapper.kt) so both paths apply the same rule.
private const val MIN_LEG_METERS = 150.0

fun dropTinyLegs(legs: List<Leg>, minMeters: Double = MIN_LEG_METERS): List<Leg> =
    legs.filterNot { it.distanceMeters < minMeters }.ifEmpty { legs }
