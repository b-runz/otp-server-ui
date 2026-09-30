// STUB: not upstream OTP code
package org.opentripplanner.ext.carpooling.routing;

import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress;
import org.opentripplanner.street.search.state.State;

/**
 * Stub: the real carpooling raptor access/egress adapter (org.opentripplanner.ext.carpooling.*)
 * is out of scope for the embedded otp-routing slice. Nothing in this slice constructs a
 * {@code CarpoolAccessEgress} (the {@link CarpoolingService} stub always returns an empty list),
 * so only enough structure to satisfy {@code instanceof}/type checks in
 * {@code RaptorPathToItineraryMapper} is provided.
 */
public class CarpoolAccessEgress implements RoutingAccessEgress {

  @Override
  public int stop() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public int c1() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public int durationInSeconds() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public boolean hasOpeningHours() {
    return false;
  }

  @Override
  public RoutingAccessEgress withPenalty(TimeAndCost penalty) {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public State getFinalState() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public boolean isWalkOnly() {
    return false;
  }

  @Override
  public TimeAndCost penalty() {
    return TimeAndCost.ZERO;
  }
}
