package one.brj.bikebus.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import one.brj.bikebus.domain.MapsIntent
import one.brj.bikebus.model.Itinerary
import one.brj.bikebus.model.Leg
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ResultsList(itineraries: List<Itinerary>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Column(modifier = modifier) {
        itineraries.forEach { itinerary ->
            ItineraryCard(itinerary, timeFormatter) { leg -> openInMaps(context, leg) }
        }
    }
}

@Composable
private fun ItineraryCard(itinerary: Itinerary, timeFormatter: DateTimeFormatter, onOpenLeg: (Leg) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "${itinerary.departureTime.format(timeFormatter)} → ${itinerary.arrivalTime.format(timeFormatter)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        formatDuration(itinerary.totalDurationSeconds),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            if (itinerary.exceedsBikeLimit) {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                BikeLimitWarning(itinerary.totalBikeDistanceMeters)
            }
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                itinerary.legs.forEachIndexed { index, leg ->
                    LegRow(leg) { onOpenLeg(leg) }
                    if (index != itinerary.legs.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BikeLimitWarning(totalBikeDistanceMeters: Double) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Over 10km bike limit (${(totalBikeDistanceMeters / 1000).roundToInt()}km)",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
internal fun LegRow(leg: Leg, onOpen: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(imageVector = iconForMode(leg.mode), contentDescription = leg.mode)
        },
        headlineContent = {
            Text("${leg.fromName ?: "?"} → ${leg.toName ?: "?"}")
        },
        supportingContent = {
            val routeLabel = leg.routeShortName?.let { formatRouteLabel(leg.mode, it) }
            // A leg under 100m (e.g. a short transfer walk) rounds to "0km · 0m",
            // which reads as broken rather than short -- omit it instead.
            val distanceDuration = if (leg.distanceMeters < 100) null
                else "${(leg.distanceMeters / 1000).roundToInt()}km · ${formatDuration(leg.durationSeconds)}"
            val text = listOfNotNull(routeLabel, distanceDuration).joinToString(" · ")
            if (text.isNotEmpty()) Text(text)
        },
        trailingContent = {
            TextButton(onClick = onOpen) { Text("Maps") }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun iconForMode(mode: String): ImageVector = when (mode) {
    "BICYCLE" -> Icons.Filled.DirectionsBike
    "WALK" -> Icons.Filled.DirectionsWalk
    "BUS", "COACH", "TROLLEYBUS" -> Icons.Filled.DirectionsBus
    "RAIL" -> Icons.Filled.Train
    "TRAM" -> Icons.Filled.Tram
    "SUBWAY" -> Icons.Filled.Subway
    "FERRY" -> Icons.Filled.DirectionsBoat
    "CAR" -> Icons.Filled.DirectionsCar
    "AIRPLANE" -> Icons.Filled.Flight
    else -> Icons.Filled.Commute
}

private fun formatRouteLabel(mode: String, shortName: String): String = when (mode) {
    "BUS", "COACH", "TROLLEYBUS" -> "Bus $shortName"
    "RAIL" -> "Train $shortName"
    "TRAM" -> "Tram $shortName"
    "SUBWAY" -> "Metro $shortName"
    "FERRY" -> "Ferry $shortName"
    else -> shortName
}

private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun openInMaps(context: Context, leg: Leg) {
    val intent = MapsIntent.buildIntent(leg)
    val target = if (MapsIntent.resolvable(context, intent)) intent else MapsIntent.withoutMapsPackage(intent)
    context.startActivity(target)
}
