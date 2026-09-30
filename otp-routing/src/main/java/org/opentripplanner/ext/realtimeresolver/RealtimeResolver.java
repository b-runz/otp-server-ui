// STUB: not upstream OTP code
package org.opentripplanner.ext.realtimeresolver;

import java.util.List;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.services.TransitAlertService;
import org.opentripplanner.transit.service.TransitService;

/**
 * Stub: the real realtime-resolver sandbox feature (org.opentripplanner.ext.realtimeresolver.*)
 * is out of scope for the embedded otp-routing slice. Returns itineraries unchanged, i.e.
 * "no additional realtime info applied" (this code path only runs when the request already
 * chose to ignore realtime updates during the main search).
 */
public class RealtimeResolver {

  public static List<Itinerary> populateLegsWithRealtime(
    List<Itinerary> itineraries,
    TransitService transitService,
    TransitAlertService transitAlertService
  ) {
    return itineraries;
  }
}
