package one.brj.bikebus.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import one.brj.bikebus.TripViewModel
import one.brj.bikebus.model.SearchMode
import one.brj.bikebus.model.TimeMode

@Composable
fun TripScreen(viewModel: TripViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ModeToggle(
            options = listOf(SearchMode.BRING_BIKE to "Bring Bike", SearchMode.PARK_AND_RIDE to "Park & Ride"),
            selected = state.searchMode,
            onSelected = viewModel::setSearchMode,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeToggle(
            options = listOf(TimeMode.DEPART_AT to "Depart at", TimeMode.ARRIVE_BY to "Arrive by"),
            selected = state.timeMode,
            onSelected = viewModel::setTimeMode,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DateField(date = state.date, onDateSelected = viewModel::setDate, modifier = Modifier.width(160.dp))
            Spacer(modifier = Modifier.width(8.dp))
            TimeField(time = state.time, onTimeSelected = viewModel::setTime, modifier = Modifier.width(120.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            AddressField(
                label = "From", query = state.fromQuery, suggestions = state.fromSuggestions,
                favorites = state.favorites, recents = state.recents,
                onQueryChanged = viewModel::onFromQueryChanged, onSuggestionSelected = viewModel::selectFromSuggestion,
                onSavedPlaceSelected = { viewModel.selectSavedPlace(it, isFrom = true) },
                onToggleFavorite = viewModel::toggleFavorite, onRemoveRecent = viewModel::removeRecent
            )
            HorizontalDivider()
            AddressField(
                label = "To", query = state.toQuery, suggestions = state.toSuggestions,
                favorites = state.favorites, recents = state.recents,
                onQueryChanged = viewModel::onToQueryChanged, onSuggestionSelected = viewModel::selectToSuggestion,
                onSavedPlaceSelected = { viewModel.selectSavedPlace(it, isFrom = false) },
                onToggleFavorite = viewModel::toggleFavorite, onRemoveRecent = viewModel::removeRecent,
                trailingContent = {
                    IconButton(onClick = viewModel::swapFromTo) {
                        Icon(imageVector = Icons.Filled.SwapVert, contentDescription = "Swap From and To")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Prefer transit hubs", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterVertically))
            Switch(checked = state.preferHubs, onCheckedChange = viewModel::setPreferHubs)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Max connections", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::decrementMaxTransfers) {
                    Icon(imageVector = Icons.Filled.Remove, contentDescription = "Fewer connections")
                }
                Text(
                    text = state.maxTransfers?.toString() ?: "Unlimited",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(72.dp), textAlign = TextAlign.Center
                )
                IconButton(onClick = viewModel::incrementMaxTransfers) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "More connections")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = viewModel::search,
            enabled = state.fromPlace != null && state.toPlace != null && !state.isLoading && !state.nearbyRoutesState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Search") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::findNearbyRoutes,
            enabled = state.fromPlace != null && state.toPlace != null && !state.isLoading && !state.nearbyRoutesState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Drop me off") }
        Spacer(modifier = Modifier.height(16.dp))
        when {
            state.showingNearbyRoutes -> NearbyRoutesResults(state = state.nearbyRoutesState, onRouteSelected = viewModel::selectNearbyRoute)
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            state.error != null -> Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            state.searched && state.itineraries.isEmpty() -> Text("No routes found")
            else -> Column {
                state.notice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 8.dp))
                }
                ResultsList(itineraries = state.itineraries)
            }
        }
    }
}
