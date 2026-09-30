package one.otpserverui

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import one.otpserverui.api.GeocodeClient
import one.otpserverui.api.GooglePlacesGeocodeClient
import one.otpserverui.api.dropMeOffRoutes
import one.otpserverui.api.geocodeRoute
import one.otpserverui.api.searchRoute
import one.otpserverui.model.TransitHub
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine

fun loadGraphOrFail(path: Path): LoadedGraph {
    check(Files.exists(path)) { "Graph file not found: $path" }
    return GraphLoader.load(path)
}

fun main() {
    val graphPath = Path.of(
        System.getenv("GRAPH_FILE_PATH") ?: error("GRAPH_FILE_PATH environment variable is required"),
    )
    val loaded = loadGraphOrFail(graphPath)
    val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
    val hubs = HubCatalog.load()
    val apiKey = System.getenv("GOOGLE_PLACES_API_KEY") ?: error("GOOGLE_PLACES_API_KEY environment variable is required")

    // The client used by GooglePlacesGeocodeClient MUST have ContentNegotiation installed with a
    // JSON converter, or every `.body<T>()` call inside it fails at runtime with no compile-time or
    // test signal (see GooglePlacesGeocodeClient's own KDoc in GeocodeRoute.kt). CIO is named
    // explicitly here rather than relying on HttpClient()'s default-engine resolution.
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
    }
    val geocodeClient = GooglePlacesGeocodeClient(httpClient, apiKey)

    embeddedServer(Netty, port = 8080, module = { module(engine, hubs, geocodeClient) }).start(wait = true)
}

fun Application.module(engine: RoutingEngine, hubs: List<TransitHub>, geocodeClient: GeocodeClient) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }
        searchRoute(engine, hubs)
        dropMeOffRoutes(engine)
        geocodeRoute(geocodeClient)
    }
}
