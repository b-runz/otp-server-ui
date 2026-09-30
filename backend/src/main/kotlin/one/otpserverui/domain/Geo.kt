package one.otpserverui.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lon2 - lon1)
    val a = sin(dPhi / 2).pow(2) + cos(p1) * cos(p2) * sin(dLambda / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

data class ClosestPoint(val lat: Double, val lon: Double, val distanceMeters: Double)

// Per-segment projection onto a polyline using a local flat-earth approximation
// (accurate at this scale -- Denmark spans under 3 degrees of latitude), rather
// than just picking the nearest vertex: real GTFS shape points average ~66m
// apart on rural routes, so vertex-only matching would be noticeably coarser
// than the road itself. Validated against a live OTP instance and real GTFS
// shape data during design (see the design spec) before being ported here.
fun closestPointOnPolyline(points: List<Pair<Double, Double>>, targetLat: Double, targetLon: Double): ClosestPoint? {
    if (points.isEmpty()) return null
    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * cos(Math.toRadians(targetLat))

    fun toXY(point: Pair<Double, Double>): Pair<Double, Double> {
        val (lat, lon) = point
        return (lon - targetLon) * mPerDegLon to (lat - targetLat) * mPerDegLat
    }

    var best: ClosestPoint? = null
    for (i in 0 until points.size - 1) {
        val (ax, ay) = toXY(points[i])
        val (bx, by) = toXY(points[i + 1])
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        var t = if (lenSq == 0.0) 0.0 else (-ax * dx - ay * dy) / lenSq
        t = t.coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        val distanceMeters = sqrt(px * px + py * py)
        if (best == null || distanceMeters < best.distanceMeters) {
            val lat = points[i].first + t * (points[i + 1].first - points[i].first)
            val lon = points[i].second + t * (points[i + 1].second - points[i].second)
            best = ClosestPoint(lat, lon, distanceMeters)
        }
    }
    return best
}
