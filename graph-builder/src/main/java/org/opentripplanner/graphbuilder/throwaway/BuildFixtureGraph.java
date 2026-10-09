package org.opentripplanner.graphbuilder.throwaway;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.opentripplanner.core.model.time.LocalDateRange;
import org.opentripplanner.datastore.api.FileType;
import org.opentripplanner.datastore.file.FileDataSource;
import org.opentripplanner.datastore.file.ZipFileDataSource;
import org.opentripplanner.ext.stopconsolidation.NoopStopConsolidationRepository;
import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.module.StreetLinkerModule;
import org.opentripplanner.graph_builder.module.TurnRestrictionModule;
import org.opentripplanner.graph_builder.module.cache.GraphBuildCacheManager;
import org.opentripplanner.graph_builder.module.geometry.CalculateWorldEnvelopeModule;
import org.opentripplanner.graph_builder.module.osm.OsmModule;
import org.opentripplanner.graph_builder.module.stopconnectivity.StopConnectivityModule;
import org.opentripplanner.graph_builder.module.transfer.DirectTransferGenerator;
import org.opentripplanner.graph_builder.module.transfer.api.RegularTransferParameters;
import org.opentripplanner.graph_builder.module.transfer.api.TransferParametersForMode;
import org.opentripplanner.gtfs.config.GtfsFeedParameters;
import org.opentripplanner.gtfs.graphbuilder.GtfsBundle;
import org.opentripplanner.gtfs.graphbuilder.GtfsModule;
import org.opentripplanner.osm.DefaultOsmProvider;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.fares.NoopFareServiceFactory;
import org.opentripplanner.routing.graph.SerializedGraphObject;
import org.opentripplanner.service.osminfo.internal.DefaultOsmInfoGraphBuildRepository;
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsRepository;
import org.opentripplanner.service.vehicleparking.internal.DefaultVehicleParkingRepository;
import org.opentripplanner.service.vehiclerental.GeofencingZoneService;
import org.opentripplanner.service.worldenvelope.internal.DefaultWorldEnvelopeRepository;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.street.internal.DefaultStreetRepository;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.linking.VertexLinker;
import org.opentripplanner.street.linking.VisibilityMode;
import org.opentripplanner.street.model.StreetConstants;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.regular.internal.TransferIndex;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.site.StopTransferPriority;
import org.opentripplanner.transit.service.TransitRepository;

/**
 * Throwaway driver (Task 4, otp-server-ui backend-json-api plan) that builds a small, real,
 * version-277-compatible serialized graph (Aarhus-area OSM + GTFS extracts) using the vendored
 * graph-building slice in this standalone project. See docs/graph-build.md (in the real
 * otp-server-ui repo, not this throwaway one) for the full write-up of what this needed and why.
 *
 * <p>Usage: {@code ./gradlew run --args="<osm.pbf> <gtfs.zip> <output graph.obj>"}
 */
public class BuildFixtureGraph {

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println(
        "Usage: BuildFixtureGraph <osm.pbf path> <gtfs.zip path> <output graph.obj path>"
      );
      System.exit(1);
    }
    File osmFile = new File(args[0]);
    File gtfsFile = new File(args[1]);
    File outputFile = new File(args[2]);

    Graph graph = new Graph();
    Deduplicator deduplicator = new Deduplicator();
    TransitRepository transitRepository = new TransitRepository();
    transitRepository.initTimeZone(ZoneId.of("Europe/Copenhagen"));
    DataImportIssueStore issueStore = DataImportIssueStore.NOOP;

    var streetDetailsRepository = new DefaultStreetDetailsRepository();
    var streetRepository = new DefaultStreetRepository();
    var vehicleParkingRepository = new DefaultVehicleParkingRepository();
    var osmInfoGraphBuildRepository = new DefaultOsmInfoGraphBuildRepository();
    var transferRepository = new DefaultTransferRepository(new TransferIndex());
    var worldEnvelopeRepository = new DefaultWorldEnvelopeRepository();

    var graphBuilder = new GraphBuilder(
      graph,
      deduplicator,
      transitRepository,
      issueStore,
      () -> {}, // no data sources to close -- this driver reads plain local files directly
      GraphBuildCacheManager.NOOP
    );
    graphBuilder.setHasTransitData(true);

    // --- OSM / street graph ---
    var osmProvider = new DefaultOsmProvider(osmFile, false);
    var osmModule = OsmModule.of(
      List.of(osmProvider),
      graph,
      osmInfoGraphBuildRepository,
      streetDetailsRepository,
      streetRepository,
      vehicleParkingRepository
    ).build();
    graphBuilder.addModule(osmModule);

    // --- GTFS / transit data ---
    var gtfsDataSource = new ZipFileDataSource(gtfsFile, FileType.GTFS);
    var gtfsFeedParameters = new GtfsFeedParameters(
      null,
      gtfsFile.toURI(),
      StopTransferPriority.defaultValue(),
      false,
      true,
      200
    );
    var gtfsBundle = new GtfsBundle(gtfsDataSource, gtfsFeedParameters);
    var gtfsModule = new GtfsModule(
      List.of(gtfsBundle),
      transitRepository,
      streetDetailsRepository,
      graph,
      deduplicator,
      issueStore,
      LocalDateRange.ofUnbounded(),
      new NoopFareServiceFactory(),
      150.0,
      120
    );
    graphBuilder.addModule(gtfsModule);

    // --- Street/transit linking + cleanup ---
    var linker = new VertexLinker(
      graph,
      GeofencingZoneService.EMPTY,
      VisibilityMode.TRAVERSE_AREA_EDGES,
      StreetConstants.DEFAULT_MAX_AREA_NODES,
      false
    );
    graphBuilder.addModule(
      new StreetLinkerModule(
        graph,
        linker,
        vehicleParkingRepository,
        transitRepository,
        issueStore
      )
    );
    graphBuilder.addModule(new TurnRestrictionModule(graph, osmInfoGraphBuildRepository));
    graphBuilder.addModule(new StopConnectivityModule(graph, issueStore));

    // --- Stop-to-stop transfers (WALK + BIKE) ---
    // Use buildDefault() (not buildRequest()) so these stay "default requests" with no
    // from/to location set -- buildRequest() always clears the defaultRequest flag, which
    // would then fail RouteRequest's from/to validation since these profiles never set one.
    // This mirrors upstream's own RouteRequestConfig.mapRouteRequest, which builds transfer
    // request profiles from RouteRequest.defaultValue() via requestBuilder.buildDefault().
    var walkTransferRequest = RouteRequest.of()
      .withJourney(journey -> journey.withTransfer(new StreetRequest(StreetMode.WALK)))
      .buildDefault();
    var bikeTransferRequest = RouteRequest.of()
      .withJourney(journey -> journey.withTransfer(new StreetRequest(StreetMode.BIKE)))
      .buildDefault();

    var transferParameters = new RegularTransferParameters(
      Duration.ofMinutes(10),
      Map.of(StreetMode.BIKE, new TransferParametersForMode(Duration.ofMinutes(20), null, null, false)),
      List.of(walkTransferRequest, bikeTransferRequest)
    );
    graphBuilder.addModule(
      new DirectTransferGenerator(
        graph,
        transitRepository,
        transferRepository,
        issueStore,
        transferParameters
      )
    );

    graphBuilder.addModule(
      new CalculateWorldEnvelopeModule(graph, transitRepository, worldEnvelopeRepository)
    );

    graphBuilder.run();

    // NOTE: osmInfoGraphBuildRepository IS threaded through here (unlike the other @Nullable
    // repositories below, left null) because Task 4 vendored a real DefaultOsmInfoGraphBuildRepository
    // into otp-routing itself (not just this throwaway project) -- see docs/graph-build.md -- so
    // it genuinely exists on otp-routing's own test classpath and can be deserialized later.
    var serializedGraphObject = new SerializedGraphObject(
      graph,
      osmInfoGraphBuildRepository,
      streetDetailsRepository,
      streetRepository,
      transitRepository,
      transferRepository,
      worldEnvelopeRepository,
      vehicleParkingRepository,
      new BuildConfig(),
      new RouterConfig(),
      graphBuilder.issueSummary(),
      null,
      null,
      new NoopStopConsolidationRepository(),
      new NoopFareServiceFactory()
    );

    outputFile.getParentFile().mkdirs();
    serializedGraphObject.save(new FileDataSource(outputFile, FileType.GRAPH));
    System.out.println("Wrote fixture graph to " + outputFile.getAbsolutePath());
  }
}
