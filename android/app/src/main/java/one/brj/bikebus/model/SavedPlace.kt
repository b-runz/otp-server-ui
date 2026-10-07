package one.brj.bikebus.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedPlace(val placeId: String, val label: String, val lat: Double, val lon: Double, val rank: Int = 0)
