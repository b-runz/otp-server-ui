// STUB: not upstream OTP code
package org.opentripplanner.ext.ridehailing;

import java.time.Instant;
import java.util.List;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress;
import org.opentripplanner.routing.api.request.RouteRequest;

/**
 * Stub: the real ride-hailing access/egress time-shifting (org.opentripplanner.ext.ridehailing.*)
 * is out of scope for the embedded otp-routing slice. Returns the results unshifted, matching "no
 * ride-hailing services configured".
 */
public class RideHailingAccessShifter {

  public static List<RoutingAccessEgress> shiftAccesses(
    boolean isAccess,
    List<RoutingAccessEgress> results,
    List<RideHailingService> services,
    RouteRequest request,
    Instant now
  ) {
    return results;
  }
}
