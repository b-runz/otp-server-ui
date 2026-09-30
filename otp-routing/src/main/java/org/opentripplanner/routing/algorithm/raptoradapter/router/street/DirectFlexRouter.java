// STUB: not upstream OTP code
package org.opentripplanner.routing.algorithm.raptoradapter.router.street;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.ext.dataoverlay.configuration.DataOverlayParameterBindings;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transfer.regular.RegularTransferService;
import org.opentripplanner.transit.service.TransitService;

/**
 * Stub: replaces the upstream direct-flex router, which depends on the excluded
 * {@code org.opentripplanner.ext.flex.FlexRouter}. Keeps the public signature
 * {@code RoutingWorker} calls; always returns no direct-flex itineraries.
 */
public class DirectFlexRouter {

  public static List<Itinerary> route(
    Graph graph,
    TransitService transitService,
    RegularTransferService transferService,
    StreetDetailsService streetDetailsService,
    FlexParameters flexParameters,
    @Nullable DataOverlayParameterBindings dataOverlayParameterBindings,
    RouteRequest request,
    AdditionalSearchDays additionalSearchDays,
    LinkingContext linkingContext
  ) {
    return List.of();
  }
}
