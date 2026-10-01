package one.otpserverui.model

data class FlagStopConnectResult(
    val legs: List<Leg>,
    val flagStopInfo: FlagStopInfo?,
    val extraRideSeconds: Double?,
    val hubName: String? = null
)
