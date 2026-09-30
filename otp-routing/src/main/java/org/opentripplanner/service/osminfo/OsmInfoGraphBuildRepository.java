// STUB: not upstream OTP code
package org.opentripplanner.service.osminfo;

import java.io.Serializable;

/**
 * Stub of upstream's {@code OsmInfoGraphBuildRepository} (which extends {@link Serializable} and
 * declares {@code addPlatform(...)}, {@code addTurnRestriction(...)}, {@code findPlatform(...)},
 * {@code listTurnRestrictions()}). Not one of those methods is ever called by
 * {@link org.opentripplanner.routing.graph.SerializedGraphObject} — it is only held as a
 * {@code @Nullable} constructor-injected field — so this is an empty marker interface. A real
 * implementation would need the excluded OSM graph-builder slice.
 */
public interface OsmInfoGraphBuildRepository extends Serializable {}
