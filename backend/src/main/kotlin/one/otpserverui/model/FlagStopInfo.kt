package one.otpserverui.model

data class FlagStopInfo(
    val flagLat: Double,
    val flagLon: Double,
    val officialFinalLegDistanceMeters: Double,
    val officialFinalLegDurationSeconds: Double,
    val flagStopDistanceMeters: Double,
    val flagStopDurationSeconds: Double
)
