// STUB: not upstream OTP code
package org.opentripplanner.ext.stopconsolidation;

/**
 * Stub: the real stop-consolidation sandbox feature (org.opentripplanner.ext.stopconsolidation.*)
 * is out of scope for the embedded otp-routing slice. Only {@link #isActive()} is called
 * anywhere in this slice ({@code RouteRequestToFilterChainMapper}); nothing implements this
 * interface here, so it is only ever seen as a (nullable) field.
 */
public interface StopConsolidationService {
  boolean isActive();
}
