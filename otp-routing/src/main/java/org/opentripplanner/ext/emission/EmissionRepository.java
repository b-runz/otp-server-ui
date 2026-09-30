// STUB: not upstream OTP code
package org.opentripplanner.ext.emission;

import java.io.Serializable;

/**
 * Stub of upstream's {@code EmissionRepository} (which extends {@link Serializable} and declares
 * several emission-lookup/accumulation methods). Not one of those methods is ever called by
 * {@link org.opentripplanner.routing.graph.SerializedGraphObject} — it is only held as a
 * {@code @Nullable} constructor-injected field — so this is an empty marker interface. A real
 * implementation would need the excluded emission sandbox slice.
 */
public interface EmissionRepository extends Serializable {}
