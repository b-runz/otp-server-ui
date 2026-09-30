package one.otpserverui

import java.nio.file.Path
import org.opentripplanner.routing.graph.SerializedGraphObject
import org.opentripplanner.street.graph.Graph
import org.opentripplanner.transfer.regular.TransferRepository
import org.opentripplanner.transit.service.TransitRepository

data class LoadedGraph(
    val graph: Graph,
    val transitRepository: TransitRepository,
    val transferRepository: TransferRepository,
)

object GraphLoader {
    fun load(path: Path): LoadedGraph {
        // SerializedGraphObject's only public load entry points are load(File) and
        // load(DataSource); the InputStream+description overload is private, so we use the
        // real public File-based API here (see SerializedGraphObject.java).
        val serialized = checkNotNull(SerializedGraphObject.load(path.toFile())) {
            "Failed to load graph from $path"
        }
        // Graph.streetIndex is declared transient (see Graph.java): SerializedGraphObject.load
        // deserializes the transit side's own indices but leaves the street spatial index null,
        // so any query that resolves a coordinate to a street vertex (VertexLinker.link ->
        // Graph.requireIndex()) throws "Graph must be indexed before querying" until this runs -
        // mirrors bikebus's own EmbeddedGraphLoader.loadFromCache, which re-indexes for the same
        // reason after its own deserialize.
        serialized.graph.index()
        return LoadedGraph(
            graph = serialized.graph,
            transitRepository = serialized.transitRepository,
            transferRepository = serialized.transferRepository,
        )
    }
}
