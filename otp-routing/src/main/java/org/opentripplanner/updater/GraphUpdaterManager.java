// STUB: not upstream OTP code
package org.opentripplanner.updater;

import java.util.List;
import java.util.Map;

/**
 * Stub: the real graph-updater lifecycle (starting/stopping updater threads, write-domain
 * callbacks) is out of scope for the embedded otp-routing slice. This preserves the field/method
 * shape that {@code TransitRepository} and {@code DefaultTransitService} need so that a
 * "no updaters configured" instance can be stored and returned.
 * <p>
 * Task 6 can instantiate this with the public no-argument constructor.
 */
public class GraphUpdaterManager implements GraphUpdaterStatus {

  public GraphUpdaterManager() {}

  @Override
  public int numberOfUpdaters() {
    return 0;
  }

  @Override
  public List<String> listUnprimedUpdaters() {
    return List.of();
  }

  @Override
  public Map<Integer, String> getUpdaterDescriptions() {
    return Map.of();
  }

  @Override
  public Class<?> getUpdaterClass(int id) {
    throw new UnsupportedOperationException("stub");
  }
}
