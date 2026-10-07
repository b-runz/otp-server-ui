package one.brj.bikebus.model

data class PlaceSuggestion(
    val placeId: String,
    val description: String,
    val mainText: String = description,
    val secondaryText: String? = null,
    val isStreet: Boolean = false
)
