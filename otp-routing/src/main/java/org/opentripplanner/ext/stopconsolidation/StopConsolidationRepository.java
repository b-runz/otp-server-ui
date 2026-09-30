// STUB: not upstream OTP code
package org.opentripplanner.ext.stopconsolidation;

import java.io.Serializable;

/**
 * Stub of upstream's {@code StopConsolidationRepository} (which extends {@link Serializable} and
 * declares {@code addGroups(...)}/{@code groups()}). Not one of those methods is ever called by
 * {@link org.opentripplanner.routing.graph.SerializedGraphObject} — it is only held as a
 * constructor-injected field — so this is an empty marker interface. A real implementation would
 * need the excluded stop-consolidation sandbox slice.
 */
public interface StopConsolidationRepository extends Serializable {}
