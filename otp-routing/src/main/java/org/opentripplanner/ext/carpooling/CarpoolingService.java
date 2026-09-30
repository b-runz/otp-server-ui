// STUB: not upstream OTP code
package org.opentripplanner.ext.carpooling;

import java.time.ZonedDateTime;
import java.util.List;
import org.opentripplanner.ext.carpooling.routing.CarpoolAccessEgress;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.transit.service.TransitServiceResolver;

/**
 * Stub: the real carpooling sandbox feature (org.opentripplanner.ext.carpooling.*) is out of
 * scope for the embedded otp-routing slice. Both methods are only reachable when
 * {@code OTPFeature.CarPooling} is on, which no wiring in this slice turns on, so an empty
 * result is always correct here.
 */
public interface CarpoolingService {
  List<Itinerary> routeDirect(RouteRequest request);

  List<CarpoolAccessEgress> routeAccessEgress(
    RouteRequest request,
    StreetRequest streetRequest,
    AccessEgressType accessOrEgress,
    TransitServiceResolver transitServiceResolver,
    ZonedDateTime transitSearchTimeZero
  );
}
