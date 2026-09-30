// STUB: not upstream OTP code
package org.opentripplanner.ext.ridehailing;

import java.util.List;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.framework.spi.ItineraryListFilter;

/**
 * Stub: the real ride-hailing itinerary decorator (org.opentripplanner.ext.ridehailing.*) is out
 * of scope for the embedded otp-routing slice. Returns itineraries unchanged, matching "no
 * ride-hailing services configured".
 */
public class DecorateWithRideHailing implements ItineraryListFilter {

  public DecorateWithRideHailing(List<RideHailingService> rideHailingServices, boolean wheelchair) {}

  @Override
  public List<Itinerary> filter(List<Itinerary> itineraries) {
    return itineraries;
  }
}
