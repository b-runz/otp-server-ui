package org.opentripplanner.gtfs.graphbuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.core.framework.deduplicator.DeduplicatorService;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.time.LocalDateRange;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.graph_builder.module.AddTransitEntitiesToGraph;
import org.opentripplanner.graph_builder.module.AddTransitEntitiesToTimetable;
import org.opentripplanner.graph_builder.module.TransitWithFutureDateValidator;
import org.opentripplanner.graph_builder.module.ValidateAndInterpolateStopTimesForEachTrip;
import org.opentripplanner.graph_builder.module.geometry.GeometryProcessor;
import org.opentripplanner.gtfs.GenerateTripPatternsOperation;
import org.opentripplanner.gtfs.interlining.InterlineProcessor;
import org.opentripplanner.gtfs.mapping.GTFSToTransitDataImportMapper;
import org.opentripplanner.model.TransitDataImport;
import org.opentripplanner.model.TripStopTimes;
import org.opentripplanner.model.calendar.CalendarServiceData;
import org.opentripplanner.model.impl.TransitDataImportBuilder;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.service.streetdetails.StreetDetailsRepository;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.TransitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GtfsModule implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsModule.class);
  /**
   * @see BuildConfig#transitServiceStart
   * @see BuildConfig#transitServiceEnd
   */
  private final LocalDateRange transitPeriodLimit;
  private final List<GtfsBundle> gtfsBundles;
  private final FareServiceFactory fareServiceFactory;

  private final TransitRepository transitRepository;
  private final StreetDetailsRepository streetDetailsRepository;
  private final Graph graph;
  private final DataImportIssueStore issueStore;
  private final DeduplicatorService deduplicator;

  private final double maxStopToShapeSnapDistance;
  private final int subwayAccessTime_s;

  public GtfsModule(
    List<GtfsBundle> bundles,
    TransitRepository transitRepository,
    StreetDetailsRepository streetDetailsRepository,
    Graph graph,
    DeduplicatorService deduplicator,
    DataImportIssueStore issueStore,
    LocalDateRange transitPeriodLimit,
    FareServiceFactory fareServiceFactory,
    double maxStopToShapeSnapDistance,
    int subwayAccessTime_s
  ) {
    this.gtfsBundles = bundles;
    this.transitRepository = transitRepository;
    this.streetDetailsRepository = streetDetailsRepository;
    this.graph = graph;
    this.deduplicator = deduplicator;
    this.issueStore = issueStore;
    this.transitPeriodLimit = transitPeriodLimit;
    this.fareServiceFactory = fareServiceFactory;
    this.maxStopToShapeSnapDistance = maxStopToShapeSnapDistance;
    this.subwayAccessTime_s = subwayAccessTime_s;
  }

  @Override
  public void buildGraph() {
    CalendarServiceData calendarServiceData = new CalendarServiceData();

    Map<String, GtfsBundle> feedIdsEncountered = new HashMap<>();

    try {
      for (GtfsBundle gtfsBundle : gtfsBundles) {
        var gtfsDao = gtfsBundle.loadDao();

        var feedId = gtfsBundle.getFeedId();
        verifyUniqueFeedId(gtfsBundle, feedIdsEncountered, feedId);

        feedIdsEncountered.put(feedId, gtfsBundle);

        GTFSToTransitDataImportMapper mapper = new GTFSToTransitDataImportMapper(
          new TransitDataImportBuilder(transitRepository.getSiteRepository(), issueStore),
          feedId,
          issueStore,
          gtfsBundle.parameters().discardMinTransferTimes(),
          gtfsBundle.parameters().stationTransferPreference()
        );
        mapper.mapStopTripAndRouteDataIntoBuilder(gtfsDao);

        TransitDataImportBuilder builder = mapper.getBuilder();

        builder.limitServiceDays(transitPeriodLimit);

        calendarServiceData.add(builder.buildCalendarServiceData());

        // NOTE (throwaway graph-builder, Task 4): fare-calendar bookkeeping and flex-trip mapping
        // are intentionally skipped -- both live in the excluded org.opentripplanner.ext.*
        // sandbox packages. See docs/graph-build.md.

        validateAndInterpolateStopTimesForEachTrip(builder.getStopTimesSortedByTrip(), issueStore);

        // We need to run this after the cleaning of the data, as stop indices might have changed
        mapper.mapAndAddTransfersToBuilder(gtfsDao);

        GeometryProcessor geometryProcessor = new GeometryProcessor(
          builder,
          maxStopToShapeSnapDistance,
          issueStore
        );

        // NB! The calls below have side effects - the builder state is updated!
        createTripPatterns(
          deduplicator,
          transitRepository,
          builder,
          calendarServiceData.getServiceIds(),
          geometryProcessor,
          issueStore
        );

        TransitDataImport dataImport = builder.build();

        addTransitRepositoryToGraph(graph, transitRepository, streetDetailsRepository, dataImport);

        if (gtfsBundle.parameters().blockBasedInterlining()) {
          new InterlineProcessor(
            transitRepository.getConstrainedTransferService(),
            builder.getStaySeatedNotAllowed(),
            gtfsBundle.parameters().maxInterlineDistance(),
            issueStore,
            calendarServiceData
          ).run(dataImport.getTripPatterns());
        }

        // NOTE (throwaway graph-builder, Task 4): fareServiceFactory is held only to keep the
        // real constructor shape; processGtfs(...) is not called (fares are out of scope here).
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    transitRepository.updateCalendarServiceData(calendarServiceData);
    TransitWithFutureDateValidator.validate(
      calendarServiceData,
      issueStore,
      transitRepository.getTimeZone()
    );
  }

  /**
   * Verifies that a feed id is not assigned twice.
   * <p>
   * Duplicates can happen in the following cases:
   *  - the feed id is configured twice in build-config.json
   *  - two GTFS feeds have the same feed_info.feed_id
   *  - a GTFS feed defines a feed_info.feed_id like '3' that collides with an auto-generated one
   * <p>
   * Debugging these cases is very confusing, so we prevent it from happening.
   */
  private static void verifyUniqueFeedId(
    GtfsBundle gtfsBundle,
    Map<String, GtfsBundle> feedIdsEncountered,
    String feedId
  ) {
    if (feedIdsEncountered.containsKey(feedId)) {
      LOG.error(
        "Feed id '{}' has been used for {} but it was already assigned to {}.",
        feedId,
        gtfsBundle,
        feedIdsEncountered.get(feedId)
      );
      throw new IllegalArgumentException("Duplicate feed id: '%s'".formatted(feedId));
    }
  }

  @Override
  public void checkInputs() {
    for (GtfsBundle bundle : gtfsBundles) {
      bundle.checkInputs();
    }
  }

  /* Private Methods */

  /**
   * This method has side effects, the {@code stopTimesByTrip} is updated.
   */
  private void validateAndInterpolateStopTimesForEachTrip(
    TripStopTimes stopTimesByTrip,
    DataImportIssueStore issueStore
  ) {
    new ValidateAndInterpolateStopTimesForEachTrip(stopTimesByTrip, true, issueStore).run();
  }

  /**
   * This method has side effects, the {@code builder} is updated with new TripPatterns.
   */
  private void createTripPatterns(
    DeduplicatorService deduplicator,
    TransitRepository transitRepository,
    TransitDataImportBuilder builder,
    Set<FeedScopedId> calServiceIds,
    GeometryProcessor geometryProcessor,
    DataImportIssueStore issueStore
  ) {
    GenerateTripPatternsOperation buildTPOp = new GenerateTripPatternsOperation(
      builder,
      issueStore,
      deduplicator,
      calServiceIds,
      geometryProcessor
    );
    buildTPOp.run();
    transitRepository.setHasFrequencyService(
      transitRepository.hasFrequencyService() || buildTPOp.hasFrequencyBasedTrips()
    );
    transitRepository.setHasScheduledService(
      transitRepository.hasScheduledService() || buildTPOp.hasScheduledTrips()
    );
  }

  private void addTransitRepositoryToGraph(
    Graph graph,
    TransitRepository transitRepository,
    StreetDetailsRepository streetDetailsRepository,
    TransitDataImport dataImport
  ) {
    AddTransitEntitiesToTimetable.addToTimetable(dataImport, transitRepository);
    AddTransitEntitiesToGraph.addToGraph(
      dataImport,
      subwayAccessTime_s,
      graph,
      streetDetailsRepository
    );
  }
}
