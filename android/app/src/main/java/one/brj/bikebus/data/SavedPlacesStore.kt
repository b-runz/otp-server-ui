package one.brj.bikebus.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.brj.bikebus.model.SavedPlace

class SavedPlacesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadFavorites(): List<SavedPlace> = load(KEY_FAVORITES)
    fun saveFavorites(places: List<SavedPlace>) = save(KEY_FAVORITES, places)

    fun loadRecents(): List<SavedPlace> = load(KEY_RECENTS)
    fun saveRecents(places: List<SavedPlace>) = save(KEY_RECENTS, places)

    private fun load(key: String): List<SavedPlace> = runCatching {
        prefs.getString(key, null)?.let { json.decodeFromString<List<SavedPlace>>(it) }
    }.getOrNull() ?: emptyList()

    private fun save(key: String, places: List<SavedPlace>) {
        prefs.edit().putString(key, json.encodeToString(places)).apply()
    }

    companion object {
        private const val PREFS_NAME = "saved_places"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
    }
}
