package one.brj.bikebus.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import one.brj.bikebus.model.Leg

object MapsIntent {

    private val TRANSIT_MODES = setOf(
        "BUS", "RAIL", "TRAM", "SUBWAY", "FERRY", "COACH", "TRANSIT",
        "TROLLEYBUS", "MONORAIL", "GONDOLA", "CABLE_CAR", "FUNICULAR", "AIRPLANE"
    )

    fun travelModeFor(mode: String): String = when (mode) {
        "BICYCLE" -> "bicycling"
        "WALK" -> "walking"
        "CAR" -> "driving"
        else -> if (mode in TRANSIT_MODES) "transit" else "walking"
    }

    fun buildIntent(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: String,
        departureEpochSecond: Long? = null
    ): Intent {
        val travelMode = travelModeFor(mode)
        var url = "https://www.google.com/maps/dir/?api=1" +
            "&origin=$fromLat,$fromLon" +
            "&destination=$toLat,$toLon" +
            "&travelmode=$travelMode"
        // departure_time is only meaningful for transit directions; Maps computes
        // bicycling/walking/driving directions live and ignores it otherwise.
        if (travelMode == "transit" && departureEpochSecond != null) {
            url += "&departure_time=$departureEpochSecond"
        }
        val uri = Uri.parse(url)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    fun buildIntent(leg: Leg): Intent = buildIntent(
        fromLat = leg.fromLat,
        fromLon = leg.fromLon,
        toLat = leg.toLat,
        toLon = leg.toLon,
        mode = leg.mode,
        departureEpochSecond = leg.departureTime.toEpochSecond()
    )

    fun resolvable(context: Context, intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null

    fun withoutMapsPackage(intent: Intent): Intent = Intent(intent).apply { setPackage(null) }
}
