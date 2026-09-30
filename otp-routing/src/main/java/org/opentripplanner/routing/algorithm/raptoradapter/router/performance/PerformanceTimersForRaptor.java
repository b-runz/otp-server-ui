package org.opentripplanner.routing.algorithm.raptoradapter.router.performance;

// MONITORING REMOVED: Micrometer (MeterRegistry, Timer) is not a dependency of this module. This
// class keeps its RaptorTimers shell so callers compile unchanged, but no longer times anything
// — each method just runs the given body.
// import io.micrometer.core.instrument.MeterRegistry;
// import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import org.opentripplanner.raptor.api.debug.RaptorTimers;
import org.opentripplanner.routing.api.request.RoutingTag;
// MONITORING REMOVED:
// import org.opentripplanner.routing.framework.MicrometerUtils;

public class PerformanceTimersForRaptor implements RaptorTimers {

  // MONITORING REMOVED:
  // // Variables to track time spent
  // private final Timer timerRoute;
  // private final Timer routeTransitTimer;
  // private final Timer applyTransfersTimer;
  // private final MeterRegistry registry;
  private final Collection<RoutingTag> routingTags;

  public PerformanceTimersForRaptor(
    String namePrefix,
    Collection<RoutingTag> routingTags
    /* MONITORING REMOVED: , MeterRegistry registry */
  ) {
    // MONITORING REMOVED:
    // this.registry = registry;
    this.routingTags = routingTags;
    // MONITORING REMOVED:
    // var tags = MicrometerUtils.mapTimingTags(routingTags);
    // timerRoute = Timer.builder("raptor." + namePrefix + ".route")
    //   .tags(tags)
    //   .register(registry);
    // routeTransitTimer = Timer.builder("raptor." + namePrefix + ".minute.transit")
    //   .tags(tags)
    //   .register(registry);
    // applyTransfersTimer = Timer.builder("raptor." + namePrefix + ".minute.transfers")
    //   .tags(tags)
    //   .register(registry);
  }

  @Override
  public void route(Runnable body) {
    // MONITORING REMOVED:
    // timerRoute.record(body);
    body.run();
  }

  @Override
  public void routeTransit(Runnable body) {
    // MONITORING REMOVED:
    // routeTransitTimer.record(body);
    body.run();
  }

  @Override
  public void applyTransfers(Runnable body) {
    // MONITORING REMOVED:
    // applyTransfersTimer.record(body);
    body.run();
  }

  @Override
  public RaptorTimers withNamePrefix(String namePrefix) {
    // MONITORING REMOVED:
    // return new PerformanceTimersForRaptor(namePrefix, routingTags, registry);
    return new PerformanceTimersForRaptor(namePrefix, routingTags);
  }
}
