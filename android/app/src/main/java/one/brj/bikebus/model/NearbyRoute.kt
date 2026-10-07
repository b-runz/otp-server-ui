package one.brj.bikebus.model

data class NearbyRoute(
    val routeGtfsId: String,
    val routeShortName: String?,
    val stopIds: List<String>,
    val distanceMeters: Double
)
