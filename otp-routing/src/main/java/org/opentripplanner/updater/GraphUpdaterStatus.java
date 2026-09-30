// STUB: not upstream OTP code
package org.opentripplanner.updater;

import java.util.List;
import java.util.Map;

/**
 * This is a read-only API for a GraphUpdaterManager, that can be safely used in the APIs to query
 * the state of the updaters.
 * <p>
 * Stub: the real graph-updater subsystem (org.opentripplanner.updater.*) is out of scope for the
 * embedded otp-routing slice. This preserves the upstream interface shape so that
 * {@code DefaultTransitService}/{@code TransitService}/{@code TransitRepository} compile.
 */
public interface GraphUpdaterStatus {
  int numberOfUpdaters();

  List<String> listUnprimedUpdaters();

  Map<Integer, String> getUpdaterDescriptions();

  Class<?> getUpdaterClass(int id);
}
