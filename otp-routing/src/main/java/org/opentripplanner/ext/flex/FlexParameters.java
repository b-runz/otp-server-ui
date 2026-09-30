// STUB: not upstream OTP code
package org.opentripplanner.ext.flex;

import java.time.Duration;

/**
 * Stub: the real flex-routing sandbox feature (org.opentripplanner.ext.flex.*) is out of scope
 * for the embedded otp-routing slice. Only carried through as a parameter and, in
 * {@code DirectFlexRouter}/{@code FlexAccessEgressRouter}, queried for the access/egress walk
 * duration limits.
 */
public class FlexParameters {

  public FlexParameters() {}

  public Duration maxAccessWalkDuration() {
    return Duration.ZERO;
  }

  public Duration maxEgressWalkDuration() {
    return Duration.ZERO;
  }
}
