package one.brj.bikebus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import one.brj.bikebus.model.PlaceSuggestion
import one.brj.bikebus.model.SavedPlace

@Composable
fun AddressField(
    label: String,
    query: String,
    suggestions: List<PlaceSuggestion>,
    favorites: List<SavedPlace>,
    recents: List<SavedPlace>,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onSavedPlaceSelected: (SavedPlace) -> Unit,
    onToggleFavorite: (placeId: String, label: String, lat: Double?, lon: Double?) -> Unit,
    onRemoveRecent: (placeId: String) -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var favoritesPickerOpen by remember { mutableStateOf(false) }
    var favoritesFilter by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length))) }

    LaunchedEffect(query) {
        if (textFieldValue.text != query) {
            textFieldValue = TextFieldValue(text = query, selection = TextRange(query.length))
        }
    }

    fun fillWithHouseNumberGap(suggestion: PlaceSuggestion) {
        val filled = if (!suggestion.secondaryText.isNullOrEmpty()) {
            "${suggestion.mainText} , ${suggestion.secondaryText}"
        } else {
            "${suggestion.mainText} "
        }
        textFieldValue = TextFieldValue(text = filled, selection = TextRange(suggestion.mainText.length + 1))
        onQueryChanged(filled)
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            IconButton(onClick = {
                favoritesPickerOpen = !favoritesPickerOpen
                favoritesFilter = ""
            }) {
                Icon(
                    imageVector = if (favoritesPickerOpen) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favoritesPickerOpen) "Close favorites" else "Show favorites"
                )
            }
            if (favoritesPickerOpen) {
                OutlinedTextField(
                    value = favoritesFilter,
                    onValueChange = { favoritesFilter = it },
                    label = { Text("$label favorites") },
                    singleLine = true,
                    trailingIcon = { ClearButton(visible = favoritesFilter.isNotEmpty()) { favoritesFilter = "" } },
                    colors = borderlessTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onQueryChanged(it.text)
                    },
                    label = { Text(label) },
                    singleLine = true,
                    trailingIcon = { ClearButton(visible = query.isNotEmpty()) { onQueryChanged("") } },
                    colors = borderlessTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            trailingContent?.invoke()
        }
        when {
            favoritesPickerOpen -> {
                favorites
                    .filter { it.label.contains(favoritesFilter, ignoreCase = true) }
                    .sortedByDescending { it.rank }
                    .forEach { place ->
                        SuggestionRow(
                            label = place.label,
                            isFavorite = true,
                            showRemove = false,
                            addHouseNumberAction = null,
                            onClick = {
                                onSavedPlaceSelected(place)
                                favoritesPickerOpen = false
                                favoritesFilter = ""
                            },
                            onToggleFavorite = { onToggleFavorite(place.placeId, place.label, place.lat, place.lon) },
                            onRemove = {}
                        )
                    }
            }
            query.isEmpty() -> {
                recents.forEach { place ->
                    SuggestionRow(
                        label = place.label,
                        isFavorite = favorites.any { it.placeId == place.placeId },
                        showRemove = true,
                        addHouseNumberAction = null,
                        onClick = { onSavedPlaceSelected(place) },
                        onToggleFavorite = { onToggleFavorite(place.placeId, place.label, place.lat, place.lon) },
                        onRemove = { onRemoveRecent(place.placeId) }
                    )
                }
            }
            else -> {
                suggestions.forEach { suggestion ->
                    SuggestionRow(
                        label = suggestion.description,
                        isFavorite = favorites.any { it.placeId == suggestion.placeId },
                        showRemove = false,
                        addHouseNumberAction = if (suggestion.isStreet) {
                            { fillWithHouseNumberGap(suggestion) }
                        } else {
                            null
                        },
                        onClick = { onSuggestionSelected(suggestion) },
                        onToggleFavorite = { onToggleFavorite(suggestion.placeId, suggestion.description, null, null) },
                        onRemove = {}
                    )
                }
            }
        }
    }
}

// Individual field borders are suppressed here so the two stacked AddressField rows
// (From/To) read as one continuous input box in TripScreen, which draws the single
// shared outer border and the divider between them.
@Composable
private fun borderlessTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

@Composable
private fun ClearButton(visible: Boolean, onClick: () -> Unit) {
    if (visible) {
        IconButton(onClick = onClick) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear")
        }
    }
}

@Composable
private fun SuggestionRow(
    label: String,
    isFavorite: Boolean,
    showRemove: Boolean,
    addHouseNumberAction: (() -> Unit)?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Text(text = label)
            if (addHouseNumberAction != null) {
                Text(
                    text = "Add house number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = addHouseNumberAction)
                        .padding(vertical = 8.dp)
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite"
            )
        }
        if (showRemove) {
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }
}
