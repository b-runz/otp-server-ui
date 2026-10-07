package one.brj.bikebus.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import one.brj.bikebus.domain.MapsIntent
import one.brj.bikebus.model.FlagStopConnectResult
import one.brj.bikebus.model.NearbyRoute
import one.brj.bikebus.model.NearbyRoutesUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NearbyRoutesResults(
    state: NearbyRoutesUiState,
    onRouteSelected: (NearbyRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
            state.searched && state.routes.isEmpty() -> Text("No bus routes found near this destination")
            else -> Column {
                state.routes.forEach { route ->
                    val isSelected = state.selectedRoute?.routeGtfsId == route.routeGtfsId
                    NearbyRouteRow(
                        route = route,
                        isSelected = isSelected,
                        connecting = isSelected && state.connecting,
                        connectResult = if (isSelected) state.connectResult else null,
                        connectError = if (isSelected) state.connectError else null,
                        onSelected = { onRouteSelected(route) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NearbyRouteRow(
    route: NearbyRoute,
    isSelected: Boolean,
    connecting: Boolean,
    connectResult: FlagStopConnectResult?,
    connectError: String?,
    onSelected: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() }
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.DirectionsBus, contentDescription = "Bus")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${route.routeShortName ?: route.routeGtfsId} · ${formatDistance(route.distanceMeters)} from destination",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (isSelected) {
            when {
                connecting -> CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                connectError != null -> Text(
                    connectError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                connectResult != null -> ConnectResultDetails(connectResult, context)
            }
        }
    }
}

@Composable
private fun ConnectResultDetails(result: FlagStopConnectResult, context: Context) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        result.hubName?.let {
            Text(
                "Routed via $it for a more reliable transfer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        result.legs.forEach { leg ->
            LegRow(leg) {
                val intent = MapsIntent.buildIntent(leg)
                val target = if (MapsIntent.resolvable(context, intent)) intent else MapsIntent.withoutMapsPackage(intent)
                context.startActivity(target)
            }
        }
        result.flagStopInfo?.let { info ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Official stop: ${formatDistance(info.officialFinalLegDistanceMeters)} " +
                            "(${formatMinutes(info.officialFinalLegDurationSeconds)})"
                    )
                    Text(
                        "Ask driver to stop near here: ${formatDistance(info.flagStopDistanceMeters)} " +
                            "(${formatMinutes(info.flagStopDurationSeconds)})"
                    )
                    result.extraRideSeconds?.let {
                        Text("Extra ride vs your last search: ${formatMinutes(it)}")
                    }
                    val lastLeg = result.legs.last()
                    TextButton(onClick = {
                        val intent = MapsIntent.buildIntent(
                            fromLat = info.flagLat,
                            fromLon = info.flagLon,
                            toLat = lastLeg.toLat,
                            toLon = lastLeg.toLon,
                            mode = lastLeg.mode
                        )
                        val target = if (MapsIntent.resolvable(context, intent)) intent else MapsIntent.withoutMapsPackage(intent)
                        context.startActivity(target)
                    }) {
                        Text("Maps to flag point")
                    }
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()}m" else String.format(Locale.US, "%.1fkm", meters / 1000)

private fun formatMinutes(seconds: Double): String = "${(seconds / 60).roundToInt()}min"
