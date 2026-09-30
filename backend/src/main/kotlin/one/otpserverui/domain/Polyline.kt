package one.otpserverui.domain

// Decodes Google's encoded polyline algorithm (precision 5) -- the format OTP
// returns for Leg.legGeometry.points and Pattern.patternGeometry.points.
// Validated against real OTP responses during design: decoded point counts
// matched the reported `length` field exactly for every leg/pattern tested.
fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    var index = 0
    var lat = 0
    var lon = 0
    val points = mutableListOf<Pair<Double, Double>>()

    while (index < encoded.length) {
        var result = 1
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63 - 1
            result += b shl shift
            shift += 5
        } while (b >= 0x1f)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        result = 1
        shift = 0
        do {
            b = encoded[index++].code - 63 - 1
            result += b shl shift
            shift += 5
        } while (b >= 0x1f)
        lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        points.add(lat * 1e-5 to lon * 1e-5)
    }
    return points
}
