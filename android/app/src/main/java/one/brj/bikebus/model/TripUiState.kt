package one.brj.bikebus.model

import java.time.LocalDate
import java.time.LocalTime

data class TripUiState(
    val searchMode: SearchMode = SearchMode.PARK_AND_RIDE,
    val timeMode: TimeMode = TimeMode.DEPART_AT,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromSuggestions: List<PlaceSuggestion> = emptyList(),
    val toSuggestions: List<PlaceSuggestion> = emptyList(),
    val fromPlace: ResolvedPlace? = null,
    val toPlace: ResolvedPlace? = null,
    val isLoading: Boolean = false,
    val itineraries: List<Itinerary> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
    val notice: String? = null,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null,
    val favorites: List<SavedPlace> = emptyList(),
    val recents: List<SavedPlace> = emptyList(),
    val showingNearbyRoutes: Boolean = false,
    val nearbyRoutesState: NearbyRoutesUiState = NearbyRoutesUiState()
)
