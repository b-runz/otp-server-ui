// STUB: not upstream OTP code
package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import org.opentripplanner.ext.flex.FlexAccessEgress;
import org.opentripplanner.framework.model.TimeAndCost;

/**
 * Stub: replaces the upstream flex access/egress raptor adapter, which depends on the excluded
 * {@code org.opentripplanner.ext.flex} package. Keeps the public signature
 * {@code AccessEgressMapper.mapFlexAccessEgresses} calls; since
 * {@code FlexAccessEgressRouter} always returns an empty collection, this constructor is never
 * actually invoked.
 */
public class FlexAccessEgressAdapter extends DefaultAccessEgress {

  public FlexAccessEgressAdapter(FlexAccessEgress flexAccessEgress) {
    super(flexAccessEgress.stop().getIndex(), flexAccessEgress.lastState());
  }

  private FlexAccessEgressAdapter(FlexAccessEgressAdapter other, TimeAndCost penalty) {
    super(other, penalty);
  }

  @Override
  public boolean hasOpeningHours() {
    return true;
  }

  @Override
  public boolean isWalkOnly() {
    return false;
  }

  @Override
  public RoutingAccessEgress withPenalty(TimeAndCost penalty) {
    return new FlexAccessEgressAdapter(this, penalty);
  }
}
