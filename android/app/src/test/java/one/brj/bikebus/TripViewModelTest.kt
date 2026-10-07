package one.brj.bikebus

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import one.brj.bikebus.data.PlacesPersistence
import one.brj.bikebus.model.NearbyRoute
import one.brj.bikebus.model.SavedPlace
import one.brj.bikebus.model.SearchMode
import one.brj.bikebus.network.OtpServerApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakePlacesPersistence : PlacesPersistence {
    private var favorites = emptyList<SavedPlace>()
    private var recents = emptyList<SavedPlace>()
    override fun loadFavorites() = favorites
    override fun saveFavorites(places: List<SavedPlace>) { favorites = places }
    override fun loadRecents() = recents
    override fun saveRecents(places: List<SavedPlace>) { recents = places }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: TripViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        viewModel = TripViewModel(
            application = Application(),
            otpServerApiOverride = OtpServerApi(OkHttpClient(), server.url("/").toString().removeSuffix("/"), "test-token", dispatcher),
            placesPersistenceOverride = FakePlacesPersistence(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `search does nothing when origin or destination is missing`() = runTest {
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.searched)
    }

    @Test
    fun `decrementMaxTransfers at zero goes to unlimited, not negative`() {
        viewModel.incrementMaxTransfers() // null -> 0
        assertEquals(0, viewModel.uiState.value.maxTransfers)
        viewModel.decrementMaxTransfers() // 0 -> null (unlimited), not -1
        assertNull(viewModel.uiState.value.maxTransfers)
    }

    @Test
    fun `setSearchMode resets itineraries and search state`() {
        viewModel.setSearchMode(SearchMode.BRING_BIKE)
        assertEquals(SearchMode.BRING_BIKE, viewModel.uiState.value.searchMode)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.itineraries)
        assertEquals(false, viewModel.uiState.value.searched)
    }

    @Test
    fun `search maps a no_coverage error to a specific message`() = runTest {
        viewModel.selectSavedPlace(SavedPlace("p1", "Origin", 55.0, 12.0), isFrom = true)
        viewModel.selectSavedPlace(SavedPlace("p2", "Destination", 56.0, 13.0), isFrom = false)
        server.enqueue(MockResponse().setBody("""{"error":"no_coverage"}""").setResponseCode(422))
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("No route exists between these points", viewModel.uiState.value.error)
    }

    @Test
    fun `search maps a network failure to the generic connectivity message`() = runTest {
        // A fresh viewModel pointed at a non-routable address, so this test never touches
        // the shared server/viewModel (and never needs to shut the server down mid-test).
        val unreachableViewModel = TripViewModel(
            application = Application(),
            otpServerApiOverride = OtpServerApi(OkHttpClient(), "http://127.0.0.1:1", "test-token", dispatcher),
            placesPersistenceOverride = FakePlacesPersistence(),
        )
        unreachableViewModel.selectSavedPlace(SavedPlace("p1", "Origin", 55.0, 12.0), isFrom = true)
        unreachableViewModel.selectSavedPlace(SavedPlace("p2", "Destination", 56.0, 13.0), isFrom = false)
        unreachableViewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Couldn't reach the routing server", unreachableViewModel.uiState.value.error)
    }

    @Test
    fun `selectNearbyRoute maps a connect error to the no-route-found message`() = runTest {
        viewModel.selectSavedPlace(SavedPlace("p1", "Origin", 55.0, 12.0), isFrom = true)
        viewModel.selectSavedPlace(SavedPlace("p2", "Destination", 56.0, 13.0), isFrom = false)
        server.enqueue(MockResponse().setBody("""{"error":"unreachable"}""").setResponseCode(422))
        viewModel.selectNearbyRoute(NearbyRoute(routeGtfsId = "RUT:1", routeShortName = "1A", stopIds = listOf("S1"), distanceMeters = 10.0))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Couldn't find a way to reach this route from your origin", viewModel.uiState.value.nearbyRoutesState.connectError)
    }
}
