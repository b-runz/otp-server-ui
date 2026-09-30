// STUB: not upstream OTP code
package org.opentripplanner.ext.flex.edgetype;

import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.State;

/**
 * Stub: the real flex-trip street edge (org.opentripplanner.ext.flex.*) is out of scope for the
 * embedded otp-routing slice. Nothing in this slice constructs a {@code FlexTripEdge} (flex graph
 * building is excluded); {@code StreetPathToLegsMapper} only casts to this type after checking
 * {@code instanceof}, which never matches since no such edge is ever added to the graph.
 */
public class FlexTripEdge extends Edge {

  private final FeedScopedId fromStopId;
  private final FeedScopedId toStopId;

  public FlexTripEdge(Vertex v1, Vertex v2, FeedScopedId fromStopId, FeedScopedId toStopId) {
    super(v1, v2);
    this.fromStopId = fromStopId;
    this.toStopId = toStopId;
  }

  public FeedScopedId fromStopId() {
    return fromStopId;
  }

  public FeedScopedId toStopId() {
    return toStopId;
  }

  @Override
  public I18NString getName() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public State[] traverse(State s0) {
    throw new UnsupportedOperationException("stub");
  }
}
