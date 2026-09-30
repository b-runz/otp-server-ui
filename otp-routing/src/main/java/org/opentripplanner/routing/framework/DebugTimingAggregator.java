package org.opentripplanner.routing.framework;

// MONITORING REMOVED: Micrometer (Clock, Timer, DistributionSummary, Metrics, MeterRegistry) is
// not a dependency of this module. This class keeps its public API as a no-op shell: no timing
// is recorded, and finishedRendering()/getDebugOutput() report all-zero durations.
// import io.micrometer.core.instrument.Clock;
// import io.micrometer.core.instrument.DistributionSummary;
// import io.micrometer.core.instrument.MeterRegistry;
// import io.micrometer.core.instrument.Metrics;
// import io.micrometer.core.instrument.Timer;
// MONITORING REMOVED: was only used by the messages list (removed with the rest of the timer bookkeeping).
// import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.api.resource.DebugOutput;
import org.opentripplanner.api.resource.TransitTimingOutput;
import org.opentripplanner.routing.api.request.RoutingTag;
// MONITORING REMOVED: was only used by the LOG field (removed with the rest of the timer bookkeeping).
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

/**
 * Keeps account of timing information within the different parts of the routing process, and is
 * responsible for logging that information.
 * <p>
 * MONITORING REMOVED: this used to publish timers/distribution-summaries to a Micrometer
 * {@code MeterRegistry}. That registry dependency has been removed for the embedded target; all
 * methods below are no-op shells kept only so callers compile unchanged.
 */
public class DebugTimingAggregator {

  // MONITORING REMOVED:
  // private static final Logger LOG = LoggerFactory.getLogger(DebugTimingAggregator.class);
  //
  // private static final long NANOS_TO_MILLIS = 1000000;
  public static final String ROUTING_TOTAL = "routing.total";
  public static final String ROUTING_RAPTOR = "routing.raptor";

  // MONITORING REMOVED:
  // private final Clock clock;
  //
  // private final Timer directStreetRouterTimer;
  // private final Timer directFlexRouterTimer;
  // private final Timer directCarpoolRouterTimer;
  //
  // private final Timer accessTimer;
  // private final Timer egressTimer;
  // private final DistributionSummary numAccessesDistribution;
  // private final DistributionSummary numEgressesDistribution;
  //
  // private final Timer preCalculationTimer;
  // private final Timer tripPatternFilterTimer;
  // private final Timer accessEgressTimer;
  // private final Timer raptorSearchTimer;
  // private final Timer directTransitSearchTimer;
  // private final Timer itineraryCreationTimer;
  // private final Timer transitRouterTimer;
  // private final Timer filteringTimer;
  // private final Timer renderingTimer;
  // private final Timer routingTotalTimer;
  // private final Timer requestTotalTimer;
  //
  // private final Timer.Sample startedCalculating;
  // private final List<String> messages = new ArrayList<>();
  // private Timer.Sample startedDirectStreetRouter;
  // private long directStreetRouterTime;
  // private Timer.Sample startedDirectFlexRouter;
  // private long directFlexRouterTime;
  // private Timer.Sample startedDirectCarpoolRouter;
  // private long directCarpoolRouterTime;
  // private Timer.Sample finishedPatternFiltering;
  // private Timer.Sample finishedAccessEgress;
  // private Timer.Sample finishedRouters;
  // private Timer.Sample finishedFiltering;
  // private Timer.Sample startedAccessCalculating;
  // private Timer.Sample startedEgressCalculating;
  // private Timer.Sample startedDirectTransitSearch;
  // private Timer.Sample startedItineraryCreation;
  // private long accessTime;
  // private long egressTime;
  // private int numAccesses;
  // private int numEgresses;
  // private long precalculationTime;
  // private Timer.Sample startedTransitRouterTime;
  // private long tripPatternFilterTime;
  // private long accessEgressTime;
  // private long raptorSearchTime;
  // private long directTransitSearchTime;
  // private long itineraryCreationTime;
  // private long transitRouterTime;
  // private long filteringTime;
  // private long renderingTime;
  // private long requestTotalTime;

  /**
   * Record the time when we first began calculating a path for this request. Note that timings will
   * not include network and server request queue overhead, which is what we want.
   */
  public DebugTimingAggregator(
    /* MONITORING REMOVED: MeterRegistry registry, */
    Collection<RoutingTag> routingRequestTags
  ) {
    // MONITORING REMOVED:
    // var tags = MicrometerUtils.mapTimingTags(routingRequestTags);
    // clock = registry.config().clock();
    // startedCalculating = Timer.start(this.clock);
    //
    // requestTotalTimer = Timer.builder(ROUTING_TOTAL).tags(tags).register(registry);
    // routingTotalTimer = Timer.builder("routing.router").tags(tags).register(registry);
    // renderingTimer = Timer.builder("routing.rendering").tags(tags).register(registry);
    // filteringTimer = Timer.builder("routing.filtering").tags(tags).register(registry);
    // transitRouterTimer = Timer.builder("routing.transit").tags(tags).register(registry);
    // itineraryCreationTimer = Timer.builder("routing.itineraryCreation")
    //   .tags(tags)
    //   .register(registry);
    // raptorSearchTimer = Timer.builder(ROUTING_RAPTOR).tags(tags).register(registry);
    // directTransitSearchTimer = Timer.builder("routing.directTransit").tags(tags).register(registry);
    // accessEgressTimer = Timer.builder("routing.accessEgress").tags(tags).register(registry);
    // tripPatternFilterTimer = Timer.builder("routing.tripPatternFiltering")
    //   .tags(tags)
    //   .register(registry);
    // preCalculationTimer = Timer.builder("routing.preCalculation").tags(tags).register(registry);
    //
    // numEgressesDistribution = DistributionSummary.builder("routing.numEgress")
    //   .tags(tags)
    //   .register(registry);
    // numAccessesDistribution = DistributionSummary.builder("routing.numAccess")
    //   .tags(tags)
    //   .register(registry);
    //
    // egressTimer = Timer.builder("routing.egress").tags(tags).register(registry);
    // accessTimer = Timer.builder("routing.access").tags(tags).register(registry);
    // directFlexRouterTimer = Timer.builder("routing.directFlex").tags(tags).register(registry);
    // directCarpoolRouterTimer = Timer.builder("routing.directCarpool").tags(tags).register(registry);
    // directStreetRouterTimer = Timer.builder("routing.directStreet").tags(tags).register(registry);
  }

  public DebugTimingAggregator() {
    this(/* MONITORING REMOVED: Metrics.globalRegistry, */ List.of());
  }

  /**
   * Record the time when the worker initialization is done, and the direct street router starts.
   */
  public void finishedPrecalculating() {
    // MONITORING REMOVED:
    // if (startedCalculating == null) {
    //   return;
    // }
    // precalculationTime = startedCalculating.stop(preCalculationTimer);
    // log("┌  Routing initialization", precalculationTime);
  }

  /** Record the time when starting the direct street router search. */
  public void startedDirectStreetRouter() {
    // MONITORING REMOVED:
    // startedDirectStreetRouter = Timer.start(clock);
  }

  /** Record the time when we finished the direct street router search. */
  public void finishedDirectStreetRouter() {
    // MONITORING REMOVED:
    // if (startedDirectStreetRouter == null) {
    //   return;
    // }
    // directStreetRouterTime = startedDirectStreetRouter.stop(directStreetRouterTimer);
  }

  /** Record the time when starting the direct flex router search. */
  public void startedDirectFlexRouter() {
    // MONITORING REMOVED:
    // startedDirectFlexRouter = Timer.start(clock);
  }

  /** Record the time when we finished the direct flex router search. */
  public void finishedDirectFlexRouter() {
    // MONITORING REMOVED:
    // if (startedDirectFlexRouter == null) {
    //   return;
    // }
    // directFlexRouterTime = startedDirectFlexRouter.stop(directFlexRouterTimer);
  }

  /** Record the time when starting the direct carpool router search. */
  public void startedDirectCarpoolRouter() {
    // MONITORING REMOVED:
    // startedDirectCarpoolRouter = Timer.start(clock);
  }

  /** Record the time when we finished the direct carpool router search. */
  public void finishedDirectCarpoolRouter() {
    // MONITORING REMOVED:
    // if (startedDirectCarpoolRouter == null) {
    //   return;
    // }
    // directCarpoolRouterTime = startedDirectCarpoolRouter.stop(directCarpoolRouterTimer);
  }

  /** Record the time when starting the transit router search. */
  public void startedTransitRouting() {
    // MONITORING REMOVED:
    // startedTransitRouterTime = Timer.start(clock);
  }

  /**
   * Record the time when we are finished with the creation of the raptor data models.
   */
  public void finishedPatternFiltering() {
    // MONITORING REMOVED:
    // finishedPatternFiltering = Timer.start(clock);
    // if (startedTransitRouterTime == null) {
    //   return;
    // }
    // tripPatternFilterTime = startedTransitRouterTime.stop(tripPatternFilterTimer);
  }

  public void startedAccessCalculating() {
    // MONITORING REMOVED:
    // startedAccessCalculating = Timer.start(clock);
  }

  public void finishedAccessCalculating() {
    // MONITORING REMOVED:
    // if (startedAccessCalculating == null) {
    //   return;
    // }
    // accessTime = startedAccessCalculating.stop(accessTimer);
  }

  public void startedEgressCalculating() {
    // MONITORING REMOVED:
    // startedEgressCalculating = Timer.start(clock);
  }

  public void finishedEgressCalculating() {
    // MONITORING REMOVED:
    // if (startedEgressCalculating == null) {
    //   return;
    // }
    // egressTime = startedEgressCalculating.stop(egressTimer);
  }

  /**
   * Record the time when we are finished with the access and egress routing.
   */
  public void finishedAccessEgress(int numAccesses, int numEgresses) {
    // MONITORING REMOVED:
    // finishedAccessEgress = Timer.start(clock);
    // if (finishedPatternFiltering == null) {
    //   return;
    // }
    // accessEgressTime = finishedPatternFiltering.stop(accessEgressTimer);
    // this.numAccesses = numAccesses;
    // numAccessesDistribution.record(numAccesses);
    // this.numEgresses = numEgresses;
    // numEgressesDistribution.record(numEgresses);
  }

  /**
   * Record the time when we are finished with the raptor search.
   */
  public void finishedRaptorSearch() {
    // MONITORING REMOVED:
    // if (finishedAccessEgress == null) {
    //   return;
    // }
    // raptorSearchTime = finishedAccessEgress.stop(raptorSearchTimer);
  }

  public void startedDirectTransitSearch() {
    // MONITORING REMOVED:
    // startedDirectTransitSearch = Timer.start(clock);
  }

  /**
   * Record the time when we are finished with the direct transit search.
   */
  public void finishedDirectTransitSearch() {
    // MONITORING REMOVED:
    // if (startedDirectTransitSearch == null) {
    //   return;
    // }
    // directTransitSearchTime = startedDirectTransitSearch.stop(directTransitSearchTimer);
  }

  public void startedItineraryCreation() {
    // MONITORING REMOVED:
    // startedItineraryCreation = Timer.start(clock);
  }

  /**
   * Record the time when we have created internal itinerary objects from the raptor responses.
   */
  public void finishedItineraryCreation() {
    // MONITORING REMOVED:
    // if (startedItineraryCreation == null) {
    //   return;
    // }
    // itineraryCreationTime = startedItineraryCreation.stop(itineraryCreationTimer);
  }

  /** Record the time when we finished the transit router search */
  public void finishedTransitRouter() {
    // MONITORING REMOVED:
    // if (startedTransitRouterTime == null) {
    //   return;
    // }
    // transitRouterTime = startedTransitRouterTime.stop(transitRouterTimer);
  }

  public void finishedRouting() {
    // MONITORING REMOVED:
    // if (startedCalculating == null) {
    //   return;
    // }
    // long routingTotalTime = startedCalculating.stop(routingTotalTimer);
    //
    // finishedRouters = Timer.start(clock);
    // if (directStreetRouterTime > 0) {
    //   log("├  Direct street routing", directStreetRouterTime);
    // }
    // if (directFlexRouterTime > 0) {
    //   log("├  Direct flex routing", directFlexRouterTime);
    // }
    //
    // if (transitRouterTime > 0) {
    //   log("│┌ Creating raptor data model", tripPatternFilterTime);
    //   log("│├ Access routing (" + numAccesses + " accesses)", accessTime);
    //   log("│├ Egress routing (" + numEgresses + " egresses)", egressTime);
    //   log("││ Access/Egress routing", accessEgressTime);
    //   log("│├ Main routing", raptorSearchTime);
    //   if (directTransitSearchTime > 0) {
    //     log("│├ Direct transit routing", directTransitSearchTime);
    //   }
    //   log("│├ Creating itineraries", itineraryCreationTime);
    //   log("├┴ Transit routing total", transitRouterTime);
    // }
    //
    // log("│  Routing total: ", routingTotalTime);
  }

  /** Record the time when we finished filtering the paths for this request. */
  public void finishedFiltering() {
    // MONITORING REMOVED:
    // finishedFiltering = Timer.start(clock);
    // if (finishedRouters == null) {
    //   return;
    // }
    // filteringTime = finishedRouters.stop(filteringTimer);
    // log("├  Filtering itineraries", filteringTime);
  }

  /** Record the time when we finished converting the internal model to API classes */
  @SuppressWarnings("Convert2MethodRef")
  public DebugOutput finishedRendering() {
    // MONITORING REMOVED:
    // if (finishedFiltering == null || startedCalculating == null) {
    //   return null;
    // }
    // renderingTime = finishedFiltering.stop(renderingTimer);
    // requestTotalTime = startedCalculating.stop(requestTotalTimer);
    // log("├  Converting model objects", renderingTime);
    // log("┴  Request total", requestTotalTime);
    // messages.forEach(m -> LOG.debug(m));
    return getDebugOutput();
  }

  /** Summarize and calculate elapsed times. */
  public DebugOutput getDebugOutput() {
    // MONITORING REMOVED:
    // return new DebugOutput(
    //   precalculationTime,
    //   directStreetRouterTime,
    //   transitRouterTime,
    //   filteringTime,
    //   renderingTime,
    //   requestTotalTime,
    //   new TransitTimingOutput(
    //     tripPatternFilterTime,
    //     accessEgressTime,
    //     raptorSearchTime,
    //     itineraryCreationTime
    //   )
    // );
    return new DebugOutput(0, 0, 0, 0, 0, 0, new TransitTimingOutput(0, 0, 0, 0));
  }

  // MONITORING REMOVED:
  // private void log(String msg, long nanos) {
  //   messages.add(String.format("%-36s: %5s ms", msg, nanos / NANOS_TO_MILLIS));
  // }
}
