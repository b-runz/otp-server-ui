// STUB: not upstream OTP code
package org.opentripplanner.ext.flex.trip;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * Stub: the real flex-trip hierarchy (org.opentripplanner.ext.flex.*) is out of scope for the
 * embedded otp-routing slice. Only the members {@code TransitRepository} and
 * {@code TransitRepositoryIndex} call are provided: nothing in this slice ever constructs a
 * {@code FlexTrip} (the {@code FlexIndex} stub's collections are always empty), so instances
 * are never actually created.
 */
public class FlexTrip<T, B> {

  private final FeedScopedId id;
  private final Trip trip;

  public FlexTrip(FeedScopedId id, Trip trip) {
    this.id = id;
    this.trip = trip;
  }

  public FeedScopedId getId() {
    return id;
  }

  public Trip getTrip() {
    return trip;
  }
}
