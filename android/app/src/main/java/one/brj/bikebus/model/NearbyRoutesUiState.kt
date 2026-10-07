package one.brj.bikebus.model

data class NearbyRoutesUiState(
    val isLoading: Boolean = false,
    val searched: Boolean = false,
    val routes: List<NearbyRoute> = emptyList(),
    val error: String? = null,
    val selectedRoute: NearbyRoute? = null,
    val connecting: Boolean = false,
    val connectResult: FlagStopConnectResult? = null,
    val connectError: String? = null
)
