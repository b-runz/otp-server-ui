package org.opentripplanner.graph_builder;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import javax.annotation.Nullable;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.framework.application.OtpAppException;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueSummary;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.graph_builder.module.cache.GraphBuildCacheManager;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.utils.lang.OtpNumberFormat;
import org.opentripplanner.utils.time.DurationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// NOTE (throwaway graph-builder, Task 4): the real upstream `create(...)` factory method (which
// wired modules via a Dagger-generated `GraphBuilderFactory`/`GraphBuilderModules` config layer)
// has been deleted from this copy -- this project's own driver
// (BuildFixtureGraph.java) constructs the needed GraphBuilderModule instances directly and adds
// them via the (now-public) addModule(...) below. `addModule` was made public (was private) for
// the same reason. See docs/graph-build.md.

/**
 * This makes a Graph out of various inputs like GTFS and OSM. It is modular: GraphBuilderModules
 * are placed in a list and run in sequence.
 */
public class GraphBuilder implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(GraphBuilder.class);

  private final Queue<GraphBuilderModule> graphBuilderModules = new LinkedList<>();
  private final Graph graph;
  private final TransitRepository transitRepository;
  private final DataImportIssueStore issueStore;
  private final Closeable closeDataSourcesHandle;
  private final DeduplicatorService deduplicator;
  private final GraphBuildCacheManager cacheManager;

  private boolean hasTransitData = false;

  public GraphBuilder(
    Graph baseGraph,
    DeduplicatorService deduplicator,
    TransitRepository transitRepository,
    DataImportIssueStore issueStore,
    Closeable closeDataSourcesHandle,
    GraphBuildCacheManager cacheManager
  ) {
    this.graph = baseGraph;
    this.deduplicator = deduplicator;
    this.transitRepository = transitRepository;
    this.issueStore = issueStore;
    this.closeDataSourcesHandle = closeDataSourcesHandle;
    this.cacheManager = cacheManager;
  }

  /**
   * Marks whether this build includes transit data (mirrors upstream's
   * {@code GraphBuilderDataSources#hasTransitData()}, set by {@code create(...)} there); this
   * throwaway driver sets it directly since it always builds transit data.
   */
  public void setHasTransitData(boolean hasTransitData) {
    this.hasTransitData = hasTransitData;
  }

  public void run() {
    try {
      // Record how long it takes to build the graph, purely for informational purposes.
      long startTime = System.currentTimeMillis();

      // Check all graph builder inputs, and fail fast to avoid waiting until the build process
      // advances.
      for (GraphBuilderModule builder : graphBuilderModules) {
        builder.checkInputs();
      }

      // because we want to garbage-collect the modules as soon as they are finished
      // we remove them from the queue during the build process
      while (!graphBuilderModules.isEmpty()) {
        var builder = graphBuilderModules.poll();
        builder.buildGraph();
      }

      new DataImportIssueSummary(issueStore.listIssues()).logSummary();

      // Log before we validate, this way we have more information if the validation fails
      logGraphBuilderCompleteStatus(startTime, graph, transitRepository, deduplicator);

      validate();
    } finally {
      try {
        cacheManager.close();
      } finally {
        closeDataSources();
      }
    }
  }

  public void addModule(GraphBuilderModule module) {
    graphBuilderModules.add(Objects.requireNonNull(module));
  }

  private boolean hasTransitData() {
    return hasTransitData;
  }

  public DataImportIssueSummary issueSummary() {
    return new DataImportIssueSummary(issueStore.listIssues());
  }

  /**
   * Validates the build. Currently, only checks if the graph has transit data if any transit data
   * sets were included in the build. If all transit data gets filtered out due to transit period
   * configuration, for example, then this function will throw a {@link OtpAppException}.
   */
  private void validate() {
    if (hasTransitData() && !transitRepository.hasTransit()) {
      throw new OtpAppException(
        "The provided transit data have no trips within the configured transit service period. " +
          "There is something wrong with your data - see the log above. Another possibility is that the " +
          "'transitServiceStart' and 'transitServiceEnd' are not correctly configured."
      );
    }
  }

  private void closeDataSources() {
    try {
      closeDataSourcesHandle.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void logGraphBuilderCompleteStatus(
    long startTime,
    Graph graph,
    TransitRepository transitRepository,
    DeduplicatorService deduplicator
  ) {
    long endTime = System.currentTimeMillis();
    String time = DurationUtils.durationToStr(Duration.ofMillis(endTime - startTime));
    var f = new OtpNumberFormat();
    var nStops = f.formatNumber(transitRepository.getSiteRepository().stopIndexSize());
    var nPatterns = f.formatNumber(transitRepository.getAllTripPatterns().size());
    var nTransfers = f.formatNumber(
      transitRepository.getConstrainedTransferService().listAll().size()
    );
    var nVertices = f.formatNumber(graph.countVertices());
    var nEdges = f.formatNumber(graph.countEdges());

    LOG.info("Graph building took {}.", time);
    LOG.info("Graph built.   |V|={} |E|={}", nVertices, nEdges);
    LOG.info(
      "Transit built. |Stops|={} |Patterns|={} |ConstrainedTransfers|={}",
      nStops,
      nPatterns,
      nTransfers
    );
    // Log size info for the deduplicator
    LOG.info("Memory optimized {}", deduplicator.toString());
  }
}
