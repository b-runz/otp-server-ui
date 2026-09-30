package one.otpserverui.routing

import java.time.Duration

/**
 * The app's routing defaults, mirroring `config/router-config.json`. See the spec's "The facade:
 * `routing` module" section: `numItineraries` 5, `transferPenalty` 900, bike and bike-to-park
 * access duration 2000 s.
 */
object RoutingDefaults {
    const val NUM_ITINERARIES: Int = 5
    const val TRANSFER_PENALTY_SECONDS: Int = 900
    val BIKE_ACCESS_MAX: Duration = Duration.ofSeconds(2000)
    val BIKE_TO_PARK_ACCESS_MAX: Duration = Duration.ofSeconds(2000)
}
