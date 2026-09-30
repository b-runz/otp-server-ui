// STUB: not upstream OTP code
package org.opentripplanner.ext.empiricaldelay;

import java.io.Serializable;

/**
 * Stub of upstream's {@code EmpiricalDelayRepository} (which extends {@link Serializable} and
 * declares {@code findEmpiricalDelay(...)}, {@code addEmpiricalDelayServiceCalendar(...)},
 * {@code addTripDelays(...)}, {@code summary()}). Not one of those methods is ever called by
 * {@link org.opentripplanner.routing.graph.SerializedGraphObject} — it is only held as a
 * {@code @Nullable} constructor-injected field — so this is an empty marker interface. A real
 * implementation would need the excluded empirical-delay sandbox slice.
 */
public interface EmpiricalDelayRepository extends Serializable {}
