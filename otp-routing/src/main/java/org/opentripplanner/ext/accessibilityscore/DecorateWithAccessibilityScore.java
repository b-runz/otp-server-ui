// STUB: not upstream OTP code
package org.opentripplanner.ext.accessibilityscore;

import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.framework.spi.ItineraryDecorator;

/**
 * Stub: the real IBI accessibility-score sandbox feature (org.opentripplanner.ext.*) is out of
 * scope for the embedded otp-routing slice. Returns itineraries undecorated (no accessibility
 * score attached), matching {@code Leg#accessibilityScore()}'s existing "null means not
 * computed" default.
 */
public class DecorateWithAccessibilityScore implements ItineraryDecorator {

  public DecorateWithAccessibilityScore(double wheelchairMaxSlope) {}

  @Override
  public Itinerary decorate(Itinerary itinerary) {
    return itinerary;
  }
}
