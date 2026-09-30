// STUB: not upstream OTP code
package org.opentripplanner.ext.stopconsolidation;

import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.framework.spi.ItineraryDecorator;

/**
 * Stub: the real stop-consolidation itinerary decorator
 * (org.opentripplanner.ext.stopconsolidation.*) is out of scope for the embedded otp-routing
 * slice. Returns itineraries unchanged, matching "no consolidated stop names configured".
 */
public class DecorateConsolidatedStopNames implements ItineraryDecorator {

  public DecorateConsolidatedStopNames(StopConsolidationService service) {}

  @Override
  public Itinerary decorate(Itinerary itinerary) {
    return itinerary;
  }
}
