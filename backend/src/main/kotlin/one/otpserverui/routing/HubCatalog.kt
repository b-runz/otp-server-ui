package one.otpserverui.routing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import one.otpserverui.model.TransitHub

/**
 * Bundled catalog of Danish transit interchange hubs (`resources/hubs.json`, copied verbatim from
 * bikebus's own bundled `transit_hubs.json` asset, generated offline by that project's
 * `scripts/tag_transit_hubs.py` from the same GTFS feed the OTP graph is built from). `stopIds`
 * are already feed-qualified (e.g. "1:000739000101") to match OTP's own gtfsId format.
 *
 * Adapted from bikebus's own `HubCatalog.load(assets: AssetManager)`: this project has no Android
 * asset system, so the same JSON-parsing/[TransitHub]-construction logic here reads the bundled
 * catalog as a plain classpath resource instead of an `AssetManager`-opened asset.
 */
object HubCatalog {
    private const val RESOURCE_NAME = "hubs.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<TransitHub>? = null

    fun load(): List<TransitHub> {
        cached?.let { return it }
        val text = checkNotNull(HubCatalog::class.java.classLoader.getResourceAsStream(RESOURCE_NAME)) {
            "Missing classpath resource: $RESOURCE_NAME"
        }.bufferedReader().use { it.readText() }
        val hubs = json.decodeFromString<HubsFileDto>(text).hubs.map {
            TransitHub(name = it.name, lat = it.lat, lon = it.lon, stopIds = it.stopIds)
        }
        cached = hubs
        return hubs
    }

    @Serializable
    private data class HubsFileDto(val hubs: List<HubDto>)

    @Serializable
    private data class HubDto(val name: String, val lat: Double, val lon: Double, val stopIds: List<String>)
}
