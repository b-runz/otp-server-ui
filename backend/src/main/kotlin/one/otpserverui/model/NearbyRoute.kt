package one.otpserverui.model

/**
 * Copied verbatim (package-renamed) from bikebus's own
 * `one.brj.bikebus.model.NearbyRoute` -- a plain data class, no
 * Android/network dependency.
 */
data class NearbyRoute(
    val routeGtfsId: String,
    val routeShortName: String?,
    val stopIds: List<String>,
    val distanceMeters: Double
)
