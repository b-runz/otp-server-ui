// STUB: not upstream OTP code
package org.opentripplanner.service.worldenvelope;

import java.io.Serializable;

/**
 * Stub of upstream's {@code WorldEnvelopeRepository} (which extends {@link Serializable} and
 * declares {@code retrieveEnvelope()}/{@code saveEnvelope(WorldEnvelope)}). Not one of those
 * methods is ever called by {@link org.opentripplanner.routing.graph.SerializedGraphObject} — it
 * is only held as a constructor-injected field — so this is an empty marker interface. A real
 * implementation would need the excluded world-envelope graph-builder slice.
 */
public interface WorldEnvelopeRepository extends Serializable {}
