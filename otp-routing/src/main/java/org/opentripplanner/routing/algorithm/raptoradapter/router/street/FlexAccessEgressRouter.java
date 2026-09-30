// STUB: not upstream OTP code
package org.opentripplanner.routing.algorithm.raptoradapter.router.street;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.ext.flex.FlexAccessEgress;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.linking.LinkingContext;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.model.edge.ExtensionRequestContext;
import org.opentripplanner.transfer.regular.RegularTransferService;
import org.opentripplanner.transit.service.TransitService;

/**
 * Stub: replaces the upstream flex access/egress router, which depends on the excluded
 * {@code org.opentripplanner.ext.flex.FlexRouter}. Keeps the public signature
 * {@code AccessEgressFetcher} calls; always returns no flex access/egress results.
 */
public class FlexAccessEgressRouter {

  private FlexAccessEgressRouter() {}

  public static Collection<FlexAccessEgress> routeAccessEgress(
    RouteRequest request,
    TransitService transitService,
    Graph graph,
    RegularTransferService transferService,
    StreetDetailsService streetDetailsService,
    AdditionalSearchDays searchDays,
    FlexParameters config,
    Collection<ExtensionRequestContext> extensionRequestContexts,
    AccessEgressType accessOrEgress,
    LinkingContext linkingContext
  ) {
    return List.of();
  }
}
