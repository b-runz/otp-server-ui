// STUB: not upstream OTP code
package org.opentripplanner.ext.flex;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitRepository;

/**
 * Stub: the real flex index (org.opentripplanner.ext.flex.*) is out of scope for the embedded
 * otp-routing slice. Only the members {@code TransitRepository}, {@code TransitRepositoryIndex}
 * and {@code DefaultTransitService} call are provided, all reporting "no flex trips/routes".
 */
public class FlexIndex {

  public FlexIndex(TransitRepository transitRepository) {}

  public boolean contains(Route route) {
    return false;
  }

  public Collection<Route> findRoutes(StopLocation stop) {
    return List.of();
  }

  public Collection<Route> getAllFlexRoutes() {
    return List.of();
  }

  public Collection<FlexTrip<?, ?>> getAllFlexTrips() {
    return List.of();
  }
}
