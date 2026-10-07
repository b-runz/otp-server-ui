package one.brj.bikebus

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.brj.bikebus.data.PlacesPersistence
import one.brj.bikebus.data.SavedPlacesStore
import one.brj.bikebus.domain.LocationProvider
import one.brj.bikebus.model.NearbyRoute
import one.brj.bikebus.model.NearbyRoutesUiState
import one.brj.bikebus.model.PlaceSuggestion
import one.brj.bikebus.model.ResolvedPlace
import one.brj.bikebus.model.SavedPlace
import one.brj.bikebus.model.SearchMode
import one.brj.bikebus.model.TimeMode
import one.brj.bikebus.model.TripUiState
import one.brj.bikebus.network.AutocompleteRequest
import one.brj.bikebus.network.Circle
import one.brj.bikebus.network.ConnectRequestDto
import one.brj.bikebus.network.ConnectOutcome
import one.brj.bikebus.network.LatLngDto
import one.brj.bikebus.network.LocationBias
import one.brj.bikebus.network.NetworkModule
import one.brj.bikebus.network.OtpServerApi
import one.brj.bikebus.network.SearchRequestDto
import one.brj.bikebus.network.SearchResult
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class TripViewModel(
    application: Application,
    private val otpServerApiOverride: OtpServerApi? = null,
    private val placesPersistenceOverride: PlacesPersistence? = null,
) : AndroidViewModel(application) {

    private val placesApi by lazy { NetworkModule.placesApi }
    private val otpServerApi by lazy { otpServerApiOverride ?: NetworkModule.otpServerApi }
    private val deviceLocation by lazy { LocationProvider.lastKnownCoarseLocation(getApplication<Application>()) }
    private val savedPlacesStore: PlacesPersistence by lazy { placesPersistenceOverride ?: SavedPlacesStore(getApplication<Application>()) }

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState

    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null
    private var connectJob: Job? = null

    init {
        _uiState.update { it.copy(favorites = savedPlacesStore.loadFavorites(), recents = savedPlacesStore.loadRecents()) }
    }

    fun setSearchMode(mode: SearchMode) = _uiState.update {
        it.copy(
            searchMode = mode, itineraries = emptyList(), error = null, searched = false, notice = null,
            showingNearbyRoutes = false, nearbyRoutesState = NearbyRoutesUiState()
        )
    }
    fun setTimeMode(mode: TimeMode) = _uiState.update {
        it.copy(timeMode = mode, itineraries = emptyList(), error = null, searched = false, notice = null)
    }
    fun setDate(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun setTime(time: LocalTime) = _uiState.update { it.copy(time = time) }
    fun setPreferHubs(enabled: Boolean) = _uiState.update {
        it.copy(preferHubs = enabled, itineraries = emptyList(), error = null, searched = false, notice = null)
    }
    fun incrementMaxTransfers() = _uiState.update {
        it.copy(maxTransfers = (it.maxTransfers ?: -1) + 1, itineraries = emptyList(), error = null, searched = false, notice = null)
    }
    fun decrementMaxTransfers() = _uiState.update {
        val current = it.maxTransfers
        it.copy(
            maxTransfers = if (current == null || current <= 0) null else current - 1,
            itineraries = emptyList(), error = null, searched = false, notice = null
        )
    }

    fun swapFromTo() = _uiState.update {
        it.copy(
            fromQuery = it.toQuery, toQuery = it.fromQuery, fromPlace = it.toPlace, toPlace = it.fromPlace,
            fromSuggestions = emptyList(), toSuggestions = emptyList(),
            itineraries = emptyList(), error = null, searched = false, notice = null
        )
    }

    fun toggleFavorite(placeId: String, label: String, lat: Double?, lon: Double?) {
        if (_uiState.value.favorites.any { it.placeId == placeId }) {
            val updatedFavorites = _uiState.value.favorites.filterNot { it.placeId == placeId }
            _uiState.update { it.copy(favorites = updatedFavorites) }
            savedPlacesStore.saveFavorites(updatedFavorites)
            return
        }
        viewModelScope.launch {
            val existingRecent = _uiState.value.recents.find { it.placeId == placeId }
            val savedPlace = existingRecent ?: run {
                if (lat != null && lon != null) {
                    SavedPlace(placeId, label, lat, lon)
                } else {
                    val details = runCatching {
                        placesApi.placeDetails(placeId = placeId, apiKey = BuildConfig.GOOGLE_PLACES_API_KEY)
                    }.getOrNull() ?: return@launch
                    SavedPlace(placeId, label, details.location.latitude, details.location.longitude)
                }
            }
            if (_uiState.value.favorites.any { it.placeId == placeId }) return@launch
            val updatedFavorites = listOf(savedPlace) + _uiState.value.favorites
            val updatedRecents = _uiState.value.recents.filterNot { it.placeId == placeId }
            _uiState.update { it.copy(favorites = updatedFavorites, recents = updatedRecents) }
            savedPlacesStore.saveFavorites(updatedFavorites)
            savedPlacesStore.saveRecents(updatedRecents)
        }
    }

    fun removeRecent(placeId: String) {
        val updatedRecents = _uiState.value.recents.filterNot { it.placeId == placeId }
        _uiState.update { it.copy(recents = updatedRecents) }
        savedPlacesStore.saveRecents(updatedRecents)
    }

    fun selectSavedPlace(savedPlace: SavedPlace, isFrom: Boolean) {
        val resolved = ResolvedPlace(label = savedPlace.label, lat = savedPlace.lat, lon = savedPlace.lon)
        rememberRecent(savedPlace.placeId, resolved)
        if (isFrom) {
            _uiState.update {
                it.copy(fromQuery = savedPlace.label, fromPlace = resolved, fromSuggestions = emptyList(), itineraries = emptyList(), error = null, searched = false, notice = null)
            }
        } else {
            _uiState.update {
                it.copy(toQuery = savedPlace.label, toPlace = resolved, toSuggestions = emptyList(), itineraries = emptyList(), error = null, searched = false, notice = null)
            }
        }
    }

    fun findNearbyRoutes() {
        val destination = _uiState.value.toPlace ?: return
        _uiState.update { it.copy(showingNearbyRoutes = true, nearbyRoutesState = NearbyRoutesUiState(isLoading = true)) }
        viewModelScope.launch {
            val result = runCatching { otpServerApi.nearbyRoutes(lat = destination.lat, lon = destination.lon) }
            result.exceptionOrNull()?.let { Log.e("TripViewModel", "findNearbyRoutes() failed", it) }
            _uiState.update {
                it.copy(
                    nearbyRoutesState = it.nearbyRoutesState.copy(
                        isLoading = false, searched = true,
                        routes = result.getOrDefault(emptyList()),
                        error = if (result.isFailure) "Couldn't reach the routing server" else null
                    )
                )
            }
        }
    }

    fun selectNearbyRoute(route: NearbyRoute) {
        val state = _uiState.value
        val origin = state.fromPlace ?: return
        val destination = state.toPlace ?: return
        connectJob?.cancel()
        _uiState.update {
            it.copy(nearbyRoutesState = it.nearbyRoutesState.copy(selectedRoute = route, connecting = true, connectResult = null, connectError = null))
        }
        connectJob = viewModelScope.launch {
            val dateTimeIso = buildIsoDateTime(state.date, state.time)
            val outcome = runCatching {
                otpServerApi.connect(
                    ConnectRequestDto(
                        originLat = origin.lat, originLon = origin.lon,
                        destinationLat = destination.lat, destinationLon = destination.lon,
                        routeGtfsId = route.routeGtfsId, routeStopIds = route.stopIds,
                        timeMode = state.timeMode.wireValue(), dateTimeIso = dateTimeIso,
                        preferHubs = state.preferHubs, maxTransfers = state.maxTransfers,
                    )
                )
            }
            result@ run {
                val errorMessage = when {
                    outcome.isFailure -> {
                        outcome.exceptionOrNull()?.let { Log.e("TripViewModel", "selectNearbyRoute() failed", it) }
                        "Couldn't reach the routing server"
                    }
                    outcome.getOrNull() is ConnectOutcome.Error -> "Couldn't find a way to reach this route from your origin"
                    else -> null
                }
                val success = (outcome.getOrNull() as? ConnectOutcome.Success)?.result
                _uiState.update {
                    if (it.nearbyRoutesState.selectedRoute?.routeGtfsId != route.routeGtfsId) return@update it
                    it.copy(nearbyRoutesState = it.nearbyRoutesState.copy(connecting = false, connectResult = success, connectError = errorMessage))
                }
            }
        }
    }

    fun onFromQueryChanged(query: String) {
        _uiState.update { it.copy(fromQuery = query, fromPlace = null, itineraries = emptyList(), error = null, searched = false, notice = null) }
        fromSearchJob?.cancel()
        if (query.length < 3) {
            _uiState.update { it.copy(fromSuggestions = emptyList()) }
            return
        }
        fromSearchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(fromSuggestions = fetchSuggestions(query)) }
        }
    }

    fun onToQueryChanged(query: String) {
        _uiState.update {
            it.copy(toQuery = query, toPlace = null, itineraries = emptyList(), error = null, searched = false, notice = null, showingNearbyRoutes = false, nearbyRoutesState = NearbyRoutesUiState())
        }
        toSearchJob?.cancel()
        if (query.length < 3) {
            _uiState.update { it.copy(toSuggestions = emptyList()) }
            return
        }
        toSearchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(toSuggestions = fetchSuggestions(query)) }
        }
    }

    fun selectFromSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update { it.copy(fromQuery = suggestion.description, fromSuggestions = emptyList()) }
        viewModelScope.launch {
            val place = resolvePlace(suggestion)
            _uiState.update { it.copy(fromPlace = place) }
            if (place != null) rememberRecent(suggestion.placeId, place)
        }
    }

    fun selectToSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update { it.copy(toQuery = suggestion.description, toSuggestions = emptyList(), showingNearbyRoutes = false, nearbyRoutesState = NearbyRoutesUiState()) }
        viewModelScope.launch {
            val place = resolvePlace(suggestion)
            _uiState.update { it.copy(toPlace = place) }
            if (place != null) rememberRecent(suggestion.placeId, place)
        }
    }

    fun search() {
        val state = _uiState.value
        val from = state.fromPlace
        val to = state.toPlace
        if (from == null || to == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, notice = null, searched = true, showingNearbyRoutes = false) }
            val dateTimeIso = buildIsoDateTime(state.date, state.time)
            val outcome = runCatching {
                otpServerApi.search(
                    SearchRequestDto(
                        mode = state.searchMode.wireValue(), timeMode = state.timeMode.wireValue(),
                        originLat = from.lat, originLon = from.lon,
                        destinationLat = to.lat, destinationLon = to.lon,
                        dateTimeIso = dateTimeIso, preferHubs = state.preferHubs, maxTransfers = state.maxTransfers,
                    )
                )
            }
            if (outcome.isFailure) {
                outcome.exceptionOrNull()?.let { Log.e("TripViewModel", "search() failed", it) }
                _uiState.update { it.copy(isLoading = false, itineraries = emptyList(), error = "Couldn't reach the routing server") }
                return@launch
            }
            when (val result = outcome.getOrThrow()) {
                is SearchResult.Success -> {
                    val requestedDateTime = OffsetDateTime.parse(dateTimeIso)
                    val notice = result.notice ?: closestOptionNotice(state.timeMode, requestedDateTime, result.itineraries)
                    _uiState.update { it.copy(isLoading = false, itineraries = result.itineraries, notice = notice) }
                }
                is SearchResult.Error -> {
                    val message = when (result.code) {
                        "no_coverage" -> "No route exists between these points"
                        else -> "Couldn't reach the routing server"
                    }
                    _uiState.update { it.copy(isLoading = false, itineraries = emptyList(), error = message) }
                }
            }
        }
    }

    private fun closestOptionNotice(timeMode: TimeMode, requested: OffsetDateTime, itineraries: List<one.brj.bikebus.model.Itinerary>): String? {
        val closest = itineraries.firstOrNull() ?: return null
        val actual = when (timeMode) {
            TimeMode.DEPART_AT -> closest.departureTime
            TimeMode.ARRIVE_BY -> closest.arrivalTime
        }
        val diffMinutes = abs(Duration.between(requested, actual).toMinutes())
        if (diffMinutes <= 20) return null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val actualFormatted = actual.format(formatter)
        return when (timeMode) {
            TimeMode.DEPART_AT -> "No routes at your requested time — showing the closest option, departing $actualFormatted"
            TimeMode.ARRIVE_BY -> "No routes at your requested time — showing the closest option, arriving $actualFormatted"
        }
    }

    private suspend fun fetchSuggestions(query: String): List<PlaceSuggestion> = try {
        val bias = deviceLocation?.let { (lat, lon) -> LocationBias(circle = Circle(center = LatLngDto(lat, lon), radius = 50_000.0)) }
        val response = placesApi.autocomplete(apiKey = BuildConfig.GOOGLE_PLACES_API_KEY, request = AutocompleteRequest(input = query, locationBias = bias))
        response.suggestions.mapNotNull { it.placePrediction }.map { prediction ->
            PlaceSuggestion(
                placeId = prediction.placeId, description = prediction.text.text,
                mainText = prediction.structuredFormat?.mainText?.text ?: prediction.text.text,
                secondaryText = prediction.structuredFormat?.secondaryText?.text,
                isStreet = prediction.types.contains("route"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun resolvePlace(suggestion: PlaceSuggestion): ResolvedPlace? = try {
        val details = placesApi.placeDetails(placeId = suggestion.placeId, apiKey = BuildConfig.GOOGLE_PLACES_API_KEY)
        ResolvedPlace(label = suggestion.description, lat = details.location.latitude, lon = details.location.longitude)
    } catch (e: Exception) {
        null
    }

    private fun rememberRecent(placeId: String, place: ResolvedPlace) {
        if (_uiState.value.favorites.any { it.placeId == placeId }) return
        val savedPlace = SavedPlace(placeId, place.label, place.lat, place.lon)
        val updatedRecents = (listOf(savedPlace) + _uiState.value.recents.filterNot { it.placeId == placeId }).take(10)
        _uiState.update { it.copy(recents = updatedRecents) }
        savedPlacesStore.saveRecents(updatedRecents)
    }

    private fun buildIsoDateTime(date: LocalDate, time: LocalTime): String =
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

private fun SearchMode.wireValue(): String = when (this) {
    SearchMode.BRING_BIKE -> "bring_bike"
    SearchMode.PARK_AND_RIDE -> "park_and_ride"
}

private fun TimeMode.wireValue(): String = when (this) {
    TimeMode.DEPART_AT -> "depart_at"
    TimeMode.ARRIVE_BY -> "arrive_by"
}
