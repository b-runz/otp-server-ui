// STUB: not upstream OTP code
package org.opentripplanner.ext.sorlandsbanen;

import javax.annotation.Nullable;
import org.opentripplanner.raptor.extensions.extrasearch.ExtraMcRouterSearch;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgresses;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.api.request.RouteRequest;

/**
 * Stub: the real "Sorlandsbanen" dual-search decorator (org.opentripplanner.ext.sorlandsbanen.*)
 * is out of scope for the embedded otp-routing slice. {@code null} is always a valid return here
 * — upstream itself returns {@code null} ("no extra search needed") in several ordinary cases —
 * so this matches "the extra search never applies" rather than fabricating a result.
 */
public class SorlandsbanenNorwayService {

  @Nullable
  public ExtraMcRouterSearch<TripSchedule> createExtraMcRouterSearch(
    RouteRequest request,
    AccessEgresses accessEgresses,
    RaptorTransitData raptorTransitData
  ) {
    return null;
  }
}
