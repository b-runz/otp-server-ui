package one.brj.bikebus.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.brj.bikebus.model.SavedPlace

interface PlacesPersistence {
    fun loadFavorites(): List<SavedPlace>
    fun saveFavorites(places: List<SavedPlace>)
    fun loadRecents(): List<SavedPlace>
    fun saveRecents(places: List<SavedPlace>)
}

class SavedPlacesStore(context: Context) : PlacesPersistence {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadFavorites(): List<SavedPlace> = load(KEY_FAVORITES)
    override fun saveFavorites(places: List<SavedPlace>) = save(KEY_FAVORITES, places)

    override fun loadRecents(): List<SavedPlace> = load(KEY_RECENTS)
    override fun saveRecents(places: List<SavedPlace>) = save(KEY_RECENTS, places)

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
