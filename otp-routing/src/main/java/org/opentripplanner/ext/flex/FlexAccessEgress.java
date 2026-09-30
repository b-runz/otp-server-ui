// STUB: not upstream OTP code
package org.opentripplanner.ext.flex;

import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.site.RegularStop;

/**
 * Stub: the real flex access/egress result (org.opentripplanner.ext.flex.*) is out of scope for
 * the embedded otp-routing slice. Nothing in this slice constructs a {@code FlexAccessEgress}
 * (the {@code FlexAccessEgressRouter} stub always returns an empty collection); this preserves
 * only the accessors {@code FlexAccessEgressAdapter} calls.
 */
public class FlexAccessEgress {

  public RegularStop stop() {
    throw new UnsupportedOperationException("stub");
  }

  public State lastState() {
    throw new UnsupportedOperationException("stub");
  }

  public boolean stopReachedOnBoard() {
    return false;
  }

  public int earliestDepartureTime(int requestedDepartureTime) {
    throw new UnsupportedOperationException("stub");
  }

  public int latestArrivalTime(int requestedArrivalTime) {
    throw new UnsupportedOperationException("stub");
  }
}
