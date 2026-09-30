// STUB: not upstream OTP code
package org.opentripplanner.ext.carpooling.internal;

import org.opentripplanner.ext.carpooling.routing.CarpoolAccessEgress;
import org.opentripplanner.model.plan.Itinerary;

/**
 * Stub: the real carpool-to-itinerary mapper (org.opentripplanner.ext.carpooling.*) is out of
 * scope for the embedded otp-routing slice. {@code RaptorPathToItineraryMapper} only reaches
 * {@link #toItinerary(CarpoolAccessEgress)} when a path leg is a {@code CarpoolAccessEgress},
 * which the {@code CarpoolingService} stub never produces, so this is unreachable in practice.
 */
public class CarpoolItineraryMapper {

  public CarpoolItineraryMapper() {}

  public Itinerary toItinerary(CarpoolAccessEgress accessEgress) {
    throw new UnsupportedOperationException("stub");
  }
}
